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

import com.spectrayan.spector.memory.model.ScoreFusionMode;
import com.spectrayan.spector.memory.synapse.AssociativePriorProvider;
import com.spectrayan.spector.memory.synapse.DecayStrategy;
import com.spectrayan.spector.memory.synapse.QueryAssociativeContext;

import static com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants.memoryTypeOrdinal;

/**
 * Fused SIMD cognitive score composition, dynamic mass calculation, and mass-dilated log recency (Phase 6).
 */
public final class CognitiveScoreFusion {

    private static final double MS_PER_DAY = 86_400_000.0;

    private CognitiveScoreFusion() {
        // utility class
    }

    /**
     * Computes the dynamic Cognitive Mass M_i from L1 cache fields.
     *
     * <p>M_i = (I_i / 10) * (1 + (A_i mod 256) / 128) * S_i^0.3</p>
     *
     * @param importance      raw importance score [0.0..10.0]
     * @param arousal         unsigned 8-bit arousal [0..255]
     * @param storageStrength consolidated storage strength [1.0..5.0]
     * @return dynamic cognitive mass
     */
    public static float computeCognitiveMass(
            final float importance, final byte arousal, final float storageStrength) {
        final float importanceNorm = importance / 10.0f;
        final float arousalNorm = 1.0f + ((arousal & 0xFF) / 128.0f);
        final float storageBoost = StorageBoostLut.fastStorageBoost(storageStrength, 0.3f);
        return importanceNorm * arousalNorm * storageBoost;
    }

    /**
     * Computes the continuous mass-dilated recency decay factor (default λ = 1.0).
     *
     * <p>Formula: R(Δt, M_i) = 1 / (1 + ln(1 + Δt_days) / (1 + M_i)) * arousalModifier</p>
     *
     * @param timestampMs       creation timestamp in epoch millis
     * @param nowMs             reference query clock in epoch millis
     * @param cognitiveMass     dynamic cognitive mass M_i
     * @param arousal           arousal intensity byte
     * @param agentRecallCount  number of prior agent recalls for reconsolidation
     * @param zeroTimeDecay     true if time decay is suspended (focus match, unpinned/unresolved)
     * @return decay multiplier in (0.0..1.0]
     */
    public static float computeMassDilatedDecay(
            final long timestampMs, final long nowMs, final float cognitiveMass,
            final byte arousal, final int agentRecallCount, final boolean zeroTimeDecay) {
        return computeMassDilatedDecay(
                timestampMs, nowMs, cognitiveMass, arousal, agentRecallCount, zeroTimeDecay, 1.0f);
    }

    /**
     * Computes the continuous mass-dilated recency decay factor with continuous lambda recency scaling (ADR-0031).
     *
     * <p>Formula: R_λ(Δt, M_i) = 1 / (1 + λ * ln(1 + Δt_days) / (1 + M_i)) * arousalModifier * reconsolidationBoost</p>
     *
     * @param timestampMs       creation timestamp in epoch millis
     * @param nowMs             reference query clock in epoch millis
     * @param cognitiveMass     dynamic cognitive mass M_i
     * @param arousal           arousal intensity byte
     * @param agentRecallCount  number of prior agent recalls for reconsolidation
     * @param zeroTimeDecay     true if time decay is suspended (focus match, unpinned/unresolved)
     * @param lambda            continuous recency scaling factor (1.0 for recall, 0.3 for wander/dream, 0.0 for timeless)
     * @return decay multiplier in (0.0..1.0]
     */
    public static float computeMassDilatedDecay(
            final long timestampMs, final long nowMs, final float cognitiveMass,
            final byte arousal, final int agentRecallCount, final boolean zeroTimeDecay,
            final float lambda) {

        if (zeroTimeDecay || lambda <= 0.0f) {
            final float reconsolidationBoost = 1.0f + 0.05f * Math.min(agentRecallCount, 10);
            return Math.min(1.0f, 1.0f * DecayStrategy.arousalModifier(arousal) * reconsolidationBoost);
        }

        final double elapsedDays = Math.max(0.0, (nowMs - timestampMs) / MS_PER_DAY);
        final float logTerm = (float) Math.log1p(elapsedDays);
        final float massDenominator = 1.0f + Math.max(0.0f, cognitiveMass);

        final float dilatedDecay = 1.0f / (1.0f + ((lambda * logTerm) / massDenominator));
        final float reconsolidationBoost = 1.0f + 0.05f * Math.min(agentRecallCount, 10);
        final float finalDecay = dilatedDecay * DecayStrategy.arousalModifier(arousal) * reconsolidationBoost;

        return Math.min(1.0f, Math.max(0.0f, finalDecay));
    }

    /**
     * Computes the final fused cognitive score for a candidate memory (Phase 6).
     */
    public static float computeFusedScore(
            final float l2dist, final float strictness, final boolean pureSimilarity,
            final long timestampMs, final long nowMs, final float cognitiveMass,
            final byte arousal, final float storageStrength, final boolean hasStorageStrength,
            final boolean twoFactorEnabled, final float sExponent, final int agentRecallCount,
            final float importance, final float beta, final float alpha, final float tagOverlap,
            final ScoreFusionMode fusionMode, final boolean valenceAlign, final byte queryValence,
            final byte valence, final float tagRelevanceBoost, final boolean focusMatch,
            final boolean zeroTimeDecay, final float hyperfocusBoost, final byte flags,
            final boolean enableAssociativePrior, final AssociativePriorProvider priorProvider,
            final long offset, final long recordTags, final QueryAssociativeContext priorContext,
            final float associativePriorDelta) {

        final float similarity = 1.0f / (1.0f + l2dist * strictness);
        if (pureSimilarity) {
            return similarity;
        }

        final float decay = computeMassDilatedDecay(
                timestampMs, nowMs, cognitiveMass, arousal, agentRecallCount, zeroTimeDecay);

        float storageBoost = 1.0f;
        if (hasStorageStrength && twoFactorEnabled && storageStrength > 1.0f) {
            storageBoost = StorageBoostLut.fastStorageBoost(storageStrength, sExponent);
        }

        final float importanceNorm = importance / 10.0f;
        final float impDecayFactor = 1.0f + beta * importanceNorm * decay * storageBoost;

        float baseScore;
        if (fusionMode == ScoreFusionMode.ADDITIVE) {
            final float baseSimilarity = alpha * similarity + (1.0f - alpha) * tagOverlap;
            baseScore = baseSimilarity * impDecayFactor;
        } else {
            baseScore = similarity * impDecayFactor;
        }

        if (valenceAlign) {
            final float valenceMultiplier = 1.0f - (Math.abs(queryValence - valence) / 255.0f);
            baseScore *= valenceMultiplier;
        }

        float finalScore;
        if (fusionMode == ScoreFusionMode.ADDITIVE) {
            finalScore = baseScore;
        } else {
            finalScore = baseScore * (1.0f + tagOverlap * tagRelevanceBoost);
        }

        if (focusMatch && hyperfocusBoost != 1.0f) {
            finalScore *= hyperfocusBoost;
        }

        if (enableAssociativePrior && priorProvider != null) {
            final float ag = priorProvider.priorFor(offset, recordTags, priorContext);
            if (fusionMode == ScoreFusionMode.ADDITIVE) {
                finalScore += associativePriorDelta * ag;
            } else {
                finalScore *= (1.0f + associativePriorDelta * ag);
            }
        }

        return finalScore;
    }
}
