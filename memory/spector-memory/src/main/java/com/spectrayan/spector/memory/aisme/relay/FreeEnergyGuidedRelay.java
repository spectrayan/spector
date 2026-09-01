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
import com.spectrayan.spector.memory.aisme.fegr.FreeEnergyCalculator;
import com.spectrayan.spector.memory.aisme.fegr.MentalStatePosterior;
import com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker;
import com.spectrayan.spector.memory.aisme.fegr.SoulConditionedWeightProvider;
import com.spectrayan.spector.memory.aisme.homeostasis.AffectiveResonanceScorer;
import com.spectrayan.spector.memory.aisme.homeostasis.HomeostaticCore;
import com.spectrayan.spector.memory.aisme.homeostasis.InteroceptiveState;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.recall.relay.RecallSignal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Function;

/**
 * RecallPathway relay that enhances candidate retrieval scores using Free-Energy Relevance Scoring (FERS).
 *
 * <h3>Biological Analog: Variational Prediction-Error Guided Memory Selection</h3>
 * <p>Scores candidate memories not only on semantic proximity, but on their mathematical capacity
 * to reduce variational free energy and situational uncertainty in the agent's generative self-model.</p>
 */
public final class FreeEnergyGuidedRelay implements SynapticRelay<RecallSignal> {

    private static final Logger log = LoggerFactory.getLogger(FreeEnergyGuidedRelay.class);

    private final MentalStateTracker tracker;
    private final FreeEnergyCalculator calculator;
    private final HomeostaticCore homeostaticCore;
    private final AffectiveResonanceScorer affectiveScorer;
    private final Function<String, float[]> embeddingLookup;

    private final SoulConditionedWeightProvider weightProvider;
    private final float alpha;
    private final float beta;
    private final float gamma;

    /**
     * Constructs a FreeEnergyGuidedRelay with default scoring weights (alpha=0.5, beta=0.35, gamma=0.15).
     *
     * @param tracker the active mental state tracker (nullable)
     * @param calculator the free energy calculation engine (nullable)
     * @param homeostaticCore the homeostatic affective core (nullable)
     * @param affectiveScorer affective resonance scorer (nullable)
     * @param embeddingLookup function to resolve candidate memory vectors by id (nullable)
     */
    public FreeEnergyGuidedRelay(
            MentalStateTracker tracker,
            FreeEnergyCalculator calculator,
            HomeostaticCore homeostaticCore,
            AffectiveResonanceScorer affectiveScorer,
            Function<String, float[]> embeddingLookup) {
        this(tracker, calculator, homeostaticCore, affectiveScorer, embeddingLookup, null, 0.5f, 0.35f, 0.15f);
    }

    /**
     * Constructs a FreeEnergyGuidedRelay with dynamic soul-conditioned weight provider.
     */
    public FreeEnergyGuidedRelay(
            MentalStateTracker tracker,
            FreeEnergyCalculator calculator,
            HomeostaticCore homeostaticCore,
            AffectiveResonanceScorer affectiveScorer,
            Function<String, float[]> embeddingLookup,
            SoulConditionedWeightProvider weightProvider) {
        this(tracker, calculator, homeostaticCore, affectiveScorer, embeddingLookup, weightProvider, 0.5f, 0.35f, 0.15f);
    }

    /**
     * Constructs a FreeEnergyGuidedRelay with explicit scoring weights.
     *
     * @param tracker active mental state tracker
     * @param calculator free energy calculator
     * @param homeostaticCore homeostatic core
     * @param affectiveScorer affective resonance scorer
     * @param embeddingLookup embedding lookup function
     * @param weightProvider optional dynamic weight provider
     * @param alpha semantic similarity weight
     * @param beta free-energy reduction weight
     * @param gamma affective resonance weight
     */
    public FreeEnergyGuidedRelay(
            MentalStateTracker tracker,
            FreeEnergyCalculator calculator,
            HomeostaticCore homeostaticCore,
            AffectiveResonanceScorer affectiveScorer,
            Function<String, float[]> embeddingLookup,
            SoulConditionedWeightProvider weightProvider,
            float alpha,
            float beta,
            float gamma) {
        this.tracker = tracker;
        this.calculator = calculator;
        this.homeostaticCore = homeostaticCore;
        this.affectiveScorer = affectiveScorer;
        this.embeddingLookup = embeddingLookup;
        this.weightProvider = weightProvider;
        this.alpha = alpha;
        this.beta = beta;
        this.gamma = gamma;
    }

