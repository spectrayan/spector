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
package com.spectrayan.spector.memory.pathway.wander.relay;

import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.cortex.ContinuityMemory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WanderGatesTest {

    @Test
    void isIdleGateEvaluation() {
        long now = System.currentTimeMillis();
        WanderSignal activeSignal = WanderSignal.builder()
                .lastActivityTimestampMs(now)
                .idleThresholdSeconds(60)
                .build();
        assertThat(WanderGates.IS_IDLE.isSatisfiedBy(activeSignal)).isFalse();

        WanderSignal idleSignal = WanderSignal.builder()
                .lastActivityTimestampMs(now - 120_000L)
                .idleThresholdSeconds(60)
                .build();
        assertThat(WanderGates.IS_IDLE.isSatisfiedBy(idleSignal)).isTrue();
    }

    @Test
    void dmnAndManifoldGatesEvaluation() {
        AismeConfig disabledConfig = AismeConfig.disabled();
        WanderSignal disabledSignal = WanderSignal.builder()
                .aismeConfig(disabledConfig)
                .build();
        assertThat(WanderGates.DMN_ENABLED.isSatisfiedBy(disabledSignal)).isFalse();
        assertThat(WanderGates.MANIFOLD_ENABLED.isSatisfiedBy(disabledSignal)).isFalse();

        AismeConfig enabledConfig = AismeConfig.defaultConfig();
        WanderSignal enabledSignal = WanderSignal.builder()
                .aismeConfig(enabledConfig)
                .build();
        assertThat(WanderGates.DMN_ENABLED.isSatisfiedBy(enabledSignal)).isTrue();
        assertThat(WanderGates.MANIFOLD_ENABLED.isSatisfiedBy(enabledSignal)).isTrue();
    }

    @Test
    void continuityGateEvaluation() {
        WanderSignal noMemorySignal = WanderSignal.builder()
                .aismeConfig(AismeConfig.defaultConfig())
                .build();
        assertThat(WanderGates.CONTINUITY_ENABLED.isSatisfiedBy(noMemorySignal)).isFalse();

        try (ContinuityMemory memory = ContinuityMemory.heap(10)) {
            WanderSignal memorySignal = WanderSignal.builder()
                    .aismeConfig(AismeConfig.defaultConfig())
                    .continuityMemory(memory)
                    .build();
            assertThat(WanderGates.CONTINUITY_ENABLED.isSatisfiedBy(memorySignal)).isTrue();
        }
    }
}
