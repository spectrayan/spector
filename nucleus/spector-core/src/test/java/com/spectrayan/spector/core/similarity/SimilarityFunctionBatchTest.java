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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SimilarityFunctionBatchTest {

    @Test
    @DisplayName("SimilarityFunction.COSINE.computeBatch works end-to-end")
    void testCosineBatch() {
        float[] query = {1.0f, 0.0f};
        float[] db = {
                1.0f, 0.0f,
                0.0f, 1.0f,
                -1.0f, 0.0f
        };

        float[] scores = SimilarityFunction.COSINE.computeBatch(query, db, 3, 2);

        assertThat(scores).hasSize(3);
        assertThat(scores[0]).isCloseTo(1.0f, within(1e-5f));
        assertThat(scores[1]).isCloseTo(0.0f, within(1e-5f));
        assertThat(scores[2]).isCloseTo(-1.0f, within(1e-5f));
    }

    @Test
    @DisplayName("SimilarityFunction.DOT_PRODUCT.computeBatch works end-to-end")
    void testDotProductBatch() {
        float[] query = {2.0f, 3.0f};
        float[] db = {
                1.0f, 0.0f,
                0.0f, 1.0f,
                2.0f, 2.0f
        };

        float[] scores = SimilarityFunction.DOT_PRODUCT.computeBatch(query, db, 3, 2);

        assertThat(scores).hasSize(3);
        assertThat(scores[0]).isCloseTo(2.0f, within(1e-5f));
        assertThat(scores[1]).isCloseTo(3.0f, within(1e-5f));
        assertThat(scores[2]).isCloseTo(10.0f, within(1e-5f));
    }

    @Test
    @DisplayName("SimilarityFunction.EUCLIDEAN.computeBatch works end-to-end")
    void testEuclideanBatch() {
        float[] query = {1.0f, 1.0f};
        float[] db = {
                1.0f, 1.0f,
                4.0f, 5.0f
        };

        float[] scores = SimilarityFunction.EUCLIDEAN.computeBatch(query, db, 2, 2);

        assertThat(scores).hasSize(2);
        assertThat(scores[0]).isCloseTo(0.0f, within(1e-5f));
        assertThat(scores[1]).isCloseTo(5.0f, within(1e-5f));
    }
}
