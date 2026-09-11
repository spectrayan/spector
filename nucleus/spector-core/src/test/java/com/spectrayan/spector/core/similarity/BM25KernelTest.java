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

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BM25KernelTest {

    @Test
    @DisplayName("IDF decreases monotonically with document frequency")
    void idfMonotonic() {
        float idfRare = BM25Kernel.idf(2, 1000);
        float idfCommon = BM25Kernel.idf(200, 1000);
        assertThat(idfRare).isGreaterThan(idfCommon);
    }

    @Test
    @DisplayName("Zero TF yields zero score")
    void zeroTfYieldsZeroScore() {
        float score = BM25Kernel.scoreTerm(0, 100, 100.0f, 1.2f, 0.75f, 2.5f);
        assertThat(score).isEqualTo(0.0f);
    }

    @Test
    @DisplayName("Scalar and batch scoreTerms produce identical results")
    void batchParity() {
        int[] tfs = {1, 3, 0, 5};
        int[] docLens = {50, 100, 80, 120};
        float avgDocLen = 87.5f;
        float k1 = 1.2f;
        float b = 0.75f;
        float idf = 1.8f;

        float[] batchScores = new float[4];
        BM25Kernel.scoreTerms(tfs, docLens, avgDocLen, k1, b, idf, batchScores, 4);

        for (int i = 0; i < 4; i++) {
            float scalarScore = BM25Kernel.scoreTerm(tfs[i], docLens[i], avgDocLen, k1, b, idf);
            assertThat(batchScores[i]).isCloseTo(scalarScore, org.assertj.core.data.Offset.offset(1e-6f));
        }
    }

    @Property
    void idfAlwaysNonNegative(
            @ForAll @IntRange(min = 0, max = 500) int docFreq,
            @ForAll @IntRange(min = 1, max = 500) int totalDocs) {
        float idf = BM25Kernel.idf(Math.min(docFreq, totalDocs), totalDocs);
        assertThat(idf).isGreaterThanOrEqualTo(0.0f);
    }
}
