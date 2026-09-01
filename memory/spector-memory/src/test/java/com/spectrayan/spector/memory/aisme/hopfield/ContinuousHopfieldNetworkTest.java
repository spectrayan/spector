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
package com.spectrayan.spector.memory.aisme.hopfield;

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
 * Unit tests for {@link ContinuousHopfieldNetwork}.
 */
class ContinuousHopfieldNetworkTest {

    private ContinuousHopfieldNetwork network;

    @BeforeEach
    void setUp() {
        network = new ContinuousHopfieldNetwork(10, 1e-4f);
    }

    @Test
    void retrieveAttractor_emptyPatterns_returnsDiffuseState() {
        float[] query = {1.0f, 0.0f};
        float[][] patterns = new float[0][];

        AttractorState state = network.retrieveAttractor(query, patterns, 5.0f);
        assertThat(state.type()).isEqualTo(AttractorType.DIFFUSE);
        assertThat(state.patternCount()).isZero();
        assertThat(state.attractorVector()).containsExactly(query);
    }

    @Test
    void retrieveAttractor_singlePattern_convergesToFixedPoint() {
        float[] query = {0.8f, 0.2f};
        float[][] patterns = {
                {1.0f, 0.0f}
        };

        AttractorState state = network.retrieveAttractor(query, patterns, 5.0f);
        assertThat(state.type()).isEqualTo(AttractorType.FIXED_POINT);
        assertThat(state.attentionWeights()[0]).isEqualTo(1.0f);
        assertThat(state.attractorVector()[0]).isCloseTo(1.0f, within(1e-4f));
        assertThat(state.attractorVector()[1]).isCloseTo(0.0f, within(1e-4f));
    }

    @Test
    void retrieveAttractor_highBeta_convergesToClosestFixedPoint() {
        float[] query = {0.9f, 0.1f};
        float[][] patterns = {
                {1.0f, 0.0f}, // closer
                {0.0f, 1.0f}  // orthogonal
        };

        // High beta (10.0) -> sharp retrieval
        AttractorState state = network.retrieveAttractor(query, patterns, 10.0f);
        assertThat(state.type()).isEqualTo(AttractorType.FIXED_POINT);
        assertThat(state.attentionWeights()[0]).isGreaterThan(0.95f);
        assertThat(state.attractorVector()[0]).isGreaterThan(0.95f);
    }

    @Test
    void retrieveAttractor_lowBeta_settlesInMetastableBlend() {
        float[] query = {0.6f, 0.6f};
        float[][] patterns = {
                {1.0f, 0.0f},
                {0.0f, 1.0f}
        };

        // Low beta (0.5) -> blended associative superposition
        AttractorState state = network.retrieveAttractor(query, patterns, 0.5f);
        assertThat(state.type()).isIn(AttractorType.METASTABLE, AttractorType.DIFFUSE);
        assertThat(state.attentionWeights()[0]).isCloseTo(state.attentionWeights()[1], within(0.2f));
        assertThat(state.attractorVector()[0]).isGreaterThan(0.2f);
        assertThat(state.attractorVector()[1]).isGreaterThan(0.2f);
    }
}
