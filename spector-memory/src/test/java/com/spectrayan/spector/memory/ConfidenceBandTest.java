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

import com.spectrayan.spector.memory.cortex.MemorySource;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ConfidenceBand}.
 */
class ConfidenceBandTest {

    private CognitiveResult result(float score) {
        return new CognitiveResult("id", "text", score, 0.5f, 1.0f, 0, (byte) 0,
                MemoryType.EPISODIC, MemorySource.OBSERVED, new String[0], 1.0f, 1.0f);
    }

    @Test
    void highWhenTopDominates() {
        List<CognitiveResult> results = List.of(result(0.9f), result(0.3f), result(0.2f));
        assertThat(ConfidenceBand.classify(results)).isEqualTo(ConfidenceBand.HIGH);
    }

    @Test
    void mediumWhenModerateGap() {
        List<CognitiveResult> results = List.of(result(0.5f), result(0.35f), result(0.3f));
        assertThat(ConfidenceBand.classify(results)).isEqualTo(ConfidenceBand.MEDIUM);
    }

    @Test
    void lowWhenClustered() {
        List<CognitiveResult> results = List.of(result(0.5f), result(0.48f), result(0.47f));
        assertThat(ConfidenceBand.classify(results)).isEqualTo(ConfidenceBand.LOW);
    }

    @Test
    void highForSingleResultWithMeaningfulScore() {
        assertThat(ConfidenceBand.classify(List.of(result(0.8f)))).isEqualTo(ConfidenceBand.HIGH);
    }

    @Test
    void mediumForSingleResultWithLowScore() {
        assertThat(ConfidenceBand.classify(List.of(result(0.05f)))).isEqualTo(ConfidenceBand.MEDIUM);
    }

    @Test
    void lowForEmptyResults() {
        assertThat(ConfidenceBand.classify(Collections.emptyList())).isEqualTo(ConfidenceBand.LOW);
    }

    @Test
    void lowForNullResults() {
        assertThat(ConfidenceBand.classify(null)).isEqualTo(ConfidenceBand.LOW);
    }

    @Test
    void highWhenSecondIsZero() {
        List<CognitiveResult> results = List.of(result(0.5f), result(0.0f));
        assertThat(ConfidenceBand.classify(results)).isEqualTo(ConfidenceBand.HIGH);
    }

    @Test
    void lowWhenBothZero() {
        List<CognitiveResult> results = List.of(result(0.0f), result(0.0f));
        assertThat(ConfidenceBand.classify(results)).isEqualTo(ConfidenceBand.LOW);
    }

    @Test
    void exactlyAtHighThreshold() {
        // ratio = 2.0 exactly → HIGH
        List<CognitiveResult> results = List.of(result(1.0f), result(0.5f));
        assertThat(ConfidenceBand.classify(results)).isEqualTo(ConfidenceBand.HIGH);
    }

    @Test
    void exactlyAtMediumThreshold() {
        // ratio = 1.2 exactly → MEDIUM
        List<CognitiveResult> results = List.of(result(0.6f), result(0.5f));
        assertThat(ConfidenceBand.classify(results)).isEqualTo(ConfidenceBand.MEDIUM);
    }
}
