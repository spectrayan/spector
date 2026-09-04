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
import com.spectrayan.spector.memory.pathway.recall.relay.RecallSignal;

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

        // 2. Synthesize Counterfactual Episodic Recombination (Schacter & Addis 2007, MR-07)
        int numCandidates = candidates.size();
        if (numCandidates >= 2) {
            int maxSimulations = Math.min(3, numCandidates - 1);
            long seed = 0xCBF29CE484222325L;
            for (float v : queryVector) {
                seed = seed * 31 + Float.floatToIntBits(v);
            }
            java.util.Random rng = new java.util.Random(seed);

            for (int s = 0; s < maxSimulations; s++) {
                int idx1 = s;
                int idx2 = (s + 1 + (rng.nextInt(numCandidates - 1))) % numCandidates;
                if (idx1 == idx2) {
                    idx2 = (idx1 + 1) % numCandidates;
                }

                CognitiveResult r1 = candidates.get(idx1);
                CognitiveResult r2 = candidates.get(idx2);
                float[] v1 = embeddingLookup.apply(r1.id());
                float[] v2 = embeddingLookup.apply(r2.id());

                if (v1 != null && v2 != null && v1.length == narrativeEngine.dimensions() && v2.length == narrativeEngine.dimensions()) {
                    float align1 = Math.max(0.1f, narrativeEngine.evaluateAlignment(v1, narrativePrior));
                    float align2 = Math.max(0.1f, narrativeEngine.evaluateAlignment(v2, narrativePrior));
                    float w1 = align1 / (align1 + align2);
                    float w2 = 1.0f - w1;

                    float[] simVec = new float[narrativeEngine.dimensions()];
                    for (int d = 0; d < simVec.length; d++) {
                        simVec[d] = w1 * v1[d] + w2 * v2[d];
                    }

                    float alignSim = narrativeEngine.evaluateAlignment(simVec, narrativePrior);

                    // PCMN Hierarchical Prediction Error Check (if PCMN is active)
                    if (pcmn != null) {
                        try {
                            float[][] mockTiers = new float[][]{simVec, simVec, narrativePrior, narrativePrior};
                            var error = pcmn.evaluateHierarchy(mockTiers);
                            if (error != null && error.totalEnergy() > 50.0f) {
                                // Excessive predictive coding error - attenuate alignSim
                                alignSim *= 0.8f;
                            }
                        } catch (Exception e) {
                            log.trace("PCMN evaluation skipped during simulation: {}", e.getMessage());
                        }
                    }

                    if (alignSim > 0.3f) {
                        float simScore = (r1.score() + r2.score()) * 0.5f * (1.0f + narrativeWeight * alignSim);
                        String simId = new com.spectrayan.spector.memory.kernel.id.TsidGenerator().generate();
                        CognitiveResult simResult = new CognitiveResult(
                                simId,
                                "[Constructive Simulation: " + r1.id() + "+" + r2.id() + "] " + r1.text() + " | " + r2.text(),
                                simScore,
                                Math.max(r1.importance(), r2.importance()),
                                0.0f,
                                0,
                                (byte) ((r1.valence() + r2.valence()) / 2),
                                com.spectrayan.spector.memory.model.MemoryType.EPISODIC,
                                com.spectrayan.spector.memory.cortex.MemorySource.INFERRED,
                                new String[]{"simulated", "counterfactual", "constructive"},
                                1.0f,
                                1.0f,
                                com.spectrayan.spector.memory.model.CognitiveResult.RetrievalMode.STANDARD,
                                null,
                                null,
                                com.spectrayan.spector.memory.model.SourceModality.TEXT,
                                java.util.Map.of(
                                        "simulation", "counterfactual_recombination",
                                        "alignSim", String.valueOf(alignSim)
                                ),
                                com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields.FLAG_SIMULATED
                        );
                        candidates.add(simResult);
                        signal.attributes().put("simVec:" + simId, simVec);
                    }
                }
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
