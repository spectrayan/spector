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
import com.spectrayan.spector.memory.kernel.layout.EngramLayout;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeader;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoreFusionMode;
import com.spectrayan.spector.memory.synapse.CognitiveScorer.ScoredRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property and behavioral tests for fused scoring formulas across ScoreFusionMode (MR-02).
 */
class FusedScoreFormulaPropertyTest {

    private static final int DIMS = 4;

    @Test
    @DisplayName("MR-02: Alpha validation rejects values outside [0.0, 1.0]")
    void alphaValidationEnforced() {
        assertThatThrownBy(() -> RecallOptions.builder().alpha(-0.1f).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alpha must be in [0.0, 1.0]");

        assertThatThrownBy(() -> RecallOptions.builder().alpha(1.05f).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alpha must be in [0.0, 1.0]");

        assertThatThrownBy(() -> RecallOptions.builder().alpha(Float.NaN).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("MR-02: MULTIPLICATIVE mode preserves backwards compatible score formula")
    void multiplicativeModeFormula() {
        SemanticMemory store = new SemanticMemory(DIMS, 10);
        EngramLayout layout = new EngramLayout(DIMS);
        byte flags = EncodingHeaderFields.withMemoryType((byte) 0, MemoryType.SEMANTIC.ordinal());

        long now = System.currentTimeMillis();
        EncodingHeader header = new EncodingHeader(
                now, 0L, 1.0f, 5.0f, 0, (short) 0, (byte) 0, flags
        );
        byte[] vecBytes = new byte[layout.quantizedVecBytes()];
        store.append(header, vecBytes);

        float[] queryVec = new float[DIMS];
        RecallOptions opts1 = RecallOptions.builder()
                .topK(5)
                .scoreFusionMode(ScoreFusionMode.MULTIPLICATIVE)
                .alpha(0.8f)
                .build();

        RecallOptions opts2 = RecallOptions.builder()
                .topK(5)
                .scoreFusionMode(ScoreFusionMode.MULTIPLICATIVE)
                .alpha(0.2f)
                .build();

        List<ScoredRecord> res1 = CognitiveScorer.score(
                store.segment(), 1, layout, queryVec, opts1, now, 0L, null, null
        );
        List<ScoredRecord> res2 = CognitiveScorer.score(
                store.segment(), 1, layout, queryVec, opts2, now, 0L, null, null
        );

        assertThat(res1).hasSize(1);
        assertThat(res2).hasSize(1);
        // Multiplicative mode does not depend on alpha
        assertThat(res1.get(0).score()).isEqualTo(res2.get(0).score());

        store.close();
    }

    @Test
    @DisplayName("MR-02: ADDITIVE mode correctly balances vector similarity and semantic tag overlap with live alpha")
    void additiveModeFormulaRespectsAlpha() {
        SemanticMemory store = new SemanticMemory(DIMS, 10);
        EngramLayout layout = new EngramLayout(DIMS);
        byte flags = EncodingHeaderFields.withMemoryType((byte) 0, MemoryType.SEMANTIC.ordinal());

        long now = System.currentTimeMillis();
        long tags = SynapticTagEncoder.encode("database", "indexing");
        EncodingHeader header = new EncodingHeader(
                now, tags, 1.0f, 5.0f, 0, (short) 0, (byte) 0, flags
        );
        byte[] vecBytes = new byte[layout.quantizedVecBytes()];
        store.append(header, vecBytes);

        float[] queryVec = new float[DIMS];
        // Query has exact tag match
        long queryTagMask = SynapticTagEncoder.encode("database", "indexing");

        RecallOptions optsVectorDominant = RecallOptions.builder()
                .topK(5)
                .scoreFusionMode(ScoreFusionMode.ADDITIVE)
                .synapticTagMask(queryTagMask)
                .alpha(1.0f) // 100% vector similarity
                .build();

        RecallOptions optsTagDominant = RecallOptions.builder()
                .topK(5)
                .scoreFusionMode(ScoreFusionMode.ADDITIVE)
                .synapticTagMask(queryTagMask)
                .alpha(0.0f) // 100% tag overlap
                .build();

        RecallOptions optsBalanced = RecallOptions.builder()
                .topK(5)
                .scoreFusionMode(ScoreFusionMode.ADDITIVE)
                .synapticTagMask(queryTagMask)
                .alpha(0.5f) // 50-50
                .build();

        List<ScoredRecord> resVec = CognitiveScorer.score(
                store.segment(), 1, layout, queryVec, optsVectorDominant, now, 0L, null, null
        );
        List<ScoredRecord> resTag = CognitiveScorer.score(
                store.segment(), 1, layout, queryVec, optsTagDominant, now, 0L, null, null
        );
        List<ScoredRecord> resBalanced = CognitiveScorer.score(
                store.segment(), 1, layout, queryVec, optsBalanced, now, 0L, null, null
        );

        float scoreVec = resVec.get(0).score();
        float scoreTag = resTag.get(0).score();
        float scoreBalanced = resBalanced.get(0).score();

        // Tag overlap = 1.0, vector similarity (0 distance) = 1.0 / (1.0 + 0) = 1.0
        // Balanced score = 0.5 * 1.0 + 0.5 * 1.0 = 1.0 * impDecayFactor
        assertThat(scoreBalanced).isCloseTo((scoreVec + scoreTag) / 2.0f, org.assertj.core.data.Offset.offset(1e-5f));

        store.close();
    }
}
