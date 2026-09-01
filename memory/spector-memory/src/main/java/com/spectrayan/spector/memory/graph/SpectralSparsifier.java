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

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Leverage-based spectral sparsification for Spector's cognitive graphs.
 *
 * <p>Uses per-edge <b>effective resistance</b> estimates from
 * {@link BridgeDetector#computeBridgeScoresSpanningTree} (Wilson's algorithm
 * uniform spanning tree sampling) to identify and prune structurally redundant
 * edges while preserving the graph's spectral structure.</p>
 *
 * <h3>Mathematical Basis</h3>
 * <p>By Kirchhoff's theorem, an edge's inclusion frequency across uniform
 * random spanning trees equals its leverage score: {@code P(e ∈ UST) = wₑ · R_eff(e)}.
 * Edges with low leverage are structurally redundant (many parallel paths exist);
 * edges with high leverage are load-bearing bridges.</p>
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Compute per-edge leverage from UST bridge scores (already [0,255])</li>
 *   <li>For each edge: if bridge ≥ threshold → KEEP; if leverage ≥ keepFloor → KEEP;
 *       else → DROP candidate</li>
 *   <li>Record candidate statistics for telemetry</li>
 * </ol>
 *
 * <h3>Tiered Rollout</h3>
 * <ul>
 *   <li><b>Tier 0 (shadow):</b> compute plan, record candidates, drop nothing</li>
 *   <li><b>Tier 1 (prune):</b> actually remove low-leverage non-bridge edges</li>
 *   <li><b>Tier 2 (reweight):</b> Spielman–Srivastava reweighting (deferred)</li>
 * </ul>
 *
 * <p><b>Thread safety:</b> Stateless utility — all state is in the plan.
 * Runs under the graph's existing lock during the reflect cycle.</p>
 *
 * @see BridgeDetector
 * @see SparsificationPlan
 * @see GraphHealthMetrics
 */
public final class SpectralSparsifier {

    private static final Logger log = LoggerFactory.getLogger(SpectralSparsifier.class);

    private SpectralSparsifier() {}

    /**
     * Computes a keep/drop plan for graph edges based on leverage (bridge) scores.
     *
     * <p>The bridge scores are interpreted as leverage estimates: higher scores
     * indicate structurally important (load-bearing) edges. Edges below
     * {@code keepFloorScore} that are not bridge-protected are candidates for
     * removal.</p>
     *
     * @param bridgeScores     per-edge bridge scores: {@code bridgeScores[node][edgeIdx]} in [0,255].
     *                         These are UST inclusion frequencies from {@link BridgeDetector}.
     * @param nodeCount        number of nodes in the graph
     * @param keepFloorScore   minimum bridge score to keep (edges below this are DROP candidates).
     *                         Specified as an int in [0,255] matching the bridge score scale.
     * @param bridgeThreshold  bridge protection threshold (edges at or above are always KEEP).
     *                         Typically 224.
     * @return a {@link SparsificationPlan} with per-edge keep/drop decisions
     */
    public static SparsificationPlan plan(int[][] bridgeScores, int nodeCount,
                                          int keepFloorScore, int bridgeThreshold) {

        if (bridgeScores == null || nodeCount == 0) {
            return new SparsificationPlan(new boolean[0][], 0, 0, 0f, 0f);
        }

        boolean[][] keep = new boolean[nodeCount][];
        int totalEdges = 0;
        int candidateCount = 0;
        long leverageSumDropped = 0;
        long leverageSumKept = 0;
        int keptCount = 0;

        for (int n = 0; n < nodeCount; n++) {
            int[] scores = bridgeScores[n];
            if (scores == null || scores.length == 0) {
                keep[n] = new boolean[0];
                continue;
            }

            keep[n] = new boolean[scores.length];
            for (int e = 0; e < scores.length; e++) {
                totalEdges++;
                int score = scores[e];

                if (score >= bridgeThreshold) {
                    // Bridge-protected: always keep
                    keep[n][e] = true;
                    leverageSumKept += score;
                    keptCount++;
                } else if (score >= keepFloorScore) {
                    // Above keep floor: keep
                    keep[n][e] = true;
                    leverageSumKept += score;
                    keptCount++;
                } else {
                    // Below keep floor, not bridge-protected: candidate for drop
                    keep[n][e] = false;
                    leverageSumDropped += score;
                    candidateCount++;
                }
            }
        }

        float meanDropped = candidateCount > 0 ? (float) leverageSumDropped / candidateCount : 0f;
        float meanKept = keptCount > 0 ? (float) leverageSumKept / keptCount : 0f;

        log.debug("Sparsification plan: {}/{} edges are drop candidates " +
                        "(meanLeverageDropped={}, meanLeverageKept={})",
                candidateCount, totalEdges, meanDropped, meanKept);

        return new SparsificationPlan(keep, totalEdges, candidateCount, meanDropped, meanKept);
    }

    /**
     * Computes the keep floor score from a fractional keep floor value.
     *
     * <p>Converts a float in [0.0, 1.0] to the [0, 255] bridge score scale.</p>
     *
     * @param keepFloor fractional keep floor (e.g., 0.15 = keep edges with leverage ≥ 15%)
     * @return integer keep floor in [0, 255]
     */
    public static int toKeepFloorScore(float keepFloor) {
        return Math.clamp(Math.round(keepFloor * 255.0f), 0, 255);
    }
}
