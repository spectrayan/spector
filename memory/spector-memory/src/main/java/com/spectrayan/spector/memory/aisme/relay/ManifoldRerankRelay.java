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
package com.spectrayan.spector.memory.aisme.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.aisme.manifold.CognitiveManifold;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.recall.relay.RecallSignal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Function;

/**
 * RecallPathway relay that re-ranks candidate memories according to Riemannian distance
 * on the personal cognitive manifold.
 *
 * <h3>Biological Analog: Experiential Manifold Distance Re-weighting</h3>
 * <p>Warps Euclidean similarity scores by applying the personal metric tensor, rewarding memories
 * that reside closer along experiential geodesic paths on the agent's subjective cognitive manifold.</p>
 */
public final class ManifoldRerankRelay implements SynapticRelay<RecallSignal> {

    private static final Logger log = LoggerFactory.getLogger(ManifoldRerankRelay.class);

    private final CognitiveManifold manifold;
    private final Function<String, float[]> embeddingLookup;
    private final float sigma;
    private final float weight;

    /**
     * Constructs a ManifoldRerankRelay with default parameters (sigma=1.0, weight=0.35).
     *
     * @param manifold the cognitive manifold manager (nullable)
     * @param embeddingLookup function to resolve candidate memory vectors by id (nullable)
     */
    public ManifoldRerankRelay(CognitiveManifold manifold, Function<String, float[]> embeddingLookup) {
        this(manifold, embeddingLookup, 1.0f, 0.35f);
    }

    /**
     * Constructs a ManifoldRerankRelay with explicit parameters.
     *
     * @param manifold cognitive manifold manager
     * @param embeddingLookup embedding lookup function
     * @param sigma kernel bandwidth for manifold similarity
     * @param weight influence multiplier for manifold similarity
     */
    public ManifoldRerankRelay(CognitiveManifold manifold, Function<String, float[]> embeddingLookup, float sigma, float weight) {
        this.manifold = manifold;
        this.embeddingLookup = embeddingLookup;
        this.sigma = sigma;
        this.weight = weight;
    }

    @Override
    public boolean transmit(final RecallSignal signal) {
        if (manifold == null || embeddingLookup == null || signal == null) {
            return true;
        }

        List<CognitiveResult> candidates = signal.candidates();
        if (candidates == null || candidates.isEmpty()) {
            return true;
        }

        float[] queryVector = signal.queryVector();
        if (queryVector == null || queryVector.length != manifold.dimensions()) {
            return true;
        }

        for (int i = 0; i < candidates.size(); i++) {
            CognitiveResult r = candidates.get(i);
            float[] candidateVec = embeddingLookup.apply(r.id());

            if (candidateVec != null && candidateVec.length == manifold.dimensions()) {
                float manifoldSim = manifold.similarity(queryVector, candidateVec, sigma);
                float boost = 1.0f + (weight * manifoldSim);
                float newScore = r.score() * boost;

                candidates.set(i, new CognitiveResult(
                        r.id(), r.text(), newScore, r.importance(),
                        r.ageDays(), r.agentRecallCount(), r.valence(),
                        r.memoryType(), r.source(), r.synapticTags(),
                        r.decayFactor(), r.ltpAdjustedDecay(),
                        r.retrievalMode(), r.breakdown(), r.trace(),
                        r.sourceModality(), r.metadata()
                ));
            }
        }

        if (log.isTraceEnabled()) {
            log.trace("ManifoldRerankRelay re-ranked {} candidates with sigma={}, weight={}",
                    candidates.size(), sigma, weight);
        }

        return true;
    }

    @Override
    public String relayName() {
        return "manifold-rerank";
    }
}
