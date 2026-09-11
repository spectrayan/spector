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
package com.spectrayan.spector.memory.aisme.policy;

import com.spectrayan.spector.core.math.SoftmaxKernel;
import com.spectrayan.spector.memory.aisme.fegr.MentalStatePosterior;
import com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker;
import com.spectrayan.spector.memory.aisme.homeostasis.HomeostaticCore;
import com.spectrayan.spector.memory.model.SoulContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Thread-safe engine performing Boltzmann softmax policy selection over Expected Free Energy G(π) scores.
 *
 * <h3>Biological Analog: Basal Ganglia Action Selection Circuit</h3>
 * <p>Evaluates candidate cognitive policies, modulates selection precision γ by homeostatic arousal
 * and dominance state, and applies softmax normalization to produce calibrated policy probabilities.</p>
 */
public final class PolicyInferenceEngine {

    private static final Logger log = LoggerFactory.getLogger(PolicyInferenceEngine.class);

    private final ExpectedFreeEnergyCalculator calculator;
    private final HomeostaticCore homeostaticCore;
    private final MentalStateTracker mentalStateTracker;
    private final float basePrecision;

    /**
     * Constructs a PolicyInferenceEngine.
     *
     * @param calculator the EFE calculator with multi-soul composite priors
     * @param homeostaticCore homeostatic state for arousal/dominance precision modulation
     * @param mentalStateTracker current posterior belief state tracker
     * @param basePrecision base precision γ₀ for Boltzmann softmax temperature
     */
    public PolicyInferenceEngine(
            ExpectedFreeEnergyCalculator calculator,
            HomeostaticCore homeostaticCore,
            MentalStateTracker mentalStateTracker,
            float basePrecision) {
        this.calculator = calculator;
        this.homeostaticCore = homeostaticCore;
        this.mentalStateTracker = mentalStateTracker;
        this.basePrecision = basePrecision;
    }

    /**
     * Evaluates candidate policies and returns a decision report with Boltzmann-selected winner.
     *
     * @param candidates list of candidate cognitive policies to evaluate
     * @param soulContexts active soul contexts for multi-soul preference composition
     * @return decision report with ranked policies and selected winner
     */
    public PolicyDecisionReport evaluate(List<CognitivePolicy> candidates, List<SoulContext> soulContexts) {
        long start = System.nanoTime();

        if (candidates == null || candidates.isEmpty()) {
            return PolicyDecisionReport.empty();
        }

        // Retrieve current posterior from MentalStateTracker
        MentalStatePosterior posterior = mentalStateTracker.currentPosterior();
        float[] currentPosteriorMean = posterior.mean();
        float[] currentPosteriorPrecision = posterior.precision();

        // Compute dynamic precision γ = γ₀ * (1 + 0.5*arousal + 0.3*dominance)
        float arousal = homeostaticCore.currentState().arousal();
        float dominance = homeostaticCore.currentState().dominance();
        float gamma = basePrecision * (1.0f + 0.5f * arousal + 0.3f * dominance);

        // Score each candidate policy
        List<PolicyDecisionReport.ScoredPolicy> rawScores = new ArrayList<>(candidates.size());
        for (CognitivePolicy policy : candidates) {
            PolicyDecisionReport.ScoredPolicy scored = calculator.evaluate(
                    policy, soulContexts, currentPosteriorMean, currentPosteriorPrecision);
            rawScores.add(scored);
        }

        // Boltzmann softmax: P(π) = exp(-γ * G(π)) / Σ exp(-γ * G(π'))
        // Max-shift stabilized via SoftmaxKernel to prevent IEEE 754 overflow
        int count = rawScores.size();
        float[] gScores = new float[count];
        for (int i = 0; i < count; i++) {
            gScores[i] = rawScores.get(i).totalG();
        }
        float[] probabilities = new float[count];
        SoftmaxKernel.computeProbabilitiesScaled(gScores, -gamma, probabilities);

        // Normalize and build ranked list
        List<PolicyDecisionReport.ScoredPolicy> ranked = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            PolicyDecisionReport.ScoredPolicy raw = rawScores.get(i);
            ranked.add(new PolicyDecisionReport.ScoredPolicy(
                    raw.policy(), raw.pragmaticRisk(), raw.epistemicGain(), raw.totalG(), probabilities[i]));
        }

        // Sort by probability descending
        ranked.sort(Comparator.comparing(PolicyDecisionReport.ScoredPolicy::probability).reversed());

        CognitivePolicy selected = ranked.isEmpty() ? null : ranked.getFirst().policy();
        long durationNanos = System.nanoTime() - start;

        log.debug("PolicyInference: evaluated {} candidates in {}µs, selected={}, γ={:.3f}",
                candidates.size(), durationNanos / 1000, selected != null ? selected.policyType() : "none", gamma);

        return new PolicyDecisionReport(selected, ranked, gamma, durationNanos, Instant.now());
    }
}
