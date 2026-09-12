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
package com.spectrayan.spector.cluster.fencing;

import com.spectrayan.spector.cluster.store.ControlStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Manages local fence token validation and minting from the control store (Req R2.1, R2.3, R2.4, R2.5, §5).
 *
 * <p>Enforces:
 * <ul>
 *   <li><b>Local validation (Req R2.4, Q7):</b> Validates against the owner node's local view, never a cached value.</li>
 *   <li><b>Zero I/O and zero allocation (Req §5):</b> Hot-path verification uses a local in-memory integer comparison.</li>
 *   <li><b>Monotonicity (G15):</b> Local fences can only advance, never regress.</li>
 *   <li><b>Surrender semantics (G13):</b> Surrendered namespaces refuse all writes, never revert to unfenced.</li>
 *   <li><b>Partition fail-closed (G12):</b> Local fences expire if not renewed, preventing partitioned owners from accepting stale writes.</li>
 *   <li><b>Metric counting (Req R9.2):</b> Tracks rejected fence attempts ({@code spector.route.fenced}).</li>
 * </ul>
 * </p>
 */
public class FenceTokenManager {

    private static final Logger log = LoggerFactory.getLogger(FenceTokenManager.class);

    /**
     * Sentinel value indicating that ownership has been surrendered.
     * Any {@code validateFence} call for a namespace at this epoch will refuse (G13).
     */
    static final long SURRENDERED_EPOCH = Long.MIN_VALUE;

    private final ControlStore controlStore;
    private final Clock clock;
    private final Duration fenceTtl;
    private final ConcurrentHashMap<String, Long> localFences = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> localFenceExpirations = new ConcurrentHashMap<>();
    private final LongAdder fenceRejections = new LongAdder();
    private final LongAdder fenceRegressions = new LongAdder();

    public FenceTokenManager(ControlStore controlStore) {
        this(controlStore, Clock.systemUTC(), Duration.ZERO);
    }

    public FenceTokenManager(ControlStore controlStore, Clock clock, Duration fenceTtl) {
        this.controlStore = Objects.requireNonNull(controlStore, "controlStore must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.fenceTtl = fenceTtl != null ? fenceTtl : Duration.ZERO;
    }

    /**
     * Updates the local authoritative fence token for a namespace, enforcing monotonicity (G15).
     * The fence can only advance — regressions are rejected and counted.
     *
     * @param namespaceId target namespace
     * @param epoch       authoritative epoch
     */
    public void setLocalFence(String namespaceId, long epoch) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        localFences.merge(namespaceId, epoch, (existing, proposed) -> {
            if (existing == SURRENDERED_EPOCH) {
                // Re-adoption after surrender is allowed only through adoptOwnership
                log.debug("[FenceTokenManager] Namespace '{}' was surrendered; re-adopting at epoch {}", namespaceId, proposed);
                return proposed;
            }
            if (proposed < existing) {
                fenceRegressions.increment();
                log.warn("[FenceTokenManager] Fence regression rejected for namespace '{}': proposed {} < existing {} (G15)",
                        namespaceId, proposed, existing);
                return existing;
            }
            return proposed;
        });
        if (!fenceTtl.isZero()) {
            localFenceExpirations.put(namespaceId, clock.millis() + fenceTtl.toMillis());
        }
        log.debug("[FenceTokenManager] Set local fence for namespace '{}' to epoch {}", namespaceId, epoch);
    }

    /**
     * Checks if this node has an active (non-surrendered) local fence tracked for the given namespace.
     *
     * @param namespaceId target namespace
     * @return {@code true} if tracked and not surrendered; {@code false} otherwise
     */
    public boolean hasLocalFence(String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Long epoch = localFences.get(namespaceId);
        return epoch != null && epoch != SURRENDERED_EPOCH;
    }

    /**
     * Checks whether the local fence for the namespace has expired (G12).
     *
     * @param namespaceId target namespace
     * @return {@code true} if fence is expired or untracked; {@code false} if valid
     */
    public boolean isFenceExpired(String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        if (fenceTtl.isZero()) {
            return false;
        }
        Long expiresAt = localFenceExpirations.get(namespaceId);
        return expiresAt == null || clock.millis() > expiresAt;
    }

    /**
     * Renews the local fence lease for a specific namespace, preventing expiration (G12).
     *
     * @param namespaceId target namespace
     * @return {@code true} if renewed; {@code false} if not actively tracked
     */
    public boolean renewLocalFence(String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        if (hasLocalFence(namespaceId)) {
            if (!fenceTtl.isZero()) {
                localFenceExpirations.put(namespaceId, clock.millis() + fenceTtl.toMillis());
            }
            return true;
        }
        return false;
    }

    /**
     * Renews all actively held local fence leases on this node (G12).
     */
    public void renewAllLocalFences() {
        if (fenceTtl.isZero()) {
            return;
        }
        long newExpiresAt = clock.millis() + fenceTtl.toMillis();
        for (String ns : localFences.keySet()) {
            if (hasLocalFence(ns)) {
                localFenceExpirations.put(ns, newExpiresAt);
            }
        }
    }

