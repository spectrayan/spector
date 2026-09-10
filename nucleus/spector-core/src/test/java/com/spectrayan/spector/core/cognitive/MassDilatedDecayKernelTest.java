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

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.FloatRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MassDilatedDecayKernelTest {

    @Test
    void testZeroTimeDecayReturnsReconsolidationBoost() {
        final float decay = MassDilatedDecayKernel.compute(
                0L, 1_000_000L, 2.0f, (byte) 0, 4, true, 1.0f);
        // reconsolidationBoost = 1 + 0.05 * 4 = 1.20 -> clamped to 1.0
        assertEquals(1.0f, decay, 1e-6f);
    }

    @Test
    void testZeroLambdaReturnsReconsolidationBoost() {
        final float decay = MassDilatedDecayKernel.compute(
                0L, 1_000_000L, 2.0f, (byte) 0, 0, false, 0.0f);
        // reconsolidationBoost = 1.0, arousal = 1.0 -> 1.0
        assertEquals(1.0f, decay, 1e-6f);
    }

    @Property
    void massDilatedDecayIsAlwaysClampedToUnitInterval(
            @ForAll long timestampMs, @ForAll long nowMs,
            @ForAll @FloatRange(min = 0.0f, max = 10.0f) float mass,
            @ForAll byte arousal, @ForAll int recalls,
            @ForAll boolean zeroTimeDecay,
            @ForAll @FloatRange(min = 0.0f, max = 2.0f) float lambda) {
        final float decay = MassDilatedDecayKernel.compute(
                timestampMs, nowMs, mass, arousal, Math.max(0, recalls), zeroTimeDecay, lambda);
        assertTrue(decay >= 0.0f && decay <= 1.0f, "Decay must be within [0, 1]");
    }

    @Property
    void batchMatchesScalar(
            @ForAll @net.jqwik.api.From("batchTimestamps") long[] timestamps,
            @ForAll @net.jqwik.api.From("batchMasses") float[] masses,
            @ForAll @net.jqwik.api.From("batchArousals") byte[] arousals) {
        final int n = timestamps.length;
        final int[] recalls = new int[n];
        final boolean[] zeroDecays = new boolean[n];
        final float[] outDecays = new float[n];
        final long now = 10_000_000L;

        MassDilatedDecayKernel.computeBatch(
                timestamps, masses, arousals, recalls, zeroDecays, now, 1.0f, outDecays, n);

        for (int i = 0; i < n; i++) {
            final float expected = MassDilatedDecayKernel.compute(
                    timestamps[i], now, masses[i], arousals[i], recalls[i], zeroDecays[i], 1.0f);
            assertEquals(expected, outDecays[i], 1e-6f);
        }
    }

    @Provide
    Arbitrary<long[]> batchTimestamps() {
        return Arbitraries.longs().between(0L, 10_000_000L).array(long[].class).ofSize(16);
    }

    @Provide
    Arbitrary<float[]> batchMasses() {
        return Arbitraries.floats().between(0.0f, 5.0f).array(float[].class).ofSize(16);
    }

    @Provide
    Arbitrary<byte[]> batchArousals() {
        return Arbitraries.bytes().array(byte[].class).ofSize(16);
    }
}
