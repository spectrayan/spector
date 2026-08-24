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
package com.spectrayan.spector.core.simd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RandomFeatureProjector}.
 */
class RandomFeatureProjectorTest {

    @Test
    void project_generatesStrictlyPositiveFeatures() {
        RandomFeatureProjector projector = new RandomFeatureProjector(16, 512, 42L);
        float[] vector = {
                0.1f, 0.2f, -0.3f, 0.4f, -0.5f, 0.6f, -0.7f, 0.8f,
                -0.1f, 0.3f, 0.5f, -0.2f, 0.4f, -0.6f, 0.8f, -0.9f
        };
        float[] features = new float[512];

        projector.project(vector, 1.0f, features);

        // Positive Random Features MUST be strictly positive for all dimensions
        for (int y = 0; y < 512; y++) {
            assertThat(features[y]).isGreaterThan(0.0f);
        }
    }

    @Test
    void estimateKernel_approximatesSelfKernelToNearUnity() {
        RandomFeatureProjector projector = new RandomFeatureProjector(8, 2048, 12345L);
        float[] a = {0.2f, -0.3f, 0.5f, 0.1f, -0.4f, 0.6f, -0.1f, 0.2f};

        // Self-kernel k(a, a) = exp(0) = 1.0
        float estimated = projector.estimateKernel(a, a, 1.0f);
        assertThat(estimated).isCloseTo(1.0f, within(0.15f));
    }

    @Test
    void estimateKernel_approximatesGaussianRbfKernel() {
        RandomFeatureProjector projector = new RandomFeatureProjector(4, 4096, 9999L);
        float[] a = {0.5f, 0.5f, 0.0f, 0.0f};
        float[] b = {0.5f, 0.0f, 0.5f, 0.0f};

        // ||a - b||^2 = (0.5)^2 + (-0.5)^2 = 0.25 + 0.25 = 0.5
        // True Gaussian RBF: exp(-0.5 * beta * 0.5) = exp(-0.25) ≈ 0.7788
        float beta = 1.0f;
        float trueKernel = (float) Math.exp(-0.5 * beta * 0.5);

        float estimated = projector.estimateKernel(a, b, beta);
        assertThat(estimated).isCloseTo(trueKernel, within(0.10f));
    }

    @Test
    void determinism_sameSeedProducesIdenticalProjections() {
        RandomFeatureProjector p1 = new RandomFeatureProjector(8, 256, 777L);
        RandomFeatureProjector p2 = new RandomFeatureProjector(8, 256, 777L);

        float[] vector = {1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f};
        float[] f1 = new float[256];
        float[] f2 = new float[256];

        p1.project(vector, 2.0f, f1);
        p2.project(vector, 2.0f, f2);

        for (int i = 0; i < 256; i++) {
            assertThat(f1[i]).isEqualTo(f2[i]);
        }
    }

    @Test
    void validation_throwsOnDimensionMismatch() {
        RandomFeatureProjector projector = new RandomFeatureProjector(4, 128, 42L);
        float[] wrongVec = {1.0f, 2.0f};
        float[] outFeat = new float[128];

        assertThatThrownBy(() -> projector.project(wrongVec, 1.0f, outFeat))
                .isInstanceOf(SpectorValidationException.class);
    }
}
