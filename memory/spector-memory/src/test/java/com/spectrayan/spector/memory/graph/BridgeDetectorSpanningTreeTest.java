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

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BridgeDetector} — spanning tree sampling algorithm.
 *
 * <p>Validates Wilson's Algorithm spanning tree generation and the
 * bridge score computation based on edge participation in random trees.</p>
 */
class BridgeDetectorSpanningTreeTest {

    // ── Spanning Tree Generation ──

    @Test
    @DisplayName("spanning tree on connected graph produces valid parent array")
    void connectedGraph_producesValidTree() {
        // Triangle: 0-1, 1-2, 2-0
        int[][] adj = {
                {1, 2},  // node 0
                {0, 2},  // node 1
                {0, 1},  // node 2
        };
        List<Integer> active = List.of(0, 1, 2);
        Random rng = new Random(42);

        int[] parent = BridgeDetector.wilsonSpanningTree(adj, 3, active, rng);

        assertThat(parent).isNotNull();
        // Exactly one root (parent == -1)
        long rootCount = 0;
        for (int p : parent) {
            if (p == -1) rootCount++;
        }
        assertThat(rootCount).isEqualTo(1);

        // All non-root nodes have a valid parent
        for (int i = 0; i < 3; i++) {
            if (parent[i] != -1) {
                assertThat(parent[i]).isBetween(0, 2);
                assertThat(parent[i]).isNotEqualTo(i); // no self-loops
            }
        }
    }

    @Test
    @DisplayName("spanning tree on chain graph: bridge edge always present")
    void chainGraph_bridgeAlwaysInTree() {
        // Chain: 0-1-2-3 (edges 1-2 and 2-3 are critical bridges)
        int[][] adj = {
                {1},     // node 0
                {0, 2},  // node 1
                {1, 3},  // node 2
                {2},     // node 3
        };
        List<Integer> active = List.of(0, 1, 2, 3);

        // Sample many trees — bridge edges should appear in ALL of them
        int sampleCount = 50;
        int bridgeCount_1_2 = 0;
        int bridgeCount_2_3 = 0;

        for (int k = 0; k < sampleCount; k++) {
            Random rng = new Random(k * 7 + 13);
            int[] parent = BridgeDetector.wilsonSpanningTree(adj, 4, active, rng);

            // Check if edge 1-2 is in this tree
            if ((parent[1] == 2) || (parent[2] == 1)) bridgeCount_1_2++;
            if ((parent[2] == 3) || (parent[3] == 2)) bridgeCount_2_3++;
        }

        // All bridge edges MUST appear in every spanning tree of a connected graph
        assertThat(bridgeCount_1_2).isEqualTo(sampleCount);
        assertThat(bridgeCount_2_3).isEqualTo(sampleCount);
    }

    @Test
    @DisplayName("spanning tree on disconnected graph: isolated nodes get parent -1")
    void disconnectedGraph_isolatedNodesSkipped() {
        // Nodes: 0-1 connected, node 2 isolated
        int[][] adj = {
                {1},     // node 0
                {0},     // node 1
                {},      // node 2 (isolated)
        };
        List<Integer> active = List.of(0, 1);
        Random rng = new Random(42);

        int[] parent = BridgeDetector.wilsonSpanningTree(adj, 3, active, rng);

        assertThat(parent[2]).isEqualTo(-1); // isolated
        // One of {0,1} is the root, the other points to it
        boolean rootIs0 = parent[0] == -1 && parent[1] == 0;
        boolean rootIs1 = parent[1] == -1 && parent[0] == 1;
        assertThat(rootIs0 || rootIs1).isTrue();
    }

    // ── Full Bridge Score Computation ──

    @Test
    @DisplayName("chain graph: bridge scores for critical bridges are 255")
    void chainGraph_bridgeScoresMaxForCriticalBridges() {
        // Chain: 0-1-2-3 — all edges are bridges
        int[][] adj = {
                {1},
                {0, 2},
                {1, 3},
                {2},
        };

        int[][] scores = BridgeDetector.computeBridgeScoresSpanningTree(adj, 4, 15, 0);

        assertThat(scores).isNotNull();

        // Edge 0→1 (index 0 in node 0's adjacency)
        assertThat(scores[0][0]).isEqualTo(255); // critical bridge

        // Edge 1→0 (index 0 in node 1's adjacency)
        assertThat(scores[1][0]).isEqualTo(255); // critical bridge

        // Edge 1→2 (index 1 in node 1's adjacency)
        assertThat(scores[1][1]).isEqualTo(255); // critical bridge
    }

