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

import com.spectrayan.spector.core.cognitive.FreeEnergyKernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

/**
 * Unit tests for {@link FreeEnergyKernel#freeEnergyGradientNorm} and {@link FreeEnergyKernel#sensorySurprisal}.
 */
class FreeEnergyKernelGradientTest {

    @Test
    @DisplayName("freeEnergyGradientNorm: computes analytical normalized gradient magnitude")
    void freeEnergyGradientNorm_matchesAnalyticalScalarCalculation() {
        int dim = 16;
        float[] meanQ = new float[dim];
        float[] obs = new float[dim];
        float[] precision = new float[dim];

        Random random = new Random(42L);
        float scalarSumSq = 0.0f;

        for (int i = 0; i < dim; i++) {
            meanQ[i] = random.nextFloat() * 2.0f - 1.0f;
            obs[i] = random.nextFloat() * 2.0f - 1.0f;
            precision[i] = random.nextFloat() * 3.0f + 0.5f;

            float diff = obs[i] - meanQ[i];
            float grad = precision[i] * diff;
            scalarSumSq += grad * grad;
        }

        float expectedNorm = (float) Math.sqrt(scalarSumSq / dim);
        float actualNorm = FreeEnergyKernel.freeEnergyGradientNorm(meanQ, obs, precision);

        assertThat(actualNorm).isCloseTo(expectedNorm, within(1e-5f));
    }

    @Test
    @DisplayName("sensorySurprisal: computes analytical normalized quadratic prediction error")
    void sensorySurprisal_matchesAnalyticalScalarCalculation() {
        int dim = 32;
        float[] meanQ = new float[dim];
        float[] obs = new float[dim];
        float[] precision = new float[dim];

        Random random = new Random(101L);
        float scalarSumQuad = 0.0f;

        for (int i = 0; i < dim; i++) {
            meanQ[i] = random.nextFloat() * 2.0f - 1.0f;
            obs[i] = random.nextFloat() * 2.0f - 1.0f;
            precision[i] = random.nextFloat() * 4.0f + 0.1f;

            float diff = obs[i] - meanQ[i];
            scalarSumQuad += precision[i] * diff * diff;
        }

        float expectedSurprisal = 0.5f * (scalarSumQuad / dim);
        float actualSurprisal = FreeEnergyKernel.sensorySurprisal(meanQ, obs, precision);

        assertThat(actualSurprisal).isCloseTo(expectedSurprisal, within(1e-5f));
    }

    @Test
    @DisplayName("Identical observation and mean produces zero gradient and zero surprisal")
    void identicalObservation_returnsZero() {
        int dim = 16;
        float[] mean = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        float[] obs = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        float[] precision = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};

        assertThat(FreeEnergyKernel.freeEnergyGradientNorm(mean, obs, precision)).isZero();
        assertThat(FreeEnergyKernel.sensorySurprisal(mean, obs, precision)).isZero();
    }
}
