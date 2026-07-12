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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bridge detection for graph edges — dual-mode: neighbor overlap heuristic
 * and random spanning tree sampling via Wilson's Algorithm.
 *
 * <h3>Biological Analog</h3>
 * <p>In neural networks, hub neurons (high betweenness centrality) are critical
 * for information relay between brain regions. Removing them fragments the network.
 * The bridge score approximates this: edges connecting otherwise-disconnected
 * neighborhoods receive high scores.</p>
 *
 * <h3>Algorithm 1: Neighbor Overlap Heuristic</h3>
 * <p>For an edge A→B, the bridge score is inversely proportional to the number
 * of shared neighbors between A and B. Fast (O(degree²)) but misses transitive
 * bridges where A and B have no direct neighbor overlap yet the edge is the only
 * path between their graph regions.</p>
 *
 * <h3>Algorithm 2: Spanning Tree Sampling (Wilson's Algorithm)</h3>
 * <p>Samples K uniform random spanning trees using <b>loop-erased random walks</b>.
 * An edge appearing in ALL sampled spanning trees is a critical bridge; one appearing
 * in FEW is redundant (many alternative paths exist). This captures transitive
 * bridges that the heuristic misses.</p>
 *
 * <h3>Complexity</h3>
 * <ul>
 *   <li>Heuristic: O(N × MAX_DEGREE²) per full graph scan</li>
 *   <li>Spanning tree: O(K × N × τ) expected, where τ is the cover time (K=15)</li>
 * </ul>
 *
 * <p>Both should be called during ReflectDaemon cycles, NOT on the hot recall path.</p>
 *
 * @see EdgeImportance
 */
public final class BridgeDetector {

    private static final Logger log = LoggerFactory.getLogger(BridgeDetector.class);

    /** Default number of spanning trees to sample. */
    public static final int DEFAULT_SAMPLE_COUNT = 15;

    /** Maximum time budget for spanning tree computation (milliseconds). */
    public static final long DEFAULT_BUDGET_MS = 500;

    private BridgeDetector() {}

    // ══════════════════════════════════════════════════════════════
    // NEIGHBOR OVERLAP HEURISTIC (original algorithm)
    // ══════════════════════════════════════════════════════════════

    /**
     * Computes the bridge score for an edge between two nodes using neighbor overlap.
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

    // ══════════════════════════════════════════════════════════════
    // SPANNING TREE SAMPLING (Wilson's Algorithm)
    // ══════════════════════════════════════════════════════════════

    /**
     * Computes bridge scores for all edges using random spanning tree sampling.
     *
     * <p>Samples {@code sampleCount} uniform random spanning trees via Wilson's
     * Algorithm (loop-erased random walk). For each edge (u,v), the bridge score
     * is proportional to the fraction of spanning trees that include it:</p>
     * <ul>
     *   <li>Edge in ALL trees → score 255 (critical bridge)</li>
     *   <li>Edge in NO trees → score 0 (maximally redundant)</li>
     * </ul>
     *
     * <p>This method operates on an adjacency list representation extracted from
     * the off-heap graph. If the graph has multiple connected components, each
     * component is processed independently (disconnected nodes are skipped).</p>
     *
     * @param adjacency   adjacency lists: {@code adjacency[node]} = array of neighbor indices.
     *                    Null entries or empty arrays indicate isolated nodes.
     * @param nodeCount   number of nodes in the graph (length of adjacency array)
     * @param sampleCount number of spanning trees to sample (recommend: 15)
     * @param budgetMs    maximum time budget in milliseconds (0 = unlimited)
     * @return bridge scores indexed as {@code [node][edgeIndex]} matching the adjacency
     *         layout, or {@code null} if computation was aborted due to time budget
     */
    public static int[][] computeBridgeScoresSpanningTree(
            int[][] adjacency, int nodeCount, int sampleCount, long budgetMs) {

        long startNanos = System.nanoTime();

        // Initialize edge participation counters: [node][edgeIdx] = count
        int[][] participationCount = new int[nodeCount][];
        for (int n = 0; n < nodeCount; n++) {
            participationCount[n] = adjacency[n] != null
                    ? new int[adjacency[n].length] : new int[0];
        }

        // Find active nodes (degree > 0)
        List<Integer> activeNodes = new ArrayList<>();
        for (int n = 0; n < nodeCount; n++) {
            if (adjacency[n] != null && adjacency[n].length > 0) {
                activeNodes.add(n);
            }
        }

        if (activeNodes.size() < 2) {
            // Trivial graph — all edges are bridges by definition
            return scoresToBridgeScores(participationCount, nodeCount, 0);
        }

        // Sample K spanning trees
        var rng = ThreadLocalRandom.current();
        int treesCompleted = 0;

        for (int k = 0; k < sampleCount; k++) {
            // Check time budget before each tree
            if (budgetMs > 0) {
                long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
                if (elapsedMs > budgetMs) {
                    log.warn("BridgeDetector spanning tree budget exceeded after {}ms ({}/{} trees)",
                            elapsedMs, treesCompleted, sampleCount);
                    return null; // Caller falls back to heuristic
                }
            }

            // Generate one spanning tree using Wilson's Algorithm
            int[] parent = wilsonSpanningTree(adjacency, nodeCount, activeNodes, rng);
            if (parent == null) continue;

            // Count edge participation: for each tree edge (child → parent),
            // increment both directions in the adjacency structure
            for (int n = 0; n < nodeCount; n++) {
                if (parent[n] < 0) continue; // root or isolated
                int p = parent[n];
                incrementEdgeParticipation(adjacency, participationCount, n, p);
                incrementEdgeParticipation(adjacency, participationCount, p, n);
            }
            treesCompleted++;
        }

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        log.debug("BridgeDetector spanning tree: {} trees in {}ms ({} active nodes)",
                treesCompleted, elapsedMs, activeNodes.size());

        return scoresToBridgeScores(participationCount, nodeCount, treesCompleted);
    }

    /**
     * Generates a uniform random spanning tree using Wilson's Algorithm.
     *
     * <p>Wilson's Algorithm produces a <b>uniformly random spanning tree</b>
     * via loop-erased random walks (LERW). The procedure:</p>
     * <ol>
     *   <li>Pick a random root node and mark it as "in tree"</li>
     *   <li>For each unvisited active node, perform a random walk until
     *       the walk reaches a node already in the tree</li>
     *   <li>Erase all loops from the walk, then add the remaining path
     *       to the tree</li>
     *   <li>Repeat until all reachable nodes are in the tree</li>
     * </ol>
     *
     * <p>Expected time: O(τ) where τ is the cover time. For sparse, small-world
     * graphs typical in HebbianGraph, this is approximately O(N log N).</p>
     *
     * @param adjacency   adjacency lists
     * @param nodeCount   total node count
     * @param activeNodes list of nodes with degree &gt; 0
     * @param rng         random number generator
     * @return parent array: {@code parent[i]} = parent of node i in the tree,
     *         or -1 for root/isolated nodes
     */
    static int[] wilsonSpanningTree(int[][] adjacency, int nodeCount,
                                     List<Integer> activeNodes,
                                     java.util.random.RandomGenerator rng) {

        int[] parent = new int[nodeCount];
        boolean[] inTree = new boolean[nodeCount];
        Arrays.fill(parent, -1);

        if (activeNodes.isEmpty()) return parent;

        // Start with a random root node
        int root = activeNodes.get(rng.nextInt(activeNodes.size()));
        inTree[root] = true;

        // Maximum walk steps to prevent infinite loops on disconnected components
        int maxSteps = nodeCount * 10;

        // Process each active node not yet in the tree
        for (int activeNode : activeNodes) {
            if (inTree[activeNode]) continue;

            // Loop-erased random walk from activeNode until we hit the tree.
            // next[u] tracks the last step from u — overwriting erases loops.
            int[] next = new int[nodeCount];
            Arrays.fill(next, -1);

            int current = activeNode;
            int steps = 0;

            while (!inTree[current] && steps < maxSteps) {
                int[] neighbors = adjacency[current];
                if (neighbors == null || neighbors.length == 0) break;

                int nextNode = neighbors[rng.nextInt(neighbors.length)];
                next[current] = nextNode;
                current = nextNode;
                steps++;
            }

            if (!inTree[current]) {
                // Disconnected component or walk exceeded budget — skip
                continue;
            }

            // Add the loop-erased path to the tree
            current = activeNode;
            while (!inTree[current]) {
                inTree[current] = true;
                parent[current] = next[current];
                current = next[current];
            }
        }

        return parent;
    }

    /**
     * Finds edge (from → to) in from's adjacency list and increments its
     * participation counter.
     */
    private static void incrementEdgeParticipation(int[][] adjacency,
                                                    int[][] participationCount,
                                                    int from, int to) {
        int[] neighbors = adjacency[from];
        if (neighbors == null) return;
        for (int i = 0; i < neighbors.length; i++) {
            if (neighbors[i] == to) {
                participationCount[from][i]++;
                return;
            }
        }
    }

    /**
     * Converts participation counts to bridge scores in [0, 255].
     *
     * <p>An edge appearing in ALL spanning trees is a critical bridge (score 255).
     * An edge in no trees is maximally redundant (score 0). The score is
     * linearly interpolated between these extremes.</p>
     */
    private static int[][] scoresToBridgeScores(int[][] participationCount,
                                                 int nodeCount, int totalTrees) {
        int[][] scores = new int[nodeCount][];

        for (int n = 0; n < nodeCount; n++) {
            int[] counts = participationCount[n];
            scores[n] = new int[counts.length];

            if (totalTrees == 0) {
                // No trees sampled — everything is a bridge (conservative)
                Arrays.fill(scores[n], 255);
            } else {
                for (int i = 0; i < counts.length; i++) {
                    // participation ratio [0, 1] → bridge score [0, 255]
                    // High participation = critical bridge
                    float ratio = (float) counts[i] / totalTrees;
                    scores[n][i] = Math.clamp(Math.round(ratio * 255.0f), 0, 255);
                }
            }
        }
        return scores;
    }
}
