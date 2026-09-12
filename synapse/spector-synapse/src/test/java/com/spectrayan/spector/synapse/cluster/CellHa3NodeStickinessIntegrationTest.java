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
import com.spectrayan.spector.synapse.catalog.Account;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.AccountFlags;
import com.spectrayan.spector.synapse.catalog.AccountProfile;
import com.spectrayan.spector.synapse.catalog.AccountQuotas;
import com.spectrayan.spector.synapse.catalog.Grant;
import com.spectrayan.spector.synapse.catalog.GrantObjectType;
import com.spectrayan.spector.synapse.catalog.GrantRole;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.NamespaceStatus;
import com.spectrayan.spector.synapse.catalog.NamespaceType;
import com.spectrayan.spector.synapse.catalog.PrincipalKind;
import com.spectrayan.spector.synapse.catalog.PrincipalType;
import com.spectrayan.spector.synapse.cluster.exception.NamespaceNotOwnedException;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.cell.CellProperties;
import com.spectrayan.spector.synapse.memory.MemoryBinding;
import com.spectrayan.spector.synapse.memory.MemoryRegistry;
import com.spectrayan.spector.synapse.memory.MemoryRequestBinder;
import com.spectrayan.spector.synapse.memory.NamespaceResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Validates 3-node cluster stickiness and single-writer concurrency rejection
 * (ADR-0034 §15.8, Req R5, R12.2, R12.6, Invariant J1).
 */
class CellHa3NodeStickinessIntegrationTest {

    private static final String CELL_ID = "cell-us-east-1";
    private static final String NODE_1 = "spector-owner-1";
    private static final String NODE_2 = "spector-owner-2";
    private static final String NODE_3 = "spector-owner-3";
    private static final List<String> MEMBERS = List.of(NODE_1, NODE_2, NODE_3);

    private AccountCatalog catalog;
    private MemoryRegistry memoryRegistry;
    private NamespaceResolver namespaceResolver;

    private MemoryRequestBinder node1Binder;
    private MemoryRequestBinder node2Binder;
    private MemoryRequestBinder node3Binder;

    private static final String ACCOUNT_ID = "acc-prod-1";
    private static final String TENANT_ID = "tenant-enterprise";

    @BeforeEach
    void setUp() {
        catalog = mock(AccountCatalog.class);
        memoryRegistry = mock(MemoryRegistry.class);
        namespaceResolver = mock(NamespaceResolver.class);
        when(memoryRegistry.namespaceResolver()).thenReturn(namespaceResolver);

        StaticMembershipSource membership = new StaticMembershipSource(CELL_ID, 1, MEMBERS);

        node1Binder = createBinder(NODE_1, membership);
        node2Binder = createBinder(NODE_2, membership);
        node3Binder = createBinder(NODE_3, membership);

        Account account = new Account(
                ACCOUNT_ID, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                "Prod Account", AccountQuotas.forProfile(AccountProfile.HUMAN_SOLO),
                AccountFlags.forProfile(AccountProfile.HUMAN_SOLO),
                ACCOUNT_ID, Instant.now(), TENANT_ID, false
        );
        when(catalog.getOrCreateAccount(eq(ACCOUNT_ID), any(), any())).thenReturn(account);
        when(catalog.getOrCreateAccount(eq(ACCOUNT_ID))).thenReturn(account);
        when(catalog.getAccount(eq(ACCOUNT_ID))).thenReturn(account);

        when(namespaceResolver.placementTenantIdFor(any(), any(), any(), any()))
                .thenReturn(TENANT_ID);
    }

    private MemoryRequestBinder createBinder(String nodeId, StaticMembershipSource membership) {
        SynapseProperties props = new SynapseProperties();
        props.auth().setEnabled(true);
        CellProperties cellProps = props.getCell();
        cellProps.setId(CELL_ID);
        cellProps.setRole("owner");
        cellProps.setNodeId(nodeId);
        cellProps.getRing().setVersion(1);
        cellProps.getRing().setMembers(MEMBERS);

        OwnershipResolver resolver = new OwnershipResolver(
                new NodeIdentity(CELL_ID, nodeId, NodeRole.OWNER), membership);

        return new MemoryRequestBinder(catalog, memoryRegistry, props, resolver);
    }

