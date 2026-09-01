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
package com.spectrayan.spector.memory.aisme.hopfield;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.memory.aisme.homeostasis.InteroceptiveState;
import com.spectrayan.spector.memory.model.CognitiveProfile;

/**
 * Maps cognitive profile traits and real-time interoceptive arousal to the Modern Hopfield
 * inverse temperature parameter beta.
 *
 * <h3>Biological Analog: Noradrenergic Neuromodulation of Attractor Precision</h3>
 * <p>High norepinephrine and focused attentional profiles tighten the attractor basins
 * (high beta, sharp retrieval), whereas divergent or relaxed states broaden attractor basins
 * (low beta, associative blending across memory traces).</p>
 */
public final class PersonalityTemperature {

    private static final float DEFAULT_BETA = 4.0f;

    private PersonalityTemperature() {
        // utility class
    }

    /**
     * Derives the adaptive inverse temperature beta from the agent cognitive profile and current affective state.
     *
     * @param profile the agent's cognitive profile (nullable)
     * @param state the current interoceptive state (nullable)
     * @return positive inverse temperature beta > 0.0f
     */
    public static float deriveBeta(CognitiveProfile profile, InteroceptiveState state) {
        float arousal = (state != null) ? state.arousal() : 0.0f;
        return deriveBeta(profile, arousal);
    }

    /**
     * Derives the adaptive inverse temperature beta from the agent cognitive profile and scalar arousal.
     *
     * @param profile the cognitive profile (nullable)
     * @param arousal normalized arousal in [-1, 1]
     * @return positive inverse temperature beta > 0.0f
     */
    public static float deriveBeta(CognitiveProfile profile, float arousal) {
        float baseBeta = DEFAULT_BETA;
        if (profile != null) {
            switch (profile) {
                case HYPERFOCUS -> baseBeta = 12.0f;
                case SYSTEMATIZER -> baseBeta = 8.0f;
                case BALANCED -> baseBeta = 4.0f;
                case DEFAULT_MODE_NETWORK -> baseBeta = 3.0f;
                case EXPLORING -> baseBeta = 2.0f;
                case DIVERGENT -> baseBeta = 1.0f;
                case EXECUTIVE_DYSFUNCTION -> baseBeta = 1.2f;
                case PARANOID_SENTINEL -> baseBeta = 15.0f;
                default -> baseBeta = DEFAULT_BETA;
            }
        }

        // Modulate with arousal: high arousal sharpens focus, low arousal broadens associations
        float clampedArousal = Math.max(-1.0f, Math.min(1.0f, arousal));
        float arousalMultiplier = 1.0f + (0.5f * clampedArousal);
        return Math.max(0.2f, baseBeta * arousalMultiplier);
    }
}
