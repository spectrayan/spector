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

class PersonalityTraitKernelTest {

    @Test
    void testLinearModulateNeuroticismExtremes() {
        // N=0 -> 1.0 + (0 - 50)/100 * 0.3 = 1.0 - 0.15 = 0.85
        final float lowN = PersonalityTraitKernel.linearModulate(0f, 50f, 100f, 0.3f, 1.0f);
        assertEquals(0.85f, lowN, 1e-5f);

        // N=50 -> 1.0
        final float midN = PersonalityTraitKernel.linearModulate(50f, 50f, 100f, 0.3f, 1.0f);
        assertEquals(1.00f, midN, 1e-5f);

        // N=100 -> 1.15
        final float highN = PersonalityTraitKernel.linearModulate(100f, 50f, 100f, 0.3f, 1.0f);
        assertEquals(1.15f, highN, 1e-5f);
    }
}
