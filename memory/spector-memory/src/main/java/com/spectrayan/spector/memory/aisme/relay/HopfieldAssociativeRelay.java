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
import com.spectrayan.spector.memory.aisme.homeostasis.HomeostaticCore;
import com.spectrayan.spector.memory.aisme.homeostasis.InteroceptiveState;
import com.spectrayan.spector.memory.aisme.hopfield.AttractorState;
import com.spectrayan.spector.memory.aisme.hopfield.ContinuousHopfieldNetwork;
import com.spectrayan.spector.memory.aisme.hopfield.PersonalityTemperature;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.recall.relay.RecallSignal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * RecallPathway relay that discovers Modern Hopfield attractor states across candidate memories
 * and modulates retrieval scores based on attractor attention weights.
 *
 * <h3>Biological Analog: CA3 Associative Attractor Dynamic Modulation</h3>
 * <p>Enhances memories that participate strongly in the coherent cognitive gestalt discovered
 * by the continuous Hopfield network, modulated by personality temperature and current arousal.</p>
 */
public final class HopfieldAssociativeRelay implements SynapticRelay<RecallSignal> {

    private static final Logger log = LoggerFactory.getLogger(HopfieldAssociativeRelay.class);

    private final ContinuousHopfieldNetwork network;
    private final Function<String, float[]> embeddingLookup;
    private final CognitiveProfile profile;
    private final HomeostaticCore homeostaticCore;
    private final float blendWeight;

    /**
     * Constructs a HopfieldAssociativeRelay with default blend weight (0.35).
     *
     * @param network the continuous Hopfield network engine (nullable)
     * @param embeddingLookup function to resolve candidate memory vectors by id (nullable)
     * @param profile the agent's cognitive profile (nullable)
     * @param homeostaticCore the homeostatic affective core (nullable)
     */
    public HopfieldAssociativeRelay(
            ContinuousHopfieldNetwork network,
            Function<String, float[]> embeddingLookup,
            CognitiveProfile profile,
            HomeostaticCore homeostaticCore) {
        this(network, embeddingLookup, profile, homeostaticCore, 0.35f);
    }

    /**
     * Constructs a HopfieldAssociativeRelay with custom blend weight.
     *
     * @param network the continuous Hopfield network engine
     * @param embeddingLookup function to resolve candidate memory vectors by id
     * @param profile the agent's cognitive profile
     * @param homeostaticCore the homeostatic core
     * @param blendWeight weight of the Hopfield attention boost multiplier
     */
    public HopfieldAssociativeRelay(
            ContinuousHopfieldNetwork network,
            Function<String, float[]> embeddingLookup,
            CognitiveProfile profile,
            HomeostaticCore homeostaticCore,
            float blendWeight) {
        this.network = network;
        this.embeddingLookup = embeddingLookup;
        this.profile = profile;
        this.homeostaticCore = homeostaticCore;
        this.blendWeight = blendWeight;
    }

    @Override
    public boolean transmit(final RecallSignal signal) {
        if (network == null || embeddingLookup == null || signal == null) {
            return true;
        }

        List<CognitiveResult> candidates = signal.candidates();
        if (candidates == null || candidates.isEmpty()) {
            return true;
        }

        float[] queryVector = signal.queryVector();
        if (queryVector == null || queryVector.length == 0) {
            return true;
        }

        int n = candidates.size();
        List<float[]> validPatternsList = new ArrayList<>(n);
        List<Integer> validIndices = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            CognitiveResult r = candidates.get(i);
            float[] vec = embeddingLookup.apply(r.id());
            if (vec != null && vec.length == queryVector.length) {
                validPatternsList.add(vec);
                validIndices.add(i);
            }
        }

        if (validPatternsList.isEmpty()) {
            return true;
        }

        float[][] patterns = validPatternsList.toArray(new float[0][]);
        InteroceptiveState interoceptiveState = (homeostaticCore != null) ? homeostaticCore.currentState() : null;
        float beta = PersonalityTemperature.deriveBeta(profile, interoceptiveState);

        AttractorState attractor = network.retrieveAttractor(queryVector, patterns, beta);
        float[] weights = attractor.attentionWeights();

        for (int k = 0; k < validIndices.size(); k++) {
            int candidateIdx = validIndices.get(k);
            CognitiveResult r = candidates.get(candidateIdx);
            float weight = (k < weights.length) ? weights[k] : 0.0f;

            float boost = 1.0f + (blendWeight * weight);
            float newScore = r.score() * boost;

            candidates.set(candidateIdx, new CognitiveResult(
                    r.id(), r.text(), newScore, r.importance(),
                    r.ageDays(), r.agentRecallCount(), r.valence(),
                    r.memoryType(), r.source(), r.synapticTags(),
                    r.decayFactor(), r.ltpAdjustedDecay(),
                    r.retrievalMode(), r.breakdown(), r.trace(),
                    r.sourceModality(), r.metadata()
            ));
        }

        if (log.isTraceEnabled()) {
            log.trace("HopfieldAssociativeRelay settled attractor type={} with {} patterns at beta={}",
                    attractor.type(), patterns.length, beta);
        }

        return true;
    }

    @Override
    public String relayName() {
        return "hopfield-associative";
    }
}
