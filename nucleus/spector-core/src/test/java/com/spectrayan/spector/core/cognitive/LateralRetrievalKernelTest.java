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

class LateralRetrievalKernelTest {

    @Test
    void testRatesAndHallucinationIndex() {
        // Zero returns guard
        assertEquals(0.0f, LateralRetrievalKernel.utilityRate(5, 0));
        assertEquals(0.0f, LateralRetrievalKernel.suppressionRate(5, 0));

        // 10 returned, 3 reinforced, 5 suppressed
        final float lur = LateralRetrievalKernel.utilityRate(3, 10);
        final float lsr = LateralRetrievalKernel.suppressionRate(5, 10);
        assertEquals(0.3f, lur, 1e-6f);
        assertEquals(0.5f, lsr, 1e-6f);

        // LHI = (1 - 0.3) * 0.5 = 0.35
        final float lhi = LateralRetrievalKernel.hallucinationIndex(lur, lsr);
        assertEquals(0.35f, lhi, 1e-6f);
    }
}
