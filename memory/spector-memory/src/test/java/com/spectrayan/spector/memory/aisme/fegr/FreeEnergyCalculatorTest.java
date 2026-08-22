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
 * Unit tests for {@link FreeEnergyCalculator}.
 */
class FreeEnergyCalculatorTest {

    private FreeEnergyCalculator calculator;
    private GenerativeSelfModel selfModel;

    @BeforeEach
    void setUp() {
        calculator = new FreeEnergyCalculator(2.0f);
        selfModel = GenerativeSelfModel.fromSoulAndProfile(null, CognitiveProfile.BALANCED, 2);
    }

    @Test
    void calculateFreeEnergy_producesPositiveScalar() {
        MentalStatePosterior posterior = selfModel.createInitialPosterior(1000L);
        float[] observation = {1.0f, -1.0f};

        float fe = calculator.calculateFreeEnergy(posterior, selfModel, observation);
        assertThat(fe).isGreaterThan(0.0f);
    }

    @Test
    void calculateFreeEnergyReduction_candidateExplainingObservation_reducesFreeEnergy() {
        MentalStatePosterior posterior = selfModel.createInitialPosterior(1000L);
        float[] observation = {2.0f, 2.0f};

        // Candidate matching the observation closely should reduce uncertainty/error
        float[] candidateExplaining = {2.0f, 2.0f};
        float deltaFExplaining = calculator.calculateFreeEnergyReduction(
                posterior, selfModel, observation, candidateExplaining, null);

        // Candidate pointing completely away
        float[] candidateOpposite = {-5.0f, -5.0f};
        float deltaFOpposite = calculator.calculateFreeEnergyReduction(
                posterior, selfModel, observation, candidateOpposite, null);

        // Explaining candidate should yield higher free-energy reduction
        assertThat(deltaFExplaining).isGreaterThan(deltaFOpposite);
    }

    @Test
    void calculateFersScore_combinesAllComponents() {
        float baseSim = 0.8f;
        float deltaF = 1.5f;
        float affectiveResonance = 0.9f;

        float fers = FreeEnergyCalculator.calculateFersScore(
                baseSim, deltaF, affectiveResonance, 0.5f, 0.35f, 0.15f);

        // base: 0.5 * 0.8 = 0.4
        // deltaF sigmoid: 1 / (1 + exp(-1.5)) ≈ 0.81757 * 0.35 ≈ 0.28615
        // affective: 0.15 * 0.9 = 0.135
        // total ≈ 0.82115
        assertThat(fers).isCloseTo(0.82115f, within(0.01f));
    }
}
