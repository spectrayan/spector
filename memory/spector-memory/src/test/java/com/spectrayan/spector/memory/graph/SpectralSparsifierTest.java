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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SpectralSparsifier} — leverage-based edge sparsification.
 *
 * <p>Validates that the sparsifier correctly identifies low-leverage edges,
 * protects bridges, and produces deterministic plans.</p>
 */
class SpectralSparsifierTest {

    // ── Bridge Protection ──

    @Test
    void bridgeProtectedEdgesAreNeverCandidates() {
        // Graph with 3 nodes: edges with bridge scores 250 (bridge) and 30 (redundant)
        int[][] bridgeScores = {
                {250, 30},   // node 0: one bridge edge, one redundant
                {250},       // node 1: bridge edge
                {30}         // node 2: redundant edge
        };

        var plan = SpectralSparsifier.plan(bridgeScores, 3, 50, 224);

        // Bridge edges (score >= 224) should be kept
        assertThat(plan.keep()[0][0]).isTrue();   // score 250 — bridge
        assertThat(plan.keep()[1][0]).isTrue();   // score 250 — bridge

        // Low-leverage edges (score 30 < keepFloor 50) should be candidates
        assertThat(plan.keep()[0][1]).isFalse();  // score 30 — candidate
        assertThat(plan.keep()[2][0]).isFalse();  // score 30 — candidate
    }

    // ── Keep Floor Threshold ──

    @Test
    void edgesAboveKeepFloorAreKept() {
        int[][] bridgeScores = {
                {100, 60, 20}  // scores: 100 (above), 60 (above), 20 (below keepFloor=50)
        };

        var plan = SpectralSparsifier.plan(bridgeScores, 1, 50, 224);

        assertThat(plan.keep()[0][0]).isTrue();   // 100 >= 50
        assertThat(plan.keep()[0][1]).isTrue();   // 60 >= 50
        assertThat(plan.keep()[0][2]).isFalse();  // 20 < 50
        assertThat(plan.candidateCount()).isEqualTo(1);
    }

    // ── Dense Cluster ──

    @Test
    void denseClusterConcentratesCandidatesOnLowLeverage() {
        // Simulate a dense cluster: many edges with low leverage (parallel paths)
        // and a few with high leverage (bridges to other regions)
        int[][] bridgeScores = new int[10][];
        for (int i = 0; i < 10; i++) {
            bridgeScores[i] = new int[]{5, 8, 3, 240};  // 3 low-leverage + 1 bridge
        }

        var plan = SpectralSparsifier.plan(bridgeScores, 10, 50, 224);

        // Should have 30 candidates (3 low-leverage per node × 10 nodes)
        assertThat(plan.candidateCount()).isEqualTo(30);

        // All bridges should be kept
        for (int i = 0; i < 10; i++) {
            assertThat(plan.keep()[i][3]).isTrue();  // bridge score 240
        }

        // Mean leverage of dropped should be much lower than kept
        assertThat(plan.meanLeverageDropped()).isLessThan(plan.meanLeverageKept());
    }

    // ── Empty / Degenerate Graphs ──

    @Test
    void emptyGraphProducesEmptyPlan() {
        var plan = SpectralSparsifier.plan(null, 0, 50, 224);
        assertThat(plan.isEmpty()).isTrue();
        assertThat(plan.totalEdges()).isZero();
    }

    @Test
    void singleEdgeGraphKeepsEdge() {
        int[][] bridgeScores = {{200}};  // score 200, above keepFloor 50

        var plan = SpectralSparsifier.plan(bridgeScores, 1, 50, 224);

        assertThat(plan.keep()[0][0]).isTrue();
        assertThat(plan.candidateCount()).isZero();
        assertThat(plan.isEmpty()).isTrue();
    }

    @Test
    void allBridgesGraphProducesEmptyPlan() {
        int[][] bridgeScores = {
                {255, 230},
                {255},
                {230}
        };

        var plan = SpectralSparsifier.plan(bridgeScores, 3, 50, 224);
        assertThat(plan.isEmpty()).isTrue();
    }

    // ── Leverage Distribution ──

    @Test
    void meanLeverageStatisticsAreCorrect() {
        int[][] bridgeScores = {
                {10, 20, 100, 150}  // keepFloor = 50: first two dropped, last two kept
        };

        var plan = SpectralSparsifier.plan(bridgeScores, 1, 50, 224);

        assertThat(plan.candidateCount()).isEqualTo(2);
        assertThat(plan.meanLeverageDropped()).isEqualTo(15f);    // (10 + 20) / 2
        assertThat(plan.meanLeverageKept()).isEqualTo(125f);      // (100 + 150) / 2
    }

    // ── Determinism ──

    @Test
    void sameBridgeScoresProduceSamePlan() {
        int[][] bridgeScores = {
                {10, 200, 30, 250},
                {5, 240, 15, 100}
        };

        var plan1 = SpectralSparsifier.plan(bridgeScores, 2, 50, 224);
        var plan2 = SpectralSparsifier.plan(bridgeScores, 2, 50, 224);

        assertThat(plan1.candidateCount()).isEqualTo(plan2.candidateCount());
        assertThat(plan1.meanLeverageDropped()).isEqualTo(plan2.meanLeverageDropped());
        assertThat(plan1.meanLeverageKept()).isEqualTo(plan2.meanLeverageKept());

        for (int n = 0; n < 2; n++) {
            for (int e = 0; e < bridgeScores[n].length; e++) {
                assertThat(plan1.keep()[n][e]).isEqualTo(plan2.keep()[n][e]);
            }
        }
    }

    // ── toKeepFloorScore ──

    @Test
    void toKeepFloorScoreConvertsCorrectly() {
        assertThat(SpectralSparsifier.toKeepFloorScore(0.0f)).isEqualTo(0);
        assertThat(SpectralSparsifier.toKeepFloorScore(0.5f)).isEqualTo(128);
        assertThat(SpectralSparsifier.toKeepFloorScore(1.0f)).isEqualTo(255);
        assertThat(SpectralSparsifier.toKeepFloorScore(0.15f)).isEqualTo(38);
    }

    // ── SparsificationPlan record ──

    @Test
    void keptCountIsCorrect() {
        var plan = new SparsificationPlan(new boolean[0][], 100, 30, 10f, 50f);
        assertThat(plan.keptCount()).isEqualTo(70);
    }
}
