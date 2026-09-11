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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IcnuSalienceKernelTest {

    @Test
    void testLinearFallbackWhenSteepnessZero() {
        final float val = IcnuSalienceKernel.fuse(
                1.0f, 0.0f, 0.0f, 0.0f,
                1.0f, 0.0f, 0.0f, 0.0f,
                0.2f, 0.0f); // steepness=0
        // raw = 1.0 -> scaled = 0.05 + 1.0 * 9.95 = 10.0
        assertEquals(10.0f, val, 1e-4f);
    }

    @Test
    void testDopaminergicMultiplicativeGating() {
        // High interest, zero novelty -> stimulus from I*N is 0 -> below threshold -> gated low
        final float boringNovelty = IcnuSalienceKernel.fuse(
                1.0f, 0.0f, 0.0f, 0.0f,
                1.0f, 0.0f, 0.0f, 0.0f,
                0.5f, 8.0f);
        assertTrue(boringNovelty < 1.0f, "Zero novelty must suppress interest in multiplicative mode");

        // High interest AND high novelty -> stimulus = 1.0 -> well above threshold 0.5 -> gated high
        final float exciting = IcnuSalienceKernel.fuse(
                1.0f, 0.0f, 1.0f, 0.0f,
                1.0f, 0.0f, 0.0f, 0.0f,
                0.5f, 8.0f);
        assertTrue(exciting > 9.0f, "High interest and novelty must produce strong importance");
    }

    @Property
    void fusedImportanceAlwaysWithinBounds(
            @ForAll @FloatRange(min = 0.0f, max = 1.0f) float i,
            @ForAll @FloatRange(min = 0.0f, max = 1.0f) float c,
            @ForAll @FloatRange(min = 0.0f, max = 1.0f) float n,
            @ForAll @FloatRange(min = 0.0f, max = 1.0f) float u,
            @ForAll @FloatRange(min = 0.0f, max = 1.0f) float threshold,
            @ForAll @FloatRange(min = 0.0f, max = 20.0f) float steepness) {
        final float val = IcnuSalienceKernel.fuse(i, c, n, u, 0.3f, 0.1f, 0.4f, 0.2f, threshold, steepness);
        assertTrue(val >= IcnuSalienceKernel.MIN_IMPORTANCE && val <= IcnuSalienceKernel.MAX_IMPORTANCE,
                "Fused importance must be within [" + IcnuSalienceKernel.MIN_IMPORTANCE + ", "
                        + IcnuSalienceKernel.MAX_IMPORTANCE + "], got " + val);
    }
}
