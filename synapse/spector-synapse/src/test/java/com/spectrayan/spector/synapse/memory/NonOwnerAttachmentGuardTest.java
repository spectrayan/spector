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
package com.spectrayan.spector.synapse.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.spectrayan.spector.cluster.OwnershipResolver;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.runtime.SpectorRuntime;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Validates that non-owner requests never invoke {@code runtime.attach} or mmap files into memory
 * (ADR-0034 §15.2, Req R5.6, R12.5, Invariant J5).
 */
class NonOwnerAttachmentGuardTest {

    @TempDir
    Path tempDir;

    private AccountCatalog catalog;
    private SpectorRuntime runtime;
    private EmbeddingProvider embedder;
    private SynapseProperties synapseProps;
    private NamespaceResolver namespaceResolver;
    private MemoryRegistry memoryRegistry;
    private ObjectMapper objectMapper;

    private static final String CELL_ID = "us-east-1";
    private static final String NODE_A = "node-a";
    private static final String NODE_B = "node-b";
    private static final String CALLER_ACCOUNT_ID = "acc-alice";
    private static final String CALLER_TENANT_ID = "tenant-marketing";

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> providerOf(T bean) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bean);
        when(provider.getIfAvailable(any())).thenAnswer(inv -> bean != null ? bean : ((java.util.function.Supplier<T>) inv.getArgument(0)).get());
        return provider;
    }

    @BeforeEach
    void setUp() {
        catalog = mock(AccountCatalog.class);
        runtime = mock(SpectorRuntime.class);
        embedder = mock(EmbeddingProvider.class);
        when(embedder.dimensions()).thenReturn(768);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        when(runtime.attach(anyString(), any())).thenAnswer(invocation -> mock(SpectorMemory.class));

        synapseProps = new SynapseProperties();
        synapseProps.setDataDir(tempDir.resolve("data").toString());
        synapseProps.getMemory().setPersistencePath(tempDir.resolve("cognitive").toString());
        synapseProps.auth().setEnabled(true);

        CellProperties cellProps = synapseProps.getCell();
        cellProps.setId(CELL_ID);
        cellProps.setRole("owner");
        cellProps.setNodeId(NODE_A);
        cellProps.getRing().setVersion(1);
        cellProps.getRing().setMembers(List.of(NODE_A, NODE_B));

        namespaceResolver = new NamespaceResolver(
                catalog,
                synapseProps,
                providerOf(embedder),
                providerOf(null),
                providerOf(null),
                providerOf(objectMapper),
                providerOf(null),
                providerOf(null),
                providerOf(null),
                providerOf(null),
                providerOf(null),
                100
        );
        namespaceResolver.setRuntime(runtime);

        memoryRegistry = mock(MemoryRegistry.class);
        when(memoryRegistry.namespaceResolver()).thenReturn(namespaceResolver);

        // Setup caller account
        Account alice = new Account(
                CALLER_ACCOUNT_ID, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                "Account " + CALLER_ACCOUNT_ID, AccountQuotas.forProfile(AccountProfile.HUMAN_SOLO),
                AccountFlags.forProfile(AccountProfile.HUMAN_SOLO),
                CALLER_ACCOUNT_ID, Instant.now(), CALLER_TENANT_ID, false
        );
        when(catalog.getOrCreateAccount(eq(CALLER_ACCOUNT_ID), any(), any())).thenReturn(alice);
        when(catalog.getOrCreateAccount(eq(CALLER_ACCOUNT_ID))).thenReturn(alice);
        when(catalog.getAccount(eq(CALLER_ACCOUNT_ID))).thenReturn(alice);
    }

    @Test
    @DisplayName("Non-owner node throws NamespaceNotOwnedException and NEVER invokes runtime.attach (Req R12.5, R5.6)")
    void testNonOwnerNeverAttaches() {
        OwnershipResolver resolver = synapseProps.getCell().toOwnershipResolver();
        MemoryRequestBinder binder = new MemoryRequestBinder(catalog, memoryRegistry, synapseProps, resolver);

        // Find a namespace owned by node-b (not node-a)
        String foreignNsId = null;
        for (int i = 0; i < 500; i++) {
            String candidate = "ns-candidate-" + i;
            RoutingKey key = new RoutingKey(CELL_ID, CALLER_TENANT_ID, candidate);
            if (!resolver.ownsLocally(key)) {
                foreignNsId = candidate;
                break;
            }
        }
        assertThat(foreignNsId).isNotNull();

        NamespaceRecord record = new NamespaceRecord(
                foreignNsId, "foreign-slug", CALLER_ACCOUNT_ID, NamespaceType.AGENT, NamespaceStatus.ACTIVE,
                "Foreign Agent", "Foreign memory", null, Instant.now(), Instant.now()
        );
        when(catalog.resolve(CALLER_ACCOUNT_ID, foreignNsId)).thenReturn(Optional.of(record));
        Grant grant = new Grant(
                "grant-1", GrantObjectType.NAMESPACE, foreignNsId, CALLER_ACCOUNT_ID,
                PrincipalType.ACCOUNT, GrantRole.OWNER, Set.of(), "admin", Instant.now(), null, null
        );
        when(catalog.authorize(CALLER_ACCOUNT_ID, foreignNsId, GrantRole.READER))
                .thenReturn(Optional.of(grant));

        Authentication auth = new TestingAuthenticationToken(CALLER_ACCOUNT_ID, "credentials", "ROLE_USER");

        final String targetNs = foreignNsId;
        assertThatThrownBy(() -> binder.bind(auth, Optional.of(targetNs)))
                .isInstanceOf(NamespaceNotOwnedException.class)
                .satisfies(ex -> {
                    NamespaceNotOwnedException notOwned = (NamespaceNotOwnedException) ex;
                    assertThat(notOwned.namespaceId()).isEqualTo(targetNs);
                    assertThat(notOwned.ownerId()).isEqualTo(NODE_B);
                    assertThat(notOwned.epoch()).isEqualTo(1L);
                });

        // Critical assertion: runtime.attach was NEVER called
        verify(runtime, never()).attach(anyString(), any());
        verify(catalog, never()).recordAccess(anyString());
    }

    @Test
    @DisplayName("Authoritative owner binds successfully and invokes runtime.attach exactly once")
    void testOwnerAttachesSuccessfully() {
        OwnershipResolver resolver = synapseProps.getCell().toOwnershipResolver();
        MemoryRequestBinder binder = new MemoryRequestBinder(catalog, memoryRegistry, synapseProps, resolver);

        // Find a namespace owned by node-a
        String localNsId = null;
        for (int i = 0; i < 500; i++) {
            String candidate = "ns-local-" + i;
            RoutingKey key = new RoutingKey(CELL_ID, CALLER_TENANT_ID, candidate);
            if (resolver.ownsLocally(key)) {
                localNsId = candidate;
                break;
            }
        }
        assertThat(localNsId).isNotNull();

        NamespaceRecord record = new NamespaceRecord(
                localNsId, "local-slug", CALLER_ACCOUNT_ID, NamespaceType.AGENT, NamespaceStatus.ACTIVE,
                "Local Agent", "Local memory", null, Instant.now(), Instant.now()
        );
        when(catalog.resolve(CALLER_ACCOUNT_ID, localNsId)).thenReturn(Optional.of(record));
        Grant grant = new Grant(
                "grant-2", GrantObjectType.NAMESPACE, localNsId, CALLER_ACCOUNT_ID,
                PrincipalType.ACCOUNT, GrantRole.OWNER, Set.of(), "admin", Instant.now(), null, null
        );
        when(catalog.authorize(CALLER_ACCOUNT_ID, localNsId, GrantRole.READER))
                .thenReturn(Optional.of(grant));

        Authentication auth = new TestingAuthenticationToken(CALLER_ACCOUNT_ID, "credentials", "ROLE_USER");

        MemoryBinding binding = binder.bind(auth, Optional.of(localNsId));
        assertThat(binding).isNotNull();
        assertThat(binding.namespaceId()).isEqualTo(localNsId);

        // Verify runtime.attach was invoked exactly once for the locally-owned namespace
        verify(runtime, times(1)).attach(eq(localNsId), any());
        verify(catalog, atLeastOnce()).recordAccess(eq(localNsId));
    }

    @Test
    @DisplayName("Negative verification (R12.8): Standalone resolver on foreign key WOULD attach without check")
    void testNegativeVerificationStandaloneWouldAttach() {
        OwnershipResolver clusterResolver = synapseProps.getCell().toOwnershipResolver();
        String foreignNsId = null;
        for (int i = 0; i < 500; i++) {
            String candidate = "ns-negative-" + i;
            RoutingKey key = new RoutingKey(CELL_ID, CALLER_TENANT_ID, candidate);
            if (!clusterResolver.ownsLocally(key)) {
                foreignNsId = candidate;
                break;
            }
        }
        assertThat(foreignNsId).isNotNull();

        NamespaceRecord record = new NamespaceRecord(
                foreignNsId, "negative-slug", CALLER_ACCOUNT_ID, NamespaceType.AGENT, NamespaceStatus.ACTIVE,
                "Negative Agent", "Negative memory", null, Instant.now(), Instant.now()
        );
        when(catalog.resolve(CALLER_ACCOUNT_ID, foreignNsId)).thenReturn(Optional.of(record));
        Grant grant = new Grant(
                "grant-3", GrantObjectType.NAMESPACE, foreignNsId, CALLER_ACCOUNT_ID,
                PrincipalType.ACCOUNT, GrantRole.OWNER, Set.of(), "admin", Instant.now(), null, null
        );
        when(catalog.authorize(CALLER_ACCOUNT_ID, foreignNsId, GrantRole.READER))
                .thenReturn(Optional.of(grant));

        // Create binder with standalone resolver (simulating pre-cluster behavior)
        MemoryRequestBinder standaloneBinder = new MemoryRequestBinder(
                catalog, memoryRegistry, synapseProps, OwnershipResolver.standalone());

        Authentication auth = new TestingAuthenticationToken(CALLER_ACCOUNT_ID, "credentials", "ROLE_USER");

        // Standalone succeeds and attaches
        MemoryBinding binding = standaloneBinder.bind(auth, Optional.of(foreignNsId));
        assertThat(binding).isNotNull();
        verify(runtime, times(1)).attach(eq(foreignNsId), any());
    }
}
