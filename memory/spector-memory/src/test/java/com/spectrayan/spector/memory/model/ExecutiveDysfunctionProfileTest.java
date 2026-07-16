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
package com.spectrayan.spector.memory.model;

import static org.assertj.core.api.Assertions.*;

import com.spectrayan.spector.memory.CognitiveProfileConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CognitiveProfile#EXECUTIVE_DYSFUNCTION} — associative,
 * context-primed recall for prefrontal cortex hypoactivation.
 */
@DisplayName("CognitiveProfile.EXECUTIVE_DYSFUNCTION")
class ExecutiveDysfunctionProfileTest {

    private static final CognitiveProfile PROFILE = CognitiveProfile.EXECUTIVE_DYSFUNCTION;

    // ══════════════════════════════════════════════════════════════
    // Enum existence
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("EXECUTIVE_DYSFUNCTION exists in the CognitiveProfile enum")
    void profileExistsInEnum() {
        assertThat(PROFILE).isNotNull();
        assertThat(CognitiveProfile.valueOf("EXECUTIVE_DYSFUNCTION")).isEqualTo(PROFILE);
    }

    // ══════════════════════════════════════════════════════════════
    // Scoring parameters
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("has correct alpha=0.3, beta=0.7, gamma=0.1 scoring weights")
    void profileHasCorrectAlphaBetaGamma() {
        assertThat(PROFILE.alpha()).isCloseTo(0.3f, within(1e-6f));
        assertThat(PROFILE.beta()).isCloseTo(0.7f, within(1e-6f));
        assertThat(PROFILE.gamma()).isCloseTo(0.1f, within(1e-6f));
    }

    // ══════════════════════════════════════════════════════════════
    // Neurodivergent group membership
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("is included in the NEURODIVERGENT profile group")
    void profileIsInNeurodivergentGroup() {
        var config = CognitiveProfileConfig.withNeurodivergent();
        assertThat(config.isEnabled(PROFILE))
                .as("EXECUTIVE_DYSFUNCTION should be in neurodivergent profiles")
                .isTrue();
    }

    // ══════════════════════════════════════════════════════════════
    // applyTo() behavior on RecallOptions.Builder
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("applyTo() sets scoringMode to ASSOCIATIVE")
    void applyToSetsScoringModeAssociative() {
        RecallOptions opts = RecallOptions.builder()
                .profile(PROFILE)
                .build();

        assertThat(opts.scoringMode()).isEqualTo(ScoringMode.ASSOCIATIVE);
    }

    @Test
    @DisplayName("applyTo() enables lateralMode")
    void applyToEnablesLateralMode() {
        RecallOptions opts = RecallOptions.builder()
                .profile(PROFILE)
                .build();

        assertThat(opts.lateralMode()).isTrue();
    }

    @Test
    @DisplayName("applyTo() sets aggressive graph expansion threshold (0.80)")
    void applyToSetsAggressiveGraphExpansion() {
        RecallOptions opts = RecallOptions.builder()
                .profile(PROFILE)
                .build();

        assertThat(opts.graphExpansionThreshold())
                .isCloseTo(0.80f, within(1e-6f));
    }

    // ══════════════════════════════════════════════════════════════
    // Valence range (from constructor: Byte.MIN_VALUE..Byte.MAX_VALUE)
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("has full valence range (MIN_VALUE to MAX_VALUE)")
    void fullValenceRange() {
        assertThat(PROFILE.minValence()).isEqualTo(Byte.MIN_VALUE);
        assertThat(PROFILE.maxValence()).isEqualTo(Byte.MAX_VALUE);
    }
}
