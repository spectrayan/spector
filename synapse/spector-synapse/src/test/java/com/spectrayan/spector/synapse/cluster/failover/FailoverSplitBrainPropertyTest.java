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
 */
class FailoverSplitBrainPropertyTest {

    @Property(tries = 50)
    void propertyNoTwoOwnersAcceptWriteForSameNamespaceAcrossFailover(
            @ForAll("namespaces") String namespace,
            @ForAll("failoverCycles") int cycles) {

        InMemoryControlStore store = new InMemoryControlStore();
        List<String> members = List.of("node-a", "node-b");
        store.updateMembership(new CellMembership("cell-1", 1, members));

        FenceTokenManager fenceMgr = new FenceTokenManager(store);
        OverrideLeaseManager overrideMgr = new OverrideLeaseManager(store);

        StaticMembershipSource membership = new StaticMembershipSource("cell-1", 1, members);
        OwnershipResolver resolverA = new OwnershipResolver(new NodeIdentity("cell-1", "node-a", NodeRole.OWNER), membership, overrideMgr);
        OwnershipResolver resolverB = new OwnershipResolver(new NodeIdentity("cell-1", "node-b", NodeRole.OWNER), membership, overrideMgr);

        MemoryRequestBinder binderA = new MemoryRequestBinder(null, null, null, resolverA, fenceMgr);
        MemoryRequestBinder binderB = new MemoryRequestBinder(null, null, null, resolverB, fenceMgr);

        RoutingKey key = new RoutingKey("cell-1", "tenant-test", namespace);

        String currentOwner = "node-a";
        String previousOwner = "node-b";

        for (int i = 1; i <= cycles; i++) {
            long epoch = store.advanceNamespaceEpoch(namespace);
            String newFence = fenceMgr.mintFenceForEpoch(namespace, epoch).toTokenString();

            // Swap owners
            String newOwner = currentOwner.equals("node-a") ? "node-b" : "node-a";
            previousOwner = currentOwner;
            currentOwner = newOwner;

            overrideMgr.setOverride(namespace, currentOwner, newFence, Duration.ofMinutes(5));

            MemoryRequestBinder currentBinder = currentOwner.equals("node-a") ? binderA : binderB;
            MemoryRequestBinder oldBinder = previousOwner.equals("node-a") ? binderA : binderB;

            // Current owner accepts write with current fence
            final String validFence = newFence;
            assertThatCode(() -> currentBinder.enforceFence(namespace, validFence)).doesNotThrowAnyException();

            // Previous owner strictly rejects write with previous fence
            String oldFence = String.valueOf(epoch - 1);
            assertThatThrownBy(() -> oldBinder.enforceFence(namespace, oldFence))
                    .isInstanceOf(FencedException.class);

            // Previous owner also rejects write even if presented with current fence because it does not own locally
            // ownsLocally is false on old owner
            boolean oldOwns = previousOwner.equals("node-a") ? resolverA.ownsLocally(key) : resolverB.ownsLocally(key);
            assertThat(oldOwns).isFalse();

            // Exactly one owner owns locally
            boolean currentOwns = currentOwner.equals("node-a") ? resolverA.ownsLocally(key) : resolverB.ownsLocally(key);
            assertThat(currentOwns).isTrue();
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
