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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LifespanThresholdKernelTest {

    @Test
    @DisplayName("compute returns baseline tau0 at epoch 0 when volume equals target")
    void compute_baselineAtEpochZeroAndTargetVolume() {
        float tau = LifespanThresholdKernel.compute(0.30f, 0.15f, 0L, 365L, 100000L, 100000L, 1.2f);
        // At t=0: ln(1+0) = 0 => ageFactor = 1.0
        // At V=Vtarget: (100k/100k)^1.2 = 1.0
        // Result = 0.30 * 1.0 * 1.0 = 0.30
        assertThat(tau).isCloseTo(0.30f, within(1e-4f));
    }

    @Test
    @DisplayName("compute scales logarithmically with operational age")
    void compute_scalesWithOperationalAge() {
        // At t = 365 (1 year): ln(1 + 1) = ln(2) approx 0.693147
        // ageFactor = 1 + 0.15 * 0.693147 = 1.10397
        // At V = 100k: capacityFactor = 1.0
        // Result = 0.30 * 1.10397 = 0.33119
        float tau1Year = LifespanThresholdKernel.compute(0.30f, 0.15f, 365L, 365L, 100000L, 100000L, 1.2f);
        assertThat(tau1Year).isCloseTo(0.3312f, within(1e-3f));

        // At t = 36,500 (100 years): ln(1 + 100) = ln(101) approx 4.61512
        // ageFactor = 1 + 0.15 * 4.61512 = 1.69227
        // Result = 0.30 * 1.69227 = 0.50768
        float tau100Years = LifespanThresholdKernel.compute(0.30f, 0.15f, 36500L, 365L, 100000L, 100000L, 1.2f);
        assertThat(tau100Years).isCloseTo(0.5077f, within(1e-3f));
        assertThat(tau100Years).isGreaterThan(tau1Year);
    }

    @Test
    @DisplayName("compute scales exponentially with storage capacity pressure")
    void compute_scalesWithStoragePressure() {
        // At t = 0: ageFactor = 1.0
        // At V = 50,000 / 100,000 = 0.5: capacityFactor = 0.5^1.2 = 0.435275
        // Result = 0.30 * 0.435275 = 0.13058
        float tauLowVolume = LifespanThresholdKernel.compute(0.30f, 0.15f, 0L, 365L, 50000L, 100000L, 1.2f);
        assertThat(tauLowVolume).isCloseTo(0.1306f, within(1e-3f));

        // At V = 200,000 / 100,000 = 2.0: capacityFactor = 2.0^1.2 = 2.2974
        // Result = 0.30 * 2.2974 = 0.6892
        float tauHighVolume = LifespanThresholdKernel.compute(0.30f, 0.15f, 0L, 365L, 200000L, 100000L, 1.2f);
        assertThat(tauHighVolume).isCloseTo(0.6892f, within(1e-3f));
        assertThat(tauHighVolume).isGreaterThan(tauLowVolume);
    }

    @Test
    @DisplayName("compute clamps to 1.0 when raw threshold exceeds maximum")
    void compute_clampsToUpperBound() {
        // At extreme age (1,000,000 epochs) and massive overflow (10x target):
        float tau = LifespanThresholdKernel.compute(0.80f, 0.50f, 1000000L, 365L, 1000000L, 100000L, 2.0f);
        assertThat(tau).isEqualTo(1.0f);
    }

    @Test
    @DisplayName("compute clamps to 0.0 for zero volume or zero tau0")
    void compute_clampsToLowerBound() {
        float tauZero = LifespanThresholdKernel.compute(0.0f, 0.15f, 100L, 365L, 100000L, 100000L, 1.2f);
        assertThat(tauZero).isEqualTo(0.0f);

        float tauZeroVolume = LifespanThresholdKernel.compute(0.30f, 0.15f, 100L, 365L, 0L, 100000L, 1.2f);
        assertThat(tauZeroVolume).isEqualTo(0.0f);
    }

    @Test
    @DisplayName("computeBatch evaluates multiple volume states correctly")
    void computeBatch_evaluatesArray() {
        long[] volumes = new long[]{0L, 50000L, 100000L, 200000L};
        float[] output = new float[4];
        LifespanThresholdKernel.computeBatch(0.30f, 0.15f, 0L, 365L, volumes, 100000L, 1.2f, output);

        assertThat(output[0]).isEqualTo(0.0f);
        assertThat(output[1]).isCloseTo(0.1306f, within(1e-3f));
        assertThat(output[2]).isCloseTo(0.3000f, within(1e-3f));
        assertThat(output[3]).isCloseTo(0.6892f, within(1e-3f));
    }

    @ParameterizedTest
    @ValueSource(floats = {-0.1f, 1.1f})
    @DisplayName("compute throws on invalid tau0")
    void compute_throwsOnInvalidTau0(float invalidTau0) {
        assertThatThrownBy(() -> LifespanThresholdKernel.compute(invalidTau0, 0.15f, 0L, 365L, 100000L, 100000L, 1.2f))
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("tau0");
    }

    @Test
    @DisplayName("compute throws on negative k or non-positive denominators")
    void compute_throwsOnInvalidParameters() {
        assertThatThrownBy(() -> LifespanThresholdKernel.compute(0.30f, -0.1f, 0L, 365L, 100000L, 100000L, 1.2f))
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("k");

        assertThatThrownBy(() -> LifespanThresholdKernel.compute(0.30f, 0.15f, 0L, 0L, 100000L, 100000L, 1.2f))
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("t0Epochs");

        assertThatThrownBy(() -> LifespanThresholdKernel.compute(0.30f, 0.15f, 0L, 365L, 100000L, 0L, 1.2f))
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("targetVolume");

        assertThatThrownBy(() -> LifespanThresholdKernel.compute(0.30f, 0.15f, 0L, 365L, 100000L, 100000L, -1.0f))
                .isInstanceOf(SpectorValidationException.class)
                .hasMessageContaining("gamma");
    }
}
