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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.spectrayan.spector.memory.model.CognitiveProfile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MentalStateTracker}.
 */
class MentalStateTrackerTest {

    private MentalStateTracker tracker;
    private GenerativeSelfModel selfModel;

    @BeforeEach
    void setUp() {
        selfModel = GenerativeSelfModel.fromSoulAndProfile(null, CognitiveProfile.BALANCED, 2);
        tracker = new MentalStateTracker(selfModel);
    }

    @Test
    void initialPosterior_matchesPrior() {
        MentalStatePosterior p = tracker.currentPosterior();
        assertThat(p.version()).isEqualTo(0);
        assertThat(p.mean()).containsExactly(selfModel.priorMean());
    }

    @Test
    void updateWithObservation_shiftsMeanAndIncreasesPrecision() {
        float[] observation = {2.0f, -2.0f};
        tracker.updateWithObservation(observation, 1000L);

        MentalStatePosterior updated = tracker.currentPosterior();
        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.timestampMs()).isEqualTo(1000L);
        assertThat(updated.mean()[0]).isGreaterThan(0.0f);
        assertThat(updated.mean()[1]).isLessThan(0.0f);
        assertThat(updated.precision()[0]).isGreaterThan(selfModel.priorPrecision()[0]);
    }

    @Test
    void decay_driftsMeanBackTowardsPrior() {
        float[] observation = {5.0f, 5.0f};
        tracker.updateWithObservation(observation, 1000L);

        float shiftedMean = tracker.currentPosterior().mean()[0];

        // Apply decay
        tracker.decay(2000L, 0.5f);

        float decayedMean = tracker.currentPosterior().mean()[0];
        assertThat(decayedMean).isLessThan(shiftedMean);
    }

    @Test
    void reset_reinitializesToPrior() {
        float[] observation = {10.0f, 10.0f};
        tracker.updateWithObservation(observation, 1000L);
        tracker.reset();

        MentalStatePosterior reset = tracker.currentPosterior();
        assertThat(reset.version()).isEqualTo(0);
        assertThat(reset.mean()).containsExactly(selfModel.priorMean());
    }

    @Test
    void adaptPriorMean_shiftsPriorMeanTowardsExperientialCentroid() {
        float[] centroid = {4.0f, 6.0f};
        tracker.adaptPriorMean(centroid, 0.1f);

        assertThat(tracker.selfModel().priorMean()[0]).isCloseTo(0.4f, within(0.01f));
        assertThat(tracker.selfModel().priorMean()[1]).isCloseTo(0.6f, within(0.01f));
    }
}