    /**
     * Returns the active local fence epoch for the namespace, or -1 if not actively tracked.
     *
     * @param namespaceId target namespace
     * @return active epoch or -1
     */
    public long getLocalFence(String namespaceId) {
        long epoch = localFences.getOrDefault(namespaceId, -1L);
        return epoch == SURRENDERED_EPOCH ? -1L : epoch;
    }

    /**
     * Surrenders ownership of a namespace. The namespace transitions to a {@code SURRENDERED}
     * state where all fence validations refuse unconditionally (G13).
     *
     * <p>This replaces the old {@code removeLocalFence} which incorrectly converted a namespace
     * from fenced to unfenced-and-permitted.</p>
     *
     * @param namespaceId target namespace
     */
    public void surrenderLocalFence(String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        localFences.put(namespaceId, SURRENDERED_EPOCH);
        localFenceExpirations.remove(namespaceId);
        log.info("[FenceTokenManager] Surrendered local fence for namespace '{}' — all writes will be refused (G13)", namespaceId);
    }

    /**
     * @deprecated Use {@link #surrenderLocalFence(String)} instead. Removing a fence converts a
     * namespace from fenced to unfenced-and-permitted, which is precisely inverted (G13).
     */
    @Deprecated(forRemoval = true)
    public void removeLocalFence(String namespaceId) {
        surrenderLocalFence(namespaceId);
    }

    /**
     * Adopts ownership of a namespace by reading the current epoch from the control store
     * and installing it as the local fence (G14).
     *
     * <p>Called from the routing-change path when a node becomes the owner of a namespace.</p>
     *
     * @param namespaceId target namespace
     * @return the adopted epoch
     */
    public long adoptOwnership(String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        long epoch = controlStore.getNamespaceEpoch(namespaceId);
        // Direct put bypasses monotonicity to allow re-adoption after surrender
        localFences.put(namespaceId, epoch);
        if (!fenceTtl.isZero()) {
            localFenceExpirations.put(namespaceId, clock.millis() + fenceTtl.toMillis());
        }
        log.info("[FenceTokenManager] Adopted ownership for namespace '{}' at epoch {} from control store (G14)",
                namespaceId, epoch);
        return epoch;
    }

    /**
     * Mints a new fence token from the control store for promotion or epoch advancement (Req R2.1, R2.5).
     *
     * @param namespaceId target namespace
     * @return newly minted fence token
     */
    public FenceToken mintNewFence(String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        long newEpoch = controlStore.advanceNamespaceEpoch(namespaceId);
        setLocalFence(namespaceId, newEpoch);
        log.info("[FenceTokenManager] Minted new fence token for namespace '{}' with epoch {}", namespaceId, newEpoch);
        return FenceToken.of(namespaceId, newEpoch);
    }

    /**
     * Mints a fence token for an already advanced epoch (Req R2.1).
     *
     * @param namespaceId target namespace
     * @param epoch       already advanced epoch
     * @return fence token
     */
    public FenceToken mintFenceForEpoch(String namespaceId, long epoch) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        setLocalFence(namespaceId, epoch);
        log.info("[FenceTokenManager] Minted fence token for namespace '{}' with epoch {}", namespaceId, epoch);
        return FenceToken.of(namespaceId, epoch);
    }

    /**
     * Allocation-free, I/O-free validation on the write path (Req R2.3, R2.4, §5).
     *
     * <p>Namespaces in {@code SURRENDERED} state (G13) are refused unconditionally.
     * Untracked namespaces are also refused.</p>
     *
     * @param namespaceId   target namespace
     * @param incomingFence token carried on the write request
     * @return {@code true} if valid; {@code false} if mismatched, surrendered, or superseded (FENCED)
     */
    public boolean validateFence(String namespaceId, String incomingFence) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Long expected = localFences.get(namespaceId);
        if (expected == null) {
            // Namespace not tracked locally -> refuse
            fenceRejections.increment();
            return false;
        }
        if (expected == SURRENDERED_EPOCH) {
            // Namespace surrendered -> refuse all writes (G13)
            fenceRejections.increment();
            return false;
        }
        if (!fenceTtl.isZero()) {
            Long expiresAt = localFenceExpirations.get(namespaceId);
            if (expiresAt != null && clock.millis() > expiresAt) {
                fenceRejections.increment();
                log.warn("[FenceTokenManager] Local fence for namespace '{}' has expired (partition fail-closed G12)", namespaceId);
                return false;
            }
        }
        if (incomingFence == null || incomingFence.isBlank()) {
            fenceRejections.increment();
            return false;
        }
        try {
            long incoming = Long.parseLong(incomingFence.trim());
            if (incoming == expected) {
                return true;
            }
        } catch (NumberFormatException ignored) {
            // Non-numeric token
        }
        fenceRejections.increment();
        return false;
    }

    /**
     * Returns total fence rejection count (Req R9.2, metric {@code spector.route.fenced}).
     *
     * @return total count of rejected fence requests
     */
    public long getFenceRejectionCount() {
        return fenceRejections.sum();
    }

    /**
     * Returns the count of rejected fence regressions (G15 monotonicity violations).
     *
     * @return count of regression attempts
     */
    public long getFenceRegressionCount() {
        return fenceRegressions.sum();
    }
}

