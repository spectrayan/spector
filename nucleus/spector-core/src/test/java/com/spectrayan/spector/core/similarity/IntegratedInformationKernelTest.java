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
 * Unit tests for {@link IntegratedInformationKernel}.
 */
class IntegratedInformationKernelTest {

    @Test
    void computeGramMatrix_createsSymmetricCosineGram() {
        float[][] vectors = {
                {1.0f, 0.0f},
                {0.0f, 1.0f}
        };
        float[][] gram = new float[2][2];

        IntegratedInformationKernel.computeGramMatrix(vectors, gram, 0.01f);

        assertThat(gram[0][0]).isCloseTo(1.01f, within(1e-5f));
        assertThat(gram[1][1]).isCloseTo(1.01f, within(1e-5f));
        assertThat(gram[0][1]).isCloseTo(0.0f, within(1e-5f));
        assertThat(gram[1][0]).isCloseTo(0.0f, within(1e-5f));
    }

    @Test
    void choleskyLogDeterminant_calculatesAccurately() {
        // Diagonal matrix [2, 0; 0, 3] -> det = 6 -> ln(6) ≈ 1.79176
        float[][] diagMatrix = {
                {2.0f, 0.0f},
                {0.0f, 3.0f}
        };

        float logDet = IntegratedInformationKernel.choleskyLogDeterminant(diagMatrix);
        assertThat(logDet).isCloseTo((float) Math.log(6.0), within(1e-4f));
    }

    @Test
    void computeMultiInformation_uncorrelatedVectors_nearZero() {
        // Orthogonal vectors Gram matrix ~ identity -> logDet ≈ sum(ln(diag)) -> I(X) ≈ 0
        float[][] gram = {
                {1.001f, 0.0f},
                {0.0f, 1.001f}
        };

        float multiInfo = IntegratedInformationKernel.computeMultiInformation(gram);
        assertThat(multiInfo).isCloseTo(0.0f, within(1e-3f));
    }

    @Test
    void computePhi_correlatedCluster_positiveSynergy() {
        // Correlated cluster with cross-coupling
        float[][] correlatedGram = {
                {1.01f, 0.8f, 0.7f},
                {0.8f, 1.01f, 0.75f},
                {0.7f, 0.75f, 1.01f}
        };

        float phi = IntegratedInformationKernel.computePhi(correlatedGram);
        assertThat(phi).isGreaterThanOrEqualTo(0.0f);
    }

    @Test
    void validation_throwsOnNullOrDimensionMismatch() {
        assertThatThrownBy(() -> IntegratedInformationKernel.computeGramMatrix(null, new float[1][1], 0f))
                .isInstanceOf(SpectorValidationException.class);
        assertThatThrownBy(() -> IntegratedInformationKernel.choleskyLogDeterminant(null))
                .isInstanceOf(SpectorValidationException.class);
    }
}
