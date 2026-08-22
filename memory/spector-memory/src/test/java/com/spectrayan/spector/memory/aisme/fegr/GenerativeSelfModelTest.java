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
package com.spectrayan.spector.memory.aisme.fegr;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.CognitiveProfile;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GenerativeSelfModel}.
 */
class GenerativeSelfModelTest {

    @Test
    void fromSoulAndProfile_hyperfocus_highPrecision() {
        GenerativeSelfModel model = GenerativeSelfModel.fromSoulAndProfile(null, CognitiveProfile.HYPERFOCUS, 4);
        assertThat(model.dimensions()).isEqualTo(4);
        assertThat(model.priorPrecision()[0]).isEqualTo(8.0f);
        assertThat(model.observationPrecision()[0]).isEqualTo(4.0f);
    }

    @Test
    void fromSoulAndProfile_divergent_flexiblePrecision() {
        GenerativeSelfModel model = GenerativeSelfModel.fromSoulAndProfile(null, CognitiveProfile.DIVERGENT, 4);
        assertThat(model.priorPrecision()[0]).isEqualTo(1.0f);
    }

    @Test
    void fromSoulAndProfile_withSoulEmbedding_initializesPriorMean() {
        float[] idEmb = {0.1f, 0.2f, 0.3f, 0.4f};
        AgentSoul soul = AgentSoul.builder()
                .id("test-soul")
                .name("Persona")
                .purposeEmbedding(idEmb)
                .build();

        GenerativeSelfModel model = GenerativeSelfModel.fromSoulAndProfile(soul, CognitiveProfile.BALANCED, 4);
        assertThat(model.priorMean()).containsExactly(0.1f, 0.2f, 0.3f, 0.4f);
        assertThat(model.priorPrecision()[0]).isEqualTo(3.0f);
    }

    @Test
    void createInitialPosterior_matchesPriorParameters() {
        GenerativeSelfModel model = GenerativeSelfModel.fromSoulAndProfile(null, CognitiveProfile.BALANCED, 3);
        MentalStatePosterior initial = model.createInitialPosterior(12345L);

        assertThat(initial.dimensions()).isEqualTo(3);
        assertThat(initial.timestampMs()).isEqualTo(12345L);
        assertThat(initial.version()).isEqualTo(0);
        assertThat(initial.mean()).containsExactly(model.priorMean());
        assertThat(initial.precision()).containsExactly(model.priorPrecision());
    }
}
