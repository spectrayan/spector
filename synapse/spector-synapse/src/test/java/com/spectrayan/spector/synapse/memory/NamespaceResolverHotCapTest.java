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
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.synapse.catalog.*;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceHotCapExceededException;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("NamespaceResolver Hot Cap and Lease-Aware Eviction Tests (Phase 3)")
class NamespaceResolverHotCapTest {

    @TempDir
    Path tempDir;

    private AccountCatalog catalog;
    private SynapseProperties synapseProps;
    private ObjectProvider<EmbeddingProvider> embedderProvider;
    private ObjectProvider<ObjectMapper> objectMapperProvider;
    private EmbeddingProvider mockEmbedder;

    private static final String ACCOUNT_ID = "0195500000001";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        catalog = mock(AccountCatalog.class);
        synapseProps = new SynapseProperties();
        synapseProps.getMemory().setPersistencePath(tempDir.toString());
        synapseProps.getMemory().setDimensions(4);

        mockEmbedder = mock(EmbeddingProvider.class);
        when(mockEmbedder.embed(any(String.class))).thenReturn(
                com.spectrayan.spector.provider.embedding.EmbeddingResult.of(new float[]{0.1f, 0.2f, 0.3f, 0.4f}, "mock"));
        when(mockEmbedder.embedBatch(any())).thenReturn(
                List.of(com.spectrayan.spector.provider.embedding.EmbeddingResult.of(new float[]{0.1f, 0.2f, 0.3f, 0.4f}, "mock")));

        embedderProvider = mock(ObjectProvider.class);
        when(embedderProvider.getIfAvailable()).thenReturn(mockEmbedder);

