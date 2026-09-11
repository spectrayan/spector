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

import net.jqwik.api.*;
import net.jqwik.api.constraints.FloatRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CognitiveMassKernelTest {

    @Test
    void testBoundaryMassValues() {
        // Zero importance -> mass is 0
        assertEquals(0.0f, CognitiveMassKernel.computeMass(0.0f, (byte) 0, 1.0f), 1e-6f);

        // Importance 10, neutral arousal 0, storage 1.0 -> 1.0 * 1.0 * 1.0 = 1.0
        assertEquals(1.0f, CognitiveMassKernel.computeMass(10.0f, (byte) 0, 1.0f), 1e-4f);

        // Arousal 128 (unsigned) -> arousalNorm = 1 + 128/128 = 2.0
        assertEquals(2.0f, CognitiveMassKernel.computeMass(10.0f, (byte) -128, 1.0f), 1e-4f);
    }

    @Test
    void testFastStorageBoostLutAccuracy() {
        for (float s = 1.0f; s <= 5.0f; s += 0.25f) {
            final float fast = CognitiveMassKernel.fastStorageBoost(s, 0.3f);
            final float exact = (float) Math.pow(s, 0.3);
            assertEquals(exact, fast, 0.005f, "LUT interpolation should closely track Math.pow");
        }
    }

    @Property
    void massIsNonNegativeForValidInputs(
            @ForAll @FloatRange(min = 0.0f, max = 10.0f) float importance,
            @ForAll byte arousal,
            @ForAll @FloatRange(min = 1.0f, max = 10.0f) float storage) {
        final float mass = CognitiveMassKernel.computeMass(importance, arousal, storage);
        assertTrue(mass >= 0.0f, "Cognitive mass must be non-negative");
    }

    @Property
    void batchMatchesScalar(
            @ForAll @net.jqwik.api.From("validFloats") float[] importances,
            @ForAll @net.jqwik.api.From("batchArousals") byte[] arousals,
            @ForAll @net.jqwik.api.From("validFloats") float[] storage) {
        final int n = importances.length;
        final float[] outMasses = new float[n];

        CognitiveMassKernel.computeMassBatch(importances, arousals, storage, outMasses, n);

        for (int i = 0; i < n; i++) {
            final float expected = CognitiveMassKernel.computeMass(importances[i], arousals[i], storage[i]);
            assertEquals(expected, outMasses[i], 1e-6f);
        }
    }

    @Provide
    Arbitrary<float[]> validFloats() {
        return Arbitraries.floats().between(0.0f, 10.0f).array(float[].class).ofSize(16);
    }

    @Provide
    Arbitrary<byte[]> batchArousals() {
        return Arbitraries.bytes().array(byte[].class).ofSize(16);
    }
}
