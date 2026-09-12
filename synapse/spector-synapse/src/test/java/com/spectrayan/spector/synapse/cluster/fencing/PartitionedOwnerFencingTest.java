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
package com.spectrayan.spector.synapse.cluster.fencing;

import com.spectrayan.spector.cluster.OwnershipResolver;
import com.spectrayan.spector.cluster.fencing.FenceTokenManager;
import com.spectrayan.spector.cluster.membership.CellMembership;
import com.spectrayan.spector.cluster.membership.StaticMembershipSource;
import com.spectrayan.spector.cluster.node.NodeIdentity;
import com.spectrayan.spector.cluster.node.NodeRole;
import com.spectrayan.spector.cluster.store.InMemoryControlStore;
import com.spectrayan.spector.synapse.memory.MemoryRequestBinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Chaos test: Partition an owner from the control store while still reachable by clients (Req R10.4, Task 2.8).
 *
 * <p>Verifies that once a namespace epoch has advanced (e.g. during failover or promotion),
 * the partitioned old owner refuses writes carrying the superseded or updated fence tokens,
 * strictly guaranteeing single-writer invariance without relying on failure detection accuracy (Q1, Q2).</p>
 */
class PartitionedOwnerFencingTest {

    @Test
    @DisplayName("Chaos: Partitioned owner ceases writes once fence is superseded in the cluster (Req R10.4, Q1, Q2)")
    void testPartitionedOwnerStopsAcceptingWrites() {
        InMemoryControlStore store = new InMemoryControlStore();
        CellMembership membership = new CellMembership("cell-1", 1, List.of("node-1", "node-2"));
        store.updateMembership(membership);

        FenceTokenManager node1Fences = new FenceTokenManager(store);
        FenceTokenManager survivorFences = new FenceTokenManager(store);

        NodeIdentity node1Identity = new NodeIdentity("cell-1", "node-1", NodeRole.OWNER);
        OwnershipResolver node1Resolver = new OwnershipResolver(
                node1Identity,
                new StaticMembershipSource("cell-1", 1, List.of("node-1", "node-2"))
        );

        MemoryRequestBinder node1Binder = new MemoryRequestBinder(
                null, null, null, node1Resolver, node1Fences
        );

        String namespaceId = "ns-chaos-partition";

        // 1. Initial State: Node 1 owns ns-chaos-partition at Epoch 1
        node1Fences.setLocalFence(namespaceId, 1L);

        // Client write carrying active fence 1 succeeds on Node 1
        assertThatCode(() -> node1Binder.enforceFence(namespaceId, "1"))
                .doesNotThrowAnyException();

        // 2. Partition happens: Node 1 is partitioned from the control store.
        // Survivor promotes namespace and advances epoch in control store to 2
        long promotedEpoch = store.advanceNamespaceEpoch(namespaceId);
        assertThat(promotedEpoch).isEqualTo(1L); // first advancement from default 0 -> 1, let's bump again
        promotedEpoch = store.advanceNamespaceEpoch(namespaceId);
        assertThat(promotedEpoch).isEqualTo(2L);
        survivorFences.setLocalFence(namespaceId, 2L);

        // 3. Client with updated route/fence reaches Node 1 with new fence "2"
        // Node 1 still holds local fence "1". Node 1 MUST REFUSE with FencedException!
        assertThatThrownBy(() -> node1Binder.enforceFence(namespaceId, "2"))
                .isInstanceOf(FencedException.class)
                .hasMessageContaining("superseded or mismatched");

        // 4. Stale client sending missing or old fence to promoted survivor MUST ALSO BE REFUSED!
        NodeIdentity survivorIdentity = new NodeIdentity("cell-1", "node-2", NodeRole.OWNER);
        OwnershipResolver survivorResolver = new OwnershipResolver(
                survivorIdentity,
                new StaticMembershipSource("cell-1", 1, List.of("node-1", "node-2"))
        );
        MemoryRequestBinder survivorBinder = new MemoryRequestBinder(
                null, null, null, survivorResolver, survivorFences
        );

        assertThatThrownBy(() -> survivorBinder.enforceFence(namespaceId, "1"))
                .isInstanceOf(FencedException.class);

        // 5. Client sending valid new fence "2" to survivor succeeds
        assertThatCode(() -> survivorBinder.enforceFence(namespaceId, "2"))
                .doesNotThrowAnyException();

        // Rejections counted properly (Req R9.2)
        assertThat(node1Fences.getFenceRejectionCount()).isGreaterThanOrEqualTo(1L);
        assertThat(survivorFences.getFenceRejectionCount()).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("Chaos G12: Partitioned owner fence lease expires and fails closed against stale client writes")
    void testPartitionedOwnerFailsClosedOnTtlExpiry() {
        InMemoryControlStore store = new InMemoryControlStore();
        CellMembership membership = new CellMembership("cell-1", 1, List.of("node-1", "node-2"));
        store.updateMembership(membership);

        class TestClock extends java.time.Clock {
            private java.time.Instant now = java.time.Instant.parse("2026-01-01T00:00:00Z");
            @Override public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
            @Override public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public java.time.Instant instant() { return now; }
            public void advance(java.time.Duration duration) { now = now.plus(duration); }
        }
        TestClock testClock = new TestClock();
        java.time.Duration fenceTtl = java.time.Duration.ofSeconds(5);

        FenceTokenManager node1Fences = new FenceTokenManager(store, testClock, fenceTtl);
        NodeIdentity node1Identity = new NodeIdentity("cell-1", "node-1", NodeRole.OWNER);
        OwnershipResolver node1Resolver = new OwnershipResolver(
                node1Identity,
                new StaticMembershipSource("cell-1", 1, List.of("node-1", "node-2"))
        );
        MemoryRequestBinder node1Binder = new MemoryRequestBinder(
                null, null, null, node1Resolver, node1Fences
        );

        String namespaceId = "ns-partition-expiry";
        node1Fences.setLocalFence(namespaceId, 1L);

        // While lease is active, write presenting valid fence "1" is accepted
        assertThatCode(() -> node1Binder.enforceFence(namespaceId, "1"))
                .doesNotThrowAnyException();

        // Advance time past the 5-second fence TTL (6 seconds) without renewal
        testClock.advance(java.time.Duration.ofSeconds(6));
        assertThat(node1Fences.isFenceExpired(namespaceId)).isTrue();

        // Stale client sending old fence "1" to partitioned node MUST BE REFUSED (G12 fail-closed)
        assertThatThrownBy(() -> node1Binder.enforceFence(namespaceId, "1"))
                .isInstanceOf(FencedException.class)
                .hasMessageContaining("superseded or mismatched");

        // If renewed, writes are accepted again
        node1Fences.renewLocalFence(namespaceId);
        assertThat(node1Fences.isFenceExpired(namespaceId)).isFalse();
        assertThatCode(() -> node1Binder.enforceFence(namespaceId, "1"))
                .doesNotThrowAnyException();
    }
}
