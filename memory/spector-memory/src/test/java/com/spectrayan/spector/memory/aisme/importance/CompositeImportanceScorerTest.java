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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.aisme.fegr.EventDensityMetrics;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.remember.relay.RememberSignal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Unit tests for {@link CompositeImportanceScorer}.
 */
class CompositeImportanceScorerTest {

    private AismeConfig config;
    private CompositeImportanceScorer scorer;

    @BeforeEach
    void setUp() {
        config = AismeConfig.builder()
                .enabled(true)
                .enableImportance(true)
                .importanceWeightSurprise(0.20f)
                .importanceWeightAffect(0.20f)
                .importanceWeightGoal(0.20f)
                .importanceWeightSocial(0.20f)
                .importanceWeightNovelty(0.20f)
                .importanceFlashbulbThreshold(0.85f)
                .build();
        scorer = new CompositeImportanceScorer(config);
    }

    @Test
    void evaluate_balancedProfile_computesArithmeticMean() {
        CompositeImportanceSignals signals = new CompositeImportanceSignals(0.5f, 0.5f, 0.5f, 0.5f, 0.5f);
        float score = scorer.evaluate(signals, CognitiveProfile.BALANCED);

        assertThat(score).isCloseTo(0.50f, within(1e-5f));
        assertThat(scorer.isFlashbulb(score)).isFalse();
    }

    @Test
    void evaluate_highSalience_triggersFlashbulb() {
        CompositeImportanceSignals signals = new CompositeImportanceSignals(0.9f, 0.95f, 0.85f, 0.90f, 0.80f);
        float score = scorer.evaluate(signals, CognitiveProfile.BALANCED);

        assertThat(score).isGreaterThanOrEqualTo(0.85f);
        assertThat(scorer.isFlashbulb(score)).isTrue();
    }

    @Test
    void evaluate_profileAdaptation_altersWeights() {
        // High affect & social, low surprise & novelty
        CompositeImportanceSignals emotionalSignal = new CompositeImportanceSignals(0.1f, 0.9f, 0.2f, 0.8f, 0.1f);

        float balancedScore = scorer.evaluate(emotionalSignal, CognitiveProfile.BALANCED);
        float sensitiveScore = scorer.evaluate(emotionalSignal, CognitiveProfile.HIGHLY_SENSITIVE);

        // HIGHLY_SENSITIVE amplifies affect and social context
        assertThat(sensitiveScore).isGreaterThan(balancedScore);
    }

    @Test
    void extractSignals_fromRememberSignal_extractsAllDimensions() {
        float[] queryVec = new float[]{1.0f, 0.0f, 0.0f, 0.0f};
        float[] goalVec = new float[]{0.9f, 0.1f, 0.0f, 0.0f};
        scorer.setGoalEmbeddings(List.of(goalVec));

        RememberSignal signal = RememberSignal.forCognitive(
                "mem-1",
                "Critical outage occurred during conversation with @lead: user:alice said error panic",
                queryVec,
                MemoryType.EPISODIC,
                new String[]{"user:alice", "outage"},
                MemorySource.OBSERVED,
                null,
                SalienceProfile.NEUTRAL,
                (short) 1
        );
        signal.eventDensityMetrics(new EventDensityMetrics(1.5f, 0.8f, 0.85f, 0.90f, true, 25.0f));

        CompositeImportanceSignals extracted = scorer.extractSignals(signal);

        assertThat(extracted.surprise()).isEqualTo(0.85f);
        assertThat(extracted.affect()).isGreaterThanOrEqualTo(0.85f);
        assertThat(extracted.socialContext()).isGreaterThanOrEqualTo(0.70f);
        assertThat(extracted.goalRelevance()).isGreaterThanOrEqualTo(0.75f);
        assertThat(extracted.novelty()).isGreaterThanOrEqualTo(0.80f);

        float score = scorer.evaluateSignal(signal, CognitiveProfile.DEBUGGING);
        assertThat(score).isGreaterThanOrEqualTo(0.80f);
    }

    @Test
    void evaluate_disabledConfig_returnsDefault() {
        AismeConfig disabledConfig = AismeConfig.builder()
                .enabled(true)
                .enableImportance(false)
                .build();
        CompositeImportanceScorer disabledScorer = new CompositeImportanceScorer(disabledConfig);

        CompositeImportanceSignals signals = new CompositeImportanceSignals(0.9f, 0.9f, 0.9f, 0.9f, 0.9f);
        float score = disabledScorer.evaluate(signals, CognitiveProfile.BALANCED);

        assertThat(score).isEqualTo(0.5f);
    }
}
