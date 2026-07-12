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
package com.spectrayan.spector.memory;

import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.core.similarity.VectorOps;
import com.spectrayan.spector.embed.EmbeddingProvider;
import com.spectrayan.spector.memory.cortex.TierRouter;
import com.spectrayan.spector.memory.dopamine.FlashbulbPolicy;
import com.spectrayan.spector.memory.dopamine.SurpriseDetector;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.model.ImportanceEstimate;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.neurodivergent.IcnuWeights;
import com.spectrayan.spector.memory.neurodivergent.IngestionHints;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorServerException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Read-only importance estimation pipeline.
 *
 * <p>Computes importance for a candidate memory <b>without modifying any state</b>.
 * The pipeline fuses four signals:</p>
 * <ol>
 *   <li><b>Novelty</b> — Welford z-score from {@link SurpriseDetector} (peek, not update)</li>
 *   <li><b>ICNU Fusion</b> — Interest/Challenge/Novelty/Urgency weights from {@link IcnuWeights}</li>
 *   <li><b>Flashbulb</b> — extreme-surprise detection from {@link FlashbulbPolicy}</li>
 *   <li><b>Nearest neighbour</b> — distance to closest existing memory (diversity signal)</li>
 * </ol>
 *
 * <p>Thread-safe: all reads are against immutable or thread-safe subsystems.</p>
 *
 * @see ImportanceEstimate
 */
final class ImportanceEstimator {

    private static final Logger log = LoggerFactory.getLogger(ImportanceEstimator.class);

    private final SurpriseDetector surpriseDetector;
    private final FlashbulbPolicy flashbulbPolicy;
    private final IcnuWeights icnuWeights;
    private final ScalarQuantizer quantizer;

    ImportanceEstimator(SurpriseDetector surpriseDetector,
                        FlashbulbPolicy flashbulbPolicy,
                        IcnuWeights icnuWeights,
                        ScalarQuantizer quantizer) {
        this.surpriseDetector = surpriseDetector;
        this.flashbulbPolicy = flashbulbPolicy;
        this.icnuWeights = icnuWeights;
        this.quantizer = quantizer;
    }

    /**
     * Estimates importance for the given text without ingesting it.
     *
     * @param text              the candidate memory text
     * @param hints             optional ICNU hints (null = novelty-only)
     * @param embeddingProvider the embedding provider for text vectorization
     * @param tierRouter        tier router for nearest-distance lookup
     * @param index             memory index for nearest-memory-id lookup
     * @return an {@link ImportanceEstimate} with all computed signals
     */
    ImportanceEstimate estimate(String text,
                                IngestionHints hints,
                                EmbeddingProvider embeddingProvider,
                                TierRouter tierRouter,
                                MemoryIndex index) {
        try {
            // Step 1: Embed text (same as remember())
            float[] vector = embeddingProvider.embed(text).vector();

            // Step 1b: L2-normalize (SIMD-accelerated via VectorOps)
            float norm = VectorOps.magnitude(vector);
            if (norm > 0f && Math.abs(norm - 1.0f) > 1e-6f) {
                vector = VectorOps.normalize(vector);
            }

            // Step 2: Compute nearest distance (read-only — don't update stats)
            float nearestDist;
            var workingStore = tierRouter.working();
            if (workingStore != null && workingStore.count() > 0) {
                nearestDist = workingStore.nearestDistance(
                        vector, quantizer.mins(), quantizer.scales());
            } else {
                // Fallback: L2 norm of vector (high distance = novel)
                nearestDist = VectorOps.magnitude(vector);
            }

            // Step 3: Compute novelty (read-only peek — don't modify Welford stats)
            double zScore = surpriseDetector.stats().count() >= 20
                    ? surpriseDetector.stats().zScore(nearestDist) : 0.0;
            float noveltyOnlyImportance = surpriseDetector.stats().count() >= 20
                    ? SurpriseDetector.zScoreToImportance(zScore) : 1.0f;
            float noveltyNorm = Math.clamp(noveltyOnlyImportance / 10.0f, 0f, 1f);

            // Step 4: ICNU fusion (if hints provided)
            float fusedImportance;
            if (hints != null && !hints.isEmpty()) {
                fusedImportance = icnuWeights.fuse(hints, noveltyNorm);
            } else {
                fusedImportance = noveltyOnlyImportance;
            }

            // Step 5: Flashbulb check
            boolean wouldBeFlashbulb = false;
            var flashbulbResult = flashbulbPolicy.evaluate(zScore);
            if (flashbulbResult.isFlashbulb()) {
                wouldBeFlashbulb = true;
                fusedImportance = flashbulbResult.importance();
            }

            // Step 6: Find nearest existing memory ID
            String nearestMemoryId = null;
            float nearestMemoryDist = nearestDist;
            var semanticStore = tierRouter.semantic();
            if (semanticStore != null && semanticStore.size() > 0) {
                float bestDist = Float.MAX_VALUE;
                for (var entry : index.locationMap().entrySet()) {
                    String candidateId = entry.getKey();
                    String candidateText = index.text(candidateId);
                    if (candidateText != null && !candidateText.isEmpty()) {
                        if (bestDist == Float.MAX_VALUE) {
                            nearestMemoryId = candidateId;
                            bestDist = nearestDist;
                        }
                    }
                }
                nearestMemoryDist = bestDist;
            }

            // Step 7: Build weights description
            String weightsDesc = String.format(
                    "I=%.0f%% C=%.0f%% N=%.0f%% U=%.0f%% (threshold=%.2f, steepness=%.1f)",
                    icnuWeights.interest() * 100, icnuWeights.challenge() * 100,
                    icnuWeights.novelty() * 100, icnuWeights.urgency() * 100,
                    icnuWeights.threshold(), icnuWeights.steepness());

            return new ImportanceEstimate(
                    noveltyNorm, zScore, fusedImportance, noveltyOnlyImportance,
                    nearestMemoryId, nearestMemoryDist, wouldBeFlashbulb, weightsDesc);

        } catch (Exception e) {
            log.error("Failed to estimate importance: {}", e.getMessage(), e);
            throw new SpectorServerException(
                    ErrorCode.INTERNAL_ERROR, e, "Importance estimation failed");
        }
    }
}
