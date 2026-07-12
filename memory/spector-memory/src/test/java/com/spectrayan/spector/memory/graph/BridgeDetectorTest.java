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
package com.spectrayan.spector.memory.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BridgeDetector} — neighbor overlap-based bridge detection.
 */
class BridgeDetectorTest {

    @Test
    @DisplayName("zero shared neighbors → maximum bridge score (255)")
    void noSharedNeighbors_maxBridgeScore() {
        int score = BridgeDetector.computeBridgeScore(0, 5, 5);
        assertThat(score).isEqualTo(255);
    }

    @Test
    @DisplayName("all neighbors shared → minimum bridge score (0)")
    void allNeighborsShared_minBridgeScore() {
        int score = BridgeDetector.computeBridgeScore(5, 5, 5);
        assertThat(score).isEqualTo(0);
    }

    @Test
    @DisplayName("partial overlap → intermediate bridge score")
    void partialOverlap_intermediateBridgeScore() {
        int score = BridgeDetector.computeBridgeScore(2, 5, 5);
        assertThat(score).isBetween(100, 200);
    }

    @Test
    @DisplayName("both nodes with degree 1 → critical bridge (255)")
    void singleEdgeNodes_criticalBridge() {
        int score = BridgeDetector.computeBridgeScore(0, 1, 1);
        assertThat(score).isEqualTo(255);
    }

    @Test
    @DisplayName("bridge score clamps to [0, 255]")
    void scoreClampsToRange() {
        int score1 = BridgeDetector.computeBridgeScore(0, 100, 100);
        int score2 = BridgeDetector.computeBridgeScore(100, 100, 100);
        assertThat(score1).isBetween(0, 255);
        assertThat(score2).isBetween(0, 255);
    }

    @Test
    @DisplayName("countSharedNeighbors finds common elements")
    void sharedNeighborCount() {
        int[] a = {1, 2, 3, 4, 5};
        int[] b = {3, 5, 7, 9};
        int shared = BridgeDetector.countSharedNeighbors(a, 5, b, 4);
        assertThat(shared).isEqualTo(2); // 3 and 5
    }

    @Test
    @DisplayName("countSharedNeighbors with no overlap → 0")
    void noSharedNeighborCount() {
        int[] a = {1, 2, 3};
        int[] b = {4, 5, 6};
        int shared = BridgeDetector.countSharedNeighbors(a, 3, b, 3);
        assertThat(shared).isEqualTo(0);
    }

    @Test
    @DisplayName("countSharedNeighbors respects count parameter")
    void respectsCountParameter() {
        int[] a = {1, 2, 3, 4, 5};
        int[] b = {3, 5, 7};
        // Only look at first 2 entries of a: {1, 2}
        int shared = BridgeDetector.countSharedNeighbors(a, 2, b, 3);
        assertThat(shared).isEqualTo(0);
    }
}
