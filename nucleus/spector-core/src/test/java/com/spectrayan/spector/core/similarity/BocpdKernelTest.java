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

import com.spectrayan.spector.core.cognitive.BocpdKernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Random;

/**
 * Unit tests for SIMD {@link BocpdKernel}.
 */
class BocpdKernelTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 7, 8, 9, 15, 16, 17, 33, 64, 128, 384, 768, 1536})
    @DisplayName("evaluateLogLikelihoodForRun matches analytical scalar formula across dimensions")
    void evaluateLogLikelihoodForRun_matchesAnalyticalScalar(int dim) {
        Random rng = new Random(2026L);

        float[] obs = new float[dim];
        float[] priorMean = new float[dim];
        float[] priorPrec = new float[dim];
        float[] sumAcc = new float[dim];
        float[] obsPrec = new float[dim];

        for (int i = 0; i < dim; i++) {
            obs[i] = rng.nextFloat() * 2.0f - 1.0f;
            priorMean[i] = rng.nextFloat() * 0.5f;
            priorPrec[i] = 0.05f + rng.nextFloat() * 0.1f;
            sumAcc[i] = rng.nextFloat() * 5.0f;
            obsPrec[i] = 1.0f + rng.nextFloat() * 2.0f;
        }

        int r = 12;

        // SIMD Kernel execution
        float simdResult = BocpdKernel.evaluateLogLikelihoodForRun(obs, priorMean, priorPrec, sumAcc, obsPrec, r);

        // Scalar analytical execution
        float scalarResult = 0.0f;
        for (int d = 0; d < dim; d++) {
            float postPrec = priorPrec[d] + r * obsPrec[d];
            float postMean = (priorPrec[d] * priorMean[d] + obsPrec[d] * sumAcc[d]) / postPrec;
            float predVar = (1.0f / postPrec) + (1.0f / obsPrec[d]);
            float predPrec = 1.0f / predVar;
            float diff = obs[d] - postMean;
            scalarResult += 0.5f * (float) Math.log(predPrec / (2.0 * Math.PI)) - 0.5f * predPrec * diff * diff;
        }

        assertThat(simdResult).isCloseTo(scalarResult, within(1e-3f));
    }

    @Test
    @DisplayName("evaluateLogLikelihoodForRun handles r=0 with null sumAccR")
    void evaluateLogLikelihoodForRun_handlesZeroRunLength() {
        int dim = 16;
        float[] obs = new float[dim];
        Arrays.fill(obs, 0.8f);
        float[] priorMean = new float[dim];
        Arrays.fill(priorMean, 0.0f);
        float[] priorPrec = new float[dim];
        Arrays.fill(priorPrec, 0.05f);
        float[] obsPrec = new float[dim];
        Arrays.fill(obsPrec, 1.0f);

        float result = BocpdKernel.evaluateLogLikelihoodForRun(obs, priorMean, priorPrec, null, obsPrec, 0);

        // Verify it is finite and non-NaN
        assertThat(Float.isFinite(result)).isTrue();
    }

    @Test
    @DisplayName("evaluateLogLikelihoodForRun rejects mismatched dimensions")
    void evaluateLogLikelihoodForRun_rejectsMismatchedDimensions() {
        float[] obs = new float[8];
        float[] priorMean = new float[16];
        float[] priorPrec = new float[8];
        float[] obsPrec = new float[8];

        assertThatThrownBy(() -> BocpdKernel.evaluateLogLikelihoodForRun(obs, priorMean, priorPrec, null, obsPrec, 0))
                .isInstanceOf(SpectorValidationException.class);
    }
}
