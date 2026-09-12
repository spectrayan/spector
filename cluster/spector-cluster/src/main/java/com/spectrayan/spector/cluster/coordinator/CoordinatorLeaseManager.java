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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages cell coordinator election and heartbeat renewal on a renewable lease (Req R1.2, R1.5, R1.6).
 *
 * <p>Key Invariants:
 * <ul>
 *   <li><b>Store-Enforced Expiry (Req R1.5):</b> Expiry is evaluated by the store's authoritative clock,
 *       never by local clock comparison.</li>
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
    private final ScheduledExecutorService scheduler;

    private final AtomicBoolean isCoordinator = new AtomicBoolean(false);
    private final AtomicLong currentLeaseVersion = new AtomicLong(0L);
    private final AtomicLong leaseGeneration = new AtomicLong(0L);
    private final List<java.util.function.Consumer<String>> leaseLossListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final AtomicLong timeWithoutCoordinatorMs = new AtomicLong(0L);
    private final AtomicReference<Instant> lastLeaderSeen = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    public CoordinatorLeaseManager(
            ControlStore controlStore,
            String nodeId,
            Duration leaseDuration,
            Duration renewInterval) {
        this.controlStore = Objects.requireNonNull(controlStore, "controlStore must not be null");
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId must not be null");
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");
        this.renewInterval = Objects.requireNonNull(renewInterval, "renewInterval must not be null");
        this.lastLeaderSeen.set(controlStore.now());

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "spector-coordinator-lease-" + nodeId);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts the background lease acquisition and heartbeat renewal task.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            scheduler.scheduleWithFixedDelay(
                    this::heartbeat,
                    0L,
                    renewInterval.toMillis(),
                    TimeUnit.MILLISECONDS
            );
            log.info("[CoordinatorLeaseManager] Started coordinator lease manager for node '{}' (duration={}, renew={})",
                    nodeId, leaseDuration, renewInterval);
        }
    }

    /**
     * Executes a single heartbeat cycle to acquire, renew, or monitor the coordinator lease.
     */
    public void heartbeat() {
        try {
            Instant now = controlStore.now();
            java.util.Optional<CoordinatorLease> validLease = controlStore.getValidCoordinatorLease();

            if (validLease.isPresent()) {
                CoordinatorLease currentLease = validLease.get();
                lastLeaderSeen.set(now);
                timeWithoutCoordinatorMs.set(0L);

                if (Objects.equals(currentLease.holderNodeId(), nodeId)) {
                    // We are current holder -> renew
                    java.util.Optional<CoordinatorLease> renewed = controlStore.acquireOrRenewCoordinatorLease(nodeId, leaseDuration);
                    if (renewed.isPresent()) {
                        currentLeaseVersion.set(renewed.get().leaseVersion());
                        isCoordinator.set(true);
                        log.debug("[CoordinatorLeaseManager] Renewed coordinator lease for node '{}' (v{}) until {}",
                                nodeId, renewed.get().leaseVersion(), renewed.get().expiresAt());
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
                java.util.Optional<CoordinatorLease> acquired = controlStore.acquireOrRenewCoordinatorLease(nodeId, leaseDuration);
                if (acquired.isPresent()) {
                    CoordinatorLease lease = acquired.get();
                    currentLeaseVersion.set(lease.leaseVersion());
                    boolean wasCoordinator = isCoordinator.getAndSet(true);
                    lastLeaderSeen.set(now);
                    timeWithoutCoordinatorMs.set(0L);
                    if (!wasCoordinator) {
                        leaseGeneration.incrementAndGet();
                        log.info("[CoordinatorLeaseManager] Node '{}' successfully elected as cell coordinator (lease v{})",
                                nodeId, lease.leaseVersion());
                    }
                } else {
                    handleLeaseLoss("Contended acquisition failed");
                    updateLeaderlessDuration(now);
                }
            }
        } catch (Exception e) {
            log.error("[CoordinatorLeaseManager] Error during coordinator heartbeat for node '{}'", nodeId, e);
            handleLeaseLoss("Exception during heartbeat: " + e.getMessage());
        }
    }

    /**
     * Store-enforced guard verifying that this node actively holds a valid, non-expired lease (Req R1.5, R1.6, G17).
     *
     * @return {@code true} if verified coordinator in the store; {@code false} if lease expired or lost
     */
    public boolean checkStoreEnforcedLeaseActive() {
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
            log.warn("[CoordinatorLeaseManager] Node '{}' ceased being cell coordinator: {}", nodeId, reason);
            for (java.util.function.Consumer<String> listener : leaseLossListeners) {
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
     * Checks if this node currently considers itself the elected coordinator.
     */
    public boolean isCoordinator() {
        return isCoordinator.get();
    }

    /**
     * Returns the active coordinator lease version, or 0 if uncoordinated (Req R1.5, G16).
     */
    public long getLeaseVersion() {
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
    public void addLeaseLossListener(java.util.function.Consumer<String> listener) {
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
        if (running.compareAndSet(true, false)) {
            log.info("[CoordinatorLeaseManager] Shutting down coordinator lease manager for node '{}'", nodeId);
            if (isCoordinator.get()) {
                try {
                    controlStore.releaseCoordinatorLease(nodeId);
                } catch (Exception e) {
                    log.warn("[CoordinatorLeaseManager] Failed to explicitly release coordinator lease for node '{}'", nodeId, e);
                }
                isCoordinator.set(false);
            }
            scheduler.shutdownNow();
        }
    }
}
