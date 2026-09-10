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

import com.spectrayan.spector.core.cognitive.CognitiveMassKernel;

/**
 * Pure cognitive mass computation and storage boost math (R17.2).
 *
 * @deprecated Use {@link CognitiveMassKernel} in {@code spector-core} instead.
 */
@Deprecated(since = "0.1.0-beta", forRemoval = true)
public final class CognitiveMass {

    private CognitiveMass() {}

    /**
     * Computes the dynamic Cognitive Mass M_i from L1 cache fields.
     */
    public static float computeCognitiveMass(
            final float importance, final byte arousal, final float storageStrength) {
        return CognitiveMassKernel.computeMass(importance, arousal, storageStrength);
    }

    /**
     * Fast approximation of {@code S^exponent} using precomputed LUT.
     */
    public static float fastStorageBoost(final float storageStrength, final float exponent) {
        return CognitiveMassKernel.fastStorageBoost(storageStrength, exponent);
    }
}
