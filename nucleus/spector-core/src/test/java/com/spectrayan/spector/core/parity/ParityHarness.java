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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * ADR-0033 Principle 6: Behavioural Parity Harness.
 *
 * <p>Provides deterministic parity assertions across migrated mathematical algorithms,
 * ensuring migrated kernels maintain bit-exact or documented epsilon equivalence against
 * baseline implementations and golden-value corpora.</p>
 */
public final class ParityHarness {

    private ParityHarness() {
        // utility class
    }

    /**
     * Asserts bit-exact equivalence between two IEEE 754 float values.
     *
     * @param actual   the actual result from the migrated kernel
     * @param expected the baseline golden value
     * @param context  description of the formula or test vector
     */
    public static void assertBitExact(float actual, float expected, String context) {
        int actualBits = Float.floatToRawIntBits(actual);
        int expectedBits = Float.floatToRawIntBits(expected);

        if (Float.isNaN(actual) && Float.isNaN(expected)) {
            return;
        }

        assertThat(actualBits)
                .as("Bit-exact parity failed for '%s': expected %f (0x%08X), got %f (0x%08X)",
                        context, expected, expectedBits, actual, actualBits)
                .isEqualTo(expectedBits);
    }

    /**
     * Asserts bit-exact equivalence between two float arrays.
     *
     * @param actual   the actual result array
     * @param expected the baseline golden array
     * @param context  description of the formula or test vector
     */
    public static void assertBitExact(float[] actual, float[] expected, String context) {
        assertThat(actual)
                .as("Array length mismatch for '%s'", context)
                .hasSameSizeAs(expected);

        for (int i = 0; i < actual.length; i++) {
            assertBitExact(actual[i], expected[i], context + "[" + i + "]");
        }
    }

    /**
     * Asserts numerical equivalence within a documented tolerance epsilon.
     *
     * @param actual   the actual result from the migrated kernel
     * @param expected the baseline golden value
     * @param epsilon  maximum allowed absolute delta
     * @param context  description of the formula or test vector
     */
    public static void assertWithinEpsilon(float actual, float expected, float epsilon, String context) {
        if (Float.isNaN(actual) && Float.isNaN(expected)) {
            return;
        }
        assertThat(actual)
                .as("Epsilon parity failed for '%s' (tolerance=%e)", context, epsilon)
                .isCloseTo(expected, within(epsilon));
    }

    /**
     * Asserts numerical equivalence within a documented tolerance epsilon for float arrays.
     *
     * @param actual   the actual result array
     * @param expected the baseline golden array
     * @param epsilon  maximum allowed absolute delta
     * @param context  description of the formula or test vector
     */
    public static void assertWithinEpsilon(float[] actual, float[] expected, float epsilon, String context) {
        assertThat(actual)
                .as("Array length mismatch for '%s'", context)
                .hasSameSizeAs(expected);

        for (int i = 0; i < actual.length; i++) {
            assertWithinEpsilon(actual[i], expected[i], epsilon, context + "[" + i + "]");
        }
    }

    /**
     * Asserts that a struct-of-arrays batch computation matches sequential scalar invocations.
     *
     * @param batchOut  output array produced by the batch SIMD seam
     * @param scalarOut output array produced by per-record scalar evaluations
     * @param epsilon   maximum permitted discrepancy
     * @param context   description of the benchmark/parity run
     */
    public static void assertBatchMatchesScalar(float[] batchOut, float[] scalarOut, float epsilon, String context) {
        assertThat(batchOut)
                .as("Batch vs Scalar output count mismatch for '%s'", context)
                .hasSameSizeAs(scalarOut);

        assertWithinEpsilon(batchOut, scalarOut, epsilon, "Batch vs Scalar parity: " + context);
    }
}
