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

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.memory.aisme.fegr.EventDensityFilter;
import com.spectrayan.spector.memory.aisme.fegr.GenerativeSelfModel;
import com.spectrayan.spector.memory.aisme.fegr.MentalStateTracker;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.remember.relay.RememberSignal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

/**
 * Unit tests for {@link EventDensityGatingRelay}.
 */
class EventDensityGatingRelayTest {

    private static final int DIMENSIONS = 16;
    private EventDensityFilter filter;
    private MentalStateTracker tracker;
    private GenerativeSelfModel selfModel;

    @BeforeEach
    void setUp() {
        float[] base = new float[DIMENSIONS];
        Arrays.fill(base, 0.2f);

        AgentSoul soul = AgentSoul.builder()
                .id(UUID.randomUUID().toString())
                .name("test-entity")
                .purposeEmbedding(base)
                .build();

        selfModel = GenerativeSelfModel.fromSoulAndProfile(soul, CognitiveProfile.BALANCED, DIMENSIONS);
        tracker = new MentalStateTracker(selfModel);
        filter = new EventDensityFilter(0.50f, 0.40f, 0.30f, 0.30f, 0.10f, 30.0f);
    }

    @Test
    @DisplayName("transmit: Aborts transmission on static/redundant sensory frame when abortOnGated=true")
    void transmit_gatesRedundantSensoryFrame() {
        EventDensityGatingRelay relay = new EventDensityGatingRelay(filter, tracker, true);
        assertThat(relay.relayName()).isEqualTo(RelayNames.EVENT_DENSITY_GATING);

        float[] staticVector = new float[DIMENSIONS];
        System.arraycopy(selfModel.priorMean(), 0, staticVector, 0, DIMENSIONS);

        RememberSignal signal = RememberSignal.forCognitive(
                "mem-1", "ambient silence", staticVector, MemoryType.SEMANTIC,
                new String[]{"ambient"}, null, null, SalienceProfile.NEUTRAL, (short) 1
        );

        boolean passed = relay.transmit(signal);

        assertThat(passed).isFalse();
        assertThat(signal.isGated()).isTrue();
        assertThat(signal.eventDensityMetrics()).isNotNull();
        assertThat(signal.eventDensityMetrics().isSalientSpike()).isFalse();
    }

    @Test
    @DisplayName("transmit: Allows transmission and marks un-gated on high-density novelty spike")
    void transmit_allowsSalientSpike() {
        EventDensityGatingRelay relay = new EventDensityGatingRelay(filter, tracker, true);

        float[] novelVector = new float[DIMENSIONS];
        for (int i = 0; i < DIMENSIONS; i++) {
            novelVector[i] = selfModel.priorMean()[i] + 3.0f;
        }

        RememberSignal signal = RememberSignal.forCognitive(
                "mem-2", "critical sudden anomaly", novelVector, MemoryType.SEMANTIC,
                new String[]{"anomaly"}, null, null, SalienceProfile.NEUTRAL, (short) 1
        );

        boolean passed = relay.transmit(signal);

        assertThat(passed).isTrue();
        assertThat(signal.isGated()).isFalse();
        assertThat(signal.eventDensityMetrics()).isNotNull();
        assertThat(signal.eventDensityMetrics().isSalientSpike()).isTrue();
    }
}
