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
import com.spectrayan.spector.cluster.membership.StaticMembershipSource;
import com.spectrayan.spector.cluster.node.NodeIdentity;
import com.spectrayan.spector.cluster.node.NodeRole;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct encoding of Invariant J1 and ADR-0034 §15.8:
 * "No two owners accept a write for the same (namespace, epoch)."
 *
 * <p>Validates that across any cluster of N owner nodes on the same ring generation,
 * exactly one node accepts local writes for any given key, and N - 1 nodes refuse.</p>
 */
class NoTwoOwnersAcceptWriteTest {

    @Test
    @DisplayName("Invariant J1: Exactly one owner accepts write for any (namespace, epoch) across 10,000 keys")
    void testNoTwoOwnersAcceptWrite() {
        List<String> memberNames = List.of(
                "spector-node-us-east-1a",
                "spector-node-us-east-1b",
                "spector-node-us-east-1c",
                "spector-node-us-east-1d",
                "spector-node-us-east-1e"
        );
        String cellId = "us-east-1";
        int ringVersion = 7;

        StaticMembershipSource membership = new StaticMembershipSource(cellId, ringVersion, memberNames);

        List<OwnershipResolver> resolvers = new ArrayList<>();
        for (String node : memberNames) {
            NodeIdentity identity = new NodeIdentity(cellId, node, NodeRole.OWNER);
            resolvers.add(new OwnershipResolver(identity, membership));
        }

        // Test across 10,000 diverse tenant and namespace keys
        for (int i = 0; i < 10_000; i++) {
            String tenantId = (i % 5 == 0) ? null : "tenant-" + (i % 50);
            String namespaceId = "ns-" + UUID.randomUUID();
            RoutingKey key = new RoutingKey(cellId, tenantId, namespaceId);

            int acceptingCount = 0;
            String acceptingNode = null;

            for (OwnershipResolver resolver : resolvers) {
                if (resolver.ownsLocally(key)) {
                    acceptingCount++;
                    acceptingNode = resolver.identity().nodeId();
                }
            }

            assertThat(acceptingCount)
                    .as("Exactly one node must accept write for key %s (Invariant J1)", key)
                    .isEqualTo(1);

            assertThat(acceptingNode).isNotNull();
        }
    }
}
