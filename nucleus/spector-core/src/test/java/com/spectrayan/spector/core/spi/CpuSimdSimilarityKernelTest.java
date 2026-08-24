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
package com.spectrayan.spector.core.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class CpuSimdSimilarityKernelTest {

    private final CpuSimdSimilarityKernel kernel = CpuSimdSimilarityKernel.INSTANCE;

    @Test
    @DisplayName("dotProduct computes accurate inner products")
    void testDotProduct() {
        float[] query = {1.0f, 2.0f, 3.0f, 4.0f};
        float[] database = {
                1.0f, 0.0f, 0.0f, 0.0f,  // dot = 1.0
                0.0f, 1.0f, 0.0f, 0.0f,  // dot = 2.0
                1.0f, 1.0f, 1.0f, 1.0f   // dot = 10.0
        };

        float[] dots = kernel.dotProduct(query, database, 3, 4);

        assertThat(dots).hasSize(3);
        assertThat(dots[0]).isCloseTo(1.0f, within(1e-5f));
        assertThat(dots[1]).isCloseTo(2.0f, within(1e-5f));
        assertThat(dots[2]).isCloseTo(10.0f, within(1e-5f));
    }

    @Test
    @DisplayName("cosineSimilarity computes normalized cosine scores")
    void testCosineSimilarity() {
        float[] query = {1.0f, 0.0f, 0.0f, 0.0f};
        float[] database = {
                1.0f, 0.0f, 0.0f, 0.0f,   // identical -> 1.0
                -1.0f, 0.0f, 0.0f, 0.0f,  // opposite -> -1.0
                0.0f, 1.0f, 0.0f, 0.0f,   // orthogonal -> 0.0
                2.0f, 0.0f, 0.0f, 0.0f    // collinear -> 1.0
        };

        float[] cosines = kernel.cosineSimilarity(query, database, 4, 4);

        assertThat(cosines).hasSize(4);
        assertThat(cosines[0]).isCloseTo(1.0f, within(1e-5f));
        assertThat(cosines[1]).isCloseTo(-1.0f, within(1e-5f));
        assertThat(cosines[2]).isCloseTo(0.0f, within(1e-5f));
        assertThat(cosines[3]).isCloseTo(1.0f, within(1e-5f));
    }

    @Test
    @DisplayName("cosineSimilarity returns zeros for zero-magnitude query")
    void testCosineZeroQuery() {
        float[] query = {0.0f, 0.0f, 0.0f, 0.0f};
        float[] database = {1.0f, 2.0f, 3.0f, 4.0f};

        float[] cosines = kernel.cosineSimilarity(query, database, 1, 4);

        assertThat(cosines).containsExactly(0.0f);
    }

    @Test
    @DisplayName("euclideanDistance computes accurate L2 distances")
    void testEuclideanDistance() {
        float[] query = {1.0f, 2.0f, 3.0f, 4.0f};
        float[] database = {
                1.0f, 2.0f, 3.0f, 4.0f,   // dist = 0.0
                1.0f, 2.0f, 3.0f, 7.0f,   // diff = (0,0,0,3) -> dist = 3.0
                4.0f, 6.0f, 3.0f, 4.0f    // diff = (3,4,0,0) -> dist = 5.0
        };

        float[] dists = kernel.euclideanDistance(query, database, 3, 4);

        assertThat(dists).hasSize(3);
        assertThat(dists[0]).isCloseTo(0.0f, within(1e-5f));
        assertThat(dists[1]).isCloseTo(3.0f, within(1e-5f));
        assertThat(dists[2]).isCloseTo(5.0f, within(1e-5f));
    }

    @Test
    @DisplayName("methods handle zero vectors gracefully")
    void testZeroVectors() {
        float[] query = {1.0f, 2.0f};
        float[] database = new float[0];

        assertThat(kernel.dotProduct(query, database, 0, 2)).isEmpty();
        assertThat(kernel.cosineSimilarity(query, database, 0, 2)).isEmpty();
        assertThat(kernel.euclideanDistance(query, database, 0, 2)).isEmpty();
    }

    @Test
    @DisplayName("methods validate input arguments")
    void testInputValidation() {
        float[] query = {1.0f, 2.0f};
        float[] database = {1.0f, 2.0f, 3.0f, 4.0f};

        assertThatThrownBy(() -> kernel.dotProduct(null, database, 2, 2))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> kernel.dotProduct(query, null, 2, 2))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> kernel.dotProduct(query, database, -1, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> kernel.dotProduct(query, database, 2, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> kernel.dotProduct(new float[]{1.0f}, database, 2, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> kernel.dotProduct(query, new float[]{1.0f}, 2, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
