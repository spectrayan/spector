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

class CpuSimdMaxSimKernelTest {

    private final CpuSimdMaxSimKernel kernel = CpuSimdMaxSimKernel.INSTANCE;

    @Test
    @DisplayName("maxSim computes correct token-level late interaction score")
    void testMaxSim() {
        float[][] queryTokens = {
                {1.0f, 0.0f, 0.0f},
                {0.0f, 1.0f, 0.0f}
        };
        float[][] docTokens = {
                {1.0f, 0.0f, 0.0f},
                {0.0f, 0.5f, 0.0f},
                {0.0f, 0.0f, 1.0f}
        };

        // Query token 0 max with doc is docToken 0 (dot = 1.0)
        // Query token 1 max with doc is docToken 1 (dot = 0.5)
        // Total score = 1.0 + 0.5 = 1.5
        float score = kernel.maxSim(queryTokens, docTokens);

        assertThat(score).isCloseTo(1.5f, within(1e-5f));
    }
}
