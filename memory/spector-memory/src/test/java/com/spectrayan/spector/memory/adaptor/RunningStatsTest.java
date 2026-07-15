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
package com.spectrayan.spector.memory.adaptor;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RunningStats} — immutable reinforcement statistics record.
 */
@DisplayName("RunningStats")
class RunningStatsTest {

    private static final float ALPHA = 0.15f;

    @Test
    @DisplayName("EMPTY sentinel has all zero values")
    void emptyRecordHasZeros() {
        RunningStats empty = RunningStats.EMPTY;
        assertThat(empty.ema()).isEqualTo(0f);
        assertThat(empty.totalSignals()).isZero();
        assertThat(empty.positiveSignals()).isZero();
        assertThat(empty.lastUpdatedMs()).isZero();
    }

    @Test
    @DisplayName("first positive signal sets EMA to 1.0")
    void firstPositiveSignalSetsEmaToOne() {
        RunningStats updated = RunningStats.EMPTY.update(true, ALPHA);
        assertThat(updated.ema()).isEqualTo(1.0f);
    }

    @Test
    @DisplayName("first negative signal sets EMA to 0.0")
    void firstNegativeSignalSetsEmaToZero() {
        RunningStats updated = RunningStats.EMPTY.update(false, ALPHA);
        assertThat(updated.ema()).isEqualTo(0.0f);
    }

    @Test
    @DisplayName("EMA blending follows formula: ema*(1-alpha) + value*alpha")
    void emaBlendingWorksCorrectly() {
        // First signal: positive → EMA = 1.0
        RunningStats s1 = RunningStats.EMPTY.update(true, ALPHA);
        assertThat(s1.ema()).isEqualTo(1.0f);

        // Second signal: negative → EMA = 1.0 * (1 - 0.15) + 0.0 * 0.15 = 0.85
        RunningStats s2 = s1.update(false, ALPHA);
        assertThat(s2.ema()).isCloseTo(0.85f, within(1e-6f));

        // Third signal: positive → EMA = 0.85 * (1 - 0.15) + 1.0 * 0.15 = 0.8725
        RunningStats s3 = s2.update(true, ALPHA);
        float expected = 0.85f * (1 - ALPHA) + 1.0f * ALPHA;
        assertThat(s3.ema()).isCloseTo(expected, within(1e-6f));
    }

    @Test
    @DisplayName("update() returns new instance — original is unchanged (immutability)")
    void updateIsImmutable() {
        RunningStats original = RunningStats.EMPTY;
        RunningStats updated = original.update(true, ALPHA);

        // Original must be unchanged
        assertThat(original.ema()).isEqualTo(0f);
        assertThat(original.totalSignals()).isZero();
        assertThat(original.positiveSignals()).isZero();
        assertThat(original.lastUpdatedMs()).isZero();

        // Updated must differ
        assertThat(updated).isNotSameAs(original);
        assertThat(updated.ema()).isEqualTo(1.0f);
        assertThat(updated.totalSignals()).isEqualTo(1);
    }

    @Test
    @DisplayName("totalSignals and positiveSignals track correctly")
    void countersIncrementCorrectly() {
        RunningStats s = RunningStats.EMPTY;
        s = s.update(true, ALPHA);   // total=1, positive=1
        s = s.update(false, ALPHA);  // total=2, positive=1
        s = s.update(true, ALPHA);   // total=3, positive=2
        s = s.update(false, ALPHA);  // total=4, positive=2

        assertThat(s.totalSignals()).isEqualTo(4);
        assertThat(s.positiveSignals()).isEqualTo(2);
    }

    @Test
    @DisplayName("lastUpdatedMs is set to current time on each update")
    void timestampUpdatedOnEachCall() {
        long before = System.currentTimeMillis();
        RunningStats s = RunningStats.EMPTY.update(true, ALPHA);
        long after = System.currentTimeMillis();

        assertThat(s.lastUpdatedMs()).isBetween(before, after);

        // Second update should have a >= timestamp
        RunningStats s2 = s.update(false, ALPHA);
        assertThat(s2.lastUpdatedMs()).isGreaterThanOrEqualTo(s.lastUpdatedMs());
    }
}
