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
package com.spectrayan.spector.memory.dream.relay;

import com.spectrayan.spector.memory.kernel.shape.DistributedMemoryTensor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DreamGatesTest {

    @Test
    void testDreamingEnabledGate() {
        DreamConfig enabledConfig = DreamConfig.defaultConfig();
        DreamSignal signalEnabled = DreamSignal.builder()
                .config(enabledConfig)
                .build();
        assertThat(DreamGates.DREAMING_ENABLED.isSatisfiedBy(signalEnabled)).isTrue();

        DreamConfig disabledConfig = DreamConfig.disabled();
        DreamSignal signalDisabled = DreamSignal.builder()
                .config(disabledConfig)
                .build();
        assertThat(DreamGates.DREAMING_ENABLED.isSatisfiedBy(signalDisabled)).isFalse();
    }

    @Test
    void testHasSeedsAndFragmentsGate() {
        DreamSignal signal = DreamSignal.builder()
                .config(DreamConfig.defaultConfig())
                .seedMemoryIds(List.of("seed-1", "seed-2"))
                .build();

        assertThat(DreamGates.HAS_SEEDS.isSatisfiedBy(signal)).isTrue();
        assertThat(DreamGates.HAS_FRAGMENTS.isSatisfiedBy(signal)).isFalse();

        signal.addFragment(new SceneFragment("seed-1", 1, "Agent", FragmentRole.AGENT, new float[]{0.1f}, (byte) 10, 20));
        assertThat(DreamGates.HAS_FRAGMENTS.isSatisfiedBy(signal)).isTrue();
    }

    @Test
    void testLangevinEnabledGate() {
        DreamSignal signalWithoutDmt = DreamSignal.builder()
                .config(DreamConfig.defaultConfig())
                .build();
        assertThat(DreamGates.LANGEVIN_ENABLED.isSatisfiedBy(signalWithoutDmt)).isFalse();

        try (DistributedMemoryTensor dmt = new DistributedMemoryTensor(8)) {
            DreamSignal signalWithDmt = DreamSignal.builder()
                    .config(DreamConfig.defaultConfig())
                    .distributedMemoryTensor(dmt)
                    .build();
            assertThat(DreamGates.LANGEVIN_ENABLED.isSatisfiedBy(signalWithDmt)).isTrue();
        }
    }
}
