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
package com.spectrayan.spector.memory.pathway.dream.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.core.similarity.VectorOps;
import com.spectrayan.spector.core.spi.AcceleratorRegistry;
import com.spectrayan.spector.memory.model.SoulContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage 7 relay in {@link com.spectrayan.spector.memory.pathway.dream.DreamPathway}.
 *
 * <h3>Biological Analog: Predictive Coding Reality Testing &amp; Expected Free Energy Verification</h3>
 * <p>Validates synthetic counterfactual simulations against stored priors and soul identity,
 * evaluating prediction error, epistemic information gain, and pragmatic plausibility to assign
 * rigorous quality scores.</p>
 *
 * @since 1.4.0
 */
public final class CounterfactualProbeRelay implements SynapticRelay<DreamSignal> {

    private static final Logger log = LoggerFactory.getLogger(CounterfactualProbeRelay.class);

    public static final float WEIGHT_EPISTEMIC_SURPRISE = 0.55f;
    public static final float WEIGHT_PRAGMATIC_PLAUSIBILITY = 0.45f;
    public static final float DEFAULT_NEUTRAL_PLAUSIBILITY = 0.50f;

    @Override
    public boolean transmit(final DreamSignal signal) {
        if (signal == null || signal.constructedScenes().isEmpty()) {
            return true;
        }

        List<DreamSignal.DreamScene> scenes = new ArrayList<>(signal.constructedScenes());
        signal.constructedScenes().clear();

        List<float[]> seedVectors = signal.seedVectors();
        float[] seedCentroid = computeCentroid(seedVectors);

        SoulContext soul = signal.primarySoul();

        // Prepare flat contiguous array of seed vectors for SIMD/GPU batch similarity
        int numSeeds = seedVectors != null ? seedVectors.size() : 0;
        int dim = (numSeeds > 0 && seedVectors.get(0) != null) ? seedVectors.get(0).length : 0;
        float[] flatSeeds = null;
        if (numSeeds > 0 && dim > 0) {
            flatSeeds = new float[numSeeds * dim];
            for (int i = 0; i < numSeeds; i++) {
                float[] s = seedVectors.get(i);
                if (s != null) {
                    System.arraycopy(s, 0, flatSeeds, i * dim, Math.min(dim, s.length));
                }
            }
        }

        float[] soulEmbedding = (soul != null && soul.identityEmbedding() != null && soul.identityEmbedding().length == dim)
                ? soul.identityEmbedding() : null;

        for (DreamSignal.DreamScene scene : scenes) {
            float[] vec = scene.embedding();

            // 1. Prediction Error / Epistemic Novelty (Surprise) via batch SPI CosineSimilarity
            float maxSimWithSeed = 0.0f;
            if (flatSeeds != null && vec != null && vec.length == dim) {
                float[] sims = AcceleratorRegistry.getSimilarityKernel().cosineSimilarity(vec, flatSeeds, numSeeds, dim);
                for (float s : sims) {
                    if (s > maxSimWithSeed) {
                        maxSimWithSeed = s;
                    }
                }
            }
            float epistemicSurprise = Math.max(0.0f, 1.0f - maxSimWithSeed);

            // 2. Pragmatic Plausibility: prioritize Soul Identity prior over generic seed centroid
            float pragmaticPlausibility = DEFAULT_NEUTRAL_PLAUSIBILITY;
            if (soulEmbedding != null && vec != null && vec.length == dim) {
                float soulSim = AcceleratorRegistry.getSimilarityKernel().cosineSimilarity(vec, soulEmbedding, 1, dim)[0];
                pragmaticPlausibility = Math.max(0.0f, (soulSim + 1.0f) / 2.0f);
            } else if (seedCentroid != null && vec != null && vec.length == seedCentroid.length) {
                float centroidSim = AcceleratorRegistry.getSimilarityKernel().cosineSimilarity(vec, seedCentroid, 1, vec.length)[0];
                pragmaticPlausibility = Math.max(0.0f, (centroidSim + 1.0f) / 2.0f);
            }

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
            log.debug("CounterfactualProbeRelay: evaluated {} constructed scenes through predictive coding verification (soul={})",
                    signal.constructedScenes().size(), soul != null ? soul.name() : "none");
        }

        return true;
    }

    private static float[] computeCentroid(List<float[]> vectors) {
        if (vectors == null || vectors.isEmpty()) return null;
        int dim = vectors.get(0).length;
        if (dim == 0) return null;

        float[] sum = new float[dim];
        int count = 0;
        for (float[] v : vectors) {
            if (v == null || v.length != dim) continue;
            sum = VectorOps.add(sum, v);
            count++;
        }
        return count > 0 ? VectorOps.scale(sum, 1.0f / count) : null;
    }

    @Override
    public String relayName() {
        return "counterfactual_probe";
    }
}
