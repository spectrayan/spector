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
package com.spectrayan.spector.cpu.kernel;

import com.spectrayan.spector.core.similarity.SimilarityFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CpuSimdCandidateKernelTest {

    private final CpuSimdCandidateKernel kernel = CpuSimdCandidateKernel.INSTANCE;

    @Test
    @DisplayName("evaluateCandidates computes correct cosine scores for candidate batch")
    void testEvaluateCandidatesCosine() {
        float[] query = {1.0f, 0.0f, 0.0f, 0.0f};
        float[] candidates = {
                1.0f, 0.0f, 0.0f, 0.0f,  // candidate 0: identical -> 1.0
                0.0f, 1.0f, 0.0f, 0.0f,  // candidate 1: orthogonal -> 0.0
                -1.0f, 0.0f, 0.0f, 0.0f  // candidate 2: opposite -> -1.0
        };
        float[] outScores = new float[3];

        kernel.evaluateCandidates(query, candidates, 3, 4, SimilarityFunction.COSINE, outScores);

        assertThat(outScores[0]).isCloseTo(1.0f, within(1e-5f));
        assertThat(outScores[1]).isCloseTo(0.0f, within(1e-5f));
        assertThat(outScores[2]).isCloseTo(-1.0f, within(1e-5f));
    }

    @Test
    @DisplayName("evaluateCandidates computes correct euclidean distances")
    void testEvaluateCandidatesEuclidean() {
        float[] query = {1.0f, 2.0f, 3.0f, 4.0f};
        float[] candidates = {
                1.0f, 2.0f, 3.0f, 4.0f,  // dist = 0.0
                1.0f, 2.0f, 3.0f, 7.0f   // diff = 3 -> dist = 3.0
        };
        float[] outScores = new float[2];

        kernel.evaluateCandidates(query, candidates, 2, 4, SimilarityFunction.EUCLIDEAN, outScores);

        assertThat(outScores[0]).isCloseTo(0.0f, within(1e-5f));
        assertThat(outScores[1]).isCloseTo(3.0f, within(1e-5f));
    }
}
