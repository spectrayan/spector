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

/**
 * Pure mathematical kernel for dynamic cognitive mass synthesising raw importance,
 * emotional arousal, and Bjork &amp; Bjork two-factor storage strength (ADR-0033 Domain 3).
 *
 * <p>Purity Tier: T1 (Pure Static, Deterministic).</p>
 */
public final class CognitiveMassKernel {

    private static final int STORAGE_BOOST_LUT_SIZE = 64;
    private static final float STORAGE_BOOST_LUT_MIN = 1.0f;
    private static final float STORAGE_BOOST_LUT_MAX = 5.0f;
    private static final float STORAGE_BOOST_LUT_RANGE = STORAGE_BOOST_LUT_MAX - STORAGE_BOOST_LUT_MIN;
    private static final float[] STORAGE_BOOST_LUT = new float[STORAGE_BOOST_LUT_SIZE];

    static {
        for (int i = 0; i < STORAGE_BOOST_LUT_SIZE; i++) {
            final float s = STORAGE_BOOST_LUT_MIN + (i / (float) (STORAGE_BOOST_LUT_SIZE - 1)) * STORAGE_BOOST_LUT_RANGE;
            STORAGE_BOOST_LUT[i] = (float) Math.pow(s, 0.3);
        }
    }

    private CognitiveMassKernel() {}

    /**
     * Computes the dynamic cognitive mass M_i:
     * <p>{@code M_i = (I_i / 10) * (1 + (A_i mod 256) / 128) * S_i^0.3}</p>
     *
     * @param importance      raw importance score [0.0, 10.0]
     * @param arousal         emotional arousal byte (signed byte interpreted as unsigned [0, 255])
     * @param storageStrength consolidated storage strength S_i
     * @return dynamic cognitive mass
     */
    public static float computeMass(
            final float importance, final byte arousal, final float storageStrength) {
        final float importanceNorm = importance / 10.0f;
        final float arousalNorm = 1.0f + ((arousal & 0xFF) / 128.0f);
        final float storageBoost = fastStorageBoost(storageStrength, 0.3f);
        return importanceNorm * arousalNorm * storageBoost;
    }

    /**
     * Fast approximation of {@code S^exponent} using a precomputed 64-entry LUT with linear interpolation.
     *
     * @param storageStrength storage strength scalar
     * @param exponent        power exponent (0.3f activates the precomputed LUT)
     * @return boosted storage multiplier
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

    /**
     * Batch cognitive mass calculation over candidate arrays for scan acceleration (Principle 3).
     */
    public static void computeMassBatch(
            final float[] importances, final byte[] arousals, final float[] storageStrengths,
            final float[] outMasses, final int count) {
        for (int i = 0; i < count; i++) {
            outMasses[i] = computeMass(importances[i], arousals[i], storageStrengths[i]);
        }
    }
}
