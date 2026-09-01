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
 * Fused SIMD cognitive score composition, mass calculation, and neuromodulatory weighting (Phase 6).
 */
public final class CognitiveScoreFusion {

    private CognitiveScoreFusion() {
        // utility class
    }

    /**
     * Computes the dynamic Cognitive Mass M_i from L1 cache fields.
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
     * Computes the final fused cognitive score for a candidate memory.
     */
    public static float computeFusedScore(
            final float l2dist, final float strictness, final boolean pureSimilarity,
            final int adjustedBucket, final byte arousal, final float storageStrength,
            final boolean hasStorageStrength, final boolean twoFactorEnabled, final float sExponent,
            final float importance, final float beta, final float alpha, final float tagOverlap,
            final ScoreFusionMode fusionMode, final boolean valenceAlign, final byte queryValence,
            final byte valence, final float tagRelevanceBoost, final boolean focusMatch,
            final float hyperfocusBoost, final byte flags, final boolean enableAssociativePrior,
            final AssociativePriorProvider priorProvider, final long offset, final long recordTags,
            final QueryAssociativeContext priorContext, final float associativePriorDelta) {

        final float similarity = 1.0f / (1.0f + l2dist * strictness);
        if (pureSimilarity) {
            return similarity;
        }

        float decay = DecayStrategy.decay(adjustedBucket) * DecayStrategy.arousalModifier(arousal);
        decay = Math.min(1.0f, decay);

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

        final int mType = memoryTypeOrdinal(flags);
        if (mType == 1 || mType == 2) { // 1 = SEMANTIC, 2 = PROCEDURAL
            finalScore *= 2.0f;
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
