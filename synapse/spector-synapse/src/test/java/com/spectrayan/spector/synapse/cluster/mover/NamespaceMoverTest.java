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
import com.spectrayan.spector.cluster.membership.CellMembership;
import com.spectrayan.spector.cluster.routing.OverrideLeaseManager;
import com.spectrayan.spector.cluster.store.InMemoryControlStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NamespaceMoverTest {

    @Test
    @DisplayName("Planned mover successfully transfers service under a new fence (Req R5.3, R5.4, R5.5)")
    void testPlannedMoveSuccess() {
        InMemoryControlStore store = new InMemoryControlStore();
        store.updateMembership(new CellMembership("cell-1", 1, List.of("node-a", "node-b")));

        CoordinatorLeaseManager coordMgr = new CoordinatorLeaseManager(store, "node-coord", Duration.ofSeconds(60), Duration.ofSeconds(10));
        coordMgr.heartbeat();

        OverrideLeaseManager overrideMgr = new OverrideLeaseManager(store, coordMgr, Duration.ofSeconds(300));
        FenceTokenManager fenceMgr = new FenceTokenManager(store);

        NamespaceMover mover = new NamespaceMover(
                store, coordMgr, overrideMgr, fenceMgr,
                (ns, target) -> true // Target catch-up succeeds
        );

        MoveStatus status = mover.moveNamespace("ns-data", "node-a", "node-b");

        assertThat(status.phase()).isEqualTo(MovePhase.COMPLETED);
        assertThat(status.targetEpoch()).isEqualTo(1L);
        assertThat(status.targetFence()).isEqualTo("1");

        // Verify override lease is set to target node
        var override = overrideMgr.getOverride("ns-data");
        assertThat(override).isPresent();
        assertThat(override.get().targetNodeId()).isEqualTo("node-b");
        assertThat(override.get().epoch()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Mover aborts if target catch-up verification fails (Req R5.4, R4.4, Q6)")
    void testMoveAbortsOnVerificationFailure() {
        InMemoryControlStore store = new InMemoryControlStore();
        store.updateMembership(new CellMembership("cell-1", 1, List.of("node-a", "node-b")));

        CoordinatorLeaseManager coordMgr = new CoordinatorLeaseManager(store, "node-coord", Duration.ofSeconds(60), Duration.ofSeconds(10));
        coordMgr.heartbeat();

        OverrideLeaseManager overrideMgr = new OverrideLeaseManager(store, coordMgr, Duration.ofSeconds(300));
        FenceTokenManager fenceMgr = new FenceTokenManager(store);

        NamespaceMover mover = new NamespaceMover(
                store, coordMgr, overrideMgr, fenceMgr,
                (ns, target) -> false // Catch-up verification FAILS
        );

        MoveStatus status = mover.moveNamespace("ns-data", "node-a", "node-b");

        assertThat(status.phase()).isEqualTo(MovePhase.FAILED);
        assertThat(overrideMgr.getOverride("ns-data")).isEmpty();
        assertThat(store.getNamespaceEpoch("ns-data")).isEqualTo(0L);
    }

    @Test
    @DisplayName("Non-coordinator cannot execute namespace moves (Req R5.3, Q4)")
    void testNonCoordinatorMoveRejected() {
        InMemoryControlStore store = new InMemoryControlStore();
        store.updateMembership(new CellMembership("cell-1", 1, List.of("node-a", "node-b")));

        CoordinatorLeaseManager followerCoord = new CoordinatorLeaseManager(store, "node-follower", Duration.ofSeconds(60), Duration.ofSeconds(10));
        // Node does NOT acquire coordinator lease

        OverrideLeaseManager overrideMgr = new OverrideLeaseManager(store, followerCoord, Duration.ofSeconds(300));
        FenceTokenManager fenceMgr = new FenceTokenManager(store);

        NamespaceMover mover = new NamespaceMover(
                store, followerCoord, overrideMgr, fenceMgr,
                (ns, target) -> true
        );

        assertThatThrownBy(() -> mover.moveNamespace("ns-data", "node-a", "node-b"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only the active cell coordinator may execute namespace moves");
    }
}
