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

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.FloatRange;
import net.jqwik.api.constraints.Positive;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HabituationKernelTest {

    @Test
    void testHabituationPenaltyStepDecay() {
        assertEquals(1.0f, HabituationKernel.penalty(1, 0.2f), 1e-6f);
        assertEquals(0.8333f, HabituationKernel.penalty(2, 0.2f), 0.001f);
        assertEquals(0.5f, HabituationKernel.penalty(6, 0.2f), 0.001f);
    }

    @Test
    void testInhibitionOfReturnLinearRecovery() {
        final long ttl = 300_000L; // 5 min
        final float floor = 0.1f;

        // Immediate recall -> floor
        assertEquals(floor, HabituationKernel.inhibitionOfReturn(0L, ttl, floor), 1e-6f);

        // Halfway through TTL -> halfway between 0.1 and 1.0 = 0.55
        assertEquals(0.55f, HabituationKernel.inhibitionOfReturn(150_000L, ttl, floor), 0.001f);

        // At or beyond TTL -> 1.0
        assertEquals(1.0f, HabituationKernel.inhibitionOfReturn(ttl, ttl, floor), 1e-6f);
        assertEquals(1.0f, HabituationKernel.inhibitionOfReturn(ttl + 10_000L, ttl, floor), 1e-6f);
    }

    @Property
    void penaltyMonotonicallyDecreases(
            @ForAll @Positive int k1, @ForAll @Positive int k2,
            @ForAll @FloatRange(min = 0.01f, max = 1.0f) float decayRate) {
        final int lower = Math.min(k1, k2);
        final int higher = Math.max(k1, k2);
        assertTrue(HabituationKernel.penalty(lower, decayRate) >= HabituationKernel.penalty(higher, decayRate));
    }
}