    private void mockNamespaceInCatalog(String namespaceId) {
        NamespaceRecord record = new NamespaceRecord(
                namespaceId, "slug-" + namespaceId, ACCOUNT_ID, NamespaceType.AGENT, NamespaceStatus.ACTIVE,
                "Title", "Desc", null, Instant.now(), Instant.now()
        );
        when(catalog.resolve(ACCOUNT_ID, namespaceId)).thenReturn(Optional.of(record));
        Grant grant = new Grant(
                "grant-" + namespaceId, GrantObjectType.NAMESPACE, namespaceId, ACCOUNT_ID,
                PrincipalType.ACCOUNT, GrantRole.OWNER, Set.of(), "admin", Instant.now(), null, null
        );
        when(catalog.authorize(ACCOUNT_ID, namespaceId, GrantRole.READER)).thenReturn(Optional.of(grant));
    }

    @Test
    @DisplayName("Stickiness: 20 namespaces consistently resolve to the exact same node across 100 rounds")
    void testStickinessAcrossNodes() {
        List<String> namespaces = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String ns = "ns-stickiness-" + i;
            namespaces.add(ns);
            mockNamespaceInCatalog(ns);
        }

        Authentication auth = new TestingAuthenticationToken(ACCOUNT_ID, "secret", "ROLE_USER");

        for (String ns : namespaces) {
            String initialOwner = null;
            // Determine initial owner
            for (MemoryRequestBinder binder : List.of(node1Binder, node2Binder, node3Binder)) {
                try {
                    MemoryBinding binding = binder.bind(auth, Optional.of(ns));
                    if (binding != null) {
                        initialOwner = binder.ownershipResolver().identity().nodeId();
                        break;
                    }
                } catch (NamespaceNotOwnedException ignored) {
                }
            }
            assertThat(initialOwner).isNotNull();
            final String expectedOwner = initialOwner;

            // Verify across 100 rounds that the assignment never changes
            for (int round = 0; round < 100; round++) {
                for (MemoryRequestBinder binder : List.of(node1Binder, node2Binder, node3Binder)) {
                    String currentNode = binder.ownershipResolver().identity().nodeId();
                    if (currentNode.equals(expectedOwner)) {
                        MemoryBinding binding = binder.bind(auth, Optional.of(ns));
                        assertThat(binding).isNotNull();
                        assertThat(binding.namespaceId()).isEqualTo(ns);
                    } else {
                        assertThatThrownBy(() -> binder.bind(auth, Optional.of(ns)))
                                .isInstanceOf(NamespaceNotOwnedException.class)
                                .satisfies(ex -> {
                                    NamespaceNotOwnedException nne = (NamespaceNotOwnedException) ex;
                                    assertThat(nne.ownerId()).isEqualTo(expectedOwner);
                                });
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Concurrency: Concurrent requests to 3 nodes yield exactly 1 accept and 2 NOT_OWNER refusals (Invariant J1)")
    void testConcurrentWritesYieldExactlyOneAcceptor() throws Exception {
        String testNs = "ns-concurrent-write-check";
        mockNamespaceInCatalog(testNs);
        Authentication auth = new TestingAuthenticationToken(ACCOUNT_ID, "secret", "ROLE_USER");

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int iteration = 0; iteration < 50; iteration++) {
                List<Callable<Object>> tasks = List.of(
                        () -> {
                            try {
                                return node1Binder.bind(auth, Optional.of(testNs));
                            } catch (Exception e) {
                                return e;
                            }
                        },
                        () -> {
                            try {
                                return node2Binder.bind(auth, Optional.of(testNs));
                            } catch (Exception e) {
                                return e;
                            }
                        },
                        () -> {
                            try {
                                return node3Binder.bind(auth, Optional.of(testNs));
                            } catch (Exception e) {
                                return e;
                            }
                        }
                );

                List<Future<Object>> futures = executor.invokeAll(tasks);
                int accepted = 0;
                int refused = 0;
                String winningOwner = null;

                for (Future<Object> f : futures) {
                    Object result = f.get();
                    if (result instanceof MemoryBinding binding) {
                        accepted++;
                    } else if (result instanceof NamespaceNotOwnedException nne) {
                        refused++;
                        if (winningOwner == null) {
                            winningOwner = nne.ownerId();
                        } else {
                            assertThat(nne.ownerId()).isEqualTo(winningOwner);
                        }
                    } else {
                        throw new AssertionError("Unexpected result: " + result);
                    }
                }

                assertThat(accepted).as("Exactly one node must accept (Invariant J1)").isEqualTo(1);
                assertThat(refused).as("Exactly two nodes must refuse (NOT_OWNER)").isEqualTo(2);
                assertThat(winningOwner).isNotNull();
            }
        }
    }
}
