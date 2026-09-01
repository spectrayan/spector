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

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.aisme.phi.ConsciousnessContinuityEvaluator;
import com.spectrayan.spector.memory.aisme.phi.ConsciousnessContinuityState;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.recall.relay.RecallSignal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * RecallPathway relay that evaluates Consciousness Continuity Phi_CC and rewards holistic synergy.
 *
 * <h3>Biological Analog: IIT Subgraph Consciousness Continuity Scoring</h3>
 * <p>Quantifies whether the retrieved memory cluster forms an integrated experiential gestalt
 * coherent with the persona's core identity soul embedding.</p>
 */
public final class ConsciousnessContinuityRelay implements SynapticRelay<RecallSignal> {

    private static final Logger log = LoggerFactory.getLogger(ConsciousnessContinuityRelay.class);

    private final ConsciousnessContinuityEvaluator evaluator;
    private final Function<String, float[]> embeddingLookup;
    private final float phiWeight;

    /**
     * Constructs a ConsciousnessContinuityRelay with default parameters.
     *
     * @param evaluator continuity evaluator (nullable)
     * @param embeddingLookup embedding resolution function (nullable)
     */
    public ConsciousnessContinuityRelay(
            ConsciousnessContinuityEvaluator evaluator,
            Function<String, float[]> embeddingLookup) {
        this(evaluator, embeddingLookup, 0.25f);
    }

    /**
     * Constructs a ConsciousnessContinuityRelay with custom phi weighting.
     *
     * @param evaluator continuity evaluator
     * @param embeddingLookup embedding lookup function
     * @param phiWeight weight multiplier for Phi_CC score boost
     */
    public ConsciousnessContinuityRelay(
            ConsciousnessContinuityEvaluator evaluator,
            Function<String, float[]> embeddingLookup,
            float phiWeight) {
        this.evaluator = evaluator;
        this.embeddingLookup = embeddingLookup;
        this.phiWeight = phiWeight;
    }

    @Override
    public boolean transmit(final RecallSignal signal) {
        if (evaluator == null || embeddingLookup == null || signal == null) {
            return true;
        }

        List<CognitiveResult> candidates = signal.candidates();
        if (candidates == null || candidates.isEmpty()) {
            return true;
        }

        List<float[]> candidateVectors = new ArrayList<>(candidates.size());
        for (CognitiveResult r : candidates) {
            float[] vec = embeddingLookup.apply(r.id());
            if (vec != null) {
                candidateVectors.add(vec);
            }
        }

        if (candidateVectors.isEmpty()) {
            return true;
        }

        ConsciousnessContinuityState state = evaluator.evaluate(candidateVectors);
        float boost = 1.0f + (phiWeight * Math.min(1.0f, state.compositePhiCC()));

        for (int i = 0; i < candidates.size(); i++) {
            CognitiveResult r = candidates.get(i);
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

        if (log.isTraceEnabled()) {
            log.trace("ConsciousnessContinuityRelay modulated {} candidates with Phi_CC={}, boost={}",
                    candidates.size(), state.compositePhiCC(), boost);
        }

        return true;
    }

    @Override
    public String relayName() {
        return "consciousness-continuity";
    }
}
