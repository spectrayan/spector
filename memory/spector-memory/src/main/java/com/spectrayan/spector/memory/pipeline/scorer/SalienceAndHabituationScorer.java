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
package com.spectrayan.spector.memory.pipeline.scorer;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.habituation.HabituationPenalty;
import com.spectrayan.spector.memory.hebbian.CoActivationRecordMemory;
import com.spectrayan.spector.memory.inhibition.SuppressionSet;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallMode;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoreBreakdown;
import com.spectrayan.spector.memory.model.ScoringMode;
import com.spectrayan.spector.memory.pipeline.GraphScoringPolicy;
import com.spectrayan.spector.memory.prospective.ProspectiveScheduler;
import com.spectrayan.spector.memory.prospective.Reminder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.spectrayan.spector.commons.observation.MemoryObservationHook;
import static com.spectrayan.spector.commons.observation.MemoryObservationHook.SCORING_HABITUATION;
import static com.spectrayan.spector.commons.observation.MemoryObservationHook.SCORING_STDP;

/**
 * Computes novelty scores, habituation decay penalties, emotional valence/arousal
 * modulation, and prospective reminder triggers.
 */
public class SalienceAndHabituationScorer {

    private static final float SATIATION_PENALTY = 0.5f;

    private final SuppressionSet suppressionSet;
    private final HabituationPenalty habituationPenalty;
    private final MemoryObservationHook hook;
    private final Map<String, Long> satiationCache = new ConcurrentHashMap<>(16);

    public SalienceAndHabituationScorer(SuppressionSet suppressionSet, HabituationPenalty habituationPenalty, MemoryObservationHook hook) {
        this.suppressionSet = suppressionSet;
        this.habituationPenalty = habituationPenalty;
        this.hook = hook != null ? hook : MemoryObservationHook.NOOP;
    }

    public SalienceAndHabituationScorer(SuppressionSet suppressionSet, HabituationPenalty habituationPenalty) {
        this(suppressionSet, habituationPenalty, MemoryObservationHook.NOOP);
    }

    public SuppressionSet suppressionSet() {
        return suppressionSet;
    }

    public HabituationPenalty habituationPenalty() {
        return habituationPenalty;
    }

    public Map<String, Long> satiationCache() {
        return satiationCache;
    }

    /** Seeds due prospective reminders as top-priority working results. */
    public void seedProspectiveReminders(List<CognitiveResult> allResults, ProspectiveScheduler prospectiveScheduler) {
        if (prospectiveScheduler == null) return;
        List<Reminder> dueReminders = prospectiveScheduler.collectDue();
        for (Reminder r : dueReminders) {
            allResults.add(new CognitiveResult(
                    r.id(), r.text(), 10.0f, 10.0f, 0f,
                    (short) 0, (byte) 0, MemoryType.WORKING, MemorySource.PROCEDURAL,
                    new String[]{"prospective"}, 1.0f, 1.0f));
        }
    }

    /**
     * Applies cognitive post-scoring in place: habituation + inhibition-of-return +
     * semantic satiation penalties, then STDP causal boost. No-op in SIMILARITY mode.
     */
    public void applyCognitiveScoring(List<CognitiveResult> allResults,
                                       RecallOptions options, long nowMs,
                                       CoActivationRecordMemory coActivationTracker,
                                       GraphScoringPolicy graphScoringPolicy) {
        if (options.scoringMode() == ScoringMode.SIMILARITY) return;

        // Habituation penalty + inhibition of return + semantic satiation
        hook.observe(SCORING_HABITUATION, Map.of(), () -> {
            for (int i = 0; i < allResults.size(); i++) {
                CognitiveResult r = allResults.get(i);
                float habPenalty = (options.recallMode() == RecallMode.LEARN)
                        ? habituationPenalty.recordAndComputePenalty(r.id())
                        : habituationPenalty.currentPenalty(r.id());
                float iorPenalty = habituationPenalty.computeInhibitionOfReturn(r.id(), nowMs);
                float combinedPenalty = Math.min(habPenalty, iorPenalty); // stronger suppression wins
    
                // Semantic Satiation: 0.5x penalty for results in the hot LRU cache
                if (satiationCache.containsKey(r.id())) {
                    combinedPenalty *= SATIATION_PENALTY;
                }
    
                if (combinedPenalty < 1.0f) {
                    float newScore = r.score() * combinedPenalty;
                    ScoreBreakdown bd = r.breakdown() != null
                            ? new ScoreBreakdown(
                                    r.breakdown().similarity(),
                                    r.breakdown().importanceDecay(),
                                    r.breakdown().tagBoostFactor(),
                                    combinedPenalty,
                                    r.breakdown().graphBoost(),
                                    r.breakdown().valenceAlignment(),
                                    newScore)
                            : null;
                    allResults.set(i, new CognitiveResult(
                            r.id(), r.text(), newScore, r.importance(), r.ageDays(),
                            r.agentRecallCount(), r.valence(), r.memoryType(), r.source(),
                            r.synapticTags(), r.decayFactor(), r.ltpAdjustedDecay(),
                            r.retrievalMode(), bd, r.trace(), r.sourceModality(), r.metadata()));
                }
            }
        });

        // STDP causal boost — cross-boost results whose tags are causally linked.
        hook.observe(SCORING_STDP, Map.of(), () -> {
            if (coActivationTracker != null && allResults.size() >= 2) {
                Set<String> contextTagSet = new HashSet<>();
                int contextLimit = Math.min(3, allResults.size());
                for (int cl = 0; cl < contextLimit; cl++) {
                    String[] ctxTags = allResults.get(cl).synapticTags();
                    if (ctxTags != null) {
                        for (String t : ctxTags) contextTagSet.add(t);
                    }
                }
    
                if (!contextTagSet.isEmpty()) {
                    List<String> contextTags = new ArrayList<>(contextTagSet);
                    float weight = graphScoringPolicy != null ? graphScoringPolicy.causalBoostWeight() : 0.1f;
                    for (int i = 0; i < allResults.size(); i++) {
                        CognitiveResult r = allResults.get(i);
                        if (r.synapticTags() == null || r.synapticTags().length == 0) continue;
    
                        float predictive = coActivationTracker.getPredictiveStrength(
                                contextTags, r.synapticTags());
                        if (predictive > 0) {
                            float boostedScore = r.score() * (1.0f + predictive * weight);
                            allResults.set(i, new CognitiveResult(
                                    r.id(), r.text(), boostedScore, r.importance(), r.ageDays(),
                                    r.agentRecallCount(), r.valence(), r.memoryType(), r.source(),
                                    r.synapticTags(), r.decayFactor(), r.ltpAdjustedDecay(),
                                    r.retrievalMode(), r.breakdown(), r.trace(), r.sourceModality(), r.metadata()));
                        }
                    }
                }
            }
        });
    }
}
