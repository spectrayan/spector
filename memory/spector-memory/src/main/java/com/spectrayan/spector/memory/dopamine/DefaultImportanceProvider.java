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
package com.spectrayan.spector.memory.dopamine;

import com.spectrayan.spector.memory.ImportanceProvider;
import com.spectrayan.spector.memory.model.ImportanceBreakdown;
import com.spectrayan.spector.memory.model.ImportanceContext;
import com.spectrayan.spector.memory.model.ImportanceResult;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.neurodivergent.IcnuWeights;
import com.spectrayan.spector.memory.neurodivergent.IngestionHints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link ImportanceProvider} that evaluates memory items
 * for novelty, ICNU salience fusion, and dynamic boosts based on a persona's profile.
 */
public final class DefaultImportanceProvider implements ImportanceProvider {

    private static final Logger log = LoggerFactory.getLogger(DefaultImportanceProvider.class);

    private final SurpriseDetector surpriseDetector;
    private final FlashbulbPolicy flashbulbPolicy;
    private final IcnuWeights defaultIcnuWeights;

    /**
     * Constructs a new DefaultImportanceProvider.
     *
     * @param surpriseDetector   the detector for novelty and surprise
     * @param flashbulbPolicy    the policy determining flashbulb memory triggers
     * @param defaultIcnuWeights the default ICNU weights to fall back on
     */
    public DefaultImportanceProvider(SurpriseDetector surpriseDetector,
                                     FlashbulbPolicy flashbulbPolicy,
                                     IcnuWeights defaultIcnuWeights) {
        this.surpriseDetector = surpriseDetector;
        this.flashbulbPolicy = flashbulbPolicy;
        this.defaultIcnuWeights = defaultIcnuWeights;
    }

    @Override
    public ImportanceResult score(ImportanceContext ctx) {
        double zScore;
        float noveltyOnlyImportance;
        
        float nearestDistance = ctx.nearestDistance();

        // Step 3: Compute novelty
        if (ctx.readOnly()) {
            // Read-only peek — don't modify Welford stats
            zScore = surpriseDetector.stats().count() >= 20
                    ? surpriseDetector.stats().zScore(nearestDistance) : 0.0;
            noveltyOnlyImportance = surpriseDetector.stats().count() >= 20
                    ? SurpriseDetector.zScoreToImportance(zScore) : 1.0f;
        } else {
            // Compute and update stats
            noveltyOnlyImportance = surpriseDetector.computeImportance(nearestDistance);
            zScore = surpriseDetector.stats().count() >= 20
                    ? surpriseDetector.stats().zScore(nearestDistance) : 0.0;
        }

        float noveltyNorm = Math.clamp(noveltyOnlyImportance / 10.0f, 0f, 1f);

        // Step 4: ICNU fusion
        float fusedImportance;
        IngestionHints hints = ctx.hints();
        SalienceProfile salienceProfile = ctx.salienceProfile();

        IcnuWeights effectiveIcnuWeights = (salienceProfile != null && salienceProfile.hasIcnuOverride())
                ? salienceProfile.icnuWeights()
                : defaultIcnuWeights;

        if (hints != null && !hints.isEmpty()) {
            // Gaming detection logging (matches original CognitiveIngestionTarget)
            if (hints.interest() == 1.0f && hints.challenge() == 1.0f
                    && hints.urgency() == 1.0f) {
                log.warn("ICNU anomaly: all-max hints (I=1.0, C=1.0, U=1.0) — possible gaming");
            }
            fusedImportance = effectiveIcnuWeights.fuse(hints, noveltyNorm);
        } else {
            fusedImportance = noveltyOnlyImportance;
        }

        // Steps 3c - 3e: Apply salience boosts
        float topicBoost = 1.0f;
        float selfBoost = 1.0f;
        float agentBoost = 1.0f;

        if (salienceProfile != null) {
            float[] vector = ctx.vector();
            
            // Step 3c: Salience-based topic boost
            if (vector != null && salienceProfile.hasInterests()) {
                topicBoost = salienceProfile.computeTopicBoost(vector);
                if (topicBoost != 1.0f) {
                    fusedImportance = Math.clamp(fusedImportance * topicBoost, 0.05f, 10.0f);
                }
            }

            // Step 3d: Persona self-relevance boost
            if (vector != null && salienceProfile.hasPersona()) {
                selfBoost = salienceProfile.computeSelfRelevanceBoost(vector);
                if (selfBoost != 1.0f) {
                    fusedImportance = Math.clamp(fusedImportance * selfBoost, 0.05f, 10.0f);
                }
            }

            // Step 3e: Agent expertise relevance boost
            if (salienceProfile.hasAgentRelevanceBoost()) {
                agentBoost = salienceProfile.agentRelevanceBoost();
                fusedImportance = Math.clamp(fusedImportance * agentBoost, 0.05f, 10.0f);
            }
        }

        // Step 5: Flashbulb check
        boolean wouldBeFlashbulb = false;
        var flashbulbResult = flashbulbPolicy.evaluate(zScore);
        if (flashbulbResult.isFlashbulb()) {
            wouldBeFlashbulb = true;
            fusedImportance = flashbulbResult.importance();
        }

        // Build ICNU weights description
        String weightsDesc = String.format(
                "I=%.0f%% C=%.0f%% N=%.0f%% U=%.0f%% (threshold=%.2f, steepness=%.1f)",
                effectiveIcnuWeights.interest() * 100, effectiveIcnuWeights.challenge() * 100,
                effectiveIcnuWeights.novelty() * 100, effectiveIcnuWeights.urgency() * 100,
                effectiveIcnuWeights.threshold(), effectiveIcnuWeights.steepness());

        // Finalize breakdown and result
        ImportanceBreakdown breakdown = new ImportanceBreakdown(
                noveltyNorm, zScore, noveltyOnlyImportance, fusedImportance,
                topicBoost, selfBoost, agentBoost, weightsDesc,
                null, nearestDistance);

        return new ImportanceResult(fusedImportance, wouldBeFlashbulb, breakdown);
    }
}

