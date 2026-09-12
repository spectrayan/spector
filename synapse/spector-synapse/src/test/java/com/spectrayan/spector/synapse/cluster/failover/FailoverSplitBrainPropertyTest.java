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
package com.spectrayan.spector.synapse.cluster.failover;

import com.spectrayan.spector.cluster.OwnershipResolver;
import com.spectrayan.spector.cluster.fencing.FenceToken;
import com.spectrayan.spector.cluster.fencing.FenceTokenManager;
import com.spectrayan.spector.cluster.membership.CellMembership;
import com.spectrayan.spector.cluster.membership.StaticMembershipSource;
import com.spectrayan.spector.cluster.node.NodeIdentity;
import com.spectrayan.spector.cluster.node.NodeRole;
import com.spectrayan.spector.cluster.routing.OverrideLeaseManager;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.cluster.store.InMemoryControlStore;
import com.spectrayan.spector.synapse.cluster.fencing.FencedException;
import com.spectrayan.spector.synapse.memory.MemoryRequestBinder;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property test: No two owners accept a write for one (namespace, epoch) across a failover (Req R8.6, Task 4.11).
 *
 * <p>Extends Phase 1 R12.2 from steady state to transition: whenever an epoch advances and ownership transfers,
 * the previous owner strictly rejects writes with superseded fence tokens, guaranteeing mutual exclusion.</p>
 *
 * <p><b>Fix (§6):</b> Each simulated node now has its own {@link FenceTokenManager} instead of sharing
 * one — the shared manager trivially ensured mutual exclusion by being a single logical owner.</p>
 */
class FailoverSplitBrainPropertyTest {

    @Property(tries = 50)
    void propertyNoTwoOwnersAcceptWriteForSameNamespaceAcrossFailover(
            @ForAll("namespaces") String namespace,
            @ForAll("failoverCycles") int cycles) {

        InMemoryControlStore store = new InMemoryControlStore();
        List<String> members = List.of("node-a", "node-b");
        store.updateMembership(new CellMembership("cell-1", 1, members));

        // §6 Fix: Each node gets its own FenceTokenManager (previously shared, creating a false single-writer illusion)
        FenceTokenManager fenceMgrA = new FenceTokenManager(store);
        FenceTokenManager fenceMgrB = new FenceTokenManager(store);
        com.spectrayan.spector.cluster.coordinator.CoordinatorLeaseManager coordMgr =
                new com.spectrayan.spector.cluster.coordinator.CoordinatorLeaseManager(store, "coord-node", Duration.ofMinutes(10), Duration.ofSeconds(10));
        coordMgr.heartbeat();
        OverrideLeaseManager overrideMgr = new OverrideLeaseManager(store, coordMgr, Duration.ofMinutes(5));

        StaticMembershipSource membership = new StaticMembershipSource("cell-1", 1, members);
        OwnershipResolver resolverA = new OwnershipResolver(new NodeIdentity("cell-1", "node-a", NodeRole.OWNER), membership, overrideMgr);
        OwnershipResolver resolverB = new OwnershipResolver(new NodeIdentity("cell-1", "node-b", NodeRole.OWNER), membership, overrideMgr);

        MemoryRequestBinder binderA = new MemoryRequestBinder(null, null, null, resolverA, fenceMgrA);
        MemoryRequestBinder binderB = new MemoryRequestBinder(null, null, null, resolverB, fenceMgrB);

        RoutingKey key = new RoutingKey("cell-1", "tenant-test", namespace);

        String currentOwner = "node-a";
        String previousOwner = "node-b";

        for (int i = 1; i <= cycles; i++) {
            long epoch = store.advanceNamespaceEpoch(namespace, coordMgr.getLeaseVersion());

            // Swap owners
            String newOwner = currentOwner.equals("node-a") ? "node-b" : "node-a";
            previousOwner = currentOwner;
            currentOwner = newOwner;

            // Mint fence on the NEW owner's manager and surrender on the old owner's manager
            FenceTokenManager currentFenceMgr = currentOwner.equals("node-a") ? fenceMgrA : fenceMgrB;
            FenceTokenManager oldFenceMgr = previousOwner.equals("node-a") ? fenceMgrA : fenceMgrB;

            String newFence = currentFenceMgr.mintFenceForEpoch(namespace, epoch).toTokenString();
            oldFenceMgr.surrenderLocalFence(namespace);

            overrideMgr.setOverride(namespace, currentOwner, newFence, Duration.ofMinutes(5));

            MemoryRequestBinder currentBinder = currentOwner.equals("node-a") ? binderA : binderB;
            MemoryRequestBinder oldBinder = previousOwner.equals("node-a") ? binderA : binderB;

            // Current owner accepts write with current fence
            final String validFence = newFence;
            assertThatCode(() -> currentBinder.enforceFence(namespace, validFence)).doesNotThrowAnyException();

            // Previous owner strictly rejects write with previous fence (surrendered)
            String oldFence = String.valueOf(epoch - 1);
            assertThatThrownBy(() -> oldBinder.enforceFence(namespace, oldFence))
                    .isInstanceOf(FencedException.class);

            // Previous owner also rejects even with the current fence (surrendered namespace)
            assertThatThrownBy(() -> oldBinder.enforceFence(namespace, validFence))
                    .isInstanceOf(FencedException.class);

            // Exactly one owner owns locally
            boolean currentOwns = currentOwner.equals("node-a") ? resolverA.ownsLocally(key) : resolverB.ownsLocally(key);
            assertThat(currentOwns).isTrue();

            boolean oldOwns = previousOwner.equals("node-a") ? resolverA.ownsLocally(key) : resolverB.ownsLocally(key);
            assertThat(oldOwns).isFalse();
        }
    }

    @Provide
    Arbitrary<String> namespaces() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(12);
    }

    @Provide
    Arbitrary<Integer> failoverCycles() {
        return Arbitraries.integers().between(1, 5);
    }
}

