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
package com.spectrayan.spector.memory;

import com.spectrayan.spector.memory.model.*;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RecallOptions#validate()} and {@link RecallOptions#parseProfile(String)}.
 */
class RecallOptionsValidationTest {

    // ══════════════════════════════════════════════════════════════
    // validate() — conflict detection
    // ══════════════════════════════════════════════════════════════

    @Test
    void defaultOptionsHaveNoWarnings() {
        List<String> warnings = RecallOptions.DEFAULT.validate();
        assertThat(warnings).isEmpty();
    }

    @Test
    void balancedProfileHasNoWarnings() {
        RecallOptions options = RecallOptions.builder()
                .profile(CognitiveProfile.BALANCED)
                .build();
        assertThat(options.validate()).isEmpty();
    }

    @Test
    void lateralModeWithHighStrictnessWarns() {
        RecallOptions options = RecallOptions.builder()
                .lateralMode(true)
                .strictnessCoefficient(10.0f)
                .build();
        List<String> warnings = options.validate();

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0)).contains("lateralMode").contains("strictness");
    }

    @Test
    void lateralModeWithLowStrictnessNoWarning() {
        RecallOptions options = RecallOptions.builder()
                .lateralMode(true)
                .strictnessCoefficient(2.0f)
                .build();
        assertThat(options.validate()).isEmpty();
    }

    @Test
    void hyperfocusPlusLateralWarns() {
        RecallOptions options = RecallOptions.builder()
                .hyperfocusMask("database", "deadlock")
                .lateralMode(true)
                .build();
        List<String> warnings = options.validate();

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0)).contains("hyperfocus").contains("lateral");
    }

    @Test
    void unnormalizedAlphaBetaWarns() {
        RecallOptions options = RecallOptions.builder()
                .alpha(0.8f)
                .beta(0.8f)  // sum = 1.6
                .build();
        List<String> warnings = options.validate();

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0)).contains("alpha").contains("beta").contains("1.0");
    }

    @Test
    void normalizedAlphaBetaNoWarning() {
        RecallOptions options = RecallOptions.builder()
                .alpha(0.7f)
                .beta(0.3f)
                .build();
        assertThat(options.validate()).isEmpty();
    }

    @Test
    void multipleConflictsReportAll() {
        RecallOptions options = RecallOptions.builder()
                .lateralMode(true)
                .strictnessCoefficient(10.0f)
                .hyperfocusMask("test")
                .alpha(0.9f)
                .beta(0.9f)
                .build();
        List<String> warnings = options.validate();

        // Should report all three: lateral+strictness, hyperfocus+lateral, alpha+beta
        assertThat(warnings).hasSize(3);
    }

    // ══════════════════════════════════════════════════════════════
    // parseProfile() — string to CognitiveProfile
    // ══════════════════════════════════════════════════════════════

    @Test
    void parseProfileUpperCase() {
        assertThat(RecallOptions.parseProfile("DEBUGGING")).isEqualTo(CognitiveProfile.DEBUGGING);
    }

    @Test
    void parseProfileLowerCase() {
        assertThat(RecallOptions.parseProfile("debugging")).isEqualTo(CognitiveProfile.DEBUGGING);
    }

    @Test
    void parseProfileMixedCase() {
        assertThat(RecallOptions.parseProfile("Debugging")).isEqualTo(CognitiveProfile.DEBUGGING);
    }

    @Test
    void parseProfileWithWhitespace() {
        assertThat(RecallOptions.parseProfile("  EXPLORING  ")).isEqualTo(CognitiveProfile.EXPLORING);
    }

    @Test
    void parseProfileNullReturnsNull() {
        assertThat(RecallOptions.parseProfile(null)).isNull();
    }

    @Test
    void parseProfileEmptyReturnsNull() {
        assertThat(RecallOptions.parseProfile("")).isNull();
    }

    @Test
    void parseProfileBlankReturnsNull() {
        assertThat(RecallOptions.parseProfile("   ")).isNull();
    }

    @Test
    void parseProfileUnknownReturnsNull() {
        assertThat(RecallOptions.parseProfile("DOES_NOT_EXIST")).isNull();
    }

    @Test
    void parseProfileAllProfilesWork() {
        for (CognitiveProfile p : CognitiveProfile.values()) {
            assertThat(RecallOptions.parseProfile(p.name()))
                    .as("Should parse: " + p.name())
                    .isEqualTo(p);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Profile integration with validate()
    // ══════════════════════════════════════════════════════════════

    @Test
    void debuggingProfileHasNoConflicts() {
        RecallOptions options = RecallOptions.builder()
                .profile(CognitiveProfile.DEBUGGING)
                .build();
        assertThat(options.validate()).isEmpty();
    }

    @Test
    void executorProfileHasNoConflicts() {
        RecallOptions options = RecallOptions.builder()
                .profile(CognitiveProfile.THE_EXECUTOR)
                .build();
        assertThat(options.validate()).isEmpty();
    }
}
