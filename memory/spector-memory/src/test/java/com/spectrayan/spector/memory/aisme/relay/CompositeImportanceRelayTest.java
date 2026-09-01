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

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import static org.assertj.core.api.Assertions.assertThat;

import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.aisme.fegr.EventDensityMetrics;
import com.spectrayan.spector.memory.aisme.importance.CompositeImportanceScorer;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.remember.relay.RememberSignal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CompositeImportanceRelay}.
 */
class CompositeImportanceRelayTest {

    private AismeConfig config;
    private CompositeImportanceScorer scorer;
    private CompositeImportanceRelay relay;

    @BeforeEach
    void setUp() {
        config = AismeConfig.builder()
                .enabled(true)
                .enableImportance(true)
                .importanceFlashbulbThreshold(0.85f)
                .build();
        scorer = new CompositeImportanceScorer(config);
        relay = new CompositeImportanceRelay(config, scorer);
    }

    @Test
    void relayName_returnsCompositeImportance() {
        assertThat(relay.relayName()).isEqualTo(RelayNames.COMPOSITE_IMPORTANCE);
    }

    @Test
    void transmit_computesImportanceAndTagsFlashbulb() {
        RememberSignal signal = RememberSignal.forCognitive(
                "mem-1",
                "Critical database error during emergency conversation with @lead: panic!",
                new float[]{1.0f, 0.0f, 0.0f},
                MemoryType.EPISODIC,
                new String[]{"user:lead", "critical"},
                MemorySource.OBSERVED,
                null,
                SalienceProfile.NEUTRAL,
                (short) 1
        );
        signal.eventDensityMetrics(new EventDensityMetrics(2.0f, 0.9f, 0.95f, 0.90f, true, 30.0f));
        signal.nearestDist(1.8f);

        boolean transmitted = relay.transmit(signal);

        assertThat(transmitted).isTrue();
        assertThat(signal.importance()).isGreaterThanOrEqualTo(0.85f);
        assertThat(signal.isFlashbulb()).isTrue();
    }

    @Test
    void transmit_lowSalience_doesNotTagFlashbulb() {
        RememberSignal signal = RememberSignal.forCognitive(
                "mem-2",
                "routine log statement with normal context",
                new float[]{0.1f, 0.1f, 0.1f},
                MemoryType.EPISODIC,
                new String[]{"logs"},
                MemorySource.OBSERVED,
                null,
                SalienceProfile.NEUTRAL,
                (short) 1
        );
        signal.nearestDist(0.1f);

        boolean transmitted = relay.transmit(signal);

        assertThat(transmitted).isTrue();
        assertThat(signal.importance()).isLessThan(0.85f);
        assertThat(signal.isFlashbulb()).isFalse();
    }

    @Test
    void transmit_disabledImportance_leavesOriginalImportance() {
        AismeConfig disabledConfig = AismeConfig.builder()
                .enabled(true)
                .enableImportance(false)
                .build();
        CompositeImportanceScorer disabledScorer = new CompositeImportanceScorer(disabledConfig);
        CompositeImportanceRelay disabledRelay = new CompositeImportanceRelay(disabledConfig, disabledScorer);

        RememberSignal signal = RememberSignal.forCognitive(
                "mem-3",
                "sample memory",
                new float[]{0.5f, 0.5f},
                MemoryType.EPISODIC,
                new String[]{"tag"},
                MemorySource.OBSERVED,
                null,
                SalienceProfile.NEUTRAL,
                (short) 1
        );
        signal.importance(0.65f);

        boolean transmitted = disabledRelay.transmit(signal);

        assertThat(transmitted).isTrue();
        assertThat(signal.importance()).isEqualTo(0.65f);
        assertThat(signal.isFlashbulb()).isFalse();
    }
}
