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

class StdpPlasticityKernelTest {

    @Test
    void testCausalAndAntiCausalDeltaWeights() {
        // dt = 0 -> 0.0
        assertEquals(0.0f, StdpPlasticityKernel.computeDeltaWeight(0L, 0.1f, 0.05f, 30000f, 30000f));

        // dt > 0 -> causal potentiation dW > 0
        final float causal = StdpPlasticityKernel.computeDeltaWeight(1000L, 0.1f, 0.05f, 30000f, 30000f);
        assertTrue(causal > 0.0f && causal <= 0.1f);

        // dt < 0 -> anti-causal depression dW < 0
        final float anti = StdpPlasticityKernel.computeDeltaWeight(-1000L, 0.1f, 0.05f, 30000f, 30000f);
        assertTrue(anti < 0.0f && anti >= -0.05f);
    }

    @Test
    void testUpdateWeightClamping() {
        // Clamping to [0.0, 1.0]
        assertEquals(1.0f, StdpPlasticityKernel.updateWeight(0.95f, 0.2f, 0.0f, 1.0f));
        assertEquals(0.0f, StdpPlasticityKernel.updateWeight(0.05f, -0.2f, 0.0f, 1.0f));
        assertEquals(0.6f, StdpPlasticityKernel.updateWeight(0.5f, 0.1f, 0.0f, 1.0f));
    }
}
