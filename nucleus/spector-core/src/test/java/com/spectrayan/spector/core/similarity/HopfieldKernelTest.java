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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the SIMD-accelerated {@link HopfieldKernel}.
 */
class HopfieldKernelTest {

    @Test
    void computePatternProjections_calculatesAccurateDotProducts() {
        float[] state = {1.0f, 2.0f, 3.0f};
        float[][] patterns = {
                {1.0f, 0.0f, 0.0f},
                {0.0f, 1.0f, 0.0f},
                {1.0f, 1.0f, 1.0f}
        };
        float[] dots = new float[3];

        HopfieldKernel.computePatternProjections(state, patterns, dots);

        assertThat(dots[0]).isCloseTo(1.0f, within(1e-5f));
        assertThat(dots[1]).isCloseTo(2.0f, within(1e-5f));
        assertThat(dots[2]).isCloseTo(6.0f, within(1e-5f));
    }

    @Test
    void softmax_sumsToOneAndRespectsBetaTemperature() {
        float[] logits = {1.0f, 2.0f, 3.0f};
        float[] sharpWeights = new float[3];
        float[] flatWeights = new float[3];

        HopfieldKernel.softmax(logits, 10.0f, sharpWeights);
        HopfieldKernel.softmax(logits, 0.1f, flatWeights);

        float sumSharp = sharpWeights[0] + sharpWeights[1] + sharpWeights[2];
        float sumFlat = flatWeights[0] + flatWeights[1] + flatWeights[2];

        assertThat(sumSharp).isCloseTo(1.0f, within(1e-5f));
        assertThat(sumFlat).isCloseTo(1.0f, within(1e-5f));

        // High beta sharpens distribution towards highest logit (index 2)
        assertThat(sharpWeights[2]).isGreaterThan(0.99f);

        // Low beta yields near-uniform distribution
        assertThat(flatWeights[0]).isCloseTo(0.333f, within(0.05f));
        assertThat(flatWeights[1]).isCloseTo(0.333f, within(0.05f));
        assertThat(flatWeights[2]).isCloseTo(0.333f, within(0.05f));
    }

    @Test
    void softmax_handlesExtremeValuesWithoutOverflow() {
        float[] extremeLogits = {1000.0f, 1005.0f, 1010.0f};
        float[] weights = new float[3];

        HopfieldKernel.softmax(extremeLogits, 1.0f, weights);

        assertThat(weights[2]).isGreaterThan(weights[1]);
        assertThat(weights[1]).isGreaterThan(weights[0]);
        assertThat(weights[0] + weights[1] + weights[2]).isCloseTo(1.0f, within(1e-5f));
    }

    @Test
    void matrixVectorProduct_computesLinearCombinationAccurately() {
        float[][] patterns = {
                {1.0f, 0.0f, 0.0f, 4.0f},
                {0.0f, 2.0f, 0.0f, 2.0f}
        };
        float[] weights = {0.75f, 0.25f};
        float[] outState = new float[4];

        HopfieldKernel.matrixVectorProduct(patterns, weights, outState);

        assertThat(outState[0]).isCloseTo(0.75f, within(1e-5f));
        assertThat(outState[1]).isCloseTo(0.50f, within(1e-5f));
        assertThat(outState[2]).isCloseTo(0.00f, within(1e-5f));
        assertThat(outState[3]).isCloseTo(3.50f, within(1e-5f)); // 0.75*4 + 0.25*2 = 3.5
    }

    @Test
    void update_performsFullHopfieldStep() {
        float[] state = {0.9f, 0.1f};
        float[][] patterns = {
                {1.0f, 0.0f},
                {0.0f, 1.0f}
        };
        float[] outState = new float[2];
        float[] outWeights = new float[2];

        HopfieldKernel.update(state, patterns, 5.0f, outState, outWeights);

        assertThat(outWeights[0]).isGreaterThan(outWeights[1]);
        assertThat(outState[0]).isGreaterThan(0.9f);
    }

    @Test
    void continuousEnergy_computesFiniteEnergy() {
        float[] state = {1.0f, 1.0f};
        float[][] patterns = {
                {1.0f, 0.0f},
                {0.0f, 1.0f}
        };

        float energy = HopfieldKernel.continuousEnergy(state, patterns, 2.0f);
        assertThat(Float.isFinite(energy)).isTrue();
    }

    @Test
    void simdTail_handlesNonAlignedDimensionCorrectly() {
        int dim = 19;
        float[] state = new float[dim];
        float[][] patterns = new float[3][dim];
        for (int d = 0; d < dim; d++) {
            state[d] = 1.0f;
            patterns[0][d] = 0.5f;
            patterns[1][d] = 1.5f;
            patterns[2][d] = 0.2f;
        }

        float[] weights = new float[3];
        float[] outState = new float[dim];

        HopfieldKernel.update(state, patterns, 1.0f, outState, weights);

        assertThat(weights[1]).isGreaterThan(weights[0]);
        assertThat(outState[dim - 1]).isGreaterThan(0.0f);
    }

    @Test
    void validation_throwsOnDimensionMismatch() {
        float[] state = {1.0f, 2.0f};
        float[][] patterns = {
                {1.0f, 2.0f, 3.0f}
        };
        float[] dots = new float[1];

        assertThatThrownBy(() -> HopfieldKernel.computePatternProjections(state, patterns, dots))
                .isInstanceOf(SpectorValidationException.class);
    }
}