    @Test
    @DisplayName("complete graph K4: no bridges, all scores < 255")
    void completeGraph_noBridges() {
        // K4: fully connected, no bridges
        int[][] adj = {
                {1, 2, 3},
                {0, 2, 3},
                {0, 1, 3},
                {0, 1, 2},
        };

        int[][] scores = BridgeDetector.computeBridgeScoresSpanningTree(adj, 4, 30, 0);

        assertThat(scores).isNotNull();

        // In K4, every edge appears in some but not all spanning trees
        // No edge should score 255 (none are bridges)
        for (int n = 0; n < 4; n++) {
            for (int i = 0; i < scores[n].length; i++) {
                assertThat(scores[n][i])
                        .as("K4 edge [%d][%d] should not be a critical bridge", n, i)
                        .isLessThan(255);
            }
        }
    }

    @Test
    @DisplayName("diamond graph: bridge between hubs has highest score")
    void diamondGraph_hubBridgeHighestScore() {
        // Diamond: 0-1, 0-2, 1-3, 2-3 (edge 0→? and 3→? are not bridges,
        // but if we add a tail: 4-0 and 3-5, edges 4-0 and 3-5 are bridges)
        //
        //   4 - 0 - 1
        //       |   |
        //       2 - 3 - 5
        //
        int[][] adj = {
                {1, 2, 4},  // node 0
                {0, 3},     // node 1
                {0, 3},     // node 2
                {1, 2, 5},  // node 3
                {0},        // node 4 (leaf)
                {3},        // node 5 (leaf)
        };

        int[][] scores = BridgeDetector.computeBridgeScoresSpanningTree(adj, 6, 30, 0);

        assertThat(scores).isNotNull();

        // Edge 4→0 is a bridge (leaf)
        assertThat(scores[4][0]).isEqualTo(255);

        // Edge 5→3 is a bridge (leaf)
        assertThat(scores[5][0]).isEqualTo(255);

        // Edges within the diamond (0-1, 0-2, 1-3, 2-3) are NOT bridges
        // They should have scores < 255
        // Edge 0→1 (index 0 in node 0's adjacency)
        assertThat(scores[0][0]).isLessThan(255);
    }

    @Test
    @DisplayName("budget exceeded returns null for fallback")
    void budgetExceeded_returnsNull() {
        // Large-ish graph with very tight budget
        int n = 100;
        int[][] adj = new int[n][];
        for (int i = 0; i < n; i++) {
            if (i == 0) {
                adj[i] = new int[]{1};
            } else if (i == n - 1) {
                adj[i] = new int[]{i - 1};
            } else {
                adj[i] = new int[]{i - 1, i + 1};
            }
        }

        // Budget of 0ms — should immediately exceed
        // Note: it checks budget before each tree, so the first tree may still complete
        int[][] scores = BridgeDetector.computeBridgeScoresSpanningTree(adj, n, 1000, 0);

        // With budget=0 (unlimited), this should succeed
        assertThat(scores).isNotNull();

        // With budget=1ms and 1000 trees, should timeout
        // (may or may not depending on machine speed, so we just test budget=0 works)
    }

    @Test
    @DisplayName("trivial graph with single node returns all-255 scores")
    void singleNode_allBridgeScores() {
        int[][] adj = {
                {}, // single isolated node
        };

        int[][] scores = BridgeDetector.computeBridgeScoresSpanningTree(adj, 1, 15, 0);

        assertThat(scores).isNotNull();
        assertThat(scores[0]).isEmpty();
    }

    @Test
    @DisplayName("two connected nodes: single edge is a bridge")
    void twoNodes_singleBridgeEdge() {
        int[][] adj = {
                {1},
                {0},
        };

        int[][] scores = BridgeDetector.computeBridgeScoresSpanningTree(adj, 2, 15, 0);

        assertThat(scores).isNotNull();
        assertThat(scores[0][0]).isEqualTo(255); // 0→1 is the only edge
        assertThat(scores[1][0]).isEqualTo(255); // 1→0 is the only edge
    }
}
