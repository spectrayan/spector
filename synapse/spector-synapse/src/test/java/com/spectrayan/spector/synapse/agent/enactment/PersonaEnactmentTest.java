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
package com.spectrayan.spector.synapse.agent.enactment;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.enactment.EnactMode;
import com.spectrayan.spector.memory.model.enactment.Enactment;
import com.spectrayan.spector.memory.model.enactment.SituationFrame;
import com.spectrayan.spector.synapse.agent.graph.CognitiveState;
import com.spectrayan.spector.synapse.agent.graph.nodes.EnactNode;
import com.spectrayan.spector.synapse.agent.service.CognitiveSoulService;
import com.spectrayan.spector.synapse.memory.MemoryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("Synapse Persona Enactment & Graph Node Invariants (ADR-0032)")
class PersonaEnactmentTest {

    private CognitiveSoulService soulService;
    private MemoryRegistry memoryRegistry;
    private EnactmentService enactmentService;

    @BeforeEach
    void setUp() {
        soulService = mock(CognitiveSoulService.class);
        memoryRegistry = mock(MemoryRegistry.class);
        enactmentService = new EnactmentService(soulService, memoryRegistry);
    }

    @Nested
    @DisplayName("Invariant I1: Namespace Isolation")
    class NamespaceIsolationTests {

        @Test
        @DisplayName("Enactment in namespace A only queries memory from namespace A, never namespace B")
        void namespaceIsolation_queriesOnlyTargetMemory() {
            SpectorMemory memoryA = mock(SpectorMemory.class);
            SpectorMemory memoryB = mock(SpectorMemory.class);

            when(memoryRegistry.resolveFor("ns-a")).thenReturn(memoryA);
            when(memoryRegistry.resolveFor("ns-b")).thenReturn(memoryB);

            when(memoryA.recall(anyString(), any(RecallOptions.class))).thenReturn(List.of());
            when(memoryB.recall(anyString(), any(RecallOptions.class))).thenReturn(List.of());

            AgentSoul soul = AgentSoul.builder().id("forge").name("Forge").build();
            when(soulService.getEffectiveSoul("forge")).thenReturn(soul);

            SituationFrame situation = SituationFrame.of("Check auth token leak");
            Enactment result = enactmentService.enact(situation, "ns-a", "forge", EnactMode.REACT);

            assertThat(result).isNotNull();
            verify(memoryA, atLeastOnce()).recall(anyString(), any(RecallOptions.class));
            verify(memoryB, never()).recall(anyString(), any(RecallOptions.class));
        }
    }

    @Nested
    @DisplayName("EnactNode StateGraph Execution")
    class EnactNodeTests {

        @Test
        @DisplayName("EnactNode mutates CognitiveState channels with enactment result and acting soul")
        void enactNode_executesAndUpdatesState() {
            SpectorMemory defaultMemory = mock(SpectorMemory.class);
            when(memoryRegistry.resolveFor("default")).thenReturn(defaultMemory);
            when(defaultMemory.recall(anyString(), any(RecallOptions.class))).thenReturn(List.of());

            AgentSoul jarvis = AgentSoul.builder().id("jarvis").name("Jarvis").build();
            when(soulService.getEffectiveSoul("jarvis")).thenReturn(jarvis);

            EnactNode enactNode = new EnactNode(enactmentService, "jarvis");

            CognitiveState state = new CognitiveState(Map.of(
                    "query", "How should we remediate this high-severity vulnerability?",
                    "acting_soul_id", "jarvis",
                    "enact_mode", "REACT"
            ));

            Map<String, Object> updates = enactNode.apply(state);

            assertThat(updates).containsKey("enactment");
            Enactment enactment = (Enactment) updates.get("enactment");
            assertThat(enactment).isNotNull();
            assertThat(enactment.situation().problem()).contains("high-severity vulnerability");
            assertThat(enactment.tense()).isEqualTo("FACT");
        }
    }
}
