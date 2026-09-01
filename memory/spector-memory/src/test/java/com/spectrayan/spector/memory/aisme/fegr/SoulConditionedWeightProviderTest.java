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

import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoringRegime;
import com.spectrayan.spector.memory.recall.relay.RecallSignal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/**
 * Unit and property tests for {@link SoulConditionedWeightProvider} (MR-03).
 */
class SoulConditionedWeightProviderTest {

    @Test
    @DisplayName("MR-03: Null soul falls back to GENERIC regime and default weights")
    void nullSoulReturnsGenericRegime() {
        SoulConditionedWeightProvider provider = new SoulConditionedWeightProvider(null);
        RecallSignal signal = RecallSignal.forTextQuery("test", RecallOptions.builder().build());

        SoulConditionedWeightProvider.FersWeights weights = provider.provideWeights(signal);

        assertThat(weights.regime()).isEqualTo(ScoringRegime.GENERIC);
        assertThat(weights.alpha()).isEqualTo(0.5f);
        assertThat(weights.beta()).isEqualTo(0.35f);
        assertThat(weights.gamma()).isEqualTo(0.15f);
        assertThat(weights.alpha() + weights.beta() + weights.gamma()).isCloseTo(1.0f, offset(1e-4f));
    }

    @Test
    @DisplayName("MR-03: Curious/Analytical agent soul boosts epistemic alpha weight in SOUL_CONDITIONED regime")
    void curiousSoulBoostsAlpha() {
        AgentSoul soul = AgentSoul.builder()
                .id("agent-researcher")
                .name("Curious Jarvis")
                .personality("Deeply curious, analytical researcher")
                .coreValues(List.of("Truth", "Thoroughness"))
                .build();

        SoulConditionedWeightProvider provider = new SoulConditionedWeightProvider(soul, 1.0f); // 1.0 = instant target without EMA
        RecallSignal signal = RecallSignal.forTextQuery("investigation", RecallOptions.builder().build());

        SoulConditionedWeightProvider.FersWeights weights = provider.provideWeights(signal);

        assertThat(weights.regime()).isEqualTo(ScoringRegime.SOUL_CONDITIONED);
        assertThat(weights.alpha()).isGreaterThan(0.40f);
        assertThat(weights.alpha() + weights.beta() + weights.gamma()).isCloseTo(1.0f, offset(1e-4f));
    }

    @Test
    @DisplayName("MR-03: EMA hysteresis smoothly transitions weights across queries")
    void emaHysteresisDampsTransitions() {
        AgentSoul soul = AgentSoul.builder()
                .id("agent-adaptive")
                .name("Adaptive")
                .personality("Goal focused")
                .build();

        // 0.20 EMA smoothing
        SoulConditionedWeightProvider provider = new SoulConditionedWeightProvider(soul, 0.20f);

        // Turn 1: normal query
        RecallSignal signal1 = RecallSignal.forTextQuery("query 1", RecallOptions.builder().build());
        SoulConditionedWeightProvider.FersWeights w1 = provider.provideWeights(signal1);

        // Turn 2: sudden switch to exploring profile
        RecallSignal signal2 = RecallSignal.forTextQuery("query 2",
                RecallOptions.builder().profile(CognitiveProfile.EXPLORING).build());
        SoulConditionedWeightProvider.FersWeights w2 = provider.provideWeights(signal2);

        // Turn 3: continue with exploring profile
        SoulConditionedWeightProvider.FersWeights w3 = provider.provideWeights(signal2);

        // w2 should be between w1 and w3 due to EMA damping
        assertThat(w2.alpha()).isBetween(Math.min(w1.alpha(), w3.alpha()), Math.max(w1.alpha(), w3.alpha()));
        assertThat(w1.alpha() + w1.beta() + w1.gamma()).isCloseTo(1.0f, offset(1e-4f));
        assertThat(w2.alpha() + w2.beta() + w2.gamma()).isCloseTo(1.0f, offset(1e-4f));
        assertThat(w3.alpha() + w3.beta() + w3.gamma()).isCloseTo(1.0f, offset(1e-4f));
    }
}
