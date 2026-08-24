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

import com.spectrayan.spector.core.cognitive.LsrHopfieldKernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the SIMD-accelerated {@link LsrHopfieldKernel}.
 */
class LsrHopfieldKernelTest {

    @Test
    void computePatternSquaredDistances_calculatesAccurateSquaredDistances() {
        float[] state = {1.0f, 2.0f, 3.0f};
        float[][] patterns = {
                {1.0f, 2.0f, 3.0f}, // dist^2 = 0
                {1.0f, 2.0f, 0.0f}, // dist^2 = 9
                {0.0f, 0.0f, 0.0f}  // dist^2 = 1+4+9 = 14
        };
        float[] sqDists = new float[3];

        LsrHopfieldKernel.computePatternSquaredDistances(state, patterns, sqDists);

        assertThat(sqDists[0]).isCloseTo(0.0f, within(1e-5f));
        assertThat(sqDists[1]).isCloseTo(9.0f, within(1e-5f));
        assertThat(sqDists[2]).isCloseTo(14.0f, within(1e-5f));
    }

    @Test
    void computeLsrWeights_calculatesEpanechnikovWeightsAndFiniteEnergy() {
        // sqDists: 0.0, 1.0, 4.0. Beta = 1.0. Critical radius sq: 2/beta = 2.0
        // pattern 0: 1.0 - 0.5*0 = 1.0
        // pattern 1: 1.0 - 0.5*1 = 0.5
        // pattern 2: 1.0 - 0.5*4 = -1.0 -> 0.0 (compact support cutoff!)
        // Sum raw = 1.5. w0 = 1.0/1.5 = 0.6667, w1 = 0.5/1.5 = 0.3333, w2 = 0.0
        float[] sqDists = {0.0f, 1.0f, 4.0f};
        float[] weights = new float[3];

        float energy = LsrHopfieldKernel.computeLsrWeights(sqDists, 1.0f, weights);

        assertThat(weights[0]).isCloseTo(2.0f / 3.0f, within(1e-5f));
        assertThat(weights[1]).isCloseTo(1.0f / 3.0f, within(1e-5f));
        assertThat(weights[2]).isCloseTo(0.0f, within(1e-5f)); // Strictly zero!
        assertThat(energy).isCloseTo(-(float) Math.log(1.5), within(1e-5f));
    }

    @Test
    void computeLsrWeights_handlesOutOfSupportQueryGracefully() {
        // All patterns are beyond r_c
        float[] sqDists = {10.0f, 20.0f};
        float[] weights = new float[2];

        float energy = LsrHopfieldKernel.computeLsrWeights(sqDists, 1.0f, weights);

        assertThat(energy).isEqualTo(Float.POSITIVE_INFINITY);
        assertThat(weights[0]).isCloseTo(0.5f, within(1e-5f));
        assertThat(weights[1]).isCloseTo(0.5f, within(1e-5f));
    }

    @Test
    void update_exactSingleStepRetrievalInsideIsolatedBasin() {
        // Single target memory pattern
        float[] target = {10.0f, 20.0f, 30.0f, 40.0f};
        float[] distractor = {-10.0f, -20.0f, -30.0f, -40.0f};
        float[][] patterns = {target, distractor};

        // Query slightly perturbed from target: perturbation norm^2 = 0.04
        float[] query = {10.1f, 20.1f, 30.1f, 40.1f};

        // beta = 4.0 -> r_c^2 = 2/beta = 0.5.
        // query is well inside target basin (dist^2 = 0.04 < 0.5) and far from distractor (dist^2 > 1000)
        float[] outState = new float[4];
        float[] outWeights = new float[2];

        float energy = LsrHopfieldKernel.update(query, patterns, 4.0f, outState, outWeights);

        assertThat(Float.isFinite(energy)).isTrue();
        assertThat(outWeights[0]).isCloseTo(1.0f, within(1e-5f));
        assertThat(outWeights[1]).isCloseTo(0.0f, within(1e-5f));

        // Exact pattern retrieval in T=1 step!
        assertThat(outState[0]).isCloseTo(10.0f, within(1e-5f));
        assertThat(outState[1]).isCloseTo(20.0f, within(1e-5f));
        assertThat(outState[2]).isCloseTo(30.0f, within(1e-5f));
        assertThat(outState[3]).isCloseTo(40.0f, within(1e-5f));
    }

    @Test
    void basinRadius_computesAccurateRadius() {
        assertThat(LsrHopfieldKernel.basinRadius(2.0f)).isCloseTo(1.0f, within(1e-5f));
        assertThat(LsrHopfieldKernel.basinRadius(8.0f)).isCloseTo(0.5f, within(1e-5f));
        assertThat(LsrHopfieldKernel.basinRadius(0.5f)).isCloseTo(2.0f, within(1e-5f));
    }

    @Test
    void continuousEnergy_reflectsCompactSupportCutoff() {
        float[] state = {1.0f, 0.0f};
        float[][] patterns = {
                {1.0f, 0.0f}, // dist^2 = 0
                {10.0f, 0.0f} // dist^2 = 81
        };

        // beta = 2.0 (r_c^2 = 1.0) -> only pattern 0 is supported (score = 1.0)
        float energy = LsrHopfieldKernel.continuousEnergy(state, patterns, 2.0f);
        assertThat(energy).isCloseTo(0.0f, within(1e-5f)); // -ln(1.0) = 0.0
    }

    @Test
    void simdTail_handlesNonAlignedDimensionCorrectly() {
        int dim = 23;
        float[] state = new float[dim];
        float[][] patterns = new float[2][dim];
        for (int d = 0; d < dim; d++) {
            state[d] = 1.0f;
            patterns[0][d] = 1.0f; // exact match
            patterns[1][d] = 5.0f; // distant
        }

        float[] weights = new float[2];
        float[] outState = new float[dim];

        LsrHopfieldKernel.update(state, patterns, 2.0f, outState, weights);

        assertThat(weights[0]).isCloseTo(1.0f, within(1e-5f));
        assertThat(weights[1]).isCloseTo(0.0f, within(1e-5f));
        assertThat(outState[dim - 1]).isCloseTo(1.0f, within(1e-5f));
    }

    @Test
    void validation_throwsOnDimensionMismatch() {
        float[] state = {1.0f, 2.0f};
        float[][] patterns = {
                {1.0f, 2.0f, 3.0f}
        };
        float[] sqDists = new float[1];

        assertThatThrownBy(() -> LsrHopfieldKernel.computePatternSquaredDistances(state, patterns, sqDists))
                .isInstanceOf(SpectorValidationException.class);
    }
}
