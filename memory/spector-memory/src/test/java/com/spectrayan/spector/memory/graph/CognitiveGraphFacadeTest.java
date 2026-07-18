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

import com.spectrayan.spector.memory.hebbian.HebbianGraph;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.GraphNeighborhood;
import com.spectrayan.spector.memory.temporal.TemporalChain;
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
        var temporalChain = mock(TemporalChain.class);
        var entityGraph = mock(EntityGraph.class);
        var hyperEntityGraph = mock(HyperEntityGraph.class);
        var index = mock(MemoryIndex.class);

        var facade = new CognitiveGraphFacade(
                hebbianGraph, temporalChain, entityGraph, hyperEntityGraph, index
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
        when(entityGraph.nameIndex()).thenReturn(nameIndex);
        when(entityGraph.memoriesForEntity(10)).thenReturn(new int[]{0, 1});
        when(entityGraph.entityType(10)).thenReturn("CONCEPT");

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
}
