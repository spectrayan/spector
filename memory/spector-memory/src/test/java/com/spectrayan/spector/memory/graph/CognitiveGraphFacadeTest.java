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
package com.spectrayan.spector.memory.graph;
import com.spectrayan.spector.kernel.store.HyperEntityGraphMemory;

import com.spectrayan.spector.memory.graph.hebbian.HebbianGraph;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.kernel.store.HyperEntityGraphMemory.HyperEdge;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.GraphNeighborhood;
import com.spectrayan.spector.kernel.store.TemporalChainMemory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CognitiveGraphFacadeTest {

    @Test
    @DisplayName("neighborhood — retrieves Entity relationships and shared entities")
    void neighborhoodRetrievesEntityAndSharedEntityRelationships() {
        // Arrange
        var hebbianGraph = mock(HebbianGraph.class);
        var temporalChain = mock(TemporalChainMemory.class);
        var entityDirectory = mock(EntityDirectory.class);
        var hyperEntityGraph = mock(HyperEntityGraphMemory.class);
        var index = mock(MemoryIndex.class);

        var facade = new CognitiveGraphFacade(
                hebbianGraph, temporalChain, entityDirectory, hyperEntityGraph, index
        );

        String mem1 = "mem-1";
        String mem2 = "mem-2";

        // Mappings: slot 0 -> mem-1, slot 1 -> mem-2
        doAnswer(invocation -> {
            Map<Integer, String> slotToId = invocation.getArgument(0);
            Map<String, Integer> idToSlot = invocation.getArgument(1);
            slotToId.put(0, mem1);
            slotToId.put(1, mem2);
            idToSlot.put(mem1, 0);
            idToSlot.put(mem2, 1);
            return null;
        }).when(index).buildGraphSlotMappings(anyMap(), anyMap());

        // Entities: "ENTITY_1" -> id 10, associated with slots 0 and 1 (shared entity)
        Map<String, Integer> nameIndex = new LinkedHashMap<>();
        nameIndex.put("ENTITY_1", 10);
        when(entityDirectory.nameIndex()).thenReturn(nameIndex);
        when(entityDirectory.memoriesForEntity(10)).thenReturn(new int[]{0, 1});
        when(entityDirectory.entityType(10)).thenReturn("CONCEPT");

        var mockEdge = mock(HyperEdge.class);
        when(mockEdge.memoryIdx()).thenReturn(1);
        when(hyperEntityGraph.findHyperedgesForEntity(10)).thenReturn(List.of(mockEdge));

        // Set up inspector to return records for mem-1 and mem-2
        var rec1 = mock(CognitiveRecord.class);
        var rec2 = mock(CognitiveRecord.class);
        when(rec1.text()).thenReturn("Memory one text");
        when(rec2.text()).thenReturn("Memory two text");
        Function<String, CognitiveRecord> inspector = id -> id.equals(mem1) ? rec1 : rec2;

        // Act
        GraphNeighborhood neighborhood = facade.neighborhood(mem1, 1, inspector);

        // Assert
        assertThat(neighborhood).isNotNull();
        assertThat(neighborhood.centerId()).isEqualTo(mem1);

        // Verify entity edges are collected
        var edges = neighborhood.edges();
        assertThat(edges).isNotEmpty();

        boolean hasEntityEdge = edges.stream().anyMatch(e -> e.type().equals("ENTITY"));
        assertThat(hasEntityEdge).isTrue();
    }

    @Test
    @DisplayName("neighborhood — discovers neighbors via EntityDirectory even when HyperEntityGraph is empty")
    void neighborhoodDiscoversNeighborsViaEntityDirectoryWhenHyperGraphEmpty() {
        var hebbianGraph = mock(HebbianGraph.class);
        var temporalChain = mock(TemporalChainMemory.class);
        var entityDirectory = mock(EntityDirectory.class);
        var hyperEntityGraph = mock(HyperEntityGraphMemory.class);
        var index = mock(MemoryIndex.class);

        var facade = new CognitiveGraphFacade(
                hebbianGraph, temporalChain, entityDirectory, hyperEntityGraph, index
        );

        String memA = "mem-A";
        String memB = "mem-B";

        doAnswer(invocation -> {
            Map<Integer, String> slotToId = invocation.getArgument(0);
            Map<String, Integer> idToSlot = invocation.getArgument(1);
            slotToId.put(5, memA);
            slotToId.put(9, memB);
            idToSlot.put(memA, 5);
            idToSlot.put(memB, 9);
            return null;
        }).when(index).buildGraphSlotMappings(anyMap(), anyMap());

        Map<String, Integer> nameIndex = new LinkedHashMap<>();
        nameIndex.put("Quantum", 20);
        when(entityDirectory.nameIndex()).thenReturn(nameIndex);
        when(entityDirectory.memoriesForEntity(20)).thenReturn(new int[]{5, 9});
        when(entityDirectory.entityType(20)).thenReturn("PROJECT");

        // HyperEntityGraph has NO edges for this entity (ADR-0003 bundle migration scenario)
        when(hyperEntityGraph.findHyperedgesForEntity(20)).thenReturn(List.of());

        var recA = mock(CognitiveRecord.class);
        var recB = mock(CognitiveRecord.class);
        when(recA.text()).thenReturn("Quantum architecture spec");
        when(recB.text()).thenReturn("Quantum performance results");
        Function<String, CognitiveRecord> inspector = id -> id.equals(memA) ? recA : recB;

        // Act
        GraphNeighborhood neighborhood = facade.neighborhood(memA, 1, inspector);

        // Assert
        assertThat(neighborhood).isNotNull();
        assertThat(neighborhood.centerId()).isEqualTo(memA);
        assertThat(neighborhood.nodes()).extracting("id").containsExactlyInAnyOrder(memA, memB);
        assertThat(neighborhood.edges()).hasSize(1);
        assertThat(neighborhood.edges().getFirst().type()).isEqualTo("ENTITY");
        assertThat(neighborhood.edges().getFirst().sourceId()).isEqualTo(memA);
        assertThat(neighborhood.edges().getFirst().targetId()).isEqualTo(memB);
    }
}
