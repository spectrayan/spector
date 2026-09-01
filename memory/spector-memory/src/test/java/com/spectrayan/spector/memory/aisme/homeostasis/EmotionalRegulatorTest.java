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
package com.spectrayan.spector.memory.aisme.homeostasis;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.AgentSoul.EmotionalBaseline;
import com.spectrayan.spector.memory.model.CognitiveProfile;

import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link EmotionalRegulator} factory.
 */
class EmotionalRegulatorTest {

    @Test
    void deriveRegulationMatrix_balanced_moderateDecay() {
        float[][] matrix = EmotionalRegulator.deriveRegulationMatrix(
                EmotionalBaseline.NEUTRAL, CognitiveProfile.BALANCED, 5);
        assertThat(matrix).hasNumberOfRows(5);
        // Diagonal should be -0.5 (moderate)
        assertThat(matrix[0][0]).isEqualTo(-0.5f);
        assertThat(matrix[1][1]).isEqualTo(-0.5f);
        // Off-diagonal should be 0
        assertThat(matrix[0][1]).isEqualTo(0.0f);
    }

    @Test
    void deriveRegulationMatrix_hyperfocus_slowDecay() {
        float[][] matrix = EmotionalRegulator.deriveRegulationMatrix(
                EmotionalBaseline.NEUTRAL, CognitiveProfile.HYPERFOCUS, 3);
        // HYPERFOCUS = slow decay (rumination)
        assertThat(matrix[0][0]).isEqualTo(-0.1f);
    }

    @Test
    void deriveRegulationMatrix_divergent_fastDecay() {
        float[][] matrix = EmotionalRegulator.deriveRegulationMatrix(
                EmotionalBaseline.NEUTRAL, CognitiveProfile.DIVERGENT, 3);
        // DIVERGENT = fast decay (rapid mood shifts)
        assertThat(matrix[0][0]).isEqualTo(-0.9f);
    }

    @Test
    void deriveEquilibrium_neutralBaseline_zeroVector() {
        float[] eq = EmotionalRegulator.deriveEquilibrium(EmotionalBaseline.NEUTRAL, 5);
        assertThat(eq).hasSize(5);
        assertThat(eq[0]).isCloseTo(0.0f, org.assertj.core.api.Assertions.within(0.01f));
    }

    @Test
    void deriveEquilibrium_warmBaseline_positiveValence() {
        float[] eq = EmotionalRegulator.deriveEquilibrium(EmotionalBaseline.WARM, 5);
        // WARM = defaultValence=30 → 30/127 ≈ 0.236
        assertThat(eq[0]).isGreaterThan(0.0f);
    }

    @Test
    void createFromSoul_returnsConfiguredCore() {
        AgentSoul soul = AgentSoul.builder()
                .id("test-agent")
                .name("Test Agent")
                .build();
        HomeostaticCore core = EmotionalRegulator.createFromSoul(
                soul, CognitiveProfile.BALANCED, 4);
        assertThat(core).isNotNull();
        assertThat(core.currentState()).isEqualTo(InteroceptiveState.NEUTRAL);
    }

    @Test
    void createFromSoul_nullSoul_doesNotThrow() {
        HomeostaticCore core = EmotionalRegulator.createFromSoul(
                null, CognitiveProfile.BALANCED, 2);
        assertThat(core).isNotNull();
    }

    @Test
    void createFromSoul_nullProfile_usesDefault() {
        AgentSoul soul = AgentSoul.builder()
                .id("test")
                .name("Test")
                .build();
        HomeostaticCore core = EmotionalRegulator.createFromSoul(soul, null, 2);
        assertThat(core).isNotNull();
    }
}
