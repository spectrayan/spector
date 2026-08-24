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

import com.spectrayan.spector.core.privacy.DifferentialPrivacyKernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

/**
 * Unit tests for {@link DifferentialPrivacyKernel}.
 */
class DifferentialPrivacyKernelTest {

    @Test
    @DisplayName("computeGaussianSigma computes exact theoretical bound")
    void computeGaussianSigma_computesExactBound() {
        float sensitivity = 1.0f;
        float epsilon = 2.0f;
        float delta = 1e-5f;

        float sigma = DifferentialPrivacyKernel.computeGaussianSigma(sensitivity, epsilon, delta);

        // sqrt(2 * ln(125000)) / 2.0 = sqrt(2 * 11.736069) / 2.0 = sqrt(23.472138) / 2.0 = 4.8448 / 2.0 = 2.4224
        assertThat(sigma).isCloseTo(2.4224f, within(1e-3f));
    }

    @Test
    @DisplayName("clipVectorL2 clips vectors exceeding threshold and preserves smaller vectors")
    void clipVectorL2_clipsExceedingNorm() {
        // Vector with L2 norm = 5.0
        float[] largeVec = {3.0f, 4.0f};
        float[] clipped = DifferentialPrivacyKernel.clipVectorL2(largeVec, 1.0f);

        assertThat(VectorOps.magnitude(clipped)).isCloseTo(1.0f, within(1e-5f));
        assertThat(clipped[0]).isCloseTo(0.6f, within(1e-5f));
        assertThat(clipped[1]).isCloseTo(0.8f, within(1e-5f));

        // Vector with L2 norm = 0.5 <= 1.0
        float[] smallVec = {0.3f, 0.4f};
        float[] preserved = DifferentialPrivacyKernel.clipVectorL2(smallVec, 1.0f);

        assertThat(preserved[0]).isEqualTo(0.3f);
        assertThat(preserved[1]).isEqualTo(0.4f);
    }

    @Test
    @DisplayName("injectGaussianNoise perturbs vector with zero-mean noise")
    void injectGaussianNoise_perturbsVector() {
        int dim = 1000;
        float[] zeros = new float[dim];
        float sigma = 1.5f;
        Random rng = new Random(42L);

        float[] noisy = DifferentialPrivacyKernel.injectGaussianNoise(zeros, sigma, rng);

        float mean = 0.0f;
        float variance = 0.0f;
        for (float v : noisy) {
            mean += v;
        }
        mean /= dim;

        for (float v : noisy) {
            variance += (v - mean) * (v - mean);
        }
        variance /= dim;

        assertThat(mean).isCloseTo(0.0f, within(0.15f));
        assertThat((float) Math.sqrt(variance)).isCloseTo(sigma, within(0.15f));
    }

    @Test
    @DisplayName("injectLaplaceNoise perturbs scalar value")
    void injectLaplaceNoise_perturbsScalar() {
        float base = 5.0f;
        Random rng = new Random(1337L);

        float noisy = DifferentialPrivacyKernel.injectLaplaceNoise(base, 1.0f, 2.0f, rng);
        assertThat(noisy).isNotEqualTo(base);
    }

    @Test
    @DisplayName("Validation: Rejects invalid parameters")
    void validation_rejectsInvalidParameters() {
        assertThatThrownBy(() -> DifferentialPrivacyKernel.computeGaussianSigma(0.0f, 2.0f, 1e-5f))
                .isInstanceOf(SpectorValidationException.class);

        assertThatThrownBy(() -> DifferentialPrivacyKernel.computeGaussianSigma(1.0f, -1.0f, 1e-5f))
                .isInstanceOf(SpectorValidationException.class);

        assertThatThrownBy(() -> DifferentialPrivacyKernel.computeGaussianSigma(1.0f, 2.0f, 0.0f))
                .isInstanceOf(SpectorValidationException.class);

        assertThatThrownBy(() -> DifferentialPrivacyKernel.clipVectorL2(null, 1.0f))
                .isInstanceOf(SpectorValidationException.class);

        assertThatThrownBy(() -> DifferentialPrivacyKernel.clipVectorL2(new float[4], 0.0f))
                .isInstanceOf(SpectorValidationException.class);
    }
}
