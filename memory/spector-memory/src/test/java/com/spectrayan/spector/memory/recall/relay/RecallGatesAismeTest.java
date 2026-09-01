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
package com.spectrayan.spector.memory.recall.relay;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.model.RecallOptions;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for AISME specifications in {@link RecallGates}.
 */
class RecallGatesAismeTest {

    @Test
    void gates_disabledWhenAismeDisabled() {
        RecallSignal signal = RecallSignal.forTextQuery("test", RecallOptions.builder().build());

        assertThat(RecallGates.HOMEOSTASIS_ENABLED.isSatisfiedBy(signal)).isFalse();
        assertThat(RecallGates.FREE_ENERGY_ENABLED.isSatisfiedBy(signal)).isFalse();
        assertThat(RecallGates.HOPFIELD_ENABLED.isSatisfiedBy(signal)).isFalse();
        assertThat(RecallGates.MANIFOLD_ENABLED.isSatisfiedBy(signal)).isFalse();
        assertThat(RecallGates.CONSTRUCTIVE_SIMULATION_ENABLED.isSatisfiedBy(signal)).isFalse();
        assertThat(RecallGates.CONSCIOUSNESS_CONTINUITY_ENABLED.isSatisfiedBy(signal)).isFalse();
        assertThat(RecallGates.CONSCIOUS_ACCESS_ENABLED.isSatisfiedBy(signal)).isFalse();
    }

    @Test
    void gates_enabledWhenAismeConfigured() {
        RecallOptions options = RecallOptions.builder()
                .enableAisme(true)
                .build();

        RecallSignal signal = RecallSignal.forTextQuery("test", options);

        assertThat(RecallGates.HOMEOSTASIS_ENABLED.isSatisfiedBy(signal)).isTrue();
        assertThat(RecallGates.FREE_ENERGY_ENABLED.isSatisfiedBy(signal)).isTrue();
        assertThat(RecallGates.HOPFIELD_ENABLED.isSatisfiedBy(signal)).isTrue();
        assertThat(RecallGates.MANIFOLD_ENABLED.isSatisfiedBy(signal)).isTrue();
        assertThat(RecallGates.CONSTRUCTIVE_SIMULATION_ENABLED.isSatisfiedBy(signal)).isTrue();
        assertThat(RecallGates.CONSCIOUSNESS_CONTINUITY_ENABLED.isSatisfiedBy(signal)).isTrue();
        assertThat(RecallGates.CONSCIOUS_ACCESS_ENABLED.isSatisfiedBy(signal)).isTrue();
    }

    @Test
    void gates_granularTogglesEvaluatedCorrectly() {
        AismeConfig config = AismeConfig.builder()
                .enabled(true)
                .enableHomeostasis(true)
                .enableFreeEnergy(false)
                .enableHopfield(true)
                .enableManifold(false)
                .enablePredictiveCoding(true)
                .enableConsciousnessContinuity(false)
                .enableGlobalWorkspace(true)
                .build();

        RecallOptions options = RecallOptions.builder()
                .aismeConfig(config)
                .build();

        RecallSignal signal = RecallSignal.forTextQuery("test", options);

        assertThat(RecallGates.HOMEOSTASIS_ENABLED.isSatisfiedBy(signal)).isTrue();
        assertThat(RecallGates.FREE_ENERGY_ENABLED.isSatisfiedBy(signal)).isFalse();
        assertThat(RecallGates.HOPFIELD_ENABLED.isSatisfiedBy(signal)).isTrue();
        assertThat(RecallGates.MANIFOLD_ENABLED.isSatisfiedBy(signal)).isFalse();
        assertThat(RecallGates.CONSTRUCTIVE_SIMULATION_ENABLED.isSatisfiedBy(signal)).isTrue();
        assertThat(RecallGates.CONSCIOUSNESS_CONTINUITY_ENABLED.isSatisfiedBy(signal)).isFalse();
        assertThat(RecallGates.CONSCIOUS_ACCESS_ENABLED.isSatisfiedBy(signal)).isTrue();
    }
}
