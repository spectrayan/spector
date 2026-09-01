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
import com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker;
import com.spectrayan.spector.memory.aisme.homeostasis.HomeostaticCore;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.recall.relay.RecallSignal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Function;

/**
 * RecallPathway terminal relay that closes the active perception loop by updating the agent's
 * mental state posterior and stepping the homeostatic affective core based on observation evidence
 * and retrieved memory valence.
 *
 * <h3>Biological Analog: Post-Recall Bayesian Belief Updating & Emotional Modulation</h3>
 * <p>In Active Inference, perceiving sensory input and recalling memories must reduce situational
 * ambiguity by shifting the posterior belief $q(s_t)$ toward observed evidence and advancing
 * interoceptive homeostatic dynamics.</p>
 */
public final class EpistemicLearningRelay implements SynapticRelay<RecallSignal> {

    private static final Logger log = LoggerFactory.getLogger(EpistemicLearningRelay.class);

    private final MentalStateTracker tracker;
    private final HomeostaticCore homeostaticCore;
    private final Function<String, float[]> embeddingLookup;
    private final int maxEvidenceMemories;
    private final float evidencePrecisionWeight;

    /**
     * Constructs an EpistemicLearningRelay with default parameters.
     *
     * @param tracker mental state tracker (nullable)
     * @param homeostaticCore homeostatic affective core (nullable)
     * @param embeddingLookup embedding lookup function for candidate memories (nullable)
     */
    public EpistemicLearningRelay(
            MentalStateTracker tracker,
            HomeostaticCore homeostaticCore,
            Function<String, float[]> embeddingLookup) {
        this(tracker, homeostaticCore, embeddingLookup, 3, 2.0f);
    }

    /**
     * Constructs an EpistemicLearningRelay with explicit hyperparameters.
     *
     * @param tracker mental state tracker (nullable)
     * @param homeostaticCore homeostatic affective core (nullable)
     * @param embeddingLookup embedding lookup function (nullable)
     * @param maxEvidenceMemories maximum number of top recalled memories to fuse as evidence
     * @param evidencePrecisionWeight precision weight allocated to each recalled memory evidence vector
     */
    public EpistemicLearningRelay(
            MentalStateTracker tracker,
            HomeostaticCore homeostaticCore,
            Function<String, float[]> embeddingLookup,
            int maxEvidenceMemories,
            float evidencePrecisionWeight) {
        this.tracker = tracker;
        this.homeostaticCore = homeostaticCore;
        this.embeddingLookup = embeddingLookup;
        this.maxEvidenceMemories = Math.max(1, maxEvidenceMemories);
        this.evidencePrecisionWeight = Math.max(0.1f, evidencePrecisionWeight);
    }

    @Override
    public String relayName() {
        return RelayNames.EPISTEMIC_LEARNING;
    }

    @Override
    public boolean transmit(final RecallSignal signal) {
        if (signal == null) {
            return true;
        }

        final float[] queryVector = signal.queryVector();
        final long now = System.currentTimeMillis();

        // 1. Update Mental State Posterior q(s_t) with Fused Query + Memory Evidence
        if (tracker != null && queryVector != null && queryVector.length == tracker.selfModel().dimensions()) {
            try {
                float[] fusedObservation = buildFusedObservation(queryVector, signal.candidates());
                tracker.updateWithObservation(fusedObservation, now);
            } catch (final RuntimeException e) {
                log.warn("Failed to update mental state posterior in EpistemicLearningRelay: {}", e.getMessage());
            }
        }

        // 2. Step Homeostatic Affective Core with Sensory Stimulus & Recalled Valence
        if (homeostaticCore != null) {
            try {
                float stimulusValence = 0.0f;
                if (signal.options() != null && signal.options().minValence() != Byte.MIN_VALUE) {
                    stimulusValence = signal.options().minValence() / 128.0f;
                }

                float memoryValence = calculateAverageMemoryValence(signal.candidates());
                float[] externalInput = new float[]{stimulusValence, 0.0f, 0.0f, 0.0f};
                float[] recallInfluence = new float[]{memoryValence, 0.0f, 0.0f, 0.0f};
                homeostaticCore.step(externalInput, recallInfluence, 0.1f);
            } catch (final RuntimeException e) {
                log.warn("Failed to step homeostatic core in EpistemicLearningRelay: {}", e.getMessage());
            }
        }

        return true;
    }

    private float[] buildFusedObservation(float[] queryVector, List<CognitiveResult> candidates) {
        if (embeddingLookup == null || candidates == null || candidates.isEmpty()) {
            return queryVector;
        }

        int dim = queryVector.length;
        float[] fused = new float[dim];
        float totalWeight = 1.0f; // Base query weight

        // Initialize with base query vector
        for (int d = 0; d < dim; d++) {
            fused[d] = queryVector[d];
        }

        int count = Math.min(maxEvidenceMemories, candidates.size());
        for (int i = 0; i < count; i++) {
            CognitiveResult r = candidates.get(i);
            float[] memVec = embeddingLookup.apply(r.id());
            if (memVec != null && memVec.length == dim) {
                float weight = evidencePrecisionWeight * Math.max(0.1f, r.score());
                for (int d = 0; d < dim; d++) {
                    fused[d] += weight * memVec[d];
                }
                totalWeight += weight;
            }
        }

        if (totalWeight > 1.0f) {
            for (int d = 0; d < dim; d++) {
                fused[d] /= totalWeight;
            }
        }

        return fused;
    }

    private float calculateAverageMemoryValence(List<CognitiveResult> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return 0.0f;
        }
        int count = Math.min(maxEvidenceMemories, candidates.size());
        float sum = 0.0f;
        for (int i = 0; i < count; i++) {
            sum += candidates.get(i).valence();
        }
        return (sum / count) / 128.0f; // Normalize byte [-128, 127] into [-1.0, 1.0]
    }
}
