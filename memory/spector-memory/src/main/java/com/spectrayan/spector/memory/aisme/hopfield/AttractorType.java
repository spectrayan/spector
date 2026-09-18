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
package com.spectrayan.spector.memory.aisme.hopfield;

/**
 * Classification of associative memory attractor basins discovered by the Modern Hopfield Network.
 *
 * <h3>Biological Analog: Hippocampal Pattern Completion & Gestalt Superposition</h3>
 * <p>Identifies whether an associative recall event resolves into a distinct singular episode
 * (fixed point), a rich blended intuition of related memories (metastable state), or a broad
 * diffuse activation of contextual associations.</p>
 */
public enum AttractorType {

    /**
     * A single memory pattern strongly dominates the energy basin (attention weight >= 0.70).
     * Corresponds to vivid, unambiguous episodic recollection.
     */
    FIXED_POINT,

    /**
     * Multiple complementary memories co-exist in an associative superposition (0.30 <= max weight < 0.70).
     * Corresponds to intuitive gestalt synthesis or multifaceted perspective.
     */
    METASTABLE,

    /**
     * Broad, low-contrast distribution of attention across memories (max weight < 0.30).
     * Corresponds to open contextual priming or exploratory cognitive state.
     */
    DIFFUSE;

    /**
     * Classifies an attention weight distribution into an attractor type.
     *
     * @param weights normalized attention weights
     * @return classified AttractorType
     */
    public static AttractorType classify(float[] weights) {
        if (weights == null || weights.length == 0) {
            return DIFFUSE;
        }
        float maxW = 0.0f;
        for (float w : weights) {
            if (w > maxW) {
                maxW = w;
            }
        }
        if (maxW >= 0.70f) {
            return FIXED_POINT;
        } else if (maxW >= 0.30f) {
            return METASTABLE;
        } else {
            return DIFFUSE;
        }
    }
}
