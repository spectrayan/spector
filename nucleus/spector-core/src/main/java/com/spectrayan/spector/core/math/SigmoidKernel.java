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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

/**
 * Pure mathematical kernel for logistic sigmoid activation functions.
 *
 * <p>Purity Tier: T1 (Pure Static, Deterministic). Single source of truth for the logistic
 * sigmoid, consolidating the four inline copies catalogued in ADR-0033 §1.1 cluster D4.</p>
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
     * @throws SpectorValidationException if either slice is out of bounds
     */
    public static void sigmoidBatch(float[] src, int srcOffset, float[] dst, int dstOffset, int length) {
        if (src == null || dst == null || length <= 0) {
            return;
        }
        validateSlice(src, srcOffset, length, "src");
        validateSlice(dst, dstOffset, length, "dst");
        for (int i = 0; i < length; i++) {
            dst[dstOffset + i] = sigmoid(src[srcOffset + i]);
        }
    }

    /**
     * Convenience batch form over whole arrays.
     *
     * @param src input scalar array
     * @param dst output array (must be at least as long as {@code src})
     * @throws SpectorValidationException if {@code dst} is shorter than {@code src}
     */
    public static void sigmoidBatch(float[] src, float[] dst) {
        if (src == null || dst == null) {
            return;
        }
        sigmoidBatch(src, 0, dst, 0, src.length);
    }

    private static void validateSlice(final float[] array, final int offset, final int length, final String name) {
        if (offset < 0 || length < 0 || offset + length > array.length) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID,
                    "Slice out of bounds for '" + name + "': offset=" + offset
                            + ", length=" + length + ", arrayLength=" + array.length);
        }
    }
}

