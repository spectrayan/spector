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
package com.spectrayan.spector.kernel.score;

/**
 * Pure cognitive mass computation and storage boost math (R17.2).
 *
 * <p>Stateless primitive math extracted from CognitiveScoreFusion for pre-SIMD gating.</p>
 */
public final class CognitiveMass {

    private static final int STORAGE_BOOST_LUT_SIZE = 64;
    private static final float STORAGE_BOOST_LUT_MIN = 1.0f;
    private static final float STORAGE_BOOST_LUT_MAX = 5.0f;
    private static final float STORAGE_BOOST_LUT_RANGE = STORAGE_BOOST_LUT_MAX - STORAGE_BOOST_LUT_MIN;
    private static final float[] STORAGE_BOOST_LUT = new float[STORAGE_BOOST_LUT_SIZE];

    static {
        for (int i = 0; i < STORAGE_BOOST_LUT_SIZE; i++) {
            float s = STORAGE_BOOST_LUT_MIN + (i / (float) (STORAGE_BOOST_LUT_SIZE - 1)) * STORAGE_BOOST_LUT_RANGE;
            STORAGE_BOOST_LUT[i] = (float) Math.pow(s, 0.3);
        }
    }

    private CognitiveMass() {}

    /**
     * Computes the dynamic Cognitive Mass M_i from L1 cache fields.
     *
     * <p>M_i = (I_i / 10) * (1 + (A_i mod 256) / 128) * S_i^0.3</p>
     */
    public static float computeCognitiveMass(
            final float importance, final byte arousal, final float storageStrength) {
        final float importanceNorm = importance / 10.0f;
        final float arousalNorm = 1.0f + ((arousal & 0xFF) / 128.0f);
        final float storageBoost = fastStorageBoost(storageStrength, 0.3f);
        return importanceNorm * arousalNorm * storageBoost;
    }

    /**
     * Fast approximation of {@code S^exponent} using precomputed LUT.
     */
    public static float fastStorageBoost(final float storageStrength, final float exponent) {
        if (exponent == 0.3f && storageStrength >= STORAGE_BOOST_LUT_MIN && storageStrength <= STORAGE_BOOST_LUT_MAX) {
            final float normalized = (storageStrength - STORAGE_BOOST_LUT_MIN)
                    * ((STORAGE_BOOST_LUT_SIZE - 1) / STORAGE_BOOST_LUT_RANGE);
            final int idx = (int) normalized;
            if (idx >= STORAGE_BOOST_LUT_SIZE - 1) {
                return STORAGE_BOOST_LUT[STORAGE_BOOST_LUT_SIZE - 1];
            }
            final float frac = normalized - idx;
            return STORAGE_BOOST_LUT[idx] + frac * (STORAGE_BOOST_LUT[idx + 1] - STORAGE_BOOST_LUT[idx]);
        }
        return (float) Math.pow(storageStrength, exponent);
    }
}
