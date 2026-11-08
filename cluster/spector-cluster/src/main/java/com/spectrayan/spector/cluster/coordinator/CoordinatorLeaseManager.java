/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.cluster.coordinator;

import com.spectrayan.spector.cluster.store.ControlStore;
import com.spectrayan.spector.cluster.store.CoordinatorLease;
import com.spectrayan.spector.cluster.store.FileControlStore;
import com.spectrayan.spector.cluster.store.InMemoryControlStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Manages cell coordinator election and heartbeat renewal on a renewable lease (Req R1.2, R1.5, R1.6).
 *
 * <p>Key Invariants:
 * <ul>
 *   <li><b>Store-Enforced Expiry (Req R1.5):</b> Expiry is evaluated by the store's authoritative clock.</li>
 *   <li><b>Local Monotonic Self-Fence (#1195):</b> Each acquire/renew records a local monotonic deadline
 *       ({@code requestStartNanos + leaseDuration - safetyMargin}). Once that deadline passes,
 *       {@link #isCoordinator()} immediately self-fences and returns {@code false} regardless of
 *       heartbeat thread state, and late renewal responses arriving after the deadline are discarded.</li>
 *   <li><b>Immediate Stop (Req R1.6):</b> A node that loses the lease ceases coordinator mutation immediately.</li>
 *   <li><b>Leaderless Resilience (Q5, Req R1.4):</b> Losing the coordinator degrades to "no new failovers";
 *       existing routing and ownership remain active and consistent.</li>
 * </ul>
 * </p>
 */
public class CoordinatorLeaseManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CoordinatorLeaseManager.class);

    private final ControlStore controlStore;
    private final String nodeId;
    private final Duration leaseDuration;
    private final Duration renewInterval;
    private final Duration safetyMargin;
    private final Duration localValidityWindow;
    private final Duration storeCallTimeout;
    private final LongSupplier nanoClock;
    private final boolean customNanoClock;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService storeCallExecutor;

    private final AtomicBoolean isCoordinator = new AtomicBoolean(false);
    private final AtomicLong currentLeaseVersion = new AtomicLong(0L);
    private final AtomicLong leaseGeneration = new AtomicLong(0L);
    private final AtomicLong localLeaseDeadlineNanos = new AtomicLong(0L);
    private final AtomicReference<Instant> localLeaseDeadlineInstant = new AtomicReference<>();
    private final List<Consumer<String>> leaseLossListeners = new CopyOnWriteArrayList<>();
    private final AtomicLong timeWithoutCoordinatorMs = new AtomicLong(0L);
    private final AtomicReference<Instant> lastLeaderSeen = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public CoordinatorLeaseManager(
            ControlStore controlStore,
            String nodeId,
            Duration leaseDuration,
            Duration renewInterval) {
        this(
                controlStore,
                nodeId,
                leaseDuration,
                renewInterval,
                defaultSafetyMargin(leaseDuration, renewInterval),
                defaultStoreCallTimeout(renewInterval),
                System::nanoTime,
                false);
    }

    public CoordinatorLeaseManager(
            ControlStore controlStore,
            String nodeId,
            Duration leaseDuration,
            Duration renewInterval,
            LongSupplier nanoClock) {
        this(
                controlStore,
                nodeId,
                leaseDuration,
                renewInterval,
                defaultSafetyMargin(leaseDuration, renewInterval),
                defaultStoreCallTimeout(renewInterval),
                nanoClock,
                true);
    }

    public CoordinatorLeaseManager(
            ControlStore controlStore,
            String nodeId,
            Duration leaseDuration,
            Duration renewInterval,
            Duration safetyMargin,
            LongSupplier nanoClock) {
        this(
                controlStore,
                nodeId,
                leaseDuration,
                renewInterval,
                safetyMargin,
                defaultStoreCallTimeout(renewInterval),
                nanoClock,
                true);
    }

    public CoordinatorLeaseManager(
            ControlStore controlStore,
            String nodeId,
            Duration leaseDuration,
            Duration renewInterval,
            Duration safetyMargin,
            Duration storeCallTimeout,
            LongSupplier nanoClock) {
        this(
                controlStore,
                nodeId,
                leaseDuration,
                renewInterval,
                safetyMargin,
                storeCallTimeout,
                nanoClock,
                true);
    }

    private CoordinatorLeaseManager(
            ControlStore controlStore,
            String nodeId,
            Duration leaseDuration,
            Duration renewInterval,
            Duration safetyMargin,
            Duration storeCallTimeout,
            LongSupplier nanoClock,
            boolean customNanoClock) {
        this.controlStore = Objects.requireNonNull(controlStore, "controlStore must not be null");
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId must not be null");
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");
        this.renewInterval = Objects.requireNonNull(renewInterval, "renewInterval must not be null");
        this.safetyMargin = safetyMargin != null && !safetyMargin.isNegative()
                ? safetyMargin
                : defaultSafetyMargin(leaseDuration, renewInterval);
        Duration validity = leaseDuration.minus(this.safetyMargin);
        this.localValidityWindow = validity.isNegative() || validity.isZero() ? Duration.ofMillis(1) : validity;
        this.storeCallTimeout = normalizeStoreCallTimeout(storeCallTimeout, renewInterval);
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock must not be null");
        this.customNanoClock = customNanoClock;
        this.lastLeaderSeen.set(controlStore.now());

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "spector-coordinator-lease-" + nodeId);
            t.setDaemon(true);
            return t;
        });
        this.storeCallExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("spector-coordinator-store-call-" + nodeId + "-", 0).factory());
    }

    private static Duration defaultSafetyMargin(Duration leaseDuration, Duration renewInterval) {
        Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");
        Objects.requireNonNull(renewInterval, "renewInterval must not be null");
        long tenPercentMs = Math.max(1L, leaseDuration.toMillis() / 10L);
        long halfGapMs = leaseDuration.compareTo(renewInterval) > 0
                ? Math.max(1L, leaseDuration.minus(renewInterval).toMillis() / 2L)
                : tenPercentMs;
        return Duration.ofMillis(Math.min(tenPercentMs, halfGapMs));
    }

    private static Duration defaultStoreCallTimeout(Duration renewInterval) {
        Objects.requireNonNull(renewInterval, "renewInterval must not be null");
        long renewMs = renewInterval.toMillis();
        if (renewMs <= 1L) {
            return Duration.ofMillis(1L);
        }
        long eightyPercentMs = Math.max(1L, (renewMs * 4L) / 5L);
        return Duration.ofMillis(Math.min(renewMs - 1L, eightyPercentMs));
    }

    private static Duration normalizeStoreCallTimeout(Duration candidate, Duration renewInterval) {
        Duration fallback = defaultStoreCallTimeout(renewInterval);
        if (candidate == null || candidate.isNegative() || candidate.isZero()) {
            return fallback;
        }
        if (renewInterval.toMillis() > 1L && candidate.compareTo(renewInterval) >= 0) {
            return fallback;
        }
        return candidate;
    }

    public Duration safetyMargin() {
        return safetyMargin;
    }

    public Duration storeCallTimeout() {
        return storeCallTimeout;
    }

    /**
     * Starts the background lease acquisition and heartbeat renewal task.
     */
    public void start() {
        if (closed.get()) {
            throw new IllegalStateException("CoordinatorLeaseManager has already been closed");
        }
        if (running.compareAndSet(false, true)) {
            scheduler.scheduleWithFixedDelay(
                    this::heartbeat,
                    0L,
                    renewInterval.toMillis(),
                    TimeUnit.MILLISECONDS
            );
            log.info("[CoordinatorLeaseManager] Started coordinator lease manager for node '{}' (duration={}, renew={}, safetyMargin={}, storeCallTimeout={})",
                    nodeId, leaseDuration, renewInterval, safetyMargin, storeCallTimeout);
        }
    }

    /**
     * Executes a single heartbeat cycle to acquire, renew, or monitor the coordinator lease.
     */
    public void heartbeat() {
        if (closed.get() || Thread.currentThread().isInterrupted()) {
            return;
        }
        try {
            // Check local self-fence before issuing store requests
            isCoordinator();

            Instant now = runStoreCall(controlStore::now);
            Optional<CoordinatorLease> validLease = runStoreCall(controlStore::getValidCoordinatorLease);

            if (validLease.isPresent()) {
                CoordinatorLease currentLease = validLease.get();
                lastLeaderSeen.set(now);
                timeWithoutCoordinatorMs.set(0L);

                if (Objects.equals(currentLease.holderNodeId(), nodeId)) {
                    // We are current holder -> renew and track local deadline from request start
                    long requestStartNanos = nanoClock.getAsLong();
                    long candidateDeadlineNanos = requestStartNanos + localValidityWindow.toNanos();
                    Instant candidateDeadlineInstant = now.plus(localValidityWindow);

                    Optional<CoordinatorLease> renewed = runStoreCall(
                            () -> controlStore.acquireOrRenewCoordinatorLease(nodeId, leaseDuration));
                    if (renewed.isPresent()) {
                        if (isPastDeadline(candidateDeadlineNanos, candidateDeadlineInstant)) {
                            handleLeaseLoss("Discarded renewal response arriving after local lease deadline");
                        } else {
                            CoordinatorLease lease = renewed.get();
                            localLeaseDeadlineNanos.set(candidateDeadlineNanos);
                            localLeaseDeadlineInstant.set(candidateDeadlineInstant);
                            currentLeaseVersion.set(lease.leaseVersion());
                            isCoordinator.set(true);
                            log.debug("[CoordinatorLeaseManager] Renewed coordinator lease for node '{}' (v{}) until {}",
                                    nodeId, lease.leaseVersion(), lease.expiresAt());
                        }
                    } else {
                        handleLeaseLoss("Renewal rejected by store");
                    }
                } else {
                    // Another node holds valid lease
                    if (isCoordinator.get()) {
                        handleLeaseLoss("Lease held by other node: " + currentLease.holderNodeId());
                    }
                }
            } else {
                // No active lease -> attempt acquisition
                long requestStartNanos = nanoClock.getAsLong();
                long candidateDeadlineNanos = requestStartNanos + localValidityWindow.toNanos();
                Instant candidateDeadlineInstant = now.plus(localValidityWindow);

                Optional<CoordinatorLease> acquired = runStoreCall(
                        () -> controlStore.acquireOrRenewCoordinatorLease(nodeId, leaseDuration));
                if (acquired.isPresent()) {
                    if (isPastDeadline(candidateDeadlineNanos, candidateDeadlineInstant)) {
                        handleLeaseLoss("Discarded acquisition response arriving after local lease deadline");
                    } else {
                        CoordinatorLease lease = acquired.get();
                        localLeaseDeadlineNanos.set(candidateDeadlineNanos);
                        localLeaseDeadlineInstant.set(candidateDeadlineInstant);
                        currentLeaseVersion.set(lease.leaseVersion());
                        boolean wasCoordinator = isCoordinator.getAndSet(true);
                        lastLeaderSeen.set(now);
                        timeWithoutCoordinatorMs.set(0L);
                        if (!wasCoordinator) {
                            leaseGeneration.incrementAndGet();
                            log.info("[CoordinatorLeaseManager] Node '{}' successfully elected as cell coordinator (lease v{})",
                                    nodeId, lease.leaseVersion());
                        }
                    }
                } else {
                    handleLeaseLoss("Contended acquisition failed");
                    updateLeaderlessDuration(now);
                }
            }
        } catch (Exception e) {
            if (closed.get() || Thread.currentThread().isInterrupted()) {
                log.debug("[CoordinatorLeaseManager] Heartbeat cancelled during shutdown for node '{}'", nodeId);
                return;
            }
            log.error("[CoordinatorLeaseManager] Error during coordinator heartbeat for node '{}'", nodeId, e);
            handleLeaseLoss("Exception during heartbeat: " + e.getMessage());
        }
    }

    private <T> T runStoreCall(Callable<T> call) throws Exception {
        Future<T> future = storeCallExecutor.submit(call);
        try {
            return future.get(storeCallTimeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new TimeoutException("Control store call timed out after " + storeCallTimeout);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw new RuntimeException(cause);
        }
    }

    private boolean isPastDeadline(long deadlineNanos, Instant deadlineInstant) {
        if (nanoClock.getAsLong() - deadlineNanos >= 0) {
            return true;
        }
        if (!customNanoClock && isSyntheticClockStore(controlStore) && deadlineInstant != null) {
            return !controlStore.now().isBefore(deadlineInstant);
        }
        return false;
    }

    private static boolean isSyntheticClockStore(ControlStore store) {
        return store instanceof InMemoryControlStore || store instanceof FileControlStore;
    }

    /**
     * Store-enforced guard verifying that this node actively holds a valid, non-expired lease (Req R1.5, R1.6, G17).
     *
     * @return {@code true} if verified coordinator in the store; {@code false} if lease expired or lost
     */
    public boolean checkStoreEnforcedLeaseActive() {
        if (!isCoordinator()) {
            return false;
        }
        long version = currentLeaseVersion.get();
        if (version <= 0 || !controlStore.isCoordinator(nodeId, version)) {
            if (isCoordinator.get()) {
                handleLeaseLoss("Store check confirmed lease inactive or held by another node");
            }
            return false;
        }
        return true;
    }

    private void handleLeaseLoss(String reason) {
        boolean wasCoordinator = isCoordinator.getAndSet(false);
        if (wasCoordinator) {
            leaseGeneration.incrementAndGet();
            currentLeaseVersion.set(0L);
            localLeaseDeadlineNanos.set(0L);
            localLeaseDeadlineInstant.set(null);
            log.warn("[CoordinatorLeaseManager] Node '{}' ceased being cell coordinator: {}", nodeId, reason);
            for (Consumer<String> listener : leaseLossListeners) {
                try {
                    listener.accept(reason);
                } catch (Exception e) {
                    log.error("[CoordinatorLeaseManager] Error invoking lease loss listener", e);
                }
            }
        }
    }

    private void updateLeaderlessDuration(Instant now) {
        Instant lastSeen = lastLeaderSeen.get();
        if (lastSeen != null && now.isAfter(lastSeen)) {
            timeWithoutCoordinatorMs.set(Duration.between(lastSeen, now).toMillis());
        }
    }

    /**
     * Checks if this node currently considers itself the elected coordinator, enforcing the local
     * monotonic lease deadline ({@code leaseDuration - safetyMargin}) on every check.
     */
    public boolean isCoordinator() {
        if (!isCoordinator.get()) {
            return false;
        }
        long deadlineNanos = localLeaseDeadlineNanos.get();
        Instant deadlineInstant = localLeaseDeadlineInstant.get();
        if (isPastDeadline(deadlineNanos, deadlineInstant)) {
            handleLeaseLoss("Local coordinator lease deadline expired (self-fenced)");
            return false;
        }
        return isCoordinator.get();
    }

    /**
     * Returns the active coordinator lease version, or 0 if uncoordinated (Req R1.5, G16).
     */
    public long getLeaseVersion() {
        if (!isCoordinator()) {
            return 0L;
        }
        return currentLeaseVersion.get();
    }

    /**
     * Returns the monotonic generation counter incremented on every coordinator transition (G23).
     */
    public long getLeaseGeneration() {
        return leaseGeneration.get();
    }

    /**
     * Registers a listener to be notified immediately when this node loses coordinator authority (G23).
     *
     * @param listener consumer receiving failure reason
     */
    public void addLeaseLossListener(Consumer<String> listener) {
        leaseLossListeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    /**
     * Returns the node ID of the current coordinator, or {@code null} if uncoordinated or expired.
     */
    public String getLeaderId() {
        return controlStore.getValidCoordinatorLease()
                .map(CoordinatorLease::holderNodeId)
                .orElse(null);
    }

    /**
     * Returns the elapsed time in milliseconds that the cell has been without an elected coordinator (Req R9.4).
     */
    public long getTimeWithoutCoordinatorMs() {
        return timeWithoutCoordinatorMs.get();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        running.set(false);
        log.info("[CoordinatorLeaseManager] Shutting down coordinator lease manager for node '{}'", nodeId);
        scheduler.shutdownNow();
        storeCallExecutor.shutdownNow();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("[CoordinatorLeaseManager] Scheduler did not terminate cleanly within 5s for node '{}'", nodeId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (isCoordinator.get()) {
            try {
                controlStore.releaseCoordinatorLease(nodeId);
            } catch (Exception e) {
                log.warn("[CoordinatorLeaseManager] Failed to explicitly release coordinator lease for node '{}'", nodeId, e);
            }
            isCoordinator.set(false);
            currentLeaseVersion.set(0L);
            localLeaseDeadlineNanos.set(0L);
            localLeaseDeadlineInstant.set(null);
        }
    }

    boolean isTerminated() {
        return scheduler.isTerminated();
    }
}
