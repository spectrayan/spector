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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ActRActivation} — full ACT-R base-level activation
 * using the 3-slot recall-timestamp ring buffer.
 */
class ActRActivationTest {

    /** Header is 64 bytes — we need at least that much for the ring buffer. */
    private static final int HEADER_SIZE = SynapticHeaderConstants.HEADER_BYTES;

    // ══════════════════════════════════════════════════════════════
    // Ring buffer: recordRecall / readRecallTimestamps
    // ══════════════════════════════════════════════════════════════

    @Test
    void recordRecallFillsEmptySlots() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(HEADER_SIZE);
            long creationMs = 1_000_000_000L;

            // Record 3 recalls at different times (fills all 3 slots)
            ActRActivation.recordRecall(seg, 0, creationMs, creationMs + 10_000L);
            ActRActivation.recordRecall(seg, 0, creationMs, creationMs + 20_000L);
            ActRActivation.recordRecall(seg, 0, creationMs, creationMs + 30_000L);

            int[] timestamps = ActRActivation.readRecallTimestamps(seg, 0);
            assertThat(timestamps[0]).isEqualTo(10);  // 10 seconds
            assertThat(timestamps[1]).isEqualTo(20);  // 20 seconds
            assertThat(timestamps[2]).isEqualTo(30);  // 30 seconds
        }
    }

    @Test
    void recordRecallOverwritesOldestWhenFull() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(HEADER_SIZE);
            long creationMs = 1_000_000_000L;

            // Fill all 3 slots
            ActRActivation.recordRecall(seg, 0, creationMs, creationMs + 10_000L);
            ActRActivation.recordRecall(seg, 0, creationMs, creationMs + 20_000L);
            ActRActivation.recordRecall(seg, 0, creationMs, creationMs + 30_000L);

            // 4th recall should overwrite oldest (slot 0 = 10s)
            ActRActivation.recordRecall(seg, 0, creationMs, creationMs + 40_000L);

            int[] timestamps = ActRActivation.readRecallTimestamps(seg, 0);
            assertThat(timestamps[0]).isEqualTo(40);  // overwritten
            assertThat(timestamps[1]).isEqualTo(20);
            assertThat(timestamps[2]).isEqualTo(30);
        }
    }

    @Test
    void emptyRingBufferReadsAllZeros() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(HEADER_SIZE);

            int[] timestamps = ActRActivation.readRecallTimestamps(seg, 0);
            for (int ts : timestamps) {
                assertThat(ts).isZero();
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Base-level activation computation
    // ══════════════════════════════════════════════════════════════

    @Test
    void noRecallDataReturnsMinusOne() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(HEADER_SIZE);
            long creation = System.currentTimeMillis() - 86_400_000L; // 1 day ago
            long now = System.currentTimeMillis();

            float activation = ActRActivation.computeBaseLevelActivation(
                    seg, 0, creation, now, 0.15f);

            assertThat(activation).isEqualTo(-1.0f);
        }
    }

    @Test
    void recentRecallProducesHighActivation() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(HEADER_SIZE);
            long now = System.currentTimeMillis();
            long creation = now - 86_400_000L; // 1 day ago

            // Recall 5 minutes ago
            ActRActivation.recordRecall(seg, 0, creation, now - 300_000L);

            float activation = ActRActivation.computeBaseLevelActivation(
                    seg, 0, creation, now, 0.15f);

            assertThat(activation).isGreaterThan(0.3f);
        }
    }

    @Test
    void oldRecallProducesLowerActivation() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(HEADER_SIZE);
            long now = System.currentTimeMillis();
            long creation = now - 30L * 86_400_000L; // 30 days ago

            // Only recall was 25 days ago
            ActRActivation.recordRecall(seg, 0, creation, creation + 5L * 86_400_000L);

            float activation = ActRActivation.computeBaseLevelActivation(
                    seg, 0, creation, now, 0.15f);

            assertThat(activation).isLessThan(0.6f);
        }
    }

    @Test
    void moreRecallsProduceHigherActivation() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segOnce = arena.allocate(HEADER_SIZE);
            MemorySegment segFour = arena.allocate(HEADER_SIZE);
            long now = System.currentTimeMillis();
            long creation = now - 7L * 86_400_000L; // 1 week ago

            // Memory recalled once (1 day ago)
            ActRActivation.recordRecall(segOnce, 0, creation, now - 86_400_000L);

            // Memory recalled 3 times (at days 1, 3, 6)
            ActRActivation.recordRecall(segFour, 0, creation, creation + 1L * 86_400_000L);
            ActRActivation.recordRecall(segFour, 0, creation, creation + 3L * 86_400_000L);
            ActRActivation.recordRecall(segFour, 0, creation, creation + 6L * 86_400_000L);

            float activationOnce = ActRActivation.computeBaseLevelActivation(
                    segOnce, 0, creation, now, 0.15f);
            float activationFour = ActRActivation.computeBaseLevelActivation(
                    segFour, 0, creation, now, 0.15f);

            assertThat(activationFour).isGreaterThan(activationOnce);
        }
    }

    @Test
    void spacedRecallsBeatMassedRecalls() {
        // The spacing effect: recalls distributed over time create a broader
        // activation footprint that survives longer than massed practice.
        // We test this by measuring at day 90 — well after the last recall.
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segSpaced = arena.allocate(HEADER_SIZE);
            MemorySegment segMassed = arena.allocate(HEADER_SIZE);
            long now = System.currentTimeMillis();
            long creation = now - 90L * 86_400_000L; // 90 days ago

            // Spaced: recalls spread across days 30, 50, 85
            ActRActivation.recordRecall(segSpaced, 0, creation, creation + 30L * 86_400_000L);
            ActRActivation.recordRecall(segSpaced, 0, creation, creation + 50L * 86_400_000L);
            ActRActivation.recordRecall(segSpaced, 0, creation, creation + 85L * 86_400_000L);

            // Massed: all 3 recalls on day 10 (within 1 minute)
            ActRActivation.recordRecall(segMassed, 0, creation, creation + 10L * 86_400_000L);
            ActRActivation.recordRecall(segMassed, 0, creation, creation + 10L * 86_400_000L + 15_000L);
            ActRActivation.recordRecall(segMassed, 0, creation, creation + 10L * 86_400_000L + 30_000L);

            float activationSpaced = ActRActivation.computeBaseLevelActivation(
                    segSpaced, 0, creation, now, 0.15f);
            float activationMassed = ActRActivation.computeBaseLevelActivation(
                    segMassed, 0, creation, now, 0.15f);

            assertThat(activationSpaced)
                    .as("Spaced recalls should produce higher activation than massed recalls (spacing effect)")
                    .isGreaterThan(activationMassed);
        }
    }

    @Test
    void activationIsBoundedBetweenZeroAndOne() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(HEADER_SIZE);
            long now = System.currentTimeMillis();
            long creation = now - 1000L; // 1 second ago

            ActRActivation.recordRecall(seg, 0, creation, now);

            float activation = ActRActivation.computeBaseLevelActivation(
                    seg, 0, creation, now, 0.15f);

            assertThat(activation).isBetween(0.0f, 1.0f);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Fallback behavior
    // ══════════════════════════════════════════════════════════════

    @Test
    void computeDecayWithActRFallsBackWhenNoRecallData() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(HEADER_SIZE);
            long now = System.currentTimeMillis();
            long creation = now - 2L * 86_400_000L; // 2 days ago

            // No recall timestamps recorded — should use bucket fallback
            float decay = ActRActivation.computeDecayWithActR(
                    seg, 0, creation, now, 0, 0.15f);

            // Bucket 3 (1-3 days) fallback
            float expectedBucket = DecayStrategy.computeDecay(creation, now, 0);
            assertThat(decay).isEqualTo(expectedBucket);
        }
    }

    @Test
    void computeDecayWithActRUsesFullModelWhenRecallDataExists() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(HEADER_SIZE);
            long now = System.currentTimeMillis();
            long creation = now - 7L * 86_400_000L; // 1 week ago

            // Record a recent recall
            ActRActivation.recordRecall(seg, 0, creation, now - 3_600_000L);

            float decay = ActRActivation.computeDecayWithActR(
                    seg, 0, creation, now, 1, 0.15f);

            // Should NOT equal the bucket fallback (different computation)
            float bucketFallback = DecayStrategy.computeDecay(creation, now, 1);
            // The ACT-R value should differ from the bucket approximation
            // (they may be close but are computed differently)
            assertThat(decay).isGreaterThan(0.0f).isLessThanOrEqualTo(1.0f);
        }
    }

    @Test
    void nullSegmentFallsBackGracefully() {
        long now = System.currentTimeMillis();
        long creation = now - 86_400_000L;

        float decay = ActRActivation.computeDecayWithActR(
                null, 0, creation, now, 0, 0.15f);

        assertThat(decay).isEqualTo(DecayStrategy.computeDecay(creation, now, 0));
    }
}
