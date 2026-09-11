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
package com.spectrayan.spector.core.math;

/**
 * Pure mathematical kernel for logistic sigmoid activation functions.
 *
 * <p>Purity Tier: T1 (Pure Static, Deterministic).</p>
 */
public final class SigmoidKernel {

    private SigmoidKernel() {}

    /**
     * Standard logistic sigmoid activation function: {@code 1.0f / (1.0f + exp(-x))}.
     *
     * @param x input scalar
     * @return activation value in open range (0.0, 1.0)
     */
    public static float sigmoid(float x) {
        return (float) (1.0 / (1.0 + Math.exp(-x)));
    }

    /**
     * Batch logistic sigmoid computation across an array of values.
     *
     * @param src       input scalar array
     * @param srcOffset offset in input array
     * @param dst       output array
     * @param dstOffset offset in output array
     * @param length    number of elements to process
     */
    public static void sigmoidBatch(float[] src, int srcOffset, float[] dst, int dstOffset, int length) {
        if (src == null || dst == null || length <= 0) {
            return;
        }
        for (int i = 0; i < length; i++) {
            dst[dstOffset + i] = sigmoid(src[srcOffset + i]);
        }
    }
}
