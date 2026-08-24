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
package com.spectrayan.spector.core.similarity;

import com.spectrayan.spector.core.expression.VocalProsodyKernel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class VocalProsodyKernelTest {
    @Test
    public void testComputeAcousticModulations() {
        float[] sensitivities = {10.0f, 5.0f, 0.2f, 0.1f, 0.5f, 0.3f, 2.0f};
        float valence = 0.5f;
        float arousal = 0.8f;
        float dominance = 0.4f;

        float[] expected = {
            0.8f * 10.0f + 0.5f * 5.0f, // 8 + 2.5 = 10.5
            1.0f + 0.8f * 0.2f + 0.5f * 0.1f, // 1 + 0.16 + 0.05 = 1.21
            1.0f + 0.8f * 0.5f, // 1 + 0.4 = 1.4
            1.0f - 0.4f * 0.3f, // 1 - 0.12 = 0.88
            0.4f * 2.0f // 0.8
        };

        float[] result = VocalProsodyKernel.computeAcousticModulations(valence, arousal, dominance, sensitivities);
        assertArrayEquals(expected, result, 1e-5f);
    }
    
    @Test
    public void testBatchComputeModulations() {
        float[] sensitivities = {10.0f, 5.0f, 0.2f, 0.1f, 0.5f, 0.3f, 2.0f};
        float[] vadTriplets = {
            0.5f, 0.8f, 0.4f,
            -0.5f, -0.8f, -0.4f
        };
        float[] out = new float[10];
        
        VocalProsodyKernel.batchComputeModulations(vadTriplets, 2, sensitivities, out);
        
        float[] expected1 = {
            10.5f, 1.21f, 1.4f, 0.88f, 0.8f
        };
        float[] expected2 = {
            -0.8f * 10.0f + -0.5f * 5.0f, // -10.5
            1.0f + -0.8f * 0.2f + -0.5f * 0.1f, // 0.79
            1.0f + -0.8f * 0.5f, // 0.6
            1.0f - -0.4f * 0.3f, // 1.12
            -0.4f * 2.0f // -0.8
        };
        
        for (int i = 0; i < 5; i++) {
            org.junit.jupiter.api.Assertions.assertEquals(expected1[i], out[i], 1e-5f);
            org.junit.jupiter.api.Assertions.assertEquals(expected2[i], out[i + 5], 1e-5f);
        }
    }
}
