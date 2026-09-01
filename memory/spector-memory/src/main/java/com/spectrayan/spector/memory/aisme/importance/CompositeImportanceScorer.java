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
package com.spectrayan.spector.memory.aisme.importance;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.core.cognitive.CompositeImportanceKernel;
import com.spectrayan.spector.core.similarity.CosineSimilarity;
import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.aisme.fegr.EventDensityMetrics;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.remember.relay.RememberSignal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Evaluator synthesizing multi-dimensional neurocognitive signals into composite importance scores \(I(o_t)\).
 *
 * <h3>Biological Analog: Multi-Limbic Salience Synthesis</h3>
 * <p>Integrates epistemic prediction error (Hippocampus/PFC), affective valence & arousal (Amygdala/Insula),
 * prospective goal alignment (dlPFC), social context (ACC), and manifold novelty.</p>
 */
public final class CompositeImportanceScorer {

    private static final Logger log = LoggerFactory.getLogger(CompositeImportanceScorer.class);

    private final AismeConfig config;
    private final float[] baseWeights;
    private final List<float[]> activeGoalEmbeddings = new CopyOnWriteArrayList<>();

    public CompositeImportanceScorer(AismeConfig config) {
        if (config == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "config must not be null");
        }
        this.config = config;
        this.baseWeights = CompositeImportanceKernel.normalizeWeights(new float[]{
                config.importanceWeightSurprise(),
                config.importanceWeightAffect(),
                config.importanceWeightGoal(),
                config.importanceWeightSocial(),
                config.importanceWeightNovelty()
        });
        log.info("Initialized CompositeImportanceScorer with base weights: [S={:.2f}, A={:.2f}, G={:.2f}, Soc={:.2f}, N={:.2f}], flashbulbThreshold={}",
                baseWeights[0], baseWeights[1], baseWeights[2], baseWeights[3], baseWeights[4], config.importanceFlashbulbThreshold());
    }

    /**
     * Registers active goal / intention embeddings for prospective relevance scoring.
     *
     * @param goalEmbeddings list of L2-normalized goal vectors
     */
    public void setGoalEmbeddings(List<float[]> goalEmbeddings) {
        activeGoalEmbeddings.clear();
        if (goalEmbeddings != null) {
            for (float[] g : goalEmbeddings) {
                if (g != null && g.length > 0) {
                    activeGoalEmbeddings.add(g);
                }
            }
        }
    }

    /**
     * Evaluates composite importance for a structured signal vector and active cognitive profile.
     *
     * @param signals component neurocognitive signals
     * @param profile active cognitive profile (nullable, defaults to configured weights)
     * @return normalized scalar importance \(I(o_t) \in [0.0, 1.0]\)
     */
    public float evaluate(CompositeImportanceSignals signals, CognitiveProfile profile) {
        if (signals == null) {
            return 0.0f;
        }
        if (!config.enableImportance()) {
            return 0.5f;
        }

        float[] weights = weightsForProfile(profile);
        return CompositeImportanceKernel.evaluate(signals.toArray(), weights);
    }

    /**
     * Extracts component signals from an in-flight {@link RememberSignal} and computes composite importance.
     *
     * @param signal in-flight remember signal
     * @param profile active cognitive profile
     * @return normalized scalar importance \(I(o_t) \in [0.0, 1.0]\)
     */
    public float evaluateSignal(RememberSignal signal, CognitiveProfile profile) {
        if (signal == null) {
            return 0.0f;
        }
        if (!config.enableImportance()) {
            return signal.importance() > 0.0f ? signal.importance() : 0.5f;
        }

        CompositeImportanceSignals signals = extractSignals(signal);
        return evaluate(signals, profile);
    }

    /**
     * Extracts component signals from a remember signal payload.
     *
     * @param signal remember signal
     * @return extracted 5-dimensional signals
     */
    public CompositeImportanceSignals extractSignals(RememberSignal signal) {
        if (signal == null) {
            return CompositeImportanceSignals.neutral();
        }

        // 1. Surprise s1
        float surprise = 0.1f;
        EventDensityMetrics edm = signal.eventDensityMetrics();
        if (edm != null) {
            surprise = edm.surprisal();
        } else if (signal.nearestDist() > 0.0f) {
            surprise = Math.min(1.0f, signal.nearestDist());
        }

        // 2. Affect s2
        float affect = 0.0f;
        if (signal.salienceProfile() != null && !signal.salienceProfile().isNeutral()) {
            affect = 0.6f;
        }
        String textLower = signal.text() != null ? signal.text().toLowerCase() : "";
        if (textLower.contains("critical") || textLower.contains("panic") || textLower.contains("outage")
                || textLower.contains("catastrophic") || textLower.contains("emergency") || textLower.contains("love")
                || textLower.contains("hate") || textLower.contains("congratulations")) {
            affect = Math.max(affect, 0.95f);
        } else if (textLower.contains("error") || textLower.contains("fail") || textLower.contains("bug")) {
            affect = Math.max(affect, 0.80f);
        }

        // 3. Goal Relevance s3 (including pre-emptive interrupt salience for critical incidents)
        float goalRelevance = 0.0f;
        float[] vec = signal.vector();
        if (vec != null && !activeGoalEmbeddings.isEmpty()) {
            for (float[] goalVec : activeGoalEmbeddings) {
                if (goalVec.length == vec.length) {
                    float sim = CosineSimilarity.compute(vec, goalVec);
                    if (sim > goalRelevance) {
                        goalRelevance = sim;
                    }
                }
            }
            goalRelevance = Math.max(0.0f, Math.min(1.0f, goalRelevance));
        }
        if (textLower.contains("critical") || textLower.contains("panic") || textLower.contains("outage")
                || textLower.contains("catastrophic") || textLower.contains("emergency")) {
            goalRelevance = Math.max(goalRelevance, 0.90f);
        } else if (textLower.contains("goal") || textLower.contains("milestone") || textLower.contains("task")
                || textLower.contains("todo") || textLower.contains("plan") || textLower.contains("commit")) {
            goalRelevance = Math.max(goalRelevance, 0.85f);
        }

        // 4. Social Context s4
        float socialContext = 0.0f;
        if (signal.tags() != null) {
            for (String tag : signal.tags()) {
                if (tag.startsWith("user:") || tag.startsWith("author:") || tag.startsWith("speaker:")
                        || tag.startsWith("interlocutor:") || tag.contains("@")) {
                    socialContext = Math.max(socialContext, 0.80f);
                }
            }
        }
        if (textLower.contains("you promised") || textLower.contains("i commit") || textLower.contains("agreement")
                || textLower.contains("meeting with") || textLower.contains("conversation")) {
            socialContext = Math.max(socialContext, 0.90f);
        }

        // 5. Novelty s5
        float novelty = 0.1f;
        if (signal.nearestDist() > 0.0f) {
            novelty = Math.clamp(signal.nearestDist() / 2.0f, 0.0f, 1.0f);
        } else if (edm != null && edm.isSalientSpike()) {
            novelty = 0.80f;
        }

        return new CompositeImportanceSignals(surprise, affect, goalRelevance, socialContext, novelty);
    }

    /**
     * Resolves the normalized 5-dimensional weight vector for the given cognitive profile.
     *
     * @param profile active cognitive profile
     * @return 5-element normalized weight array
     */
    public float[] weightsForProfile(CognitiveProfile profile) {
        if (profile == null) {
            return baseWeights;
        }

        return switch (profile) {
            case DEBUGGING, PARANOID_SENTINEL ->
                // High surprise & affect
                    CompositeImportanceKernel.normalizeWeights(new float[]{0.35f, 0.25f, 0.20f, 0.10f, 0.10f});
            case EXPLORING, DIVERGENT ->
                // High novelty & surprise
                    CompositeImportanceKernel.normalizeWeights(new float[]{0.30f, 0.10f, 0.15f, 0.10f, 0.35f});
            case THE_EXECUTOR, SYSTEMATIZER ->
                // High goal relevance & social
                    CompositeImportanceKernel.normalizeWeights(new float[]{0.15f, 0.05f, 0.50f, 0.20f, 0.10f});
            case HIGHLY_SENSITIVE, RECALLING ->
                // High affect & social context
                    CompositeImportanceKernel.normalizeWeights(new float[]{0.10f, 0.40f, 0.15f, 0.30f, 0.05f});
            case CRITICAL ->
                // Heavy goal & affect
                    CompositeImportanceKernel.normalizeWeights(new float[]{0.25f, 0.25f, 0.35f, 0.10f, 0.05f});
            default ->
                    baseWeights;
        };
    }

    /**
     * Tests whether a composite importance score crosses the flashbulb threshold.
     *
     * @param importanceScore evaluated composite score
     * @return true if score meets or exceeds flashbulb threshold
     */
    public boolean isFlashbulb(float importanceScore) {
        return importanceScore >= config.importanceFlashbulbThreshold();
    }

    public AismeConfig config() {
        return config;
    }
}
