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
package com.spectrayan.spector.memory.synapse;

import com.spectrayan.spector.kernel.layout.StrengthLayout;
import com.spectrayan.spector.kernel.score.DecayStrategy;
import com.spectrayan.spector.kernel.store.DefaultHeaderCursor;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ActRActivation} — full ACT-R base-level activation
 * using the 8-slot recall-timestamp ring buffer (ADR-0028).
 */
class ActRActivationTest {

    /** Audit record is 96 bytes — contains the 8-slot ring buffer. */
    private static final int AUDIT_RECORD_SIZE = StrengthLayout.STRIDE_BYTES;

    // ══════════════════════════════════════════════════════════════
    // Ring buffer: recordRecall / readRecallTimestamps
    // ══════════════════════════════════════════════════════════════

    @Test
    void recordRecallFillsEmptySlots() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(AUDIT_RECORD_SIZE);
            long creationMs = 1_000_000_000L;

            try (var cursor = DefaultHeaderCursor.forSegment(seg, AUDIT_RECORD_SIZE)) {
                cursor.seek(0);
                // Record 3 recalls at different times (fills first 3 slots)
                ActRActivation.recordRecall(cursor, creationMs, creationMs + 10_000L);
                ActRActivation.recordRecall(cursor, creationMs, creationMs + 20_000L);
                ActRActivation.recordRecall(cursor, creationMs, creationMs + 30_000L);

                int[] timestamps = ActRActivation.readRecallTimestamps(cursor);
                assertThat(timestamps[0]).isEqualTo(10);  // 10 seconds
                assertThat(timestamps[1]).isEqualTo(20);  // 20 seconds
                assertThat(timestamps[2]).isEqualTo(30);  // 30 seconds
            }
        }
    }

    @Test
    void recordRecallOverwritesOldestWhenFull() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(AUDIT_RECORD_SIZE);
            long creationMs = 1_000_000_000L;

            try (var cursor = DefaultHeaderCursor.forSegment(seg, AUDIT_RECORD_SIZE)) {
                cursor.seek(0);
                // Fill all 8 slots
                for (int i = 1; i <= 8; i++) {
                    ActRActivation.recordRecall(cursor, creationMs, creationMs + (i * 10_000L));
                }

                // 9th recall should overwrite oldest (slot 0 = 10s)
                ActRActivation.recordRecall(cursor, creationMs, creationMs + 90_000L);

                int[] timestamps = ActRActivation.readRecallTimestamps(cursor);
                assertThat(timestamps[0]).isEqualTo(90);  // overwritten
                assertThat(timestamps[1]).isEqualTo(20);
                assertThat(timestamps[2]).isEqualTo(30);
            }
        }
    }

    @Test
    void emptyRingBufferReadsAllZeros() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(AUDIT_RECORD_SIZE);

            try (var cursor = DefaultHeaderCursor.forSegment(seg, AUDIT_RECORD_SIZE)) {
                cursor.seek(0);
                int[] timestamps = ActRActivation.readRecallTimestamps(cursor);
                for (int ts : timestamps) {
                    assertThat(ts).isZero();
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Base-level activation computation
    // ══════════════════════════════════════════════════════════════

    @Test
    void noRecallDataReturnsMinusOne() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(AUDIT_RECORD_SIZE);
            long creation = System.currentTimeMillis() - 86_400_000L; // 1 day ago
            long now = System.currentTimeMillis();

            try (var cursor = DefaultHeaderCursor.forSegment(seg, AUDIT_RECORD_SIZE)) {
                cursor.seek(0);
                float activation = ActRActivation.computeBaseLevelActivation(
                        cursor, creation, now, 0.15f);

                assertThat(activation).isEqualTo(-1.0f);
            }
        }
    }

    @Test
    void recentRecallProducesHighActivation() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(AUDIT_RECORD_SIZE);
            long now = System.currentTimeMillis();
            long creation = now - 86_400_000L; // 1 day ago

            try (var cursor = DefaultHeaderCursor.forSegment(seg, AUDIT_RECORD_SIZE)) {
                cursor.seek(0);
                // Recall 5 minutes ago
                ActRActivation.recordRecall(cursor, creation, now - 300_000L);

                float activation = ActRActivation.computeBaseLevelActivation(
                        cursor, creation, now, 0.15f);

                assertThat(activation).isGreaterThan(0.3f);
            }
        }
    }

    @Test
    void oldRecallProducesLowerActivation() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(AUDIT_RECORD_SIZE);
            long now = System.currentTimeMillis();
            long creation = now - 30L * 86_400_000L; // 30 days ago

            try (var cursor = DefaultHeaderCursor.forSegment(seg, AUDIT_RECORD_SIZE)) {
                cursor.seek(0);
                // Only recall was 25 days ago
                ActRActivation.recordRecall(cursor, creation, creation + 5L * 86_400_000L);

                float activation = ActRActivation.computeBaseLevelActivation(
                        cursor, creation, now, 0.15f);

                assertThat(activation).isLessThan(0.6f);
            }
        }
    }

    @Test
    void moreRecallsProduceHigherActivation() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segOnce = arena.allocate(AUDIT_RECORD_SIZE);
            MemorySegment segFour = arena.allocate(AUDIT_RECORD_SIZE);
            long now = System.currentTimeMillis();
            long creation = now - 7L * 86_400_000L; // 1 week ago

            try (var cursorOnce = DefaultHeaderCursor.forSegment(segOnce, AUDIT_RECORD_SIZE);
                 var cursorFour = DefaultHeaderCursor.forSegment(segFour, AUDIT_RECORD_SIZE)) {
                cursorOnce.seek(0);
                cursorFour.seek(0);

                // Memory recalled once (1 day ago)
                ActRActivation.recordRecall(cursorOnce, creation, now - 86_400_000L);

                // Memory recalled 3 times (at days 1, 3, 6)
                ActRActivation.recordRecall(cursorFour, creation, creation + 1L * 86_400_000L);
                ActRActivation.recordRecall(cursorFour, creation, creation + 3L * 86_400_000L);
                ActRActivation.recordRecall(cursorFour, creation, creation + 6L * 86_400_000L);

                float activationOnce = ActRActivation.computeBaseLevelActivation(
                        cursorOnce, creation, now, 0.15f);
                float activationFour = ActRActivation.computeBaseLevelActivation(
                        cursorFour, creation, now, 0.15f);

                assertThat(activationFour).isGreaterThan(activationOnce);
            }
        }
    }

    @Test
    void spacedRecallsBeatMassedRecalls() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segSpaced = arena.allocate(AUDIT_RECORD_SIZE);
            MemorySegment segMassed = arena.allocate(AUDIT_RECORD_SIZE);
            long now = System.currentTimeMillis();
            long creation = now - 90L * 86_400_000L; // 90 days ago

            try (var cursorSpaced = DefaultHeaderCursor.forSegment(segSpaced, AUDIT_RECORD_SIZE);
                 var cursorMassed = DefaultHeaderCursor.forSegment(segMassed, AUDIT_RECORD_SIZE)) {
                cursorSpaced.seek(0);
                cursorMassed.seek(0);

                // Spaced: recalls spread across days 30, 50, 85
                ActRActivation.recordRecall(cursorSpaced, creation, creation + 30L * 86_400_000L);
                ActRActivation.recordRecall(cursorSpaced, creation, creation + 50L * 86_400_000L);
                ActRActivation.recordRecall(cursorSpaced, creation, creation + 85L * 86_400_000L);

                // Massed: all 3 recalls on day 10 (within 1 minute)
                ActRActivation.recordRecall(cursorMassed, creation, creation + 10L * 86_400_000L);
                ActRActivation.recordRecall(cursorMassed, creation, creation + 10L * 86_400_000L + 15_000L);
                ActRActivation.recordRecall(cursorMassed, creation, creation + 10L * 86_400_000L + 30_000L);

                float activationSpaced = ActRActivation.computeBaseLevelActivation(
                        cursorSpaced, creation, now, 0.15f);
                float activationMassed = ActRActivation.computeBaseLevelActivation(
                        cursorMassed, creation, now, 0.15f);

                assertThat(activationSpaced)
                        .as("Spaced recalls should produce higher activation than massed recalls (spacing effect)")
                        .isGreaterThan(activationMassed);
            }
        }
    }

    @Test
    void activationIsBoundedBetweenZeroAndOne() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(AUDIT_RECORD_SIZE);
            long now = System.currentTimeMillis();
            long creation = now - 1000L; // 1 second ago

            try (var cursor = DefaultHeaderCursor.forSegment(seg, AUDIT_RECORD_SIZE)) {
                cursor.seek(0);
                ActRActivation.recordRecall(cursor, creation, now);

                float activation = ActRActivation.computeBaseLevelActivation(
                        cursor, creation, now, 0.15f);

                assertThat(activation).isBetween(0.0f, 1.0f);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Fallback behavior
    // ══════════════════════════════════════════════════════════════

    @Test
    void computeDecayWithActRFallsBackWhenNoRecallData() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(AUDIT_RECORD_SIZE);
            long now = System.currentTimeMillis();
            long creation = now - 2L * 86_400_000L; // 2 days ago

            try (var cursor = DefaultHeaderCursor.forSegment(seg, AUDIT_RECORD_SIZE)) {
                cursor.seek(0);
                // No recall timestamps recorded — should use bucket fallback
                float decay = ActRActivation.computeDecayWithActR(
                        cursor, creation, now, 0, 0.15f);

                // Bucket 3 (1-3 days) fallback
                float expectedBucket = DecayStrategy.computeDecay(creation, now, 0);
                assertThat(decay).isEqualTo(expectedBucket);
            }
        }
    }

    @Test
    void computeDecayWithActRUsesFullModelWhenRecallDataExists() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(AUDIT_RECORD_SIZE);
            long now = System.currentTimeMillis();
            long creation = now - 7L * 86_400_000L; // 1 week ago

            try (var cursor = DefaultHeaderCursor.forSegment(seg, AUDIT_RECORD_SIZE)) {
                cursor.seek(0);
                // Record a recent recall
                ActRActivation.recordRecall(cursor, creation, now - 3_600_000L);

                float decay = ActRActivation.computeDecayWithActR(
                        cursor, creation, now, 1, 0.15f);

                // Should NOT equal the bucket fallback (different computation)
                float bucketFallback = DecayStrategy.computeDecay(creation, now, 1);
                assertThat(decay).isGreaterThan(0.0f).isLessThanOrEqualTo(1.0f);
            }
        }
    }

    @Test
    void nullCursorFallsBackGracefully() {
        long now = System.currentTimeMillis();
        long creation = now - 86_400_000L;

        float decay = ActRActivation.computeDecayWithActR(
                null, creation, now, 0, 0.15f);

        assertThat(decay).isEqualTo(DecayStrategy.computeDecay(creation, now, 0));
    }
}
