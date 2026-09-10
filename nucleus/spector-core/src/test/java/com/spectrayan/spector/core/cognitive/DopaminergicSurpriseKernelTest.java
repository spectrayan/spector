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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DopaminergicSurpriseKernelTest {

    @Test
    void testZScoreToImportanceKnownPoints() {
        // At center (z=1.0), sigmoid is 0.5 -> 0.05 + 0.5 * 9.95 = 5.025
        final float centerImportance = DopaminergicSurpriseKernel.zScoreToImportance(1.0);
        assertEquals(5.025f, centerImportance, 0.01f);

        // Extreme negative z -> approaches 0.05
        final float lowImportance = DopaminergicSurpriseKernel.zScoreToImportance(-10.0);
        assertEquals(0.05f, lowImportance, 0.01f);

        // Extreme positive z -> approaches 10.0
        final float highImportance = DopaminergicSurpriseKernel.zScoreToImportance(10.0);
        assertEquals(10.0f, highImportance, 0.01f);
    }

    @Test
    void testDualSurpriseWeighting() {
        assertEquals(4.0f, DopaminergicSurpriseKernel.dualSurprise(5.0f, 2.0f, 0.67f), 0.1f);
        assertEquals(5.0f, DopaminergicSurpriseKernel.dualSurprise(5.0f, 2.0f, 1.0f), 1e-6f);
        assertEquals(2.0f, DopaminergicSurpriseKernel.dualSurprise(5.0f, 2.0f, 0.0f), 1e-6f);
    }

    @Test
    void testFlashbulbGating() {
        assertFalse(DopaminergicSurpriseKernel.isFlashbulb(2.9));
        assertFalse(DopaminergicSurpriseKernel.isFlashbulb(3.0));
        assertTrue(DopaminergicSurpriseKernel.isFlashbulb(3.01));
        assertTrue(DopaminergicSurpriseKernel.isFlashbulb(5.0));
    }

    @Property
    void importanceIsAlwaysWithinBounds(@ForAll double zScore) {
        final float importance = DopaminergicSurpriseKernel.zScoreToImportance(zScore);
        assertTrue(importance >= DopaminergicSurpriseKernel.MIN_IMPORTANCE,
                "Importance must be >= MIN_IMPORTANCE");
        assertTrue(importance <= DopaminergicSurpriseKernel.MAX_IMPORTANCE,
                "Importance must be <= MAX_IMPORTANCE");
    }
}
