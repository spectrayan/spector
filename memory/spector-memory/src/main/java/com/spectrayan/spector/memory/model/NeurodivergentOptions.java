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
package com.spectrayan.spector.memory.model;

/**
 * Neurodivergent cognitive profile parameters for recall queries.
 *
 * <p>Controls hyperfocus mode (strict tag-gated attention narrowing) and
 * lateral retrieval mode (cross-domain divergent thinking).</p>
 *
 * @param hyperfocusMask            Bloom filter mask for hyperfocus gating (0L = disabled)
 * @param hyperfocusBoost           post-score multiplier for hyperfocus-matched memories
 * @param lateralMode               enable orthogonal/lateral retrieval
 * @param lateralDistanceThreshold  minimum L2 distance for lateral candidates
 * @param lateralMaxResults         maximum lateral candidates (-1 = topK/3)
 * @param lateralMinTagOverlap      minimum tag overlap ratio for lateral candidates
 */
public record NeurodivergentOptions(
        long hyperfocusMask,
        float hyperfocusBoost,
        boolean lateralMode,
        float lateralDistanceThreshold,
        int lateralMaxResults,
        float lateralMinTagOverlap
) {
    /** Default: no neurodivergent modulation. */
    public static final NeurodivergentOptions DEFAULT = new NeurodivergentOptions(
            0L, 1.0f, false, 1.2f, -1, 0.5f);

    /** Returns true if hyperfocus is active. */
    public boolean hasHyperfocus() {
        return hyperfocusMask != 0L;
    }
}
