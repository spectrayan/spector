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

/**
 * Approximate bridge detection for graph edges using neighbor overlap heuristic.
 *
 * <h3>Biological Analog</h3>
 * <p>In neural networks, hub neurons (high betweenness centrality) are critical
 * for information relay between brain regions. Removing them fragments the network.
 * The bridge score approximates this: edges connecting otherwise-disconnected
 * neighborhoods receive high scores.</p>
 *
 * <h3>Algorithm: Neighbor Overlap Heuristic</h3>
 * <p>For an edge A→B, the bridge score is inversely proportional to the number
 * of shared neighbors between A and B. If they share many neighbors, the edge
 * is redundant (many alternative paths exist). If they share zero neighbors,
 * this edge is likely the only path between their neighborhoods — a critical bridge.</p>
 *
 * <h3>Performance</h3>
 * <p>Cost per edge: O(degree(A) × degree(B)) for intersection. At MAX_DEGREE=24,
 * this is ≤576 comparisons. Total cost for full graph: O(N × MAX_DEGREE³).
 * Should be called during ReflectDaemon cycles, NOT on the hot recall path.</p>
 *
 * @see EdgeImportance
 */
public final class BridgeDetector {

    private BridgeDetector() {}

    /**
     * Computes the bridge score for an edge between two nodes.
     *
     * <p>Returns a value in [0, 255] where 255 = critical bridge (no shared
     * neighbors) and 0 = maximally redundant (all neighbors shared).</p>
     *
     * @param sharedNeighborCount number of common neighbors between the two nodes
     * @param degreeA             degree of node A
     * @param degreeB             degree of node B
     * @return bridge score (0-255, unsigned byte range)
     */
    public static int computeBridgeScore(int sharedNeighborCount, int degreeA, int degreeB) {
        if (degreeA <= 1 && degreeB <= 1) {
            // Both nodes have only this edge — it's a critical bridge
            return 255;
        }

        // Jaccard-like overlap: shared / min(degreeA, degreeB)
        // High overlap → low bridge score; zero overlap → max bridge score
        int minDegree = Math.max(1, Math.min(degreeA, degreeB));
        float overlapRatio = (float) sharedNeighborCount / minDegree;

        // Invert: 0 overlap → 255 (critical bridge), full overlap → 0 (redundant)
        int score = Math.round((1.0f - overlapRatio) * 255.0f);
        return Math.clamp(score, 0, 255);
    }

    /**
     * Counts the number of shared neighbors between two nodes in the HebbianGraph.
     *
     * <p>Uses a simple O(degree²) scan — acceptable for MAX_DEGREE ≤ 48.
     * For larger degrees, consider a sorted merge or bitset intersection.</p>
     *
     * @param neighborsA neighbor indices of node A (unsorted)
     * @param countA     number of valid entries in neighborsA
     * @param neighborsB neighbor indices of node B (unsorted)
     * @param countB     number of valid entries in neighborsB
     * @return number of common neighbor indices
     */
    public static int countSharedNeighbors(int[] neighborsA, int countA,
                                            int[] neighborsB, int countB) {
        int shared = 0;
        for (int i = 0; i < countA; i++) {
            int a = neighborsA[i];
            for (int j = 0; j < countB; j++) {
                if (a == neighborsB[j]) {
                    shared++;
                    break;
                }
            }
        }
        return shared;
    }
}