        objectMapperProvider = mock(ObjectProvider.class);
        when(objectMapperProvider.getIfAvailable(any())).thenReturn(new ObjectMapper());
    }

    @Test
    @DisplayName("Hot cap eviction: unleased oldest instance is evicted when account hot cap is reached")
    void testHotCapEvictsUnleasedInstance() {
        // Account with maxHotNamespaces = 2
        Account account = new Account(
                ACCOUNT_ID, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                "Test User", new AccountQuotas(4, 2, -1, -1),
                new AccountFlags(true, true, true), "ns-default", Instant.now()
        );
        when(catalog.getOrCreateAccount(ACCOUNT_ID)).thenReturn(account);

        NamespaceRecord defaultRec = new NamespaceRecord(
                "ns-default", "default", ACCOUNT_ID, NamespaceType.DEFAULT,
                NamespaceStatus.ACTIVE, "Default", "", null, Instant.now(), null
        );
        NamespaceRecord proj1Rec = new NamespaceRecord(
                "ns-proj1", "project-1", ACCOUNT_ID, NamespaceType.PROJECT,
                NamespaceStatus.ACTIVE, "Project 1", "", null, Instant.now(), null
        );
        NamespaceRecord proj2Rec = new NamespaceRecord(
                "ns-proj2", "project-2", ACCOUNT_ID, NamespaceType.PROJECT,
                NamespaceStatus.ACTIVE, "Project 2", "", null, Instant.now(), null
        );

        when(catalog.resolve(ACCOUNT_ID, "default")).thenReturn(Optional.of(defaultRec));
        when(catalog.resolve(ACCOUNT_ID, "project-1")).thenReturn(Optional.of(proj1Rec));
        when(catalog.resolve(ACCOUNT_ID, "project-2")).thenReturn(Optional.of(proj2Rec));

        NamespaceResolver resolver = new NamespaceResolver(
                catalog, synapseProps, embedderProvider, null, null,
                objectMapperProvider, null, null, null, null, null, 10
        );

        // Open 1st (default)
        SpectorMemory mem1 = resolver.resolve(ACCOUNT_ID, "default");
        assertThat(mem1).isNotNull();
        assertThat(resolver.cachedInstanceCount()).isEqualTo(1);

        // Open 2nd (project-1)
        SpectorMemory mem2 = resolver.resolve(ACCOUNT_ID, "project-1");
        assertThat(mem2).isNotNull();
        assertThat(resolver.cachedInstanceCount()).isEqualTo(2);

        // Open 3rd (project-2): should evict oldest unleased (default) since cap is 2
        SpectorMemory mem3 = resolver.resolve(ACCOUNT_ID, "project-2");
        assertThat(mem3).isNotNull();
        assertThat(resolver.cachedInstanceCount()).isEqualTo(2);

        resolver.close();
    }

    @Test
    @DisplayName("Hot cap rejection: throws NamespaceHotCapExceededException when all hot instances are leased")
    void testHotCapThrowsWhenAllLeased() {
        // Account with maxHotNamespaces = 2
        Account account = new Account(
                ACCOUNT_ID, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                "Test User", new AccountQuotas(4, 2, -1, -1),
                new AccountFlags(true, true, true), "ns-default", Instant.now()
        );
        when(catalog.getOrCreateAccount(ACCOUNT_ID)).thenReturn(account);

        NamespaceRecord defaultRec = new NamespaceRecord(
                "ns-default", "default", ACCOUNT_ID, NamespaceType.DEFAULT,
                NamespaceStatus.ACTIVE, "Default", "", null, Instant.now(), null
        );
        NamespaceRecord proj1Rec = new NamespaceRecord(
                "ns-proj1", "project-1", ACCOUNT_ID, NamespaceType.PROJECT,
                NamespaceStatus.ACTIVE, "Project 1", "", null, Instant.now(), null
        );
        NamespaceRecord proj2Rec = new NamespaceRecord(
                "ns-proj2", "project-2", ACCOUNT_ID, NamespaceType.PROJECT,
                NamespaceStatus.ACTIVE, "Project 2", "", null, Instant.now(), null
        );

        when(catalog.resolve(ACCOUNT_ID, "default")).thenReturn(Optional.of(defaultRec));
        when(catalog.resolve(ACCOUNT_ID, "project-1")).thenReturn(Optional.of(proj1Rec));
        when(catalog.resolve(ACCOUNT_ID, "project-2")).thenReturn(Optional.of(proj2Rec));

        NamespaceResolver resolver = new NamespaceResolver(
                catalog, synapseProps, embedderProvider, null, null,
                objectMapperProvider, null, null, null, null, null, 10
        );

        // Open and lease 1st and 2nd
        SpectorMemory mem1 = resolver.resolve(ACCOUNT_ID, "default");
        SpectorMemory mem2 = resolver.resolve(ACCOUNT_ID, "project-1");

        DefaultSpectorMemory dsm1 = (DefaultSpectorMemory) mem1;
        DefaultSpectorMemory dsm2 = (DefaultSpectorMemory) mem2;

        dsm1.acquireLease();
        dsm2.acquireLease();
        try {
            assertThat(dsm1.hasActiveLeases()).isTrue();
            assertThat(dsm2.hasActiveLeases()).isTrue();

            // Attempting to open 3rd must fail because both are leased
            assertThatThrownBy(() -> resolver.resolve(ACCOUNT_ID, "project-2"))
                    .isInstanceOf(NamespaceHotCapExceededException.class);
        } finally {
            dsm1.releaseLease();
            dsm2.releaseLease();
        }

        resolver.close();
    }

    @Test
    @DisplayName("Process cap: evicts oldest unleased instance across process when maxInstances is reached")
    void testProcessCapEviction() {
        Account account = new Account(
                ACCOUNT_ID, PrincipalKind.SERVICE, AccountProfile.SERVICE,
                "Service Account", new AccountQuotas(100, 100, -1, -1),
                new AccountFlags(true, true, true), "ns-default", Instant.now()
        );
        when(catalog.getOrCreateAccount(ACCOUNT_ID)).thenReturn(account);

        NamespaceRecord r1 = new NamespaceRecord("ns-1", "p1", ACCOUNT_ID, NamespaceType.PROJECT, NamespaceStatus.ACTIVE, "1", "", null, Instant.now(), null);
        NamespaceRecord r2 = new NamespaceRecord("ns-2", "p2", ACCOUNT_ID, NamespaceType.PROJECT, NamespaceStatus.ACTIVE, "2", "", null, Instant.now(), null);
        NamespaceRecord r3 = new NamespaceRecord("ns-3", "p3", ACCOUNT_ID, NamespaceType.PROJECT, NamespaceStatus.ACTIVE, "3", "", null, Instant.now(), null);

        when(catalog.resolve(ACCOUNT_ID, "p1")).thenReturn(Optional.of(r1));
        when(catalog.resolve(ACCOUNT_ID, "p2")).thenReturn(Optional.of(r2));
        when(catalog.resolve(ACCOUNT_ID, "p3")).thenReturn(Optional.of(r3));

        // Resolver with process maxInstances = 2
        NamespaceResolver resolver = new NamespaceResolver(
                catalog, synapseProps, embedderProvider, null, null,
                objectMapperProvider, null, null, null, null, null, 2
        );

        resolver.resolve(ACCOUNT_ID, "p1");
        resolver.resolve(ACCOUNT_ID, "p2");
        assertThat(resolver.cachedInstanceCount()).isEqualTo(2);

        // Open 3rd: evicts p1
        resolver.resolve(ACCOUNT_ID, "p3");
        assertThat(resolver.cachedInstanceCount()).isEqualTo(2);

        resolver.close();
    }
}
