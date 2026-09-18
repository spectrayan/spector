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
package com.spectrayan.spector.memory.model;

/**
 * Strategy for fusing vector similarity and semantic (tag) similarity in cognitive scoring (MR-02).
 */
public enum ScoreFusionMode {
    /**
     * Multiplicative fusion (default): similarity is the primary signal; importance, decay, and tag overlap
     * act as multiplicative re-ranking factors:
     * {@code S_final = S_vector * (1 + beta * I_norm * decay * S_boost) * (1 + tagOverlap * tagBoost) * ...}
     */
    MULTIPLICATIVE,

    /**
     * Additive convex combination: vector similarity and semantic tag similarity are combined via
     * weight parameter {@code alpha} in [0.0, 1.0]:
     * {@code S_base = (alpha * S_vector + (1.0 - alpha) * tagOverlap) * (1 + beta * I_norm * decay * S_boost)}
     * {@code S_final = S_base * ...}
     */
    ADDITIVE
}
