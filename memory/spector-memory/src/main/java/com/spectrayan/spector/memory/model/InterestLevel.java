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
 * Interest intensity level for salience-based importance modulation.
 *
 * <p>Users express interest in natural language ("I care about database performance")
 * and select a level. The system computes cosine similarity between the memory
 * embedding and the interest embedding, then scales by the level multiplier.</p>
 *
 * <h3>Multiplier Effects</h3>
 * <pre>
 *   CRITICAL → 2.0×   "This is my top priority"
 *   HIGH     → 1.5×   "Very interested"
 *   MEDIUM   → 1.25×  "Somewhat interested"
 *   LOW      → 0.5×   "Not really interested"
 *   IGNORE   → 0.1×   "Don't bother me with this"
 * </pre>
 *
 * <p>The effective boost for a memory is:
 * {@code boost = level.multiplier() × cosine(memoryEmbedding, interestEmbedding)}.
 * Only applied when cosine similarity exceeds a threshold (default: 0.5).</p>
 */
public enum InterestLevel {

    /** Top priority — 2× importance boost. */
    CRITICAL(2.0f),

    /** High interest — 1.5× importance boost. */
    HIGH(1.5f),

    /** Moderate interest — 1.25× importance boost. */
    MEDIUM(1.25f),

    /** Low interest — 0.5× importance dampening. */
    LOW(0.5f),

    /** Suppress — 0.1× importance dampening (near-invisible). */
    IGNORE(0.1f);

    private final float multiplier;

    InterestLevel(float multiplier) {
        this.multiplier = multiplier;
    }

    /** Returns the importance multiplier for this level. */
    public float multiplier() {
        return multiplier;
    }

    /** Returns true if this level boosts importance (multiplier > 1.0). */
    public boolean isBoost() {
        return multiplier > 1.0f;
    }

    /** Returns true if this level dampens importance (multiplier < 1.0). */
    public boolean isDampen() {
        return multiplier < 1.0f;
    }
}
