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
package com.spectrayan.spector.core.parity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ADR-0033 ParityHarness Validation")
class ParityHarnessTest {

    @Test
    @DisplayName("Bit-exact parity succeeds for identical values and NaNs")
    void bitExactSuccess() {
        ParityHarness.assertBitExact(1.2345f, 1.2345f, "identical float");
        ParityHarness.assertBitExact(Float.NaN, Float.NaN, "NaN matches NaN");

        float[] a = {1.0f, 2.5f, -0.75f};
        float[] b = {1.0f, 2.5f, -0.75f};
        ParityHarness.assertBitExact(a, b, "float array");
    }

    @Test
    @DisplayName("Bit-exact parity fails on slightest mantissa difference")
    void bitExactFailure() {
        float a = 1.0f;
        float b = Math.nextUp(1.0f);

        assertThatThrownBy(() -> ParityHarness.assertBitExact(a, b, "nextUp float"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Bit-exact parity failed");
    }

    @Test
    @DisplayName("Epsilon parity succeeds within tolerance")
    void epsilonSuccess() {
        float actual = 1.000001f;
        float expected = 1.000000f;
        ParityHarness.assertWithinEpsilon(actual, expected, 1e-5f, "close float");

        float[] a = {1.000001f, 2.000002f};
        float[] b = {1.000000f, 2.000000f};
        ParityHarness.assertWithinEpsilon(a, b, 1e-5f, "close float array");
    }

    @Test
    @DisplayName("Batch matches scalar helper functions properly")
    void batchMatchesScalar() {
        float[] batch = {0.1f, 0.2f, 0.3f};
        float[] scalar = {0.1f, 0.2f, 0.3f};
        ParityHarness.assertBatchMatchesScalar(batch, scalar, 1e-6f, "test scan");
    }
}
