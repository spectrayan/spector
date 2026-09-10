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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerLawDecayKernelTest {

    @Test
    void testDefaultBucketsMonotonicityAndPermastoreFloor() {
        final float[] buckets = PowerLawDecayKernel.DEFAULT_BUCKETS;
        assertEquals(12, buckets.length);
        assertEquals(1.0f, buckets[0], 1e-6f);

        for (int i = 1; i < buckets.length; i++) {
            assertTrue(buckets[i] <= buckets[i - 1] + 1e-6f,
                    "Decay buckets should be monotonically non-increasing");
            assertTrue(buckets[i] >= PowerLawDecayKernel.DEFAULT_PERMASTORE_FLOOR - 1e-6f,
                    "Bucket must respect permastore floor");
        }
    }

    @Test
    void testAgeToBucketRanges() {
        final long now = 10_000_000_000L;
        assertEquals(0, PowerLawDecayKernel.ageToBucket(now + 1000L, now)); // future
        assertEquals(0, PowerLawDecayKernel.ageToBucket(now - 1000L, now));  // 1s
        assertEquals(1, PowerLawDecayKernel.ageToBucket(now - 2 * PowerLawDecayKernel.HOUR_MS, now));
        assertEquals(2, PowerLawDecayKernel.ageToBucket(now - 12 * PowerLawDecayKernel.HOUR_MS, now));
        assertEquals(3, PowerLawDecayKernel.ageToBucket(now - 2 * PowerLawDecayKernel.DAY_MS, now));
        assertEquals(4, PowerLawDecayKernel.ageToBucket(now - 5 * PowerLawDecayKernel.DAY_MS, now));
        assertEquals(11, PowerLawDecayKernel.ageToBucket(now - 6 * PowerLawDecayKernel.YEAR_MS, now));
    }

    @Test
    void testReconsolidationAndAutoRecallAdjustments() {
        assertEquals(6, PowerLawDecayKernel.adjustForReconsolidation(6, 0));
        assertEquals(3, PowerLawDecayKernel.adjustForReconsolidation(6, 1));
        assertEquals(1, PowerLawDecayKernel.adjustForReconsolidation(6, 2));
        assertEquals(0, PowerLawDecayKernel.adjustForReconsolidation(6, 5));

        assertEquals(6, PowerLawDecayKernel.adjustForAutoRecall(6, 0));
        assertEquals(6, PowerLawDecayKernel.adjustForAutoRecall(6, 2));
        assertEquals(5, PowerLawDecayKernel.adjustForAutoRecall(6, 3));
        assertEquals(4, PowerLawDecayKernel.adjustForAutoRecall(6, 10)); // max shift is 2
    }

    @Test
    void testArousalModifierQuartiles() {
        assertEquals(1.00f, PowerLawDecayKernel.arousalModifier((byte) 0), 1e-6f);
        assertEquals(1.00f, PowerLawDecayKernel.arousalModifier((byte) 63), 1e-6f);
        assertEquals(1.15f, PowerLawDecayKernel.arousalModifier((byte) 64), 1e-6f);
        assertEquals(1.15f, PowerLawDecayKernel.arousalModifier((byte) 127), 1e-6f);
        assertEquals(1.35f, PowerLawDecayKernel.arousalModifier((byte) -128), 1e-6f); // 128 unsigned
        assertEquals(1.65f, PowerLawDecayKernel.arousalModifier((byte) -1), 1e-6f);   // 255 unsigned
    }

    @Property
    void decayIsAlwaysWithinUnitInterval(
            @ForAll long timestampMs, @ForAll long nowMs,
            @ForAll int recallCount, @ForAll byte arousal) {
        final float decay = PowerLawDecayKernel.computeDecayWithArousal(
                timestampMs, nowMs, Math.max(0, recallCount), arousal, PowerLawDecayKernel.DEFAULT_BUCKETS);
        assertTrue(decay >= 0.0f && decay <= 1.0f, "Decay must be within [0, 1]");
    }

    @Property
    void batchMatchesScalar(
            @ForAll @net.jqwik.api.From("batchTimestamps") long[] timestamps,
            @ForAll @net.jqwik.api.From("batchArousals") byte[] arousals) {
        final int n = timestamps.length;
        final int[] recalls = new int[n];
        final float[] outDecays = new float[n];
        final long now = 10_000_000L;

        PowerLawDecayKernel.computeDecayBatch(
                timestamps, recalls, arousals, now, PowerLawDecayKernel.DEFAULT_BUCKETS, outDecays, n);

        for (int i = 0; i < n; i++) {
            final float expected = PowerLawDecayKernel.computeDecayWithArousal(
                    timestamps[i], now, recalls[i], arousals[i], PowerLawDecayKernel.DEFAULT_BUCKETS);
            assertEquals(expected, outDecays[i], 1e-6f);
        }
    }

    @Provide
    Arbitrary<long[]> batchTimestamps() {
        return Arbitraries.longs().between(0L, 10_000_000L).array(long[].class).ofSize(16);
    }

    @Provide
    Arbitrary<byte[]> batchArousals() {
        return Arbitraries.bytes().array(byte[].class).ofSize(16);
    }
}
