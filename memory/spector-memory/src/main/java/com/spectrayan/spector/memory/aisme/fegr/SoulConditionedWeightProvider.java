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
package com.spectrayan.spector.memory.aisme.fegr;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.ScoringRegime;
import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.memory.recall.relay.RecallSignal;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Computes dynamic, soul-conditioned Free-Energy Relevance Scoring (FERS) weights (alpha, beta, gamma)
 * with Exponential Moving Average (EMA) hysteresis damping (MR-03).
 *
 * <p>Conditioned on active SoulContext (goals, core values, personality) and query cognitive profile,
 * ensuring seamless transitions without erratic weight oscillation between consecutive queries.</p>
 */
public final class SoulConditionedWeightProvider {

    /**
     * Immutable container for computed FERS weights and active regime.
     */
    public record FersWeights(
            float alpha,
            float beta,
            float gamma,
            ScoringRegime regime
    ) {
        public static final FersWeights DEFAULT_GENERIC = new FersWeights(0.5f, 0.35f, 0.15f, ScoringRegime.GENERIC);
    }

    private static final float DEFAULT_EMA_ALPHA = 0.15f; // smoothing factor (hysteresis)

    private final SoulContext soulContext;
    private final float emaSmoothing;
    private final AtomicReference<FersWeights> smoothedWeights;

    public SoulConditionedWeightProvider(SoulContext soulContext) {
        this(soulContext, DEFAULT_EMA_ALPHA);
    }

    public SoulConditionedWeightProvider(SoulContext soulContext, float emaSmoothing) {
        this.soulContext = soulContext;
        this.emaSmoothing = Math.clamp(emaSmoothing, 0.01f, 1.0f);
        this.smoothedWeights = new AtomicReference<>(
                soulContext != null ? computeTargetWeights(soulContext, null) : FersWeights.DEFAULT_GENERIC
        );
    }

    /**
     * Computes the current effective FERS weights for the given recall signal,
     * applying EMA damping to prevent sharp oscillations across consecutive turns.
     *
     * @param signal recall signal containing query and context
     * @return effective weights and regime
     */
    public FersWeights provideWeights(RecallSignal signal) {
        if (soulContext == null) {
            return FersWeights.DEFAULT_GENERIC;
        }

        CognitiveProfile profile = (signal != null && signal.options() != null)
                ? signal.options().resolvedProfile()
                : null;

        FersWeights target = computeTargetWeights(soulContext, profile);

        // Update smoothed weights via EMA
        return smoothedWeights.updateAndGet(prev -> {
            float smoothedAlpha = (1.0f - emaSmoothing) * prev.alpha() + emaSmoothing * target.alpha();
            float smoothedBeta = (1.0f - emaSmoothing) * prev.beta() + emaSmoothing * target.beta();
            float smoothedGamma = (1.0f - emaSmoothing) * prev.gamma() + emaSmoothing * target.gamma();

            // Re-normalize to guarantee sum == 1.0
            float total = smoothedAlpha + smoothedBeta + smoothedGamma;
            if (total > 0.0f) {
                smoothedAlpha /= total;
                smoothedBeta /= total;
                smoothedGamma /= total;
            }

            return new FersWeights(smoothedAlpha, smoothedBeta, smoothedGamma, ScoringRegime.SOUL_CONDITIONED);
        });
    }

    private static FersWeights computeTargetWeights(SoulContext soul, CognitiveProfile profile) {
        float rawAlpha = 0.50f; // Epistemic (semantic similarity)
        float rawBeta  = 0.35f; // Teleological (free-energy minimization / goals)
        float rawGamma = 0.15f; // Pragmatic / Homeostatic (affective baseline)

        if (soul instanceof AgentSoul agentSoul) {
            // High curiosity / research / explore -> boost alpha (epistemic)
            if (agentSoul.personality() != null) {
                String p = agentSoul.personality().toLowerCase();
                if (p.contains("curious") || p.contains("analytical") || p.contains("research")) {
                    rawAlpha += 0.10f;
                }
                if (p.contains("goal") || p.contains("focused") || p.contains("strategic") || p.contains("decisive")) {
                    rawBeta += 0.10f;
                }
                if (p.contains("cautious") || p.contains("empathetic") || p.contains("supportive")) {
                    rawGamma += 0.10f;
                }
            }

            if (agentSoul.coreValues() != null && !agentSoul.coreValues().isEmpty()) {
                rawBeta += 0.05f * Math.min(3, agentSoul.coreValues().size());
            }
        }

        if (profile != null) {
            switch (profile) {
                case HYPERFOCUS, SYSTEMATIZER, DEBUGGING, CRITICAL -> {
                    rawBeta += 0.15f; // strongly goal/importance directed
                    rawGamma -= 0.05f;
                }
                case DIVERGENT, EXPLORING -> {
                    rawAlpha += 0.15f; // epistemic exploration
                    rawBeta -= 0.10f;
                }
                case RECALLING -> {
                    rawGamma += 0.15f; // affective resonance
                    rawAlpha -= 0.05f;
                }
                default -> {}
            }
        }

        rawAlpha = Math.max(0.1f, rawAlpha);
        rawBeta = Math.max(0.1f, rawBeta);
        rawGamma = Math.max(0.05f, rawGamma);

        float sum = rawAlpha + rawBeta + rawGamma;
        return new FersWeights(rawAlpha / sum, rawBeta / sum, rawGamma / sum, ScoringRegime.SOUL_CONDITIONED);
    }
}
