/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.core.graph;

import java.util.Arrays;
import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * Pure mathematical kernel for graph centrality and bridge detection (ADR-0033 Domain 4, #11 &amp; #12).
 *
 * <p>Purity Tier: T1 (neighbor overlap) &amp; T3 (Wilson's spanning tree, deterministic given seed).</p>
 */
public final class GraphCentralityKernel {

    public static final int DEFAULT_SAMPLE_COUNT = 15;
    public static final int DEFAULT_MAX_WALK_STEPS = 10000;

    private GraphCentralityKernel() {}

    /**
     * Computes the bridge score for an edge between two nodes using neighbor overlap (ADR-0033 #11).
     *
     * <p>Returns a value in [0, 255] where 255 = critical bridge (no shared neighbors)
     * and 0 = maximally redundant (all neighbors shared).</p>
     *
     * @param sharedNeighbors number of common neighbors between the two nodes
     * @param degreeA         degree of node A
     * @param degreeB         degree of node B
     * @return bridge score in [0, 255]
     */
    public static int neighborOverlapBridgeScore(final int sharedNeighbors, final int degreeA, final int degreeB) {
        if (degreeA <= 1 && degreeB <= 1) {
            return 255;
        }
        final int minDegree = Math.max(1, Math.min(degreeA, degreeB));
        final float overlapRatio = (float) sharedNeighbors / minDegree;
        final int score = Math.round((1.0f - overlapRatio) * 255.0f);
        return Math.clamp(score, 0, 255);
    }

    /**
     * Counts the number of shared neighbors between two nodes given their neighbor index arrays.
     *
     * @param neighborsA neighbor array of node A
     * @param countA     valid count in neighborsA
     * @param neighborsB neighbor array of node B
     * @param countB     valid count in neighborsB
     * @return count of common neighbor indices
     */
    public static int countSharedNeighbors(
            final int[] neighborsA, final int countA, final int[] neighborsB, final int countB) {
        if (neighborsA == null || neighborsB == null || countA <= 0 || countB <= 0) {
            return 0;
        }
        final int limitA = Math.min(countA, neighborsA.length);
        final int limitB = Math.min(countB, neighborsB.length);
        int shared = 0;
        for (int i = 0; i < limitA; i++) {
            final int a = neighborsA[i];
            for (int j = 0; j < limitB; j++) {
                if (a == neighborsB[j]) {
                    shared++;
                    break;
                }
            }
        }
        return shared;
    }

    /**
     * Computes bridge scores for all edges using random spanning tree sampling via Wilson's Algorithm (ADR-0033 #12).
     *
     * <p>Purity Tier: T3. Deterministic given {@code seed}. Bounded by {@code maxWalkSteps}.</p>
     *
     * @param adjacency    adjacency list: {@code adjacency[node]} = array of neighbor indices
     * @param nodeCount    number of nodes
     * @param sampleCount  number of spanning trees to sample
     * @param maxWalkSteps maximum steps per random walk
     * @param seed         PRNG seed for deterministic execution
     * @return bridge scores indexed matching {@code adjacency[node][edgeIndex]}, clamped to [0, 255]
     */
    public static int[][] computeWilsonBridgeScores(
            final int[][] adjacency, final int nodeCount, final int sampleCount, final int maxWalkSteps, final long seed) {
        if (adjacency == null || nodeCount <= 0) {
            return new int[0][];
        }

        final int[][] participationCount = new int[nodeCount][];
        int activeCount = 0;
        for (int n = 0; n < nodeCount; n++) {
            if (adjacency[n] != null && adjacency[n].length > 0) {
                participationCount[n] = new int[adjacency[n].length];
                activeCount++;
            } else {
                participationCount[n] = new int[0];
            }
        }

        if (activeCount < 2) {
            return scoresToBridgeScores(participationCount, nodeCount, 0);
        }

        final int[] activeNodes = new int[activeCount];
        int idx = 0;
        for (int n = 0; n < nodeCount; n++) {
            if (adjacency[n] != null && adjacency[n].length > 0) {
                activeNodes[idx++] = n;
            }
        }

        final Random rng = new Random(seed);
        int treesCompleted = 0;
        final int boundSteps = Math.max(100, maxWalkSteps);

        for (int k = 0; k < sampleCount; k++) {
            final int[] parent = wilsonSpanningTree(adjacency, nodeCount, activeNodes, activeCount, boundSteps, rng);
            if (parent == null) {
                continue;
            }

            for (int n = 0; n < nodeCount; n++) {
                if (parent[n] < 0) {
                    continue;
                }
                final int p = parent[n];
                incrementEdgeParticipation(adjacency, participationCount, n, p);
                incrementEdgeParticipation(adjacency, participationCount, p, n);
            }
            treesCompleted++;
        }

        return scoresToBridgeScores(participationCount, nodeCount, treesCompleted);
    }

    /**
     * Generates a uniform random spanning tree using Wilson's Algorithm (loop-erased random walk).
     *
     * @param adjacency   adjacency lists
     * @param nodeCount   total node count
     * @param activeNodes array of active node indices (degree &gt; 0)
     * @param activeCount count of active nodes
     * @param maxSteps    maximum walk steps
     * @param rng         random generator
     * @return parent array where {@code parent[i]} = parent of node i, or -1 for root/isolated
     */
    public static int[] wilsonSpanningTree(
            final int[][] adjacency,
            final int nodeCount,
            final int[] activeNodes,
            final int activeCount,
            final int maxSteps,
            final RandomGenerator rng) {
        if (activeCount == 0) {
            final int[] parent = new int[nodeCount];
            Arrays.fill(parent, -1);
            return parent;
        }

        final int[] parent = new int[nodeCount];
        final boolean[] inTree = new boolean[nodeCount];
        Arrays.fill(parent, -1);

        final int root = activeNodes[rng.nextInt(activeCount)];
        inTree[root] = true;

        final int[] next = new int[nodeCount];

        for (int i = 0; i < activeCount; i++) {
            final int activeNode = activeNodes[i];
            if (inTree[activeNode]) {
                continue;
            }

            Arrays.fill(next, -1);
            int current = activeNode;
            int steps = 0;

            while (!inTree[current] && steps < maxSteps) {
                final int[] neighbors = adjacency[current];
                if (neighbors == null || neighbors.length == 0) {
                    break;
                }
                final int nextNode = neighbors[rng.nextInt(neighbors.length)];
                next[current] = nextNode;
                current = nextNode;
                steps++;
            }

            if (!inTree[current]) {
                continue;
            }

            current = activeNode;
            while (!inTree[current]) {
                inTree[current] = true;
                parent[current] = next[current];
                current = next[current];
            }
        }

        return parent;
    }

    private static void incrementEdgeParticipation(
            final int[][] adjacency, final int[][] participationCount, final int from, final int to) {
        final int[] neighbors = adjacency[from];
        if (neighbors == null) {
            return;
        }
        for (int i = 0; i < neighbors.length; i++) {
            if (neighbors[i] == to) {
                participationCount[from][i]++;
                return;
            }
        }
    }

    private static int[][] scoresToBridgeScores(
            final int[][] participationCount, final int nodeCount, final int totalTrees) {
        final int[][] scores = new int[nodeCount][];
        for (int n = 0; n < nodeCount; n++) {
            final int[] counts = participationCount[n];
            scores[n] = new int[counts.length];
            if (totalTrees == 0) {
                Arrays.fill(scores[n], 255);
            } else {
                for (int i = 0; i < counts.length; i++) {
                    final float ratio = (float) counts[i] / totalTrees;
                    scores[n][i] = Math.clamp(Math.round(ratio * 255.0f), 0, 255);
                }
            }
        }
        return scores;
    }
}
