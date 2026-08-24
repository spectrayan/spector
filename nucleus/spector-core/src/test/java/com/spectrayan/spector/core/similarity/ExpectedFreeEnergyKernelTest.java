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

import com.spectrayan.spector.core.cognitive.ExpectedFreeEnergyKernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the SIMD-accelerated {@link ExpectedFreeEnergyKernel}.
 */
class ExpectedFreeEnergyKernelTest {

    @Test
    void pragmaticRisk_identicalDistributions_isZero() {
        float[] mean = {0.5f, -0.2f, 0.8f, 1.2f};
        float[] prec = {2.0f, 3.0f, 1.5f, 4.0f};

        float risk = ExpectedFreeEnergyKernel.pragmaticRisk(mean, mean, prec, prec);
        assertThat(risk).isCloseTo(0.0f, within(1e-5f));
    }

    @Test
    void pragmaticRisk_shiftedMeans_isPositive() {
        float[] meanQ = {1.0f, 0.0f};
        float[] precQ = {2.0f, 2.0f};
        float[] meanP = {0.0f, 0.0f};
        float[] precP = {2.0f, 2.0f};

        // For equal precisions pi: KL = 0.5 * pi * (mu_q - mu_p)^2 = 0.5 * 2.0 * (1.0)^2 = 1.0
        float risk = ExpectedFreeEnergyKernel.pragmaticRisk(meanQ, meanP, precQ, precP);
        assertThat(risk).isCloseTo(1.0f, within(1e-5f));
    }

    @Test
    void epistemicAmbiguity_highPrecision_lowEntropy() {
        // High precision (narrow distribution) -> Low entropy (low ambiguity)
        float[] precHigh = {100.0f, 100.0f};
        float ambHigh = ExpectedFreeEnergyKernel.epistemicAmbiguity(precHigh);
        
        // Low precision (wide distribution) -> High entropy (high ambiguity)
        float[] precLow = {1.0f, 1.0f};
        float ambLow = ExpectedFreeEnergyKernel.epistemicAmbiguity(precLow);

        assertThat(ambHigh).isLessThan(ambLow);
    }

    @Test
    void epistemicAmbiguity_lowPrecision_highEntropy() {
        float[] precLow = {1.0f, 1.0f};
        float ambLow = ExpectedFreeEnergyKernel.epistemicAmbiguity(precLow);
        
        float[] precHigh = {10.0f, 10.0f};
        float ambHigh = ExpectedFreeEnergyKernel.epistemicAmbiguity(precHigh);

        assertThat(ambLow).isGreaterThan(ambHigh);
    }

    @Test
    void expectedFreeEnergy_combinesBothCorrectly() {
        float[] meanQ = {1.0f, 0.0f};
        float[] precQ = {2.0f, 2.0f};
        float[] meanP = {0.0f, 0.0f};
        float[] precP = {2.0f, 2.0f};
        float[] postPrec = {4.0f, 4.0f};

        float pragmaticWeight = 1.0f;
        float epistemicWeight = 1.0f;

        float efe = ExpectedFreeEnergyKernel.expectedFreeEnergy(meanQ, meanP, precQ, precP, postPrec, pragmaticWeight, epistemicWeight);
        float pragmatic = ExpectedFreeEnergyKernel.pragmaticRisk(meanQ, meanP, precQ, precP);
        float epistemic = ExpectedFreeEnergyKernel.epistemicAmbiguity(postPrec);

        assertThat(efe).isCloseTo(pragmatic * pragmaticWeight + epistemic * epistemicWeight, within(1e-5f));
    }

    @Test
    void inputValidation_throwsOnNullOrDimensionMismatch() {
        float[] a = {1.0f, 2.0f};
        float[] b = {1.0f};
        
        assertThatThrownBy(() -> ExpectedFreeEnergyKernel.pragmaticRisk(a, a, b, a))
                .isInstanceOf(SpectorValidationException.class);
                
        assertThatThrownBy(() -> ExpectedFreeEnergyKernel.epistemicAmbiguity(null))
                .isInstanceOf(SpectorValidationException.class);
                
        assertThatThrownBy(() -> ExpectedFreeEnergyKernel.epistemicAmbiguity(new float[0]))
                .isInstanceOf(SpectorValidationException.class);
    }
}
