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

import com.spectrayan.spector.memory.habituation.HabituationPenalty;
import com.spectrayan.spector.memory.pipeline.RecallListener;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RecallMode} integration.
 *
 * <p>Verifies that OBSERVE mode suppresses all mutations while LEARN mode
 * preserves existing behavior.</p>
 */
class RecallModeTest {

    // ══════════════════════════════════════════════════════════════
    // RecallMode enum basics
    // ══════════════════════════════════════════════════════════════

    @Test
    void recallModeHasThreeValues() {
        assertThat(RecallMode.values()).containsExactly(
                RecallMode.LEARN, RecallMode.OBSERVE, RecallMode.REPLAY);
    }

    @Test
    void defaultRecallOptionsUsesLearnMode() {
        assertThat(RecallOptions.DEFAULT.recallMode()).isEqualTo(RecallMode.LEARN);
    }

    @Test
    void builderDefaultsToLearnMode() {
        RecallOptions options = RecallOptions.builder().build();
        assertThat(options.recallMode()).isEqualTo(RecallMode.LEARN);
    }

    @Test
    void builderCanSetObserveMode() {
        RecallOptions options = RecallOptions.builder()
                .recallMode(RecallMode.OBSERVE)
                .build();
        assertThat(options.recallMode()).isEqualTo(RecallMode.OBSERVE);
    }

    @Test
    void recallModeIsIndependentOfProfile() {
        RecallOptions options = RecallOptions.builder()
                .profile(CognitiveProfile.DEBUGGING)
                .recallMode(RecallMode.OBSERVE)
                .build();
        assertThat(options.recallMode()).isEqualTo(RecallMode.OBSERVE);
    }

    // ══════════════════════════════════════════════════════════════
    // HabituationPenalty: read-only vs mutating
    // ══════════════════════════════════════════════════════════════

    @Test
    void learnModeIncrementsHabituationCounter() {
        HabituationPenalty hab = new HabituationPenalty(0.2f);
        String id = "mem-1";

        float first = hab.recordAndComputePenalty(id);
        float second = hab.recordAndComputePenalty(id);

        assertThat(first).isEqualTo(1.0f); // 1st return = no penalty
        assertThat(second).isLessThan(first); // 2nd return = penalized
    }

    @Test
    void observeModeReadsWithoutIncrementing() {
        HabituationPenalty hab = new HabituationPenalty(0.2f);
        String id = "mem-1";

        // Simulate OBSERVE: use currentPenalty() instead of recordAndComputePenalty()
        float first = hab.currentPenalty(id);
        float second = hab.currentPenalty(id);

        assertThat(first).isEqualTo(1.0f); // never seen = no penalty
        assertThat(second).isEqualTo(1.0f); // still no penalty — counter didn't increment
        assertThat(hab.trackedCount()).isZero(); // nothing was tracked
    }

    @Test
    void observeModeThenLearnModeStartsFresh() {
        HabituationPenalty hab = new HabituationPenalty(0.2f);
        String id = "mem-1";

        // Multiple OBSERVE reads — no mutation
        hab.currentPenalty(id);
        hab.currentPenalty(id);
        hab.currentPenalty(id);

        // First LEARN read — should be 1.0 (fresh, not penalized from OBSERVE reads)
        float learnFirst = hab.recordAndComputePenalty(id);
        assertThat(learnFirst).isEqualTo(1.0f);
    }

    // ══════════════════════════════════════════════════════════════
    // RecallMode parsing (for MCP integration)
    // ══════════════════════════════════════════════════════════════

    @Test
    void recallModeValueOfLearn() {
        assertThat(RecallMode.valueOf("LEARN")).isEqualTo(RecallMode.LEARN);
    }

    @Test
    void recallModeValueOfObserve() {
        assertThat(RecallMode.valueOf("OBSERVE")).isEqualTo(RecallMode.OBSERVE);
    }

    // ══════════════════════════════════════════════════════════════
    // Integration: RecallOptions with RecallMode + validate()
    // ══════════════════════════════════════════════════════════════

    @Test
    void observeModeWithProfileHasNoValidationWarnings() {
        RecallOptions options = RecallOptions.builder()
                .profile(CognitiveProfile.DEBUGGING)
                .recallMode(RecallMode.OBSERVE)
                .build();
        assertThat(options.validate()).isEmpty();
    }

    @Test
    void observeModePreservesAllOtherSettings() {
        RecallOptions options = RecallOptions.builder()
                .topK(20)
                .minImportance(0.5f)
                .profile(CognitiveProfile.CRITICAL)
                .recallMode(RecallMode.OBSERVE)
                .build();

        assertThat(options.topK()).isEqualTo(20);
        assertThat(options.minImportance()).isEqualTo(0.5f);
        assertThat(options.alpha()).isEqualTo(0.2f); // from CRITICAL
        assertThat(options.beta()).isEqualTo(0.8f);  // from CRITICAL
        assertThat(options.recallMode()).isEqualTo(RecallMode.OBSERVE);
    }
}
