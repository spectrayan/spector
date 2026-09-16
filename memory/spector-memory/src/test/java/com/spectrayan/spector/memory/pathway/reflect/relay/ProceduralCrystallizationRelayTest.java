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

import com.spectrayan.spector.memory.pathway.FakeRememberPathway;

import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;


import com.spectrayan.spector.kernel.engram.EncodingHeader;
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
import com.spectrayan.spector.kernel.store.EpisodicMemory;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.memory.cortex.PartitionHandle;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.kernel.store.HyperEntityGraphMemory;
import com.spectrayan.spector.kernel.api.EpisodeRecord;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.neuromod.neurodivergent.RememberHints;
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
        FakeRememberPathway fakeRemember = new FakeRememberPathway();
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
                .embeddingProvider(embeddingProvider)
                .hyperEntityGraph(hyperEntityGraph)
                .entityDirectory(entityDirectory)
                .build();
        signal.bind(fakeRemember.inContext("test"));

        boolean result = relay.transmit(signal);

        assertThat(result).isTrue();
        assertThat(signal.proceduralCrystallizedCount()).isGreaterThan(0);

        assertThat(fakeRemember.invocationCount()).isEqualTo(1);
        final var crystallized = fakeRemember.received().getFirst();
        assertThat(crystallized.vector()).containsExactly(0.1f, 0.2f);
        assertThat(crystallized.type()).isEqualTo(MemoryType.PROCEDURAL);
        assertThat(crystallized.tags())
                .containsExactly("procedural", "crystallized", "skill");
        assertThat(crystallized.source()).isEqualTo(MemorySource.REFLECTED);
        assertThat(crystallized.header()).isNotNull();

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
    void transmit_withRememberPathway_crystallizesWithProvenanceFlagsAndSoulVersion() {
        PartitionManager partitionManager = mock(PartitionManager.class);
        PartitionHandle handle = mock(PartitionHandle.class);
        CognitiveMemoryRouter router = mock(CognitiveMemoryRouter.class);
        EpisodicMemory logStore = mock(EpisodicMemory.class);
        FakeRememberPathway fakeRemember = new FakeRememberPathway((short) 4);
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
                .embeddingProvider(embeddingProvider)
                .build();
        signal.bind(fakeRemember.inContext("test"));

        boolean result = relay.transmit(signal);

        assertThat(result).isTrue();
        assertThat(fakeRemember.invocationCount()).isEqualTo(1);
        final var crystallized = fakeRemember.received().getFirst();
        assertThat(crystallized.vector()).containsExactly(0.3f, 0.4f);
        assertThat(crystallized.type()).isEqualTo(MemoryType.PROCEDURAL);
        assertThat(crystallized.source()).isEqualTo(MemorySource.REFLECTED);
        // Soul version now arrives via the context's SoulVersionSource, not a RememberPathway.
        var header = crystallized.header();

        assertThat(com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields.isCrystallized(header.consolidationFlags())).isTrue();
        assertThat(header.soulVersion()).isEqualTo((short) 4);
    }
}
