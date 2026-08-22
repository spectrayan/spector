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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PredictiveCodingKernel} SIMD kernel.
 */
class PredictiveCodingKernelTest {

    @Test
    void computePrecisionWeightedError_computesProductCorrectly() {
        float[] actual = {3.0f, 1.0f};
        float[] predicted = {1.0f, 4.0f};
        float[] precision = {2.0f, 0.5f};
        float[] outError = new float[2];

        // err[0] = 2.0 * (3.0 - 1.0) = 4.0
        // err[1] = 0.5 * (1.0 - 4.0) = -1.5
        PredictiveCodingKernel.computePrecisionWeightedError(actual, predicted, precision, outError);

        assertThat(outError[0]).isCloseTo(4.0f, within(1e-5f));
        assertThat(outError[1]).isCloseTo(-1.5f, within(1e-5f));
    }

    @Test
    void computeTierEnergy_halfSumWeightedSquaredErrors() {
        float[] actual = {3.0f, 1.0f};
        float[] predicted = {1.0f, 4.0f};
        float[] precision = {2.0f, 0.5f};

        // Energy = 0.5 * [ 2.0 * (2.0)^2 + 0.5 * (-3.0)^2 ] = 0.5 * [ 8.0 + 4.5 ] = 6.25
        float energy = PredictiveCodingKernel.computeTierEnergy(actual, predicted, precision);

        assertThat(energy).isCloseTo(6.25f, within(1e-5f));
    }

    @Test
    void computeHierarchicalEnergy_sumsAcrossTiers() {
        float[][] actual = {
                {2.0f},
                {4.0f}
        };
        float[][] predicted = {
                {0.0f},
                {2.0f}
        };
        float[][] precision = {
                {1.0f},
                {1.0f}
        };

        // Tier 0: 0.5 * 1.0 * (2-0)^2 = 2.0
        // Tier 1: 0.5 * 1.0 * (4-2)^2 = 2.0
        // Total: 4.0
        float total = PredictiveCodingKernel.computeHierarchicalEnergy(actual, predicted, precision);
        assertThat(total).isCloseTo(4.0f, within(1e-5f));
    }

    @Test
    void affineProjection_multipliesMatrixAndAddsBias() {
        float[] source = {1.0f, 2.0f};
        float[][] W = {
                {2.0f, 0.0f},
                {1.0f, 3.0f}
        };
        float[] bias = {0.5f, -1.0f};
        float[] target = new float[2];

        // target[0] = 2*1 + 0*2 + 0.5 = 2.5
        // target[1] = 1*1 + 3*2 - 1.0 = 6.0
        PredictiveCodingKernel.affineProjection(source, W, bias, target);

        assertThat(target[0]).isCloseTo(2.5f, within(1e-5f));
        assertThat(target[1]).isCloseTo(6.0f, within(1e-5f));
    }

    @Test
    void simdTail_handlesNonAlignedDimensionCorrectly() {
        int dim = 21;
        float[] actual = new float[dim];
        float[] predicted = new float[dim];
        float[] prec = new float[dim];
        float[] outErr = new float[dim];

        for (int i = 0; i < dim; i++) {
            actual[i] = i * 0.1f + 1.0f;
            predicted[i] = i * 0.1f;
            prec[i] = 2.0f;
        }

        PredictiveCodingKernel.computePrecisionWeightedError(actual, predicted, prec, outErr);
        assertThat(outErr[dim - 1]).isCloseTo(2.0f, within(1e-4f));

        float energy = PredictiveCodingKernel.computeTierEnergy(actual, predicted, prec);
        // Each dim: 0.5 * 2.0 * 1^2 = 1.0. Total = 21.0
        assertThat(energy).isCloseTo(21.0f, within(1e-4f));
    }

    @Test
    void validation_throwsOnDimensionMismatch() {
        float[] a = {1.0f, 2.0f};
        float[] b = {1.0f};
        assertThatThrownBy(() -> PredictiveCodingKernel.computeTierEnergy(a, b, a))
                .isInstanceOf(SpectorValidationException.class);
    }
}
