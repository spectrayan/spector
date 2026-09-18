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
package com.spectrayan.spector.memory.aisme.pcmn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.memory.model.CognitiveProfile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PredictiveCodingNetwork}.
 */
class PredictiveCodingNetworkTest {

    private PredictiveCodingNetwork network;

    @BeforeEach
    void setUp() {
        network = new PredictiveCodingNetwork(2, 4, CognitiveProfile.BALANCED);
    }

    @Test
    void construction_properties() {
        assertThat(network.dimensions()).isEqualTo(2);
        assertThat(network.tierCount()).isEqualTo(4);
    }

    @Test
    void evaluateHierarchy_identicalTiers_zeroEnergy() {
        float[][] actual = {
                {1.0f, 2.0f},
                {1.0f, 2.0f},
                {1.0f, 2.0f},
                {1.0f, 2.0f}
        };

        HierarchicalPredictionError error = network.evaluateHierarchy(actual);
        assertThat(error.totalEnergy()).isEqualTo(0.0f);
        assertThat(error.tierCount()).isEqualTo(4);
    }

    @Test
    void evaluateHierarchy_divergentTiers_positiveEnergy() {
        float[][] actual = {
                {0.0f, 0.0f}, // Working
                {1.0f, 1.0f}, // Episodic
                {2.0f, 2.0f}, // Semantic
                {3.0f, 3.0f}  // Procedural
        };

        HierarchicalPredictionError error = network.evaluateHierarchy(actual);
        assertThat(error.totalEnergy()).isGreaterThan(0.0f);
        assertThat(error.weightedErrorVectors().length).isEqualTo(4);
    }

    @Test
    void invalidArguments_throwValidationException() {
        float[][] invalidTiers = {
                {1.0f, 2.0f}
        };

        assertThatThrownBy(() -> network.evaluateHierarchy(invalidTiers))
                .isInstanceOf(SpectorValidationException.class);
    }
}
