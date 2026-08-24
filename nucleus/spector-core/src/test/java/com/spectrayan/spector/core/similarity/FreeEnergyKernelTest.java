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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the SIMD-accelerated {@link FreeEnergyKernel}.
 */
class FreeEnergyKernelTest {

    @Test
    void identicalDistributions_klIsZero() {
        float[] mean = {0.5f, -0.2f, 0.8f, 1.2f};
        float[] prec = {2.0f, 3.0f, 1.5f, 4.0f};

        float kl = FreeEnergyKernel.gaussianKLDivergence(mean, prec, mean, prec);
        assertThat(kl).isCloseTo(0.0f, within(1e-5f));
    }

    @Test
    void shiftedMeans_klIsPositive() {
        float[] meanQ = {1.0f, 0.0f};
        float[] precQ = {2.0f, 2.0f};
        float[] meanP = {0.0f, 0.0f};
        float[] precP = {2.0f, 2.0f};

        // For equal precisions pi: KL = 0.5 * pi * (mu_q - mu_p)^2 = 0.5 * 2.0 * (1.0)^2 = 1.0
        float kl = FreeEnergyKernel.gaussianKLDivergence(meanQ, precQ, meanP, precP);
        assertThat(kl).isCloseTo(1.0f, within(1e-5f));
    }

    @Test
    void differentPrecisions_klMatchesAnalyticalValue() {
        float[] meanQ = {0.0f};
        float[] precQ = {4.0f}; // varQ = 0.25
        float[] meanP = {0.0f};
        float[] precP = {1.0f}; // varP = 1.0

        // D_KL = 0.5 * (ln(4/1) + 1/4 - 1) = 0.5 * (1.386294 + 0.25 - 1.0) = 0.5 * 0.636294 = 0.318147
        float kl = FreeEnergyKernel.gaussianKLDivergence(meanQ, precQ, meanP, precP);
        assertThat(kl).isCloseTo(0.318147f, within(1e-4f));
    }

    @Test
    void precisionWeightedFusion_computesExactBayesianPosterior() {
        float[] meanA = {1.0f, -2.0f};
        float[] precA = {2.0f, 4.0f};
        float[] meanB = {3.0f, 2.0f};
        float[] precB = {2.0f, 4.0f};

        float[] outMean = new float[2];
        float[] outPrec = new float[2];

        FreeEnergyKernel.precisionWeightedFusion(meanA, precA, meanB, precB, outMean, outPrec);

        // Dim 0: prec = 2+2=4. mean = (2*1 + 2*3)/4 = 8/4 = 2.0
        // Dim 1: prec = 4+4=8. mean = (4*-2 + 4*2)/8 = 0.0
        assertThat(outPrec[0]).isEqualTo(4.0f);
        assertThat(outPrec[1]).isEqualTo(8.0f);
        assertThat(outMean[0]).isEqualTo(2.0f);
        assertThat(outMean[1]).isEqualTo(0.0f);
    }

    @Test
    void variationalFreeEnergy_sumsKlAndExpectedNll() {
        float[] meanQ = {0.5f, -0.5f};
        float[] precQ = {2.0f, 2.0f};
        float[] meanP = {0.0f, 0.0f};
        float[] precP = {1.0f, 1.0f};
        float[] obs = {0.5f, -0.5f};
        float[] obsPrec = {4.0f, 4.0f};

        float fe = FreeEnergyKernel.variationalFreeEnergy(meanQ, precQ, meanP, precP, obs, obsPrec);
        float kl = FreeEnergyKernel.gaussianKLDivergence(meanQ, precQ, meanP, precP);
        float nll = FreeEnergyKernel.negativeExpectedLogLikelihood(meanQ, precQ, obs, obsPrec);

        assertThat(fe).isCloseTo(kl + nll, within(1e-5f));
    }

    @Test
    void nonSimdAlignedDimension_handlesMaskedTailAccurately() {
        int dim = 17; // non-power-of-two, non-16/8 multiple
        float[] meanA = new float[dim];
        float[] precA = new float[dim];
        float[] meanB = new float[dim];
        float[] precB = new float[dim];

        for (int i = 0; i < dim; i++) {
            meanA[i] = i * 0.1f;
            precA[i] = 1.0f + i * 0.2f;
            meanB[i] = i * 0.1f + 0.05f;
            precB[i] = 2.0f + i * 0.1f;
        }

        float kl = FreeEnergyKernel.gaussianKLDivergence(meanA, precA, meanB, precB);
        assertThat(kl).isGreaterThan(0.0f);

        float[] fusedMean = new float[dim];
        float[] fusedPrec = new float[dim];
        FreeEnergyKernel.precisionWeightedFusion(meanA, precA, meanB, precB, fusedMean, fusedPrec);
        assertThat(fusedPrec[dim - 1]).isCloseTo(precA[dim - 1] + precB[dim - 1], within(1e-5f));
    }

    @Test
    void inputValidation_throwsOnDimensionMismatch() {
        float[] a = {1.0f, 2.0f};
        float[] b = {1.0f};
        assertThatThrownBy(() -> FreeEnergyKernel.gaussianKLDivergence(a, a, b, a))
                .isInstanceOf(SpectorValidationException.class);
    }
}
