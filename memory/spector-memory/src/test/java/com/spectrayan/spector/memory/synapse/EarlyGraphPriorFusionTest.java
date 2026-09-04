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
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoreFusionMode;
import com.spectrayan.spector.memory.synapse.CognitiveScorer.ScoredRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit and invariant tests for Early O(1) Graph Associative Prior in Phase 6 fusion (MR-06).
 */
class EarlyGraphPriorFusionTest {

    private static final int DIMS = 8;
    private SemanticMemory store;
    private CognitiveRecordLayout layout;
    private float[] queryVector;
    private long nowMs;

    @BeforeEach
    void setUp() {
        store = new SemanticMemory(DIMS, 10);
        layout = new CognitiveRecordLayout(DIMS);
        nowMs = System.currentTimeMillis();
        queryVector = new float[]{1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};

        byte flags = EncodingHeaderFields.withMemoryType((byte) 0, MemoryType.SEMANTIC.ordinal());

        // Record 0: Novel memory (perfect similarity, zero prior)
        CognitiveHeader h0 = new CognitiveHeader(nowMs, 0L, 1.0f, 5.0f, 0, (short) 0, (byte) 0, flags);
        byte[] v0 = new byte[]{(byte) 255, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128};
        store.append(h0, v0);

        // Record 1: Associative memory (slightly lower similarity, has graph prior)
        CognitiveHeader h1 = new CognitiveHeader(nowMs, 0L, 1.0f, 5.0f, 0, (short) 0, (byte) 0, flags);
        byte[] v1 = new byte[]{(byte) 230, (byte) 150, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128};
        store.append(h1, v1);
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    @Test
    @DisplayName("MR-06: Associative prior boosts connected record without eliminating novel record")
    void associativePriorBoostsConnectedRecordWithoutEliminatingNovel() {
        AssociativePriorProvider provider = (candidateOffset, recordTags, ctx) -> {
            // Record 1 at offset layout.stride() has prior 0.8, Record 0 has prior 0.0
            return candidateOffset == layout.stride() ? 0.8f : 0.0f;
        };
        QueryAssociativeContext ctx = new QueryAssociativeContext(List.of("mem-ctx"), List.of("tag1"), 1L);

        RecallOptions baseOpts = RecallOptions.builder()
                .topK(2)
                .enableAssociativePrior(false)
                .build();

        List<ScoredRecord> withoutPrior = CognitiveScorer.score(
                store.segment(), 2, layout, queryVector, baseOpts, nowMs, 0L, null, null, provider, ctx);

        assertThat(withoutPrior).hasSize(2);
        // Without prior, Record 0 is top ranked due to higher vector similarity
        assertThat(withoutPrior.get(0).offset()).isEqualTo(0L);

        RecallOptions withPriorOpts = RecallOptions.builder()
                .topK(2)
                .enableAssociativePrior(true)
                .associativePriorDelta(0.40f)
                .build();

        List<ScoredRecord> withPrior = CognitiveScorer.score(
                store.segment(), 2, layout, queryVector, withPriorOpts, nowMs, 0L, null, null, provider, ctx);

        assertThat(withPrior).hasSize(2);
        // With prior, Record 1 gets boosted above Record 0
        assertThat(withPrior.get(0).offset()).isEqualTo((long) layout.stride());
        // NOVEL MEMORY (Record 0) IS NEVER ELIMINATED
        assertThat(withPrior.get(1).offset()).isEqualTo(0L);
    }

    @Test
    @DisplayName("MR-06: Disabled associative prior is bit-identical to default scorer")
    void disabledAssociativePriorIsBitIdentical() {
        AssociativePriorProvider provider = (offset, tags, ctx) -> 0.9f;
        QueryAssociativeContext ctx = new QueryAssociativeContext(List.of("ctx"), List.of("tag"), 1L);

        RecallOptions defaultOpts = RecallOptions.builder()
                .topK(2)
                .enableAssociativePrior(false)
                .build();

        List<ScoredRecord> results1 = CognitiveScorer.score(
                store.segment(), 2, layout, queryVector, defaultOpts, nowMs, 0L, null, null);

        List<ScoredRecord> results2 = CognitiveScorer.score(
                store.segment(), 2, layout, queryVector, defaultOpts, nowMs, 0L, null, null, provider, ctx);

        assertThat(results1.size()).isEqualTo(results2.size());
        for (int i = 0; i < results1.size(); i++) {
            assertThat(results1.get(i).score()).isEqualTo(results2.get(i).score());
            assertThat(results1.get(i).offset()).isEqualTo(results2.get(i).offset());
        }
    }

    @Test
    @DisplayName("MR-06: Additive mode applies linear delta modulation")
    void additiveModeLinearDeltaModulation() {
        AssociativePriorProvider provider = (candidateOffset, recordTags, ctx) -> candidateOffset == 0L ? 0.5f : 0.0f;
        QueryAssociativeContext ctx = new QueryAssociativeContext(List.of("ctx"), List.of(), 0L);

        RecallOptions baseAdditive = RecallOptions.builder()
                .topK(2)
                .scoreFusionMode(ScoreFusionMode.ADDITIVE)
                .enableAssociativePrior(false)
                .build();

        List<ScoredRecord> baseResults = CognitiveScorer.score(
                store.segment(), 2, layout, queryVector, baseAdditive, nowMs, 0L, null, null);

        RecallOptions priorAdditive = RecallOptions.builder()
                .topK(2)
                .scoreFusionMode(ScoreFusionMode.ADDITIVE)
                .enableAssociativePrior(true)
                .associativePriorDelta(0.10f)
                .build();

        List<ScoredRecord> priorResults = CognitiveScorer.score(
                store.segment(), 2, layout, queryVector, priorAdditive, nowMs, 0L, null, null, provider, ctx);

        assertThat(priorResults).hasSize(2);
        // Record 0 has base score + delta * 0.5 = baseScore + 0.05
        assertThat(priorResults.get(0).score()).isCloseTo(baseResults.get(0).score() + 0.05f, org.assertj.core.data.Offset.offset(0.001f));
    }
}
