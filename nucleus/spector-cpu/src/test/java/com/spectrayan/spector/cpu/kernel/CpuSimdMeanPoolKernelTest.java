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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CpuSimdMeanPoolKernelTest {

    private final CpuSimdMeanPoolKernel kernel = CpuSimdMeanPoolKernel.INSTANCE;

    @Test
    @DisplayName("Mean-pool and normalize 384-dimensional token tensor")
    void testMeanPool384Dimensions() {
        int seqLen = 4;
        int dim = 384;
        float[][] tokens = new float[seqLen][dim];
        long[] mask = new long[]{1L, 1L, 1L, 0L}; // last token is padding

        for (int i = 0; i < seqLen; i++) {
            for (int d = 0; d < dim; d++) {
                tokens[i][d] = (float) Math.sin(i + d);
            }
        }

        float[] pooled = kernel.poolAndNormalize(tokens, mask);
        assertThat(pooled).hasSize(dim);

        float sumSq = 0.0f;
        for (float v : pooled) {
            sumSq += v * v;
        }
        float norm = (float) Math.sqrt(sumSq);
        assertThat(norm).isBetween(0.9999f, 1.0001f);
    }

    @Test
    @DisplayName("Mean-pool and normalize 768-dimensional token tensor")
    void testMeanPool768Dimensions() {
        int seqLen = 8;
        int dim = 768;
        float[][] tokens = new float[seqLen][dim];
        long[] mask = new long[]{1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L};

        for (int i = 0; i < seqLen; i++) {
            for (int d = 0; d < dim; d++) {
                tokens[i][d] = (float) Math.cos(i * 0.1 + d * 0.01);
            }
        }

        float[] pooled = kernel.poolAndNormalize(tokens, mask);
        assertThat(pooled).hasSize(dim);

        float sumSq = 0.0f;
        for (float v : pooled) {
            sumSq += v * v;
        }
        float norm = (float) Math.sqrt(sumSq);
        assertThat(norm).isBetween(0.9999f, 1.0001f);
    }

    @Test
    @DisplayName("Input validation on empty array")
    void testEmptyArrayThrows() {
        assertThatThrownBy(() -> kernel.poolAndNormalize(new float[0][], new long[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