    @Override
    public boolean transmit(final RecallSignal signal) {
        if (tracker == null || calculator == null || signal == null) {
            return true;
        }

        List<CognitiveResult> candidates = signal.candidates();
        if (candidates == null || candidates.isEmpty()) {
            return true;
        }

        float[] observation = signal.queryVector();
        if (observation == null || observation.length != tracker.selfModel().dimensions()) {
            return true;
        }

        MentalStatePosterior posterior = tracker.currentPosterior();
        InteroceptiveState interoceptiveState = (homeostaticCore != null) ? homeostaticCore.currentState() : null;

        SoulConditionedWeightProvider.FersWeights weights = (weightProvider != null)
                ? weightProvider.provideWeights(signal)
                : new SoulConditionedWeightProvider.FersWeights(alpha, beta, gamma, com.spectrayan.spector.memory.model.ScoringRegime.GENERIC);

        float activeAlpha = weights.alpha();
        float activeBeta = weights.beta();
        float activeGamma = weights.gamma();
        com.spectrayan.spector.memory.model.ScoringRegime activeRegime = weights.regime();

        for (int i = 0; i < candidates.size(); i++) {
            CognitiveResult r = candidates.get(i);
            float baseSim = r.score();
            float deltaF = 0.0f;

            if (embeddingLookup != null) {
                float[] candidateVec = embeddingLookup.apply(r.id());
                if (candidateVec != null && candidateVec.length == posterior.dimensions()) {
                    deltaF = calculator.calculateFreeEnergyReduction(
                            posterior,
                            tracker.selfModel(),
                            observation,
                            candidateVec,
                            null
                    );
                }
            }

            float affectiveResonance = 0.5f;
            if (affectiveScorer != null && interoceptiveState != null) {
                affectiveResonance = affectiveScorer.score(interoceptiveState, r.valence(), 0.5f);
            }

            float fersScore = FreeEnergyCalculator.calculateFersScore(
                    baseSim,
                    deltaF,
                    affectiveResonance,
                    activeAlpha,
                    activeBeta,
                    activeGamma
            );

            com.spectrayan.spector.memory.model.ScoreBreakdown breakdown = new com.spectrayan.spector.memory.model.ScoreBreakdown(
                    baseSim,
                    r.importance() * r.decayFactor(),
                    1.0f,
                    1.0f,
                    1.0f,
                    1.0f,
                    fersScore,
                    activeAlpha,
                    activeBeta,
                    activeGamma,
                    activeRegime
            );

            candidates.set(i, new CognitiveResult(
                    r.id(), r.text(), fersScore, r.importance(),
                    r.ageDays(), r.agentRecallCount(), r.valence(),
                    r.memoryType(), r.source(), r.synapticTags(),
                    r.decayFactor(), r.ltpAdjustedDecay(),
                    r.retrievalMode(), breakdown, r.trace(),
                    r.sourceModality(), r.metadata(), r.consolidationFlags()
            ));
        }

        if (log.isTraceEnabled()) {
            log.trace("FreeEnergyGuidedRelay processed {} candidates with regime={}, alpha={}, beta={}, gamma={}",
                    candidates.size(), activeRegime, activeAlpha, activeBeta, activeGamma);
        }

        return true;
    }

    @Override
    public String relayName() {
        return "free-energy-guided";
    }
}
