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
package com.spectrayan.spector.memory.aisme.phi;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.memory.model.AgentSoul;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Unit tests for {@link ConsciousnessContinuityEvaluator}.
 */
class ConsciousnessContinuityEvaluatorTest {

    @Test
    void evaluate_emptyCandidates_returnsEmptyState() {
        ConsciousnessContinuityEvaluator evaluator = new ConsciousnessContinuityEvaluator(null);
        ConsciousnessContinuityState state = evaluator.evaluate(List.of());

        assertThat(state.candidateCount()).isZero();
        assertThat(state.isCohesive()).isTrue();
    }

    @Test
    void evaluate_candidateClusterWithSoul_computesState() {
        float[] soulEmb = {1.0f, 0.0f};
        AgentSoul soul = AgentSoul.builder()
                .id("soul-1")
                .name("CoreSelf")
                .purposeEmbedding(soulEmb)
                .build();

        ConsciousnessContinuityEvaluator evaluator = new ConsciousnessContinuityEvaluator(soul, 0.01f, 1.0f);

        List<float[]> candidates = List.of(
                new float[]{1.0f, 0.0f},
                new float[]{0.95f, 0.05f}
        );

        ConsciousnessContinuityState state = evaluator.evaluate(candidates);

        assertThat(state.candidateCount()).isEqualTo(2);
        assertThat(state.soulAlignment()).isGreaterThan(0.9f);
        assertThat(state.isCohesive()).isTrue();
    }
}
