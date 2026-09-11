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
package com.spectrayan.spector.core.cognitive;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActRActivationKernelTest {

    @Test
    void testFanFactor() {
        assertEquals(1.0f, ActRActivationKernel.fanFactor(0), 1e-6f);
        assertEquals(1.0f, ActRActivationKernel.fanFactor(-5), 1e-6f);
        assertEquals(1.0f, ActRActivationKernel.fanFactor(1), 1e-6f);
        assertEquals(0.5f, ActRActivationKernel.fanFactor(4), 1e-6f);
        assertEquals((float) (1.0 / 3.0), ActRActivationKernel.fanFactor(9), 1e-6f);
    }

    @Test
    void testEmptySlotsReturnSentinel() {
        final int[] emptyRing = new int[8];
        final long now = 100_000L;
        final float activation = ActRActivationKernel.computeBucketActivation(
                emptyRing, 0L, now, PowerLawDecayKernel.DEFAULT_BUCKETS);
        assertEquals(-1.0f, activation, 1e-6f, "No recall history must return -1.0 sentinel");

        assertEquals(-1.0f, ActRActivationKernel.computeBaseLevelActivation(new long[0], 0.5f));
        assertEquals(-1.0f, ActRActivationKernel.computeBaseLevelActivation(null, 0.5f));
    }

    @Test
    void testBucketActivationComputation() {
        final int[] ring = new int[] { 10, 0, 0, 0, 0, 0, 0, 0 };
        final long creation = 0L;
        final long now = 100_000L; // 100s since creation; relSec=10 -> 90s age

        final float act = ActRActivationKernel.computeBucketActivation(
                ring, creation, now, PowerLawDecayKernel.DEFAULT_BUCKETS);
        assertTrue(act > 0.0f && act < 1.0f, "Activation must be strictly within (0, 1)");
    }

    @Test
    void testBaseLevelActivationFormula() {
        final long[] ages = new long[] { 1000L, 2000L };
        final float act = ActRActivationKernel.computeBaseLevelActivation(ages, 0.5f);
        assertTrue(act > 0.0f && act < 1.0f);
    }

    @Test
    void testBatchActivationsMatchScalar() {
        final int[][] rings = new int[][] {
                { 10, 0, 0, 0, 0, 0, 0, 0 },
                { 0, 0, 0, 0, 0, 0, 0, 0 },
                { 5, 15, 0, 0, 0, 0, 0, 0 }
        };
        final long[] creations = new long[] { 0L, 1000L, 2000L };
        final long now = 50_000L;
        final float[] outActivations = new float[3];

        ActRActivationKernel.computeBucketActivations(
                rings, creations, now, PowerLawDecayKernel.DEFAULT_BUCKETS, outActivations, 3);

        for (int i = 0; i < 3; i++) {
            final float expected = ActRActivationKernel.computeBucketActivation(
                    rings[i], creations[i], now, PowerLawDecayKernel.DEFAULT_BUCKETS);
            assertEquals(expected, outActivations[i], 1e-6f);
        }
    }
}
