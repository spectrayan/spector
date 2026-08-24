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
package com.spectrayan.spector.core.similarity;

import com.spectrayan.spector.core.cognitive.CompositeImportanceKernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

/**
 * Unit tests for {@link CompositeImportanceKernel}.
 */
class CompositeImportanceKernelTest {

    @Test
    @DisplayName("evaluate matches scalar implementation for uniform weights")
    void evaluate_matchesScalar_uniform() {
        float[] signals = {0.8f, 0.4f, 0.9f, 0.2f, 0.6f};
        float[] weights = {0.2f, 0.2f, 0.2f, 0.2f, 0.2f};

        float simdResult = CompositeImportanceKernel.evaluate(signals, weights);
        float scalarResult = CompositeImportanceKernel.evaluateScalar(signals, weights);

        assertThat(simdResult).isCloseTo(scalarResult, within(1e-5f));
        assertThat(simdResult).isCloseTo(0.58f, within(1e-5f));
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 42L, 2026L, 9999L})
    @DisplayName("evaluate matches scalar for randomized signals and weights across seeds")
    void evaluate_matchesScalar_random(long seed) {
        Random rng = new Random(seed);
        float[] rawSignals = new float[5];
        float[] rawWeights = new float[5];

        for (int i = 0; i < 5; i++) {
            rawSignals[i] = rng.nextFloat();
            rawWeights[i] = rng.nextFloat();
        }

        float[] weights = CompositeImportanceKernel.normalizeWeights(rawWeights);

        float simdResult = CompositeImportanceKernel.evaluate(rawSignals, weights);
        float scalarResult = CompositeImportanceKernel.evaluateScalar(rawSignals, weights);

        assertThat(simdResult).isCloseTo(scalarResult, within(1e-5f));
        assertThat(simdResult).isBetween(0.0f, 1.0f);
    }

    @Test
    @DisplayName("evaluate clamps to [0.0, 1.0]")
    void evaluate_clampsBounds() {
        float[] signals = {1.5f, 2.0f, 1.0f, 1.0f, 1.0f};
        float[] weights = {0.5f, 0.5f, 0.0f, 0.0f, 0.0f};

        float result = CompositeImportanceKernel.evaluate(signals, weights);
        assertThat(result).isEqualTo(1.0f);
    }

    @Test
    @DisplayName("normalizeWeights enforces non-negativity and sum equals 1.0")
    void normalizeWeights_normalizesSumToOne() {
        float[] raw = {1.0f, 2.0f, 3.0f, 4.0f, 0.0f};
        float[] norm = CompositeImportanceKernel.normalizeWeights(raw);

        assertThat(norm[0] + norm[1] + norm[2] + norm[3] + norm[4]).isCloseTo(1.0f, within(1e-5f));
        assertThat(norm[0]).isCloseTo(0.1f, within(1e-5f));
        assertThat(norm[1]).isCloseTo(0.2f, within(1e-5f));
        assertThat(norm[2]).isCloseTo(0.3f, within(1e-5f));
        assertThat(norm[3]).isCloseTo(0.4f, within(1e-5f));
        assertThat(norm[4]).isCloseTo(0.0f, within(1e-5f));
    }

    @Test
    @DisplayName("Validation exceptions for invalid arrays")
    void evaluate_throwsOnInvalidArrays() {
        assertThatThrownBy(() -> CompositeImportanceKernel.evaluate(null, new float[5]))
                .isInstanceOf(SpectorValidationException.class);
        assertThatThrownBy(() -> CompositeImportanceKernel.evaluate(new float[5], null))
                .isInstanceOf(SpectorValidationException.class);
        assertThatThrownBy(() -> CompositeImportanceKernel.evaluate(new float[4], new float[5]))
                .isInstanceOf(SpectorValidationException.class);
        assertThatThrownBy(() -> CompositeImportanceKernel.normalizeWeights(new float[]{-1.0f, 1.0f, 1.0f, 1.0f, 1.0f}))
                .isInstanceOf(SpectorValidationException.class);
    }
}
