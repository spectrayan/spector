/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.memory.pathway.recall.relay;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.config.properties.AismeProperties;
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
        AismeProperties config = AismeProperties.builder()
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
