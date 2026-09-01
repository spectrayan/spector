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
import com.spectrayan.spector.memory.aisme.privacy.DifferentialPrivacyEngine;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.remember.relay.RememberSignal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

/**
 * Unit tests for {@link DifferentialPrivacyRelay}.
 */
class DifferentialPrivacyRelayTest {

    @Test
    @DisplayName("transmit perturbs vector and scalar importance when privacy is enabled")
    void transmit_perturbsSignal() {
        AismeConfig config = AismeConfig.builder()
                .enablePrivacy(true)
                .privacyEpsilon(2.0f)
                .privacyDelta(1e-5f)
                .privacyClippingNorm(1.0f)
                .build();

        DifferentialPrivacyEngine engine = new DifferentialPrivacyEngine(config, new Random(42L));
        DifferentialPrivacyRelay relay = new DifferentialPrivacyRelay(config, engine);

        float[] originalVector = {0.3f, 0.4f, 0.5f};
        RememberSignal signal = RememberSignal.forCognitive(
                "mem-1",
                "Sensory observation",
                originalVector.clone(),
                MemoryType.EPISODIC,
                new String[]{"perception"},
                MemorySource.OBSERVED,
                null,
                SalienceProfile.NEUTRAL,
                (short) 1
        );
        signal.importance(0.8f);

        boolean passed = relay.transmit(signal);

        assertThat(passed).isTrue();
        assertThat(signal.privacyPerturbedVector()).isNotNull();
        assertThat(signal.vector()).isNotEqualTo(originalVector);
        assertThat(signal.importance()).isNotEqualTo(0.8f);
        assertThat(signal.importance()).isBetween(0.0f, 1.0f);
        assertThat(engine.consumedEpsilon()).isEqualTo(4.0); // 1 vector + 1 scalar
    }

    @Test
    @DisplayName("transmit leaves signal untouched when privacy is disabled")
    void transmit_leavesUntouchedWhenDisabled() {
        AismeConfig config = AismeConfig.builder()
                .enablePrivacy(false)
                .build();

        DifferentialPrivacyEngine engine = new DifferentialPrivacyEngine(config);
        DifferentialPrivacyRelay relay = new DifferentialPrivacyRelay(config, engine);

        float[] originalVector = {0.3f, 0.4f, 0.5f};
        RememberSignal signal = RememberSignal.forCognitive(
                "mem-2",
                "Sensory observation",
                originalVector.clone(),
                MemoryType.EPISODIC,
                new String[]{"perception"},
                MemorySource.OBSERVED,
                null,
                SalienceProfile.NEUTRAL,
                (short) 1
        );
        signal.importance(0.75f);

        relay.transmit(signal);

        assertThat(signal.privacyPerturbedVector()).isNull();
        assertThat(signal.vector()).containsExactly(originalVector);
        assertThat(signal.importance()).isEqualTo(0.75f);
        assertThat(engine.consumedEpsilon()).isEqualTo(0.0);
    }
}
