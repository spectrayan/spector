/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.memory.pathway.reflect.relay;

import com.spectrayan.spector.memory.pathway.FakeRememberPathway;

import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.memory.pathway.SoulVersionSource;
import com.spectrayan.spector.memory.pathway.remember.relay.RememberSignal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    void transmit_withoutSkillPathway_doesNotInvokeRememberPathway() {
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
        when(rec1.sequenceId()).thenReturn(1);
        when(rec1.body()).thenReturn("User reported NPE on login endpoint".getBytes(StandardCharsets.UTF_8));

        EpisodeRecord rec2 = mock(EpisodeRecord.class);
        when(rec2.sessionId()).thenReturn(42L);
        when(rec2.sequenceId()).thenReturn(2);
        when(rec2.body()).thenReturn("Added null check to auth context validator".getBytes(StandardCharsets.UTF_8));

        when(logStore.readTurns(List.of(100L, 200L), true)).thenReturn(List.of(rec1, rec2));

        ReflectSignal signal = ReflectSignal.builder()
                .partitionManager(partitionManager)
                .embeddingProvider(embeddingProvider)
                .hyperEntityGraph(hyperEntityGraph)
                .entityDirectory(entityDirectory)
                .build();
        // Catalog contains only RememberPathway (no SkillPathway)
        signal.bind(fakeRemember.inContext("test"));

        boolean result = relay.transmit(signal);

        assertThat(result).isTrue();
        // ADR invariant: when SkillPathway is absent, ProceduralCrystallizationRelay does NOT fall back to RememberPathway
        assertThat(signal.proceduralCrystallizedCount()).isZero();
        assertThat(fakeRemember.invocationCount()).isZero();
        verifyNoInteractions(hyperEntityGraph);
    }

    @Test
    void transmit_withSingleTurnSession_doesNotDispatch() {
        PartitionManager partitionManager = mock(PartitionManager.class);
        PartitionHandle handle = mock(PartitionHandle.class);
        CognitiveMemoryRouter router = mock(CognitiveMemoryRouter.class);
        EpisodicMemory logStore = mock(EpisodicMemory.class);
        FakeRememberPathway fakeRemember = new FakeRememberPathway();

        when(partitionManager.snapshot()).thenReturn(List.of(handle));
        when(handle.router()).thenReturn(router);
        when(router.episodic()).thenReturn(logStore);

        when(logStore.unconsolidatedTurnOffsets()).thenReturn(List.of(100L));

        EpisodeRecord rec1 = mock(EpisodeRecord.class);
        when(rec1.sessionId()).thenReturn(42L);
        when(rec1.sequenceId()).thenReturn(1);
        when(rec1.body()).thenReturn("Single turn only".getBytes(StandardCharsets.UTF_8));

        when(logStore.readTurns(List.of(100L), true)).thenReturn(List.of(rec1));

        com.spectrayan.spector.commons.pathway.DefaultPathwayCatalog catalog = fakeRemember.inCatalog();
        com.spectrayan.spector.memory.pathway.skill.SkillPathway skillPathway = com.spectrayan.spector.memory.pathway.skill.SkillPathway.standard();
        catalog.register(com.spectrayan.spector.memory.pathway.skill.SkillPathway.class, skillPathway);

        com.spectrayan.spector.commons.pathway.PathwayContext ctx = com.spectrayan.spector.commons.pathway.DefaultPathwayContext.builder()
                .namespaceId("test-ns")
                .catalog(catalog)
                .build();

        ReflectSignal signal = ReflectSignal.builder()
                .partitionManager(partitionManager)
                .build();
        signal.bind(ctx);

        boolean result = relay.transmit(signal);

        assertThat(result).isTrue();
        assertThat(signal.proceduralCrystallizedCount()).isZero();
        assertThat(fakeRemember.invocationCount()).isZero();
    }

    @Test
    void transmit_withSkillPathway_dispatchesViaSkillPathway() {
        PartitionManager partitionManager = mock(PartitionManager.class);
        PartitionHandle handle = mock(PartitionHandle.class);
        CognitiveMemoryRouter router = mock(CognitiveMemoryRouter.class);
        EpisodicMemory logStore = mock(EpisodicMemory.class);
        FakeRememberPathway fakeRemember = new FakeRememberPathway((short) 5);
        EmbeddingProvider embeddingProvider = mock(EmbeddingProvider.class);
        HyperEntityGraphMemory hyperEntityGraph = mock(HyperEntityGraphMemory.class);
        EntityDirectory entityDirectory = mock(EntityDirectory.class);

        when(partitionManager.snapshot()).thenReturn(List.of(handle));
        when(handle.router()).thenReturn(router);
        when(router.episodic()).thenReturn(logStore);

        when(logStore.unconsolidatedTurnOffsets()).thenReturn(List.of(100L, 200L));

        EpisodeRecord rec1 = mock(EpisodeRecord.class);
        when(rec1.sessionId()).thenReturn(42L);
        when(rec1.sequenceId()).thenReturn(1);
        when(rec1.body()).thenReturn("Turn 1".getBytes(StandardCharsets.UTF_8));
        EpisodeRecord rec2 = mock(EpisodeRecord.class);
        when(rec2.sessionId()).thenReturn(42L);
        when(rec2.sequenceId()).thenReturn(2);
        when(rec2.body()).thenReturn("Turn 2".getBytes(StandardCharsets.UTF_8));

        when(logStore.readTurns(List.of(100L, 200L), true)).thenReturn(List.of(rec1, rec2));

        EmbeddingResult embedResult = mock(EmbeddingResult.class);
        when(embedResult.vector()).thenReturn(new float[]{0.5f, 0.6f});
        when(embeddingProvider.embed(anyString())).thenReturn(embedResult);

        com.spectrayan.spector.commons.pathway.DefaultPathwayCatalog catalog = fakeRemember.inCatalog();
        com.spectrayan.spector.memory.pathway.skill.SkillPathway skillPathway = com.spectrayan.spector.memory.pathway.skill.SkillPathway.standard();
        catalog.register(com.spectrayan.spector.memory.pathway.skill.SkillPathway.class, skillPathway);

        com.spectrayan.spector.commons.pathway.PathwayContext ctx = com.spectrayan.spector.commons.pathway.DefaultPathwayContext.builder()
                .namespaceId("test-ns")
                .catalog(catalog)
                .bind(SoulVersionSource.class, () -> (short) 5)
                .build();

        ReflectSignal signal = ReflectSignal.builder()
                .partitionManager(partitionManager)
                .embeddingProvider(embeddingProvider)
                .hyperEntityGraph(hyperEntityGraph)
                .entityDirectory(entityDirectory)
                .build();
        signal.bind(ctx);

        boolean result = relay.transmit(signal);

        assertThat(result).isTrue();
        assertThat(signal.proceduralCrystallizedCount()).isEqualTo(1);
        assertThat(fakeRemember.invocationCount()).isEqualTo(1);

        RememberSignal rs = fakeRemember.received().getFirst();
        assertThat(rs.type()).isEqualTo(MemoryType.PROCEDURAL);
        assertThat(rs.source()).isEqualTo(MemorySource.PROCEDURAL);
        assertThat(rs.header()).isNotNull();
        assertThat(rs.header().importance()).isEqualTo(0.15f);
        assertThat(rs.header().source()).isEqualTo(com.spectrayan.spector.kernel.api.EngramSource.DISTILLED);
        assertThat(rs.header().consolidationFlags()).isEqualTo(EncodingHeaderFields.FLAG_CRYSTALLIZED);
        assertThat(rs.consolidationFlagsOverlay()).isEqualTo(EncodingHeaderFields.FLAG_CRYSTALLIZED);
    }
}
