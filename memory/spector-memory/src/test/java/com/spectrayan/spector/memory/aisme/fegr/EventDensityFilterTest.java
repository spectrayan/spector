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
package com.spectrayan.spector.memory.aisme.fegr;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.CognitiveProfile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

/**
 * Unit tests for {@link EventDensityFilter}.
 */
class EventDensityFilterTest {

    private static final int DIMENSIONS = 16;
    private GenerativeSelfModel selfModel;
    private MentalStateTracker tracker;
    private EventDensityFilter filter;

    @BeforeEach
    void setUp() {
        float[] baseEmbedding = new float[DIMENSIONS];
        Arrays.fill(baseEmbedding, 0.5f);

        AgentSoul soul = AgentSoul.builder()
                .id(UUID.randomUUID().toString())
                .name("test-agent")
                .purposeEmbedding(baseEmbedding)
                .build();

        selfModel = GenerativeSelfModel.fromSoulAndProfile(soul, CognitiveProfile.BALANCED, DIMENSIONS);
        tracker = new MentalStateTracker(selfModel);
        filter = new EventDensityFilter(0.50f, 0.40f, 0.30f, 0.30f, 0.10f, 30.0f);
    }

    @Test
    @DisplayName("evaluate: Static background identical to prior produces low density and suppresses spike")
    void staticObservation_producesLowDensityAndNonSalient() {
        float[] staticObs = new float[DIMENSIONS];
        System.arraycopy(selfModel.priorMean(), 0, staticObs, 0, DIMENSIONS);

        EventDensityMetrics metrics = filter.evaluate(tracker.posterior(), selfModel, staticObs);

        assertThat(metrics.eventDensity()).isLessThan(0.50f);
        assertThat(metrics.isSalientSpike()).isFalse();
        assertThat(metrics.dynamicSamplingRateHz()).isLessThan(5.0f);
        assertThat(metrics.klDivergence()).isZero();
        assertThat(metrics.freeEnergyGradientNorm()).isZero();
        assertThat(metrics.surprisal()).isZero();
    }

    @Test
    @DisplayName("evaluate: Novel observation with high prediction error triggers salient spike and upscales sampling rate")
    void novelObservation_triggersSalientSpike() {
        float[] novelObs = new float[DIMENSIONS];
        for (int i = 0; i < DIMENSIONS; i++) {
            novelObs[i] = selfModel.priorMean()[i] + 2.0f; // Significant prediction error
        }

        EventDensityMetrics metrics = filter.evaluate(tracker.posterior(), selfModel, novelObs);

        assertThat(metrics.eventDensity()).isGreaterThan(0.50f);
        assertThat(metrics.isSalientSpike()).isTrue();
        assertThat(metrics.dynamicSamplingRateHz()).isGreaterThan(20.0f);
        assertThat(metrics.freeEnergyGradientNorm()).isGreaterThan(0.0f);
        assertThat(metrics.surprisal()).isGreaterThan(0.0f);
    }

    @Test
    @DisplayName("evaluate: Throws exception on null or dimension mismatched inputs")
    void invalidInputs_throwsValidationException() {
        assertThatThrownBy(() -> filter.evaluate(null, selfModel, new float[DIMENSIONS]))
                .isInstanceOf(SpectorValidationException.class);

        assertThatThrownBy(() -> filter.evaluate(tracker.posterior(), null, new float[DIMENSIONS]))
                .isInstanceOf(SpectorValidationException.class);

        assertThatThrownBy(() -> filter.evaluate(tracker.posterior(), selfModel, null))
                .isInstanceOf(SpectorValidationException.class);

        assertThatThrownBy(() -> filter.evaluate(tracker.posterior(), selfModel, new float[8]))
                .isInstanceOf(SpectorValidationException.class);
    }
}
