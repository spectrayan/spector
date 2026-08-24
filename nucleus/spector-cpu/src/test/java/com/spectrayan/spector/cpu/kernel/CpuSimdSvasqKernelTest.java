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
package com.spectrayan.spector.cpu.kernel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CpuSimdSvasqKernelTest {

    private final CpuSimdSvasqKernel kernel = CpuSimdSvasqKernel.INSTANCE;

    @Test
    @DisplayName("computeDistances computes scaled dot products against quantized codes")
    void testComputeDistances() {
        float[] rotatedQuery = {2.0f, 3.0f, -1.0f, 0.5f};
        byte[] quantizedVectors = {
                1, 2, 3, 4,     // dot = 2*1 + 3*2 + (-1)*3 + 0.5*4 = 2 + 6 - 3 + 2 = 7.0
                0, 1, 0, 2      // dot = 0 + 3 + 0 + 1 = 4.0
        };
        float[] scales = {2.0f, 0.5f};
        float[] outDistances = new float[2];

        kernel.computeDistances(rotatedQuery, quantizedVectors, scales, 2, 4, outDistances);

        assertThat(outDistances[0]).isCloseTo(14.0f, within(1e-5f));
        assertThat(outDistances[1]).isCloseTo(2.0f, within(1e-5f));
    }
}
