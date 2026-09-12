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
package com.spectrayan.spector.cluster.routing;

import com.spectrayan.spector.cluster.coordinator.CoordinatorLeaseManager;
import com.spectrayan.spector.cluster.store.ControlStore;
import com.spectrayan.spector.cluster.store.OverrideLeaseRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Manages explicit namespace override leases pinning namespaces to survivor or target nodes (Req R3.1–R3.6).
 *
 * <p>Enforces:
 * <ul>
 *   <li><b>Precedence over Hash (Req R3.1):</b> Active override leases supersede Ketama hash ring assignment.</li>
 *   <li><b>Coordinator Authority (Req R3.2, Q4):</b> Only the elected coordinator may create, renew, or delete overrides.</li>
 *   <li><b>TTL and Expiration (Req R3.3):</b> Overrides expire after a bounded TTL to prevent permanent topology drift.</li>
 *   <li><b>Observability (Req R3.6):</b> Exposes active override count and maximum override age.</li>
 * </ul>
 * </p>
 */
public class OverrideLeaseManager {

    private static final Logger log = LoggerFactory.getLogger(OverrideLeaseManager.class);

    private final ControlStore controlStore;
    private final CoordinatorLeaseManager coordinatorLeaseManager;
    private final Duration defaultTtl;

    public OverrideLeaseManager(ControlStore controlStore) {
        this(controlStore, null, Duration.ofSeconds(300));
    }

    public OverrideLeaseManager(
            ControlStore controlStore,
            CoordinatorLeaseManager coordinatorLeaseManager,
            Duration defaultTtl) {
        this.controlStore = Objects.requireNonNull(controlStore, "controlStore must not be null");
        this.coordinatorLeaseManager = coordinatorLeaseManager;
        this.defaultTtl = Objects.requireNonNull(defaultTtl, "defaultTtl must not be null");
    }

    /**
     * Retrieves the active override lease for the given namespace if present and not expired (Req R3.1, R3.3).
     *
     * @param namespaceId namespace identifier
     * @return optional containing active override record
     */
    public Optional<OverrideLeaseRecord> getOverride(String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        return controlStore.getOverride(namespaceId);
    }

    /**
     * Pins a namespace to a specific target node under coordinator authority (Req R3.1, R3.2).
     *
     * @param namespaceId  namespace to pin
     * @param targetNodeId node designated as authoritative owner
     * @param fence        fence token string associated with this pin
     * @param ttl          time-to-live duration, or {@code null} to use default
     * @return the created override record
     */
    public OverrideLeaseRecord setOverride(String namespaceId, String targetNodeId, String fence, Duration ttl) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Objects.requireNonNull(targetNodeId, "targetNodeId must not be null");
        verifyCoordinatorAuthority();

        Duration effectiveTtl = ttl != null ? ttl : defaultTtl;
        long epoch = controlStore.advanceNamespaceEpoch(namespaceId);
        Instant expiresAt = controlStore.now().plus(effectiveTtl);

        OverrideLeaseRecord record = new OverrideLeaseRecord(namespaceId, targetNodeId, epoch, fence, expiresAt);
        controlStore.setOverride(record);
        log.info("[OverrideLeaseManager] Coordinator pinned namespace '{}' -> node '{}' (epoch={}, fence='{}', ttl={}s)",
                namespaceId, targetNodeId, epoch, fence, effectiveTtl.toSeconds());
        return record;
    }

    /**
     * Renews an existing active override lease under coordinator authority (Req R3.2, R3.3, R3.4).
     *
     * @param namespaceId namespace to renew
     * @param ttl         renewal duration, or {@code null} to use default
     * @return the renewed override record
     */
    public OverrideLeaseRecord renewOverride(String namespaceId, Duration ttl) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        verifyCoordinatorAuthority();

        Optional<OverrideLeaseRecord> existing = controlStore.getOverride(namespaceId);
        if (existing.isEmpty()) {
            throw new IllegalStateException("Cannot renew non-existent or expired override for namespace: " + namespaceId);
        }
        OverrideLeaseRecord current = existing.get();
        Duration effectiveTtl = ttl != null ? ttl : defaultTtl;
        Instant expiresAt = controlStore.now().plus(effectiveTtl);

        OverrideLeaseRecord renewed = new OverrideLeaseRecord(
                current.namespaceId(),
                current.targetNodeId(),
                current.epoch(),
                current.fence(),
                expiresAt,
                current.createdAt()
        );
        controlStore.setOverride(renewed);
        log.info("[OverrideLeaseManager] Coordinator renewed override for namespace '{}' -> node '{}' until {}",
                namespaceId, renewed.targetNodeId(), expiresAt);
        return renewed;
    }

    /**
     * Removes an active override lease under coordinator authority (Req R3.2).
     *
     * @param namespaceId namespace to release
     */
    public void removeOverride(String namespaceId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        verifyCoordinatorAuthority();

        controlStore.removeOverride(namespaceId);
        log.info("[OverrideLeaseManager] Coordinator removed override for namespace '{}'", namespaceId);
    }

    /**
     * Verifies whether the hash-default owner of a namespace is alive and ready before allowing
     * an override to safely expire or be explicitly removed (Req R3.4).
     *
     * @param namespaceId        namespace identifier
     * @param hashDefaultOwner   node designated by the consistent hash ring
     * @param isNodeAliveAndReady predicate evaluating whether candidate node is healthy and has data
     * @return {@code true} if safe to return to hash default owner; {@code false} if black-hole risk
     */
    public boolean canSafelyExpireOrRemove(
            String namespaceId,
            String hashDefaultOwner,
            java.util.function.Predicate<String> isNodeAliveAndReady) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Objects.requireNonNull(hashDefaultOwner, "hashDefaultOwner must not be null");
        Objects.requireNonNull(isNodeAliveAndReady, "isNodeAliveAndReady must not be null");
        return isNodeAliveAndReady.test(hashDefaultOwner);
    }

    /**
     * Lists all active non-expired override leases (Req R3.6).
     *
     * @return list of active override records
     */
    public List<OverrideLeaseRecord> listActiveOverrides() {
        return controlStore.listOverrides();
    }

    /**
     * Returns the count of active override leases (Req R3.6).
     *
     * @return active override count
     */
    public int getActiveOverrideCount() {
        return controlStore.listOverrides().size();
    }

    /**
     * Returns the maximum age among all currently active override leases (Req R3.6).
     *
     * @return maximum override age duration, or {@link Duration#ZERO} if no overrides active
     */
    public Duration getMaxOverrideAge() {
        Instant now = controlStore.now();
        return controlStore.listOverrides().stream()
                .map(record -> record.age(now))
                .max(Duration::compareTo)
                .orElse(Duration.ZERO);
    }

    /**
     * Returns the age of the active override lease for the given namespace (Req R3.6).
     *
     * @param namespaceId namespace identifier
     * @return optional containing duration elapsed since override creation
     */
    public Optional<Duration> getOverrideAge(String namespaceId) {
        Instant now = controlStore.now();
        return controlStore.getOverride(namespaceId)
                .map(record -> record.age(now));
    }

    private void verifyCoordinatorAuthority() {
        if (coordinatorLeaseManager != null && !coordinatorLeaseManager.checkStoreEnforcedLeaseActive()) {
            throw new IllegalStateException("Only the active cell coordinator may create, update, or remove override leases (Req R3.2, Q4)");
        }
    }
}
