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
package com.spectrayan.spector.memory.synapse;

import com.spectrayan.spector.memory.cortex.SemanticMemory;
import com.spectrayan.spector.memory.cortex.StrengthMemory;
import com.spectrayan.spector.memory.kernel.layout.EngramLayout;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeader;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CognitiveScorer Strength Region Authority Tests (Issue #733, Task 3.1)")
class CognitiveScorerStrengthAuthorityTest {

    private static final int DIMS = 8;

    @Test
    @DisplayName("Verify CognitiveScorer uses StrengthMemory storage strength and recall count when present")
    void testCognitiveScorerUsesStrengthMemoryWhenPresent() {
        final long nowMs = 1716900000000L;
        final long timestamp = nowMs - (30L * 86_400_000L); // 30 days old

        final SemanticMemory store = new SemanticMemory(DIMS, 10);
        try {
            final EngramLayout layout = store.cognitiveLayout();

            // Header has V1 default storageStrength = 1.0f, agentRecallCount = 0
            final EncodingHeader header = new EncodingHeader(
                    timestamp, 0L, 1.0f, 6.0f, 0, (short) 0, (byte) 0, (byte) 0,
                    (byte) 50, 1.0f);
            final byte[] vec = new byte[DIMS];
            store.append(header, vec);

            final float[] queryVector = new float[DIMS];
            final RecallOptions options = RecallOptions.builder()
                    .topK(5)
                    .build();

            // 1. Scoring without strengthStore reads header (storageStrength = 1.0f, agentRecallCount = 0)
            final List<CognitiveScorer.ScoredRecord> withoutStrength = CognitiveScorer.score(
                    store.primarySegment(), 1, layout, queryVector, options, nowMs, 0L,
                    null, null, null, null, null, null);
            assertThat(withoutStrength).hasSize(1);
            float scoreWithout = withoutStrength.get(0).score();

            // 2. Setup StrengthMemory with boosted storage strength (3.0f) and agent recall count (5)
            final StrengthMemory strengthStore = StrengthMemory.heap(10, 10, 10);
            strengthStore.initializeDefault(MemoryType.SEMANTIC, 0, 6.0f, 3.0f, 5);

            final List<CognitiveScorer.ScoredRecord> withStrength = CognitiveScorer.score(
                    store.primarySegment(), 1, layout, queryVector, options, nowMs, 0L,
                    null, null, null, null, strengthStore, MemoryType.SEMANTIC);
            assertThat(withStrength).hasSize(1);
            float scoreWith = withStrength.get(0).score();

            // Storage strength boost (3.0f vs 1.0f) and reconsolidation count (5 vs 0) increase score
            assertThat(scoreWith)
                    .as("Strength region authority should boost score via storage strength & reconsolidation")
                    .isGreaterThan(scoreWithout);
        } finally {
            store.close();
        }
    }

    @Test
    @DisplayName("Verify CognitiveScorer uses effectiveImportance from StrengthMemory")
    void testCognitiveScorerUsesEffectiveImportance() {
        final long nowMs = 1716900000000L;
        final long timestamp = nowMs - 1000L;

        final SemanticMemory store = new SemanticMemory(DIMS, 10);
        try {
            final EngramLayout layout = store.cognitiveLayout();

            // Header has low base importance = 2.0f
            final EncodingHeader header = new EncodingHeader(
                    timestamp, 0L, 1.0f, 2.0f, 0, (short) 0, (byte) 0, (byte) 0);
            final byte[] vec = new byte[DIMS];
            store.append(header, vec);

            final float[] queryVector = new float[DIMS];
            // Filter requiring minImportance = 5.0f
            final RecallOptions options = RecallOptions.builder()
                    .topK(5)
                    .minImportance(5.0f)
                    .build();

            // Without strengthStore, record is filtered out because header importance is 2.0 < 5.0
            final List<CognitiveScorer.ScoredRecord> withoutStrength = CognitiveScorer.score(
                    store.primarySegment(), 1, layout, queryVector, options, nowMs, 0L,
                    null, null, null, null, null, null);
            assertThat(withoutStrength).isEmpty();

            // With strengthStore having effectiveImportance = 8.0f, record passes filter
            final StrengthMemory strengthStore = StrengthMemory.heap(10, 10, 10);
            strengthStore.initializeDefault(MemoryType.SEMANTIC, 0, 8.0f, 1.0f, 0);

            final List<CognitiveScorer.ScoredRecord> withStrength = CognitiveScorer.score(
                    store.primarySegment(), 1, layout, queryVector, options, nowMs, 0L,
                    null, null, null, null, strengthStore, MemoryType.SEMANTIC);
            assertThat(withStrength).hasSize(1);
        } finally {
            store.close();
        }
    }
}
