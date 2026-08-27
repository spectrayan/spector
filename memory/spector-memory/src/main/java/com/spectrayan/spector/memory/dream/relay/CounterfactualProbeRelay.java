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
package com.spectrayan.spector.memory.dream.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage 7 relay in {@link com.spectrayan.spector.memory.DreamPathway}.
 *
 * <h3>Biological Analog: Predictive Coding Reality Testing &amp; Expected Free Energy Verification</h3>
 * <p>Validates synthetic counterfactual simulations against stored priors, evaluating
 * @since 1.4.0
 */
public final class CounterfactualProbeRelay implements SynapticRelay<DreamSignal> {

    private static final Logger log = LoggerFactory.getLogger(CounterfactualProbeRelay.class);

    public static final float WEIGHT_EPISTEMIC_SURPRISE = 0.55f;
    public static final float WEIGHT_PRAGMATIC_PLAUSIBILITY = 0.45f;

    @Override
    public boolean transmit(final DreamSignal signal) {
        if (signal == null || signal.constructedScenes().isEmpty()) {
            return true;
        }

        List<DreamSignal.DreamScene> scenes = new ArrayList<>(signal.constructedScenes());
        signal.constructedScenes().clear();

        List<float[]> seedVectors = signal.seedVectors();
        float[] seedCentroid = computeCentroid(seedVectors);

        for (DreamSignal.DreamScene scene : scenes) {
            float[] vec = scene.embedding();

            // 1. Prediction Error / Epistemic Novelty (Surprise)
            float maxSimWithSeed = 0.0f;
            if (seedVectors != null && !seedVectors.isEmpty() && vec != null && vec.length > 0) {
                for (float[] seedVec : seedVectors) {
                    float sim = cosineSimilarity(vec, seedVec);
                    if (sim > maxSimWithSeed) {
                        maxSimWithSeed = sim;
                    }
                }
            }
            float epistemicSurprise = Math.max(0.0f, 1.0f - maxSimWithSeed);

            // 2. Pragmatic Plausibility (Coherence with global seed centroid)
            float pragmaticPlausibility = (seedCentroid != null && vec != null && vec.length > 0)
                    ? Math.max(0.0f, (cosineSimilarity(vec, seedCentroid) + 1.0f) / 2.0f)
                    : 0.5f;

            // 3. Expected Free Energy Quality Score
            float qualityScore = WEIGHT_EPISTEMIC_SURPRISE * epistemicSurprise + WEIGHT_PRAGMATIC_PLAUSIBILITY * pragmaticPlausibility;
            qualityScore = Math.max(0.0f, Math.min(1.0f, qualityScore));

            DreamSignal.DreamScene evaluated = new DreamSignal.DreamScene(
                    scene.id(),
                    scene.narrative(),
                    scene.insightText(),
                    scene.embedding(),
                    scene.sourceIds(),
                    qualityScore,
                    scene.triageOutcome()
            );

            signal.addConstructedScene(evaluated);
        }

        if (log.isDebugEnabled()) {
            log.debug("CounterfactualProbeRelay: evaluated {} constructed scenes through predictive coding verification",
                    signal.constructedScenes().size());
        }

        return true;
    }

    private static float[] computeCentroid(List<float[]> vectors) {
        if (vectors == null || vectors.isEmpty()) return null;
        int dim = vectors.get(0).length;
        if (dim == 0) return null;

        float[] centroid = new float[dim];
        for (float[] v : vectors) {
            if (v == null || v.length != dim) continue;
            for (int d = 0; d < dim; d++) {
                centroid[d] += v[d];
            }
        }
        for (int d = 0; d < dim; d++) {
            centroid[d] /= vectors.size();
        }
        return centroid;
    }

    private static float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) return 0.0f;
        int minLen = Math.min(a.length, b.length);
        float dot = 0.0f, normA = 0.0f, normB = 0.0f;
        for (int i = 0; i < minLen; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA <= 0.0f || normB <= 0.0f) return 0.0f;
        return (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    @Override
    public String relayName() {
        return "counterfactual_probe";
    }
}
