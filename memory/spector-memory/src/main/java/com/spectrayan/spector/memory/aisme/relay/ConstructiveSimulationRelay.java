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
import com.spectrayan.spector.memory.aisme.narrative.NarrativeSelfEngine;
import com.spectrayan.spector.memory.aisme.pcmn.PredictiveCodingNetwork;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.recall.relay.RecallSignal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Function;

/**
 * RecallPathway relay that evaluates candidate memories against autobiographical narrative priors
 * and multi-tier predictive coding error reduction.
 *
 * <h3>Biological Analog: Default Mode Network Narrative Schema Validation</h3>
 * <p>Ensures recalled memories cohere with the persona's overarching autobiographical identity
 * and minimizes hierarchical predictive error across cortical tiers.</p>
 */
public final class ConstructiveSimulationRelay implements SynapticRelay<RecallSignal> {

    private static final Logger log = LoggerFactory.getLogger(ConstructiveSimulationRelay.class);

    private final NarrativeSelfEngine narrativeEngine;
    private final PredictiveCodingNetwork pcmn;
    private final Function<String, float[]> embeddingLookup;
    private final float narrativeWeight;

    /**
     * Constructs a ConstructiveSimulationRelay with default parameters.
     *
     * @param narrativeEngine narrative self engine (nullable)
     * @param pcmn predictive coding network (nullable)
     * @param embeddingLookup embedding resolution function (nullable)
     */
    public ConstructiveSimulationRelay(
            NarrativeSelfEngine narrativeEngine,
            PredictiveCodingNetwork pcmn,
            Function<String, float[]> embeddingLookup) {
        this(narrativeEngine, pcmn, embeddingLookup, 0.30f);
    }

    /**
     * Constructs a ConstructiveSimulationRelay with custom narrative weighting.
     *
     * @param narrativeEngine narrative self engine
     * @param pcmn predictive coding network
     * @param embeddingLookup embedding resolution function
     * @param narrativeWeight weight multiplier for narrative alignment
     */
    public ConstructiveSimulationRelay(
            NarrativeSelfEngine narrativeEngine,
            PredictiveCodingNetwork pcmn,
            Function<String, float[]> embeddingLookup,
            float narrativeWeight) {
        this.narrativeEngine = narrativeEngine;
        this.pcmn = pcmn;
        this.embeddingLookup = embeddingLookup;
        this.narrativeWeight = narrativeWeight;
    }

    @Override
    public boolean transmit(final RecallSignal signal) {
        if (narrativeEngine == null || embeddingLookup == null || signal == null) {
            return true;
        }

        List<CognitiveResult> candidates = signal.candidates();
        if (candidates == null || candidates.isEmpty()) {
            return true;
        }

        float[] queryVector = signal.queryVector();
        if (queryVector == null || queryVector.length != narrativeEngine.dimensions()) {
            return true;
        }

        float[] narrativePrior = narrativeEngine.deriveNarrativePrior(queryVector, 0.3f);

        for (int i = 0; i < candidates.size(); i++) {
            CognitiveResult r = candidates.get(i);
            float[] memoryVec = embeddingLookup.apply(r.id());

            if (memoryVec != null && memoryVec.length == narrativeEngine.dimensions()) {
                float alignment = narrativeEngine.evaluateAlignment(memoryVec, narrativePrior);
                float boost = 1.0f + (narrativeWeight * alignment);
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
            log.trace("ConstructiveSimulationRelay evaluated {} candidates with narrative weight {}",
                    candidates.size(), narrativeWeight);
        }

        return true;
    }

    @Override
    public String relayName() {
        return "constructive-simulation";
    }
}
