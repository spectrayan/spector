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
package com.spectrayan.spector.memory.pathway.pipeline.graph;

import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.EntityExtractor;
import com.spectrayan.spector.memory.cortex.index.IndexRecordMemory;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.graph.temporal.TemporalFact;
import com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph;
import com.spectrayan.spector.memory.graph.temporal.TemporalQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TemporalFactWeavingStageTest {

    @Mock
    private TemporalKnowledgeGraph tkg;

    @Mock
    private EntityDirectory entityDirectory;

    @Mock
    private EntityExtractor entityExtractor;

    @Mock
    private MemoryIndex index;

    @Test
    @DisplayName("Weaves temporal facts via fast EntityDirectory and index slot without calling LLM extractor")
    void testFastZeroLlmWeaving() {
        when(tkg.factCount()).thenReturn(5);

        IndexRecordMemory.MemoryLocation loc = new IndexRecordMemory.MemoryLocation(MemoryType.SEMANTIC, 0L, 7);
        when(index.locate("mem-100")).thenReturn(loc);

        // Slot 7 is linked to entity ID 3
        when(entityDirectory.entityCount()).thenReturn(4);
        when(entityDirectory.memoryRefCount(anyInt())).thenReturn(0);
        when(entityDirectory.memoryRefCount(3)).thenReturn(1);
        when(entityDirectory.memoryRefAt(3, 0)).thenReturn(7);

        var fluentQuery = mock(TemporalQuery.class);
        when(tkg.factsAbout(3)).thenReturn(fluentQuery);
        when(fluentQuery.validAt(any())).thenReturn(fluentQuery);
        var mockFact = new TemporalFact(
                1, 3, 10, 4, 0L, (short) 0, 1000L, Long.MAX_VALUE, 1000L, 0.9f, -1, (byte) 0);
        when(fluentQuery.resolve()).thenReturn(List.of(mockFact));

        var stage = new TemporalFactWeavingStage(tkg, entityDirectory, entityExtractor, index);

        CognitiveResult candidate = new CognitiveResult(
                "mem-100", "Alice works at Spectrayan", 0.95f, 0.8f,
                0f, 1, (byte) 0, MemoryType.SEMANTIC, MemorySource.USER_STATED,
                new String[]{"work"}, 0.95f, 0.95f
        );
        List<CognitiveResult> candidates = new ArrayList<>(List.of(candidate));

        stage.weave(candidates, new float[768], RecallOptions.builder().topK(5).build());

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().metadata()).containsEntry("tkg_valid_facts", "1");

        // Verify EntityExtractor was NEVER called
        verify(entityExtractor, never()).extract(any(), any());
    }
}
