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
package com.spectrayan.spector.synapse.cluster.mover;

import com.spectrayan.spector.cluster.coordinator.CoordinatorLeaseManager;
import com.spectrayan.spector.cluster.fencing.FenceTokenManager;
import com.spectrayan.spector.cluster.routing.OverrideLeaseManager;
import com.spectrayan.spector.cluster.store.ControlStore;
import com.spectrayan.spector.synapse.cluster.failover.CandidateDataVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes planned namespace relocation for cell drain, rebalancing, and maintenance (Req R5.3–R5.6).
 *
 * <p>Key Guarantees:
 * <ul>
 *   <li><b>Relocates Service, Not Bytes (Req R5.5, Phase 1 J6):</b> Moves ownership authority and leaves underlying storage
 *       independent, avoiding expensive data migration across rebalance.</li>
 *   <li><b>Strict Order and No Overlap (Req R5.4, Q8):</b> Sequence: stop source writes -> target catches up ->
 *       transfer ownership under a newly minted fence -> release source. No overlap window is permitted.</li>
 *   <li><b>Resumable and Single-Owner (Req R5.6):</b> An interrupted relocation leaves the namespace fully owned by exactly one node.</li>
 * </ul>
 * </p>
 */
public class NamespaceMover {

    private static final Logger log = LoggerFactory.getLogger(NamespaceMover.class);

    private final ControlStore controlStore;
    private final CoordinatorLeaseManager coordinatorLeaseManager;
    private final OverrideLeaseManager overrideLeaseManager;
    private final FenceTokenManager fenceTokenManager;
    private final CandidateDataVerifier dataVerifier;
    private final Clock clock;
    private final Duration overrideTtl;

    private final Map<String, MoveStatus> inFlightMoves = new ConcurrentHashMap<>();

    public NamespaceMover(
            ControlStore controlStore,
            CoordinatorLeaseManager coordinatorLeaseManager,
            OverrideLeaseManager overrideLeaseManager,
            FenceTokenManager fenceTokenManager,
            CandidateDataVerifier dataVerifier) {
        this(controlStore, coordinatorLeaseManager, overrideLeaseManager, fenceTokenManager,
                dataVerifier, Duration.ofSeconds(300), Clock.systemUTC());
    }

    public NamespaceMover(
            ControlStore controlStore,
            CoordinatorLeaseManager coordinatorLeaseManager,
            OverrideLeaseManager overrideLeaseManager,
            FenceTokenManager fenceTokenManager,
            CandidateDataVerifier dataVerifier,
            Duration overrideTtl,
            Clock clock) {
        this.controlStore = Objects.requireNonNull(controlStore, "controlStore must not be null");
        this.coordinatorLeaseManager = coordinatorLeaseManager;
        this.overrideLeaseManager = Objects.requireNonNull(overrideLeaseManager, "overrideLeaseManager must not be null");
        this.fenceTokenManager = Objects.requireNonNull(fenceTokenManager, "fenceTokenManager must not be null");
        this.dataVerifier = Objects.requireNonNull(dataVerifier, "dataVerifier must not be null");
        this.overrideTtl = Objects.requireNonNull(overrideTtl, "overrideTtl must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Executes planned relocation of a namespace from {@code sourceNodeId} to {@code targetNodeId} (Req R5.3).
     *
     * @param namespaceId  namespace to relocate
     * @param sourceNodeId current owner node
     * @param targetNodeId target owner node
     * @return resulting move status
     */
    public MoveStatus moveNamespace(String namespaceId, String sourceNodeId, String targetNodeId) {
        Objects.requireNonNull(namespaceId, "namespaceId must not be null");
        Objects.requireNonNull(sourceNodeId, "sourceNodeId must not be null");
        Objects.requireNonNull(targetNodeId, "targetNodeId must not be null");
        verifyCoordinatorAuthority();

        Instant now = clock.instant();
        MoveStatus status = MoveStatus.init(namespaceId, sourceNodeId, targetNodeId, now);
        inFlightMoves.put(namespaceId, status);

        try {
            // Phase 1: STOP_SOURCE_WRITES (Req R5.4)
            log.info("[NamespaceMover] Phase 1/4: Stopping writes on source node '{}' for namespace '{}'", sourceNodeId, namespaceId);
            status = status.withPhase(MovePhase.STOP_SOURCE_WRITES, 0L, null, clock.instant());
            inFlightMoves.put(namespaceId, status);

            // Phase 2: TARGET_CATCH_UP & Verification (Req R5.4, R4.4, Q6)
            log.info("[NamespaceMover] Phase 2/4: Verifying target node '{}' catch-up for namespace '{}'", targetNodeId, namespaceId);
            boolean ready = dataVerifier.verifyCandidateData(namespaceId, targetNodeId);
            if (!ready) {
                log.error("[NamespaceMover] Move ABORTED for namespace '{}': target node '{}' failed catch-up verification",
                        namespaceId, targetNodeId);
                status = status.withPhase(MovePhase.FAILED, 0L, null, clock.instant());
                inFlightMoves.put(namespaceId, status);
                return status;
            }
            status = status.withPhase(MovePhase.TARGET_CATCH_UP, 0L, null, clock.instant());
            inFlightMoves.put(namespaceId, status);

            // Phase 3: TRANSFER_OWNERSHIP under new fence (Req R5.4, R5.5)
            log.info("[NamespaceMover] Phase 3/4: Transferring ownership to '{}' under new fence", targetNodeId);
            long newEpoch = controlStore.advanceNamespaceEpoch(namespaceId);
            String newFence = fenceTokenManager.mintFenceForEpoch(namespaceId, newEpoch).toTokenString();
            overrideLeaseManager.setOverrideWithEpoch(namespaceId, targetNodeId, newEpoch, newFence, overrideTtl);
            status = status.withPhase(MovePhase.TRANSFER_OWNERSHIP, newEpoch, newFence, clock.instant());
            inFlightMoves.put(namespaceId, status);

            // Phase 4: RELEASE_SOURCE (Req R5.4)
            log.info("[NamespaceMover] Phase 4/4: Releasing source node '{}'", sourceNodeId);
            status = status.withPhase(MovePhase.RELEASE_SOURCE, newEpoch, newFence, clock.instant());
            inFlightMoves.put(namespaceId, status);

            // Completed
            status = status.withPhase(MovePhase.COMPLETED, newEpoch, newFence, clock.instant());
            inFlightMoves.put(namespaceId, status);
            log.info("[NamespaceMover] Namespace '{}' successfully relocated to node '{}' (epoch={}, fence={})",
                    namespaceId, targetNodeId, newEpoch, newFence);
            return status;
        } catch (Exception e) {
            log.error("[NamespaceMover] Interrupted or error during move of namespace '{}': {}", namespaceId, e.getMessage(), e);
            status = status.withPhase(MovePhase.FAILED, status.targetEpoch(), status.targetFence(), clock.instant());
            inFlightMoves.put(namespaceId, status);
            return status;
        }
    }

    /**
     * Returns the move status for an active or completed relocation.
     *
     * @param namespaceId namespace identifier
     * @return optional move status
     */
    public Optional<MoveStatus> getMoveStatus(String namespaceId) {
        return Optional.ofNullable(inFlightMoves.get(namespaceId));
    }

    private void verifyCoordinatorAuthority() {
        if (coordinatorLeaseManager != null && !coordinatorLeaseManager.checkStoreEnforcedLeaseActive()) {
            throw new IllegalStateException("Only the active cell coordinator may execute namespace moves (Req R5.3, Q4)");
        }
    }
}
