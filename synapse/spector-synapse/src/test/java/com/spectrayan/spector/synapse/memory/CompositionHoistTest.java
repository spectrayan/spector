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
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.ParallelEmbeddingPipeline;
import com.spectrayan.spector.synapse.catalog.*;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Group 1 Composition Hoist Assertions (R12 / Tasks 1.2-1.5)")
class CompositionHoistTest {

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
        synapseProps.getMemory().setDimensions(16);

        mockEmbedder = mock(EmbeddingProvider.class);
        when(mockEmbedder.embed(any(String.class))).thenReturn(
                com.spectrayan.spector.provider.embedding.EmbeddingResult.of(new float[16], "mock"));
        when(mockEmbedder.embedBatch(any())).thenReturn(
                List.of(com.spectrayan.spector.provider.embedding.EmbeddingResult.of(new float[16], "mock")));

        embedderProvider = mock(ObjectProvider.class);
        when(embedderProvider.getIfAvailable()).thenReturn(mockEmbedder);

        objectMapperProvider = mock(ObjectProvider.class);
        when(objectMapperProvider.getIfAvailable(any())).thenReturn(new ObjectMapper());
    }

    @Test
    @DisplayName("Task 1.3 & 1.5: Embedding wrappers and pipeline are hoisted and shared across cold binds")
    void testEmbeddingPipelineHoistedAcrossBinds() throws Exception {
        Account account = new Account(
                ACCOUNT_ID, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                "Test User", new AccountQuotas(10, 10, -1, -1),
                new AccountFlags(true, true, true), "ns-default", Instant.now()
        );
        when(catalog.getOrCreateAccount(ACCOUNT_ID)).thenReturn(account);

        NamespaceRecord rec1 = new NamespaceRecord(
                "ns-1", "ns-1", ACCOUNT_ID, NamespaceType.PROJECT,
                NamespaceStatus.ACTIVE, "ns-1", "", null, Instant.now(), null
        );
        NamespaceRecord rec2 = new NamespaceRecord(
                "ns-2", "ns-2", ACCOUNT_ID, NamespaceType.PROJECT,
                NamespaceStatus.ACTIVE, "ns-2", "", null, Instant.now(), null
        );
        when(catalog.resolve(ACCOUNT_ID, "ns-1")).thenReturn(Optional.of(rec1));
        when(catalog.resolve(ACCOUNT_ID, "ns-2")).thenReturn(Optional.of(rec2));

        NamespaceResolver resolver = new NamespaceResolver(
                catalog, synapseProps, embedderProvider, null, null,
                objectMapperProvider, null, null, null, null, null, 10
        );

        // Before any bind, hoisted pipeline is null
        assertThat(resolver.hoistedPipeline()).isNull();

        // Bind 1: cold bind for ns-1
        SpectorMemory mem1 = resolver.resolve(ACCOUNT_ID, "ns-1");
        assertThat(mem1).isNotNull();
        ParallelEmbeddingPipeline pipeline1 = resolver.hoistedPipeline();
        assertThat(pipeline1).isNotNull();

        // Bind 2: cold bind for ns-2
        SpectorMemory mem2 = resolver.resolve(ACCOUNT_ID, "ns-2");
        assertThat(mem2).isNotNull();
        ParallelEmbeddingPipeline pipeline2 = resolver.hoistedPipeline();

        // Pipeline instance MUST be identical (hoisted, not re-wrapped)
        assertThat(pipeline2).isSameAs(pipeline1);

        resolver.close();
    }

    @Test
    @DisplayName("Task 1.4: Startup fails fast when aisme.enabled && enablePredictiveCoding && maxNamespaces > 8")
    void testAismePredictiveCodingFailFastWhenMaxNamespacesExceeds8() {
        MemoryProperties memProps = new MemoryProperties();
        memProps.setDimensions(768);
        memProps.setMaxNamespaces(9); // > 8
        memProps.getAisme().setEnabled(true);
        memProps.getAisme().setEnablePredictiveCoding(true);

        SpectorProperties props = SpectorProperties.of(memProps);

        assertThatThrownBy(() -> DefaultSpectorMemory.builder(props)
                .embeddingProvider(mockEmbedder)
                .build())
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("PredictiveCodingNetwork")
                .hasMessageContaining("MiB per namespace")
                .satisfies(ex -> {
                    SpectorValidationException sve = (SpectorValidationException) ex;
                    assertThat(sve.errorCode()).isEqualTo(ErrorCode.CONFIG_VALUE_INVALID);
                });
    }

    @Test
    @DisplayName("Task 1.4: Startup succeeds when maxNamespaces <= 8 even with AISME predictive coding enabled")
    void testAismePredictiveCodingAllowedWhenMaxNamespacesAtOrBelow8() {
        MemoryProperties memProps = new MemoryProperties();
        memProps.setDimensions(16);
        memProps.setMaxNamespaces(8); // <= 8
        memProps.getAisme().setEnabled(true);
        memProps.getAisme().setEnablePredictiveCoding(true);

        SpectorProperties props = SpectorProperties.of(memProps);

        SpectorMemory mem = DefaultSpectorMemory.builder(props)
                .embeddingProvider(mockEmbedder)
                .build();
        assertThat(mem).isNotNull();
        mem.close();
    }

    @Test
    @DisplayName("Task 1.4: Startup succeeds when enablePredictiveCoding is false even with maxNamespaces > 8")
    void testAismeAllowedWhenPredictiveCodingDisabled() {
        MemoryProperties memProps = new MemoryProperties();
        memProps.setDimensions(16);
        memProps.setMaxNamespaces(100); // default > 8
        memProps.getAisme().setEnabled(true);
        memProps.getAisme().setEnablePredictiveCoding(false); // disabled

        SpectorProperties props = SpectorProperties.of(memProps);

        SpectorMemory mem = DefaultSpectorMemory.builder(props)
                .embeddingProvider(mockEmbedder)
                .build();
        assertThat(mem).isNotNull();
        mem.close();
    }
}
