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
package com.spectrayan.spector.memory.aisme.homeostasis;

import com.spectrayan.spector.core.similarity.AffectiveDistance;
import com.spectrayan.spector.memory.model.CognitiveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Computes mood-congruent scoring — memories matching the current emotional state score higher.
 *
 * <h3>Biological Analog: Mood-Congruent Memory Retrieval</h3>
 * <p>Just as humans more easily recall memories that match their current emotional state,
 * this scorer biases memory retrieval towards results that resonate with the agent's current
 * interoceptive state (valence and arousal).</p>
 */
public final class AffectiveResonanceScorer {

    private static final Logger log = LoggerFactory.getLogger(AffectiveResonanceScorer.class);

    public AffectiveResonanceScorer() {
    }

    /**
     * Scores a single memory's affective resonance against current state (valence only).
     *
     * @param currentState the current interoceptive state
     * @param memoryValence the valence of the memory (-128 to +127)
     * @param sigma the bandwidth parameter for the Gaussian kernel
     * @return the resonance score
     */
    public float score(InteroceptiveState currentState, byte memoryValence, float sigma) {
        float memV = memoryValence / 127.0f;
        float[] stateVec = new float[]{ currentState.valence() };
        float[] memVec = new float[]{ memV };
        return AffectiveDistance.compute(stateVec, memVec, sigma);
    }

    /**
     * Scores a single memory's affective resonance against current state (valence and arousal).
     *
     * @param currentState the current interoceptive state
     * @param memoryValence the valence of the memory (-128 to +127)
     * @param memoryArousal the arousal of the memory (-128 to +127)
     * @param sigma the bandwidth parameter for the Gaussian kernel
     * @return the resonance score
     */
    public float score(InteroceptiveState currentState, byte memoryValence, byte memoryArousal, float sigma) {
        float memV = memoryValence / 127.0f;
        float memA = memoryArousal / 127.0f;
        float[] stateVec = new float[]{ currentState.valence(), currentState.arousal() };
        float[] memVec = new float[]{ memV, memA };
        return AffectiveDistance.compute(stateVec, memVec, sigma);
    }

    /**
     * Applies affective resonance as a multiplicative bias to existing scores.
     *
     * @param results the list of candidate cognitive results to bias
     * @param currentState the current interoceptive state
     * @param weight the strength of the bias multiplier
     * @param sigma the bandwidth parameter for the Gaussian kernel
     */
    public void applyBias(List<CognitiveResult> results, InteroceptiveState currentState, float weight, float sigma) {
        if (results == null || results.isEmpty() || currentState == null) {
            return;
        }

        for (int i = 0; i < results.size(); i++) {
            CognitiveResult r = results.get(i);
            
            // Score based on valence since CognitiveResult tracks valence as byte
            float resonance = score(currentState, r.valence(), sigma);
            
            // Multiplicative bias scaling
            float biasMultiplier = 1.0f + (resonance * weight);
            float newScore = r.score() * biasMultiplier;

            results.set(i, new CognitiveResult(
                    r.id(), r.text(), newScore, r.importance(),
                    r.ageDays(), r.agentRecallCount(), r.valence(),
                    r.memoryType(), r.source(), r.synapticTags(),
                    r.decayFactor(), r.ltpAdjustedDecay(),
                    r.retrievalMode(), r.breakdown(), r.trace(),
                    r.sourceModality(), r.metadata()
            ));
        }

        if (log.isTraceEnabled()) {
            log.trace("Applied affective resonance bias to {} cognitive results with weight={}, sigma={}", results.size(), weight, sigma);
        }
    }
}
