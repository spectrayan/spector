/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.pathway.reflect.relay;


import com.spectrayan.spector.memory.kernel.layout.EncodingHeader;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.memory.persist.PartitionManager;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.EpisodicMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.cortex.PartitionHandle;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.model.EpisodeRecord;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.neuromod.neurodivergent.IngestionHints;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;

/**
 * Unit tests for {@link ProceduralCrystallizationRelay}.
 */
class ProceduralCrystallizationRelayTest {

    private ProceduralCrystallizationRelay relay;

    @BeforeEach
    void setUp() {
        relay = new ProceduralCrystallizationRelay();
    }

    @Test
    void transmit_crystallizesProceduralSkillFromEpisodicTurns() {
        PartitionManager partitionManager = mock(PartitionManager.class);
        PartitionHandle handle = mock(PartitionHandle.class);
        CognitiveMemoryRouter router = mock(CognitiveMemoryRouter.class);
        EpisodicMemory logStore = mock(EpisodicMemory.class);
        RememberPathway rememberPathway = mock(RememberPathway.class);
        EmbeddingProvider embeddingProvider = mock(EmbeddingProvider.class);
        HyperEntityGraphMemory hyperEntityGraph = mock(HyperEntityGraphMemory.class);
        EntityDirectory entityDirectory = mock(EntityDirectory.class);

        when(partitionManager.snapshot()).thenReturn(List.of(handle));
        when(handle.router()).thenReturn(router);
        when(router.episodic()).thenReturn(logStore);

        when(logStore.unconsolidatedTurnOffsets()).thenReturn(List.of(100L, 200L));

        EpisodeRecord rec1 = mock(EpisodeRecord.class);
        when(rec1.sessionId()).thenReturn(42L);
        when(rec1.body()).thenReturn("User reported NPE on login endpoint".getBytes(StandardCharsets.UTF_8));

        EpisodeRecord rec2 = mock(EpisodeRecord.class);
        when(rec2.sessionId()).thenReturn(42L);
        when(rec2.body()).thenReturn("Added null check to auth context validator".getBytes(StandardCharsets.UTF_8));

        when(logStore.readTurns(List.of(100L, 200L), true)).thenReturn(List.of(rec1, rec2));

        EmbeddingResult embedResult = mock(EmbeddingResult.class);
        when(embedResult.vector()).thenReturn(new float[]{0.1f, 0.2f});
        when(embeddingProvider.embed(anyString())).thenReturn(embedResult);
        when(entityDirectory.intern(anyString(), anyString())).thenReturn(5);

        ReflectSignal signal = ReflectSignal.builder()
                .partitionManager(partitionManager)
                .rememberPathway(rememberPathway)
                .embeddingProvider(embeddingProvider)
                .hyperEntityGraph(hyperEntityGraph)
                .entityDirectory(entityDirectory)
                .build();

        boolean result = relay.transmit(signal);

        assertThat(result).isTrue();
        assertThat(signal.proceduralCrystallizedCount()).isGreaterThan(0);

        verify(rememberPathway).ingestCognitiveWithHeader(
                anyString(),
                anyString(),
                eq(new float[]{0.1f, 0.2f}),
                eq(MemoryType.PROCEDURAL),
                eq(new String[]{"procedural", "crystallized", "skill"}),
                eq(MemorySource.REFLECTED),
                any(com.spectrayan.spector.memory.kernel.layout.EncodingHeader.class)
        );

        verify(hyperEntityGraph).addHyperedge(
                eq(new int[]{5}),
                eq(new int[]{HyperEntityGraphMemory.ROLE_DERIVED_FROM}),
                eq(HyperEntityGraphMemory.TYPE_RELATIONSHIP),
                eq(1.0f),
                eq(0),
                any(Long.class)
        );
    }

    @Test
    void transmit_withIngestionTarget_crystallizesWithProvenanceFlagsAndSoulVersion() {
        PartitionManager partitionManager = mock(PartitionManager.class);
        PartitionHandle handle = mock(PartitionHandle.class);
        CognitiveMemoryRouter router = mock(CognitiveMemoryRouter.class);
        EpisodicMemory logStore = mock(EpisodicMemory.class);
        RememberPathway rememberPathway =
                mock(RememberPathway.class);
        when(rememberPathway.currentSoulVersion()).thenReturn((short) 4);
        EmbeddingProvider embeddingProvider = mock(EmbeddingProvider.class);

        when(partitionManager.snapshot()).thenReturn(List.of(handle));
        when(handle.router()).thenReturn(router);
        when(router.episodic()).thenReturn(logStore);

        when(logStore.unconsolidatedTurnOffsets()).thenReturn(List.of(100L, 200L));

        EpisodeRecord rec1 = mock(EpisodeRecord.class);
        when(rec1.sessionId()).thenReturn(42L);
        when(rec1.body()).thenReturn("Turn 1".getBytes(StandardCharsets.UTF_8));
        EpisodeRecord rec2 = mock(EpisodeRecord.class);
        when(rec2.sessionId()).thenReturn(42L);
        when(rec2.body()).thenReturn("Turn 2".getBytes(StandardCharsets.UTF_8));

        when(logStore.readTurns(List.of(100L, 200L), true)).thenReturn(List.of(rec1, rec2));

        EmbeddingResult embedResult = mock(EmbeddingResult.class);
        when(embedResult.vector()).thenReturn(new float[]{0.3f, 0.4f});
        when(embeddingProvider.embed(anyString())).thenReturn(embedResult);

        ReflectSignal signal = ReflectSignal.builder()
                .partitionManager(partitionManager)
                .rememberPathway(rememberPathway)
                .embeddingProvider(embeddingProvider)
                .build();

        boolean result = relay.transmit(signal);

        assertThat(result).isTrue();
        org.mockito.ArgumentCaptor<com.spectrayan.spector.memory.kernel.layout.EncodingHeader> captor =
                org.mockito.ArgumentCaptor.forClass(com.spectrayan.spector.memory.kernel.layout.EncodingHeader.class);
        verify(rememberPathway).ingestCognitiveWithHeader(
                anyString(), anyString(), eq(new float[]{0.3f, 0.4f}), eq(MemoryType.PROCEDURAL), any(), eq(MemorySource.REFLECTED), captor.capture()
        );

        var header = captor.getValue();
        assertThat(com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields.isCrystallized(header.consolidationFlags())).isTrue();
        assertThat(header.soulVersion()).isEqualTo((short) 4);
    }
}
