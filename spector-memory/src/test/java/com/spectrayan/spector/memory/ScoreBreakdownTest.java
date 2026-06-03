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
package com.spectrayan.spector.memory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ScoreBreakdown}.
 */
class ScoreBreakdownTest {

    @Test
    void traceProducesReadableOutput() {
        ScoreBreakdown bd = new ScoreBreakdown(0.82f, 0.45f, 1.30f, 0.85f, 1.12f, 1.00f, 0.4231f);
        String trace = bd.trace();

        assertThat(trace).contains("0.8200");   // similarity
        assertThat(trace).contains("0.4500");   // imp×decay
        assertThat(trace).contains("1.30×");    // tag_boost
        assertThat(trace).contains("0.85×");    // habituation
        assertThat(trace).contains("1.12×");    // graph_boost
        assertThat(trace).contains("1.00×");    // valence_align
        assertThat(trace).contains("0.4231");   // final
    }

    @Test
    void dominantFactorIsSimilarityWhenHigher() {
        ScoreBreakdown bd = new ScoreBreakdown(0.9f, 0.3f, 1.0f, 1.0f, 1.0f, 1.0f, 0.9f);
        assertThat(bd.dominantFactor()).isEqualTo("similarity");
    }

    @Test
    void dominantFactorIsImportanceDecayWhenHigher() {
        ScoreBreakdown bd = new ScoreBreakdown(0.2f, 0.8f, 1.0f, 1.0f, 1.0f, 1.0f, 0.8f);
        assertThat(bd.dominantFactor()).isEqualTo("importance_decay");
    }

    @Test
    void weakestMultiplierIdentifiesHabituation() {
        ScoreBreakdown bd = new ScoreBreakdown(0.5f, 0.5f, 1.2f, 0.3f, 1.1f, 1.0f, 0.15f);
        assertThat(bd.weakestMultiplier()).isEqualTo("habituation");
    }

    @Test
    void weakestMultiplierIdentifiesValenceAlignment() {
        ScoreBreakdown bd = new ScoreBreakdown(0.5f, 0.5f, 1.2f, 1.0f, 1.1f, 0.2f, 0.1f);
        assertThat(bd.weakestMultiplier()).isEqualTo("valence_alignment");
    }

    @Test
    void noneBreakdownHasZeroScores() {
        assertThat(ScoreBreakdown.NONE.finalScore()).isEqualTo(0f);
        assertThat(ScoreBreakdown.NONE.similarity()).isEqualTo(0f);
        assertThat(ScoreBreakdown.NONE.habituationPenalty()).isEqualTo(1f);
    }

    @Test
    void cognitiveResultWithBreakdown() {
        ScoreBreakdown bd = new ScoreBreakdown(0.8f, 0.5f, 1.0f, 1.0f, 1.0f, 1.0f, 0.65f);
        CognitiveResult result = new CognitiveResult(
                "id-1", "test", 0.65f, 0.5f, 1.0f, 0, (byte) 0,
                MemoryType.EPISODIC, null, new String[0], 0.9f, 0.85f,
                CognitiveResult.RetrievalMode.STANDARD, bd);

        assertThat(result.hasBreakdown()).isTrue();
        assertThat(result.breakdown().similarity()).isEqualTo(0.8f);
    }

    @Test
    void cognitiveResultWithoutBreakdownHasNullBreakdown() {
        CognitiveResult result = new CognitiveResult(
                "id-1", "test", 0.65f, 0.5f, 1.0f, 0, (byte) 0,
                MemoryType.EPISODIC, null, new String[0], 0.9f, 0.85f);

        assertThat(result.hasBreakdown()).isFalse();
        assertThat(result.breakdown()).isNull();
    }
}
