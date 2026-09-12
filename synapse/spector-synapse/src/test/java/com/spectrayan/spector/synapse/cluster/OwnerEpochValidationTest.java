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
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.cluster.exception.NamespaceNotOwnedException;
import com.spectrayan.spector.synapse.cluster.exception.StaleRouteException;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.cell.CellProperties;
import com.spectrayan.spector.synapse.memory.MemoryRegistry;
import com.spectrayan.spector.synapse.memory.MemoryRequestBinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OwnerEpochValidationTest {

    private static final String CELL_ID = "cell-alpha";
    private static final String NODE_1 = "node-1";
    private static final String NODE_2 = "node-2";
    private static final List<String> MEMBERS = List.of(NODE_1, NODE_2);

    @Test
    @DisplayName("Req R7.3, Invariant K2: Owner rejects incoming request with older epoch as STALE_ROUTE (421)")
    void testOwnerRejectsStaleEpoch() {
        int activeEpoch = 5;
        StaticMembershipSource membership = new StaticMembershipSource(CELL_ID, activeEpoch, MEMBERS);
        OwnershipResolver resolver = new OwnershipResolver(
                new NodeIdentity(CELL_ID, NODE_1, NodeRole.OWNER), membership
        );

        SynapseProperties props = new SynapseProperties();
        CellProperties cellProps = props.getCell();
        cellProps.setId(CELL_ID);
        cellProps.setRole("owner");
        cellProps.setNodeId(NODE_1);

        MemoryRequestBinder binder = new MemoryRequestBinder(
                mock(AccountCatalog.class),
                mock(MemoryRegistry.class),
                props,
                resolver
        );

        // Find a routing key owned by node-1
        RoutingKey ownedKey = null;
        for (int i = 0; i < 50; i++) {
            RoutingKey candidate = RoutingKey.ofTenanted(CELL_ID, "tenant-1", "ns-epoch-" + i);
            if (resolver.ownsLocally(candidate)) {
                ownedKey = candidate;
                break;
            }
        }
        assertThat(ownedKey).isNotNull();

        // 1. Client provides stale epoch 4 < activeEpoch 5 -> must throw StaleRouteException
        final RoutingKey targetKey = ownedKey;
        assertThatThrownBy(() -> binder.enforceOwnership(targetKey, 4L))
                .isInstanceOf(StaleRouteException.class)
                .hasMessageContaining("stale");

        // 2. Client provides active epoch 5 -> must pass
        assertThatCode(() -> binder.enforceOwnership(targetKey, 5L))
                .doesNotThrowAnyException();

        // 3. Client provides newer epoch 6 -> must pass
        assertThatCode(() -> binder.enforceOwnership(targetKey, 6L))
                .doesNotThrowAnyException();

        // 4. Client provides null epoch (direct call or non-versioned) -> must pass
        assertThatCode(() -> binder.enforceOwnership(targetKey, (Long) null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Req R7.7, Invariant K2: Owner validates locally independent of headers (refuses non-owned key)")
    void testOwnerRefusesNonOwnedKeyRegardlessOfEpoch() {
        int activeEpoch = 5;
        StaticMembershipSource membership = new StaticMembershipSource(CELL_ID, activeEpoch, MEMBERS);
        OwnershipResolver resolver = new OwnershipResolver(
                new NodeIdentity(CELL_ID, NODE_1, NodeRole.OWNER), membership
        );

        SynapseProperties props = new SynapseProperties();
        props.getCell().setId(CELL_ID);
        props.getCell().setRole("owner");
        props.getCell().setNodeId(NODE_1);

        MemoryRequestBinder binder = new MemoryRequestBinder(
                mock(AccountCatalog.class), mock(MemoryRegistry.class), props, resolver
        );

        // Find a routing key owned by node-2 (not node-1)
        RoutingKey nonOwnedKey = null;
        for (int i = 0; i < 50; i++) {
            RoutingKey candidate = RoutingKey.ofTenanted(CELL_ID, "tenant-1", "ns-other-" + i);
            if (!resolver.ownsLocally(candidate)) {
                nonOwnedKey = candidate;
                break;
            }
        }
        assertThat(nonOwnedKey).isNotNull();

        // Even with matching or newer epoch header, node-1 MUST refuse because it doesn't own the namespace locally (K2)
        final RoutingKey targetKey = nonOwnedKey;
        assertThatThrownBy(() -> binder.enforceOwnership(targetKey, (long) activeEpoch))
                .isInstanceOf(NamespaceNotOwnedException.class);
    }
}
