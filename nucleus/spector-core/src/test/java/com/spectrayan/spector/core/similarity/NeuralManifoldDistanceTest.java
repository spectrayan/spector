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

import com.spectrayan.spector.core.cognitive.NeuralManifoldDistance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NeuralManifoldDistance} SIMD kernel.
 */
class NeuralManifoldDistanceTest {

    @Test
    void identityMetric_matchesStandardEuclideanDistance() {
        float[] x = {1.0f, 2.0f, 3.0f};
        float[] y = {1.0f, 5.0f, 7.0f};

        // delta = [0, -3, -4]
        // Euclidean sqDist = 0^2 + (-3)^2 + (-4)^2 = 25
        float sqDist = NeuralManifoldDistance.squaredDistance(x, y, null, null);
        float dist = NeuralManifoldDistance.distance(x, y, null, null);

        assertThat(sqDist).isCloseTo(25.0f, within(1e-5f));
        assertThat(dist).isCloseTo(5.0f, within(1e-5f));
    }

    @Test
    void diagonalScaling_warpsAxesCorrectly() {
        float[] x = {1.0f, 2.0f};
        float[] y = {3.0f, 4.0f};
        // delta = [-2, -2]

        float[] diag = {2.0f, 0.5f};
        // sqDist = 2*(-2)^2 + 0.5*(-2)^2 = 2*4 + 0.5*4 = 8 + 2 = 10
        float sqDist = NeuralManifoldDistance.squaredDistance(x, y, diag, null);

        assertThat(sqDist).isCloseTo(10.0f, within(1e-5f));
    }

    @Test
    void lowRankComponents_addsCovarianceDistance() {
        float[] x = {1.0f, 1.0f};
        float[] y = {0.0f, 0.0f};
        // delta = [1, 1]

        float[] diag = {1.0f, 1.0f}; // diag sqDist = 2
        float[][] lowRank = {
                {2.0f, 1.0f} // U_0^T * delta = 2*1 + 1*1 = 3 -> squared = 9
        };

        // Total sqDist = 2 + 9 = 11
        float sqDist = NeuralManifoldDistance.squaredDistance(x, y, diag, lowRank);

        assertThat(sqDist).isCloseTo(11.0f, within(1e-5f));
    }

    @Test
    void similarity_gaussianKernelProperties() {
        float[] x = {1.0f, 2.0f};
        float[] identical = {1.0f, 2.0f};
        float[] distant = {10.0f, 20.0f};

        float simIdentical = NeuralManifoldDistance.similarity(x, identical, null, null, 1.0f);
        float simDistant = NeuralManifoldDistance.similarity(x, distant, null, null, 1.0f);

        assertThat(simIdentical).isCloseTo(1.0f, within(1e-5f));
        assertThat(simDistant).isLessThan(0.001f);
    }

    @Test
    void batchSimilarity_computesAllElements() {
        float[] query = {1.0f, 0.0f};
        float[][] candidates = {
                {1.0f, 0.0f},
                {0.0f, 1.0f}
        };

        float[] sims = NeuralManifoldDistance.batchSimilarity(query, candidates, null, null, 1.0f);

        assertThat(sims).hasSize(2);
        assertThat(sims[0]).isCloseTo(1.0f, within(1e-5f));
        assertThat(sims[1]).isLessThan(sims[0]);
    }

    @Test
    void simdTail_handlesNonAlignedDimensionCorrectly() {
        int dim = 23;
        float[] x = new float[dim];
        float[] y = new float[dim];
        float[] diag = new float[dim];

        for (int i = 0; i < dim; i++) {
            x[i] = i * 0.1f;
            y[i] = i * 0.1f + 1.0f; // delta = -1.0 everywhere
            diag[i] = 2.0f;
        }

        // sqDist = 23 * (2.0 * (-1)^2) = 46.0
        float sqDist = NeuralManifoldDistance.squaredDistance(x, y, diag, null);

        assertThat(sqDist).isCloseTo(46.0f, within(1e-4f));
    }

    @Test
    void validation_throwsOnDimensionMismatch() {
        float[] a = {1.0f, 2.0f};
        float[] b = {1.0f};

        assertThatThrownBy(() -> NeuralManifoldDistance.squaredDistance(a, b, null, null))
                .isInstanceOf(SpectorValidationException.class);
    }
}
