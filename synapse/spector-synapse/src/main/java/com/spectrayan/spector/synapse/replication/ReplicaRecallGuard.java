/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.replication;

import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.synapse.config.replication.ReplicationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Enforces the bounded-staleness replica recall contract and unconditional write refusal on replicas
 * (ADR-0034 §10, §15.5, Invariants N2, N7, Req R10.1–R10.6).
 */
public class ReplicaRecallGuard {

    private static final Logger log = LoggerFactory.getLogger(ReplicaRecallGuard.class);

    public static final String HEADER_ALLOW_REPLICA = "X-Spector-Allow-Replica";
    public static final String HEADER_REPLICA_FRESHNESS = "X-Spector-Replica-Freshness";

    /**
     * Replica freshness declaration returned when a replica read is validated (G10).
     *
     * @param appliedHwm high-water mark applied on this replica
     * @param snapshotTimeMs timestamp of the applied snapshot in epoch milliseconds
     * @param lagMs elapsed milliseconds between current time and snapshot time
     */
    public record ReplicaFreshness(long appliedHwm, long snapshotTimeMs, long lagMs) {
        public String toHeaderValue() {
            return "hwm=" + appliedHwm + ",snapshotTimeMs=" + snapshotTimeMs + ",lagMs=" + lagMs;
        }
    }

    private final ReplicationProperties replicationProperties;

    public ReplicaRecallGuard(ReplicationProperties replicationProperties) {
        this.replicationProperties = replicationProperties != null ? replicationProperties : new ReplicationProperties();
    }

    /**
     * Checks if the incoming request headers permit replica reads (Req R10.1, R10.3).
     * Defaults to false at gateway (Req R10.3).
     *
     * @param headers HTTP request headers (case-insensitive lookup supported)
     * @return true only if replica reads are globally enabled AND explicit header is present and equals "true"
     */
    public boolean isReplicaReadPermitted(Map<String, String> headers) {
        if (!replicationProperties.isReplicaReadsEnabled()) {
            return false;
        }
        if (headers == null || headers.isEmpty()) {
            return false;
        }

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (HEADER_ALLOW_REPLICA.equalsIgnoreCase(entry.getKey())) {
                return "true".equalsIgnoreCase(entry.getValue());
            }
        }
        return false;
    }

    /**
     * Evaluates the three simultaneous requirements for serving replica recall (Req R10.1, R10.2, R10.4):
     * <ol>
     *   <li>Namespace is mapped locally</li>
     *   <li>(now - snapshotTimeMs) &lt;= maxReplicaLagMs (convergent after WAL tail replay, G10)</li>
     *   <li>Request explicitly permits replica reads</li>
     * </ol>
     *
     * @param key routing key
     * @param isLocallyMapped whether namespace is currently mapped on this replica
     * @param appliedHwm high-water mark currently applied on the replica (G10)
     * @param lastSnapshotTimeMs exact timestamp of last applied snapshot (Req R10.2)
     * @param nowMs current epoch milliseconds
     * @param requestPermitsReplica whether incoming request explicitly permitted replica reads
     * @return {@link ReplicaFreshness} containing applied HWM, snapshot time, and measured lag (G10)
     * @throws ReplicaStalenessExceededException if freshness bound is exceeded (Req R10.4, N7)
     * @throws IllegalStateException if not mapped locally or request does not permit replica
     */
    public ReplicaFreshness validateReplicaRecall(
            RoutingKey key,
            boolean isLocallyMapped,
            long appliedHwm,
            long lastSnapshotTimeMs,
            long nowMs,
            boolean requestPermitsReplica
    ) {
        Objects.requireNonNull(key, "routingKey must not be null");

        // Requirement 1: Request must permit replica reads
        if (!requestPermitsReplica) {
            throw new IllegalStateException("Replica read refused: request does not carry " + HEADER_ALLOW_REPLICA + ": true");
        }

        // Requirement 2: Must be mapped locally (not on-demand unmapped)
        if (!isLocallyMapped) {
            throw new IllegalStateException("Replica read refused: namespace '" + key.namespaceId() + "' is not mapped locally on replica");
        }

        // Requirement 3: Staleness must be within maxReplicaLag (computed from applied snapshot time, Req R10.2, convergent after WAL tail replay)
        long maxLagMs = replicationProperties.getMaxReplicaLagSeconds() * 1000L;
        long currentLagMs = Math.max(0, nowMs - lastSnapshotTimeMs);

        if (currentLagMs > maxLagMs) {
            log.warn("Replica recall refused for '{}': lag {}ms > maxAllowed {}ms (Invariant N7)",
                    key.namespaceId(), currentLagMs, maxLagMs);
            throw new ReplicaStalenessExceededException(key.namespaceId(), currentLagMs, maxLagMs);
        }

        log.debug("Replica recall permitted for '{}': current lag {}ms (limit {}ms)",
                key.namespaceId(), currentLagMs, maxLagMs);

        return new ReplicaFreshness(appliedHwm, lastSnapshotTimeMs, currentLagMs);
    }

    /**
     * Evaluates replica recall readiness using default -1 HWM indicator (G10 backward compatibility).
     *
     * @param key routing key
     * @param isLocallyMapped whether namespace is currently mapped on this replica
     * @param lastSnapshotTimeMs exact timestamp of last applied snapshot
     * @param nowMs current epoch milliseconds
     * @param requestPermitsReplica whether incoming request explicitly permitted replica reads
     * @return {@link ReplicaFreshness} containing snapshot time and measured lag
     */
    public ReplicaFreshness validateReplicaRecall(
            RoutingKey key,
            boolean isLocallyMapped,
            long lastSnapshotTimeMs,
            long nowMs,
            boolean requestPermitsReplica
    ) {
        return validateReplicaRecall(key, isLocallyMapped, -1L, lastSnapshotTimeMs, nowMs, requestPermitsReplica);
    }

    /**
     * Unconditionally refuses write operations on a replica node (Req R10.6, Invariant N2).
     *
     * @param namespaceId namespace identifier
     * @param isReplicaNode whether the current node is operating in REPLICA role
     * @throws ReplicaWriteRefusedException if this node is a replica
     */
    public void validateWritePermitted(String namespaceId, boolean isReplicaNode) {
        if (isReplicaNode) {
            log.error("Write attempted on replica for namespace '{}'. Unconditionally refusing (Invariant N2)", namespaceId);
            throw new ReplicaWriteRefusedException(namespaceId);
        }
    }
}
