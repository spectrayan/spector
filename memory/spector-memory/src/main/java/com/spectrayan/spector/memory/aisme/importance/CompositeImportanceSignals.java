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
package com.spectrayan.spector.memory.aisme.importance;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

/**
 * 5-dimensional normalized neurocognitive signal vector \(\boldsymbol{s}(o_t) \in [0.0, 1.0]^5\).
 *
 * <h3>Biological Analog: Multi-Limbic Salience Signals</h3>
 * <ul>
 *   <li><b>Surprise \(s_1\)</b>: Hippocampal & predictive coding prediction error.</li>
 *   <li><b>Affect \(s_2\)</b>: Amygdala arousal-weighted emotional valence.</li>
 *   <li><b>Goal Relevance \(s_3\)</b>: Prefrontal intentional congruence.</li>
 *   <li><b>Social Context \(s_4\)</b>: Anterior cingulate / insular interlocutor significance.</li>
 *   <li><b>Novelty \(s_5\)</b>: Manifold representational distance.</li>
 * </ul>
 *
 * @param surprise       prediction error surprisal in [0.0, 1.0]
 * @param affect         arousal-weighted affective valence in [0.0, 1.0]
 * @param goalRelevance  prospective goal alignment in [0.0, 1.0]
 * @param socialContext  social/interpersonal significance in [0.0, 1.0]
 * @param novelty        representational manifold novelty in [0.0, 1.0]
 */
public record CompositeImportanceSignals(
        float surprise,
        float affect,
        float goalRelevance,
        float socialContext,
        float novelty
) {

    /**
     * Converts the component signals into a 5-element float array for SIMD execution.
     *
     * @return float array of length 5
     */
    public float[] toArray() {
        return new float[]{
                Math.clamp(surprise, 0.0f, 1.0f),
                Math.clamp(affect, 0.0f, 1.0f),
                Math.clamp(goalRelevance, 0.0f, 1.0f),
                Math.clamp(socialContext, 0.0f, 1.0f),
                Math.clamp(novelty, 0.0f, 1.0f)
        };
    }

    /**
     * Baseline neutral signal vector.
     */
    public static CompositeImportanceSignals neutral() {
        return new CompositeImportanceSignals(0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
    }
}
