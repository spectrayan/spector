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
package com.spectrayan.spector.synapse.cluster;

import com.spectrayan.spector.cluster.OwnershipResolver;
import com.spectrayan.spector.cluster.membership.CellMembership;
import com.spectrayan.spector.cluster.membership.StaticMembershipSource;
import com.spectrayan.spector.cluster.node.NodeIdentity;
import com.spectrayan.spector.cluster.node.NodeRole;
import com.spectrayan.spector.cluster.routing.OverrideLeaseManager;
import com.spectrayan.spector.cluster.routing.RouteBinding;
import com.spectrayan.spector.cluster.routing.RouteMode;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.cluster.store.InMemoryControlStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates that an active lease override supersedes the Ketama hash ring assignment
 * (ADR-0034 §15.4, Req R3.1, R3.5).
 */
class OverrideBeatsHashTest {

    @Test
    @DisplayName("Active override lease supersedes Ketama hash ring routing assignment (Req R3.1, R3.5)")
    void testOverrideBeatsHash() {
        InMemoryControlStore store = new InMemoryControlStore();
        List<String> members = List.of("node-1", "node-2", "node-3");
        CellMembership membership = new CellMembership("cell-1", 1, members);
        store.updateMembership(membership);

        StaticMembershipSource membershipSource = new StaticMembershipSource("cell-1", 1, members);
        OverrideLeaseManager overrideLeaseManager = new OverrideLeaseManager(store);

        NodeIdentity node1 = new NodeIdentity("cell-1", "node-1", NodeRole.OWNER);
        NodeIdentity node2 = new NodeIdentity("cell-1", "node-2", NodeRole.OWNER);
        NodeIdentity node3 = new NodeIdentity("cell-1", "node-3", NodeRole.OWNER);

        OwnershipResolver resolver1 = new OwnershipResolver(node1, membershipSource, overrideLeaseManager);
        OwnershipResolver resolver2 = new OwnershipResolver(node2, membershipSource, overrideLeaseManager);
        OwnershipResolver resolver3 = new OwnershipResolver(node3, membershipSource, overrideLeaseManager);

        RoutingKey key = new RoutingKey("cell-1", "tenant-alpha", "ns-target");

        // Without override: ring designates the hash owner
        RouteBinding defaultRoute = resolver1.resolve(key);
        assertThat(defaultRoute.mode()).isEqualTo(RouteMode.HASH);
        String hashOwner = defaultRoute.ownerId();
        assertThat(members).contains(hashOwner);

        // Pick a target survivor that is different from the hash owner
        String targetSurvivor = members.stream()
                .filter(n -> !n.equals(hashOwner))
                .findFirst()
                .orElseThrow();

        // Pin the namespace to the survivor with an override lease and fence token
        overrideLeaseManager.setOverride("ns-target", targetSurvivor, "fence-999", Duration.ofMinutes(5));

        // OwnershipResolver MUST resolve to survivor node with RouteMode.OVERRIDE
        RouteBinding overriddenRoute = resolver1.resolve(key);
        assertThat(overriddenRoute.mode()).isEqualTo(RouteMode.OVERRIDE);
        assertThat(overriddenRoute.ownerId()).isEqualTo(targetSurvivor);
        assertThat(overriddenRoute.fence()).isEqualTo("fence-999");
        assertThat(overriddenRoute.epoch()).isEqualTo(1L);

        // ownsLocally check matches survivor and refuses hashOwner
        OwnershipResolver targetResolver = targetSurvivor.equals("node-1") ? resolver1
                : targetSurvivor.equals("node-2") ? resolver2 : resolver3;
        OwnershipResolver hashResolver = hashOwner.equals("node-1") ? resolver1
                : hashOwner.equals("node-2") ? resolver2 : resolver3;

        assertThat(targetResolver.ownsLocally(key)).isTrue();
        assertThat(hashResolver.ownsLocally(key)).isFalse();

        // When override is removed, routing safely reverts back to hash owner
        overrideLeaseManager.removeOverride("ns-target");
        RouteBinding revertedRoute = resolver1.resolve(key);
        assertThat(revertedRoute.mode()).isEqualTo(RouteMode.HASH);
        assertThat(revertedRoute.ownerId()).isEqualTo(hashOwner);
        assertThat(hashResolver.ownsLocally(key)).isTrue();
        assertThat(targetResolver.ownsLocally(key)).isFalse();
    }
}
