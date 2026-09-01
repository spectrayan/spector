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
package com.spectrayan.spector.memory.aisme.homeostasis;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link HomeostaticCore} Neural ODE engine.
 */
class HomeostaticCoreTest {

    private static final int DIM = 5; // 3 VAD + 2 interoceptive channels

    private HomeostaticCore core;

    @BeforeEach
    void setUp() {
        // Simple diagonal A matrix with negative decay (stable equilibrium)
        float[][] aPerson = new float[DIM][DIM];
        for (int i = 0; i < DIM; i++) {
            aPerson[i][i] = -0.5f;
        }

        // Identity-scaled B and C
        float[][] bInput = new float[DIM][DIM];
        float[][] cRecall = new float[DIM][DIM];
        for (int i = 0; i < DIM; i++) {
            bInput[i][i] = 0.2f;
            cRecall[i][i] = 0.1f;
        }

        // Low noise for deterministic-ish tests
        float[] sigma = new float[DIM];
        for (int i = 0; i < DIM; i++) {
            sigma[i] = 0.001f;
        }

        core = new HomeostaticCore(aPerson, bInput, cRecall, sigma);
    }

    @Test
    void initialState_isNeutral() {
        InteroceptiveState state = core.currentState();
        assertThat(state).isEqualTo(InteroceptiveState.NEUTRAL);
    }

    @Test
    void stepWithZeroInput_remainsNearNeutral() {
        float[] zeroInput = new float[DIM];
        for (int i = 0; i < 10; i++) {
            core.step(zeroInput, 0.1f);
        }
        InteroceptiveState state = core.currentState();
        // Should stay near zero with only tiny noise
        assertThat(state.valence()).isCloseTo(0.0f, within(0.1f));
        assertThat(state.arousal()).isCloseTo(0.0f, within(0.1f));
    }

    @Test
    void stepWithPositiveInput_increasesValence() {
        float[] input = new float[DIM];
        input[0] = 1.0f; // Strong positive valence input

        // Apply several steps
        for (int i = 0; i < 20; i++) {
            core.step(input, 0.1f);
        }
        InteroceptiveState state = core.currentState();
        assertThat(state.valence()).isGreaterThan(0.0f);
    }

    @Test
    void stepWithRecallInfluence_affectsState() {
        float[] input = new float[DIM];
        float[] recall = new float[DIM];
        recall[1] = 1.0f; // High arousal recall

        for (int i = 0; i < 20; i++) {
            core.step(input, recall, 0.1f);
        }
        InteroceptiveState state = core.currentState();
        assertThat(state.arousal()).isGreaterThan(0.0f);
    }

    @Test
    void stateValues_areClamped() {
        // Extreme input to push state past limits
        float[] extremeInput = new float[DIM];
        for (int i = 0; i < DIM; i++) {
            extremeInput[i] = 100.0f;
        }

        for (int i = 0; i < 100; i++) {
            core.step(extremeInput, 0.5f);
        }
        InteroceptiveState state = core.currentState();
        assertThat(state.valence()).isBetween(-1.0f, 1.0f);
        assertThat(state.arousal()).isBetween(-1.0f, 1.0f);
        assertThat(state.dominance()).isBetween(-1.0f, 1.0f);
    }

    @Test
    void reset_restoresToNeutral() {
        float[] input = {1.0f, 1.0f, 1.0f, 1.0f, 1.0f};
        core.step(input, 0.1f);
        core.reset();
        assertThat(core.currentState()).isEqualTo(InteroceptiveState.NEUTRAL);
    }

    @Test
    void resetToState_restoresToSpecifiedState() {
        InteroceptiveState target = new InteroceptiveState(
                0.5f, -0.3f, 0.2f, new float[]{0.1f, -0.1f}, 5000L, 42);
        core.reset(target);
        assertThat(core.currentState().valence()).isEqualTo(0.5f);
        assertThat(core.currentState().arousal()).isEqualTo(-0.3f);
        assertThat(core.currentState().version()).isEqualTo(42);
    }

    @Test
    void versionIncrements_onEachStep() {
        float[] input = new float[DIM];
        core.step(input, 0.1f);
        int v1 = core.currentState().version();
        core.step(input, 0.1f);
        int v2 = core.currentState().version();
        assertThat(v2).isGreaterThan(v1);
    }

    @Test
    void decayTowardEquilibrium_withNegativeA() {
        // Set initial state away from equilibrium
        core.reset(new InteroceptiveState(0.8f, 0.8f, 0.8f, new float[]{0.8f, 0.8f}, 0L, 0));
        float[] zeroInput = new float[DIM];

        // Run many steps — with negative A diagonal, state should decay toward 0
        for (int i = 0; i < 200; i++) {
            core.step(zeroInput, 0.05f);
        }
        InteroceptiveState state = core.currentState();
        assertThat(Math.abs(state.valence())).isLessThan(0.3f);
    }
}
