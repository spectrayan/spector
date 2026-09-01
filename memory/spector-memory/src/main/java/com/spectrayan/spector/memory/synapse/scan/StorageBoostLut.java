/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.synapse.scan;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

/**
 * Precomputed lookup table for {@code storageStrength^0.3} in the hot scoring scan.
 *
 * <p>Replaces {@code Math.pow(storageStrength, 0.3)} in the hot loop (~150 cycles → ~3 cycles).
 * Maps S ∈ [1.0, 5.0] to S^0.3 via 64-entry linear interpolation with < 0.2% error.</p>
 */
public final class StorageBoostLut {

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

    private StorageBoostLut() {
        // utility class
    }

    /**
     * Fast approximation of {@code S^exponent} using precomputed LUT.
     * Falls back to {@link Math#pow} for exponents other than 0.3 or storage strengths outside [1.0, 5.0].
     *
     * @param storageStrength consolidated storage strength in [1.0, 5.0]
     * @param exponent        power exponent (default 0.3)
     * @return storage boost multiplier
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
