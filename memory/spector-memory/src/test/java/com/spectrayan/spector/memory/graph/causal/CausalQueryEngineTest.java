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
package com.spectrayan.spector.memory.graph.causal;

import com.spectrayan.spector.memory.graph.CognitiveGraphFacade;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory.HyperEdge;
import com.spectrayan.spector.memory.graph.OntologyConfig;
import com.spectrayan.spector.memory.graph.TypeRegistryMemory;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.temporal.TemporalFact;
import com.spectrayan.spector.memory.temporal.TemporalKnowledgeGraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CausalQueryEngineTest {

    @Test
    @DisplayName("traceWhy — single-hop backward causal reasoning")
    void traceWhyDirectOneHop() {
        var entityDir = mock(EntityDirectory.class);
        var hyperGraph = mock(HyperEntityGraphMemory.class);
        var tkg = mock(TemporalKnowledgeGraph.class);
        var typeReg = mock(TypeRegistryMemory.class);
        var index = mock(MemoryIndex.class);

        when(entityDir.nameIndex()).thenReturn(Map.of(
                "deployment failure", 0,
                "memory leak", 1
        ));
        when(entityDir.entityName(0)).thenReturn("Deployment Failure");
        when(entityDir.entityName(1)).thenReturn("Memory Leak");

        when(tkg.predicateRegistry()).thenReturn(typeReg);
        when(tkg.retractedFactIds()).thenReturn(java.util.Set.of());

        // Fact: Deployment Failure (0) CAUSED_BY (100) Memory Leak (1)
        TemporalFact fact = new TemporalFact(1, 0, 100, 1, 0, (short) 0, 0, Long.MAX_VALUE, 1000L, 0.9f, -1, (byte) 0);
        var temporalQuery = mock(com.spectrayan.spector.memory.temporal.TemporalQuery.class);
        when(temporalQuery.resolveAll()).thenReturn(List.of(fact));
        when(tkg.factsAbout(0)).thenReturn(temporalQuery);

        // For memory leak (1), no further causes
        var emptyQuery = mock(com.spectrayan.spector.memory.temporal.TemporalQuery.class);
        when(emptyQuery.resolveAll()).thenReturn(List.of());
        when(tkg.factsAbout(1)).thenReturn(emptyQuery);

        when(typeReg.nameOf(100)).thenReturn("CAUSED_BY");

        CausalQueryEngine engine = new CausalQueryEngine(
                entityDir, hyperGraph, tkg, typeReg,
                OntologyConfig.defaultInstance(), index, null
        );

        CausalChain chain = engine.traceWhy("Deployment Failure", 5);

        assertThat(chain.isEmpty()).isFalse();
        assertThat(chain.hopCount()).isEqualTo(1);
        assertThat(chain.targetEntity()).isEqualTo("Deployment Failure");
        assertThat(chain.rootCauseOrEffect()).isEqualTo("Memory Leak");
        assertThat(chain.direction()).isEqualTo(CausalChain.Direction.BACKWARD_WHY);
        assertThat(chain.steps().get(0).sourceEntity()).isEqualTo("Deployment Failure");
        assertThat(chain.steps().get(0).relation()).isEqualTo("CAUSED_BY");
        assertThat(chain.steps().get(0).targetEntity()).isEqualTo("Memory Leak");
        assertThat(chain.explanation()).contains("Deployment Failure").contains("Memory Leak").contains("Identified Root Cause");
    }

    @Test
    @DisplayName("traceWhy — multi-hop causal reasoning (3 hops)")
    void traceWhyMultiHopThreeSteps() {
        var entityDir = mock(EntityDirectory.class);
        var hyperGraph = mock(HyperEntityGraphMemory.class);
        var tkg = mock(TemporalKnowledgeGraph.class);
        var typeReg = mock(TypeRegistryMemory.class);
        var index = mock(MemoryIndex.class);

        when(entityDir.nameIndex()).thenReturn(Map.of(
                "outage", 0,
                "memory leak", 1,
                "unclosed arena", 2,
                "payload spike", 3
        ));
        when(entityDir.entityName(0)).thenReturn("Outage");
        when(entityDir.entityName(1)).thenReturn("Memory Leak");
        when(entityDir.entityName(2)).thenReturn("Unclosed Arena");
        when(entityDir.entityName(3)).thenReturn("Payload Spike");

        when(tkg.predicateRegistry()).thenReturn(typeReg);
        when(tkg.retractedFactIds()).thenReturn(java.util.Set.of());

        // Step 1: Outage (0) CAUSED_BY (100) Memory Leak (1)
        var q0 = mock(com.spectrayan.spector.memory.temporal.TemporalQuery.class);
        when(q0.resolveAll()).thenReturn(List.of(new TemporalFact(1, 0, 100, 1, 0, (short) 0, 0, Long.MAX_VALUE, 1000L, 0.95f, -1, (byte) 0)));
        when(tkg.factsAbout(0)).thenReturn(q0);

        // Step 2: Memory Leak (1) TRIGGERED_BY (101) Unclosed Arena (2)
        var q1 = mock(com.spectrayan.spector.memory.temporal.TemporalQuery.class);
        when(q1.resolveAll()).thenReturn(List.of(new TemporalFact(2, 1, 101, 2, 0, (short) 0, 0, Long.MAX_VALUE, 900L, 0.90f, -1, (byte) 0)));
        when(tkg.factsAbout(1)).thenReturn(q1);

        // Step 3: Unclosed Arena (2) RESULTED_FROM (102) Payload Spike (3)
        var q2 = mock(com.spectrayan.spector.memory.temporal.TemporalQuery.class);
        when(q2.resolveAll()).thenReturn(List.of(new TemporalFact(3, 2, 102, 3, 0, (short) 0, 0, Long.MAX_VALUE, 800L, 0.85f, -1, (byte) 0)));
        when(tkg.factsAbout(2)).thenReturn(q2);

        // Terminal: Payload Spike (3) has no further causes
        var q3 = mock(com.spectrayan.spector.memory.temporal.TemporalQuery.class);
        when(q3.resolveAll()).thenReturn(List.of());
        when(tkg.factsAbout(3)).thenReturn(q3);

        when(typeReg.nameOf(100)).thenReturn("CAUSED_BY");
        when(typeReg.nameOf(101)).thenReturn("TRIGGERED_BY");
        when(typeReg.nameOf(102)).thenReturn("RESULTED_FROM");

        CausalQueryEngine engine = new CausalQueryEngine(
                entityDir, hyperGraph, tkg, typeReg,
                OntologyConfig.defaultInstance(), index, null
        );

        CausalChain chain = engine.traceWhy("Outage", 5);

        assertThat(chain.isEmpty()).isFalse();
        assertThat(chain.hopCount()).isEqualTo(3);
        assertThat(chain.targetEntity()).isEqualTo("Outage");
        assertThat(chain.rootCauseOrEffect()).isEqualTo("Payload Spike");
        assertThat(chain.confidence()).isGreaterThan(0.7f);
        assertThat(chain.explanation()).contains("Outage").contains("Memory Leak").contains("Unclosed Arena").contains("Payload Spike");
    }

    @Test
    @DisplayName("traceEffects — forward consequence reasoning")
    void traceEffectsForwardConsequences() {
        var entityDir = mock(EntityDirectory.class);
        var hyperGraph = mock(HyperEntityGraphMemory.class);
        var tkg = mock(TemporalKnowledgeGraph.class);
        var typeReg = mock(TypeRegistryMemory.class);
        var index = mock(MemoryIndex.class);

        when(entityDir.nameIndex()).thenReturn(Map.of(
                "payload spike", 0,
                "unclosed arena", 1,
                "memory leak", 2
        ));
        when(entityDir.entityName(0)).thenReturn("Payload Spike");
        when(entityDir.entityName(1)).thenReturn("Unclosed Arena");
        when(entityDir.entityName(2)).thenReturn("Memory Leak");

        when(tkg.predicateRegistry()).thenReturn(typeReg);
        when(tkg.retractedFactIds()).thenReturn(java.util.Set.of());

        // Step 1: Payload Spike (0) TRIGGERED (200) Unclosed Arena (1)
        var q0 = mock(com.spectrayan.spector.memory.temporal.TemporalQuery.class);
        when(q0.resolveAll()).thenReturn(List.of(new TemporalFact(1, 0, 200, 1, 0, (short) 0, 0, Long.MAX_VALUE, 1000L, 0.95f, -1, (byte) 0)));
        when(tkg.factsAbout(0)).thenReturn(q0);

        // Step 2: Unclosed Arena (1) LED_TO (201) Memory Leak (2)
        var q1 = mock(com.spectrayan.spector.memory.temporal.TemporalQuery.class);
        when(q1.resolveAll()).thenReturn(List.of(new TemporalFact(2, 1, 201, 2, 0, (short) 0, 0, Long.MAX_VALUE, 1100L, 0.90f, -1, (byte) 0)));
        when(tkg.factsAbout(1)).thenReturn(q1);

        // Terminal: Memory Leak (2) has no further effects
        var q2 = mock(com.spectrayan.spector.memory.temporal.TemporalQuery.class);
        when(q2.resolveAll()).thenReturn(List.of());
        when(tkg.factsAbout(2)).thenReturn(q2);

        when(typeReg.nameOf(200)).thenReturn("TRIGGERED");
        when(typeReg.nameOf(201)).thenReturn("LED_TO");

        CausalQueryEngine engine = new CausalQueryEngine(
                entityDir, hyperGraph, tkg, typeReg,
                OntologyConfig.defaultInstance(), index, null
        );

        CausalChain chain = engine.traceEffects("Payload Spike", 5);

        assertThat(chain.isEmpty()).isFalse();
        assertThat(chain.hopCount()).isEqualTo(2);
        assertThat(chain.targetEntity()).isEqualTo("Payload Spike");
        assertThat(chain.rootCauseOrEffect()).isEqualTo("Memory Leak");
        assertThat(chain.direction()).isEqualTo(CausalChain.Direction.FORWARD_EFFECTS);
        assertThat(chain.explanation()).contains("Cascading Effects").contains("**Terminal Consequence:** `Memory Leak`");
    }

    @Test
    @DisplayName("traceWhy — cycle detection avoids infinite loop")
    void traceWhyCycleDetection() {
        var entityDir = mock(EntityDirectory.class);
        var hyperGraph = mock(HyperEntityGraphMemory.class);
        var tkg = mock(TemporalKnowledgeGraph.class);
        var typeReg = mock(TypeRegistryMemory.class);
        var index = mock(MemoryIndex.class);

        when(entityDir.nameIndex()).thenReturn(Map.of(
                "a", 0,
                "b", 1
        ));
        when(entityDir.entityName(0)).thenReturn("A");
        when(entityDir.entityName(1)).thenReturn("B");

        when(tkg.predicateRegistry()).thenReturn(typeReg);
        when(tkg.retractedFactIds()).thenReturn(java.util.Set.of());

        // Cycle: A caused by B, and B caused by A
        var q0 = mock(com.spectrayan.spector.memory.temporal.TemporalQuery.class);
        when(q0.resolveAll()).thenReturn(List.of(new TemporalFact(1, 0, 100, 1, 0, (short) 0, 0, Long.MAX_VALUE, 1000L, 0.9f, -1, (byte) 0)));
        when(tkg.factsAbout(0)).thenReturn(q0);

        var q1 = mock(com.spectrayan.spector.memory.temporal.TemporalQuery.class);
        when(q1.resolveAll()).thenReturn(List.of(new TemporalFact(2, 1, 100, 0, 0, (short) 0, 0, Long.MAX_VALUE, 900L, 0.9f, -1, (byte) 0)));
        when(tkg.factsAbout(1)).thenReturn(q1);

        when(typeReg.nameOf(100)).thenReturn("CAUSED_BY");

        CausalQueryEngine engine = new CausalQueryEngine(
                entityDir, hyperGraph, tkg, typeReg,
                OntologyConfig.defaultInstance(), index, null
        );

        // Must terminate safely after 1 step because A is already visited
        CausalChain chain = engine.traceWhy("A", 10);

        assertThat(chain.isEmpty()).isFalse();
        assertThat(chain.hopCount()).isEqualTo(1);
        assertThat(chain.rootCauseOrEffect()).isEqualTo("B");
    }

    @Test
    @DisplayName("facade — CognitiveGraphFacade exposes traceWhy and traceEffects cleanly")
    void facadeDelegation() {
        var entityDir = mock(EntityDirectory.class);
        var hyperGraph = mock(HyperEntityGraphMemory.class);
        var tkg = mock(TemporalKnowledgeGraph.class);
        var typeReg = mock(TypeRegistryMemory.class);
        var index = mock(MemoryIndex.class);

        when(entityDir.nameIndex()).thenReturn(Map.of("service error", 0));
        when(entityDir.entityName(0)).thenReturn("Service Error");
        when(tkg.predicateRegistry()).thenReturn(typeReg);
        when(tkg.retractedFactIds()).thenReturn(java.util.Set.of());

        var emptyQ = mock(com.spectrayan.spector.memory.temporal.TemporalQuery.class);
        when(emptyQ.resolveAll()).thenReturn(List.of());
        when(tkg.factsAbout(0)).thenReturn(emptyQ);

        CognitiveGraphFacade facade = new CognitiveGraphFacade(
                null, null, entityDir, hyperGraph, tkg, OntologyConfig.defaultInstance(), index
        );

        CausalChain chain = facade.traceWhy("Service Error");
        assertThat(chain.targetEntity()).isEqualTo("Service Error");
        assertThat(chain.isEmpty()).isTrue();
    }
}
