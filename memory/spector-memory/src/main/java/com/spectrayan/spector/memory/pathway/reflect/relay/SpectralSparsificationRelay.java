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
package com.spectrayan.spector.memory.pathway.reflect.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.memory.graph.BridgeDetector;
import com.spectrayan.spector.memory.graph.SparsificationPlan;
import com.spectrayan.spector.memory.graph.SpectralSparsifier;
import com.spectrayan.spector.memory.graph.hebbian.HebbianEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Spectral Sparsification Relay — leverage-based edge pruning for cognitive graphs.
 *
 * <p>Runs after {@link EntityMaintenanceRelay} (so bridge scores are fresh) and
 * after {@link HebbianHomeostasisRelay} (so weights are current post-decay).
 * Computes a keep/drop plan using per-edge effective resistance from UST sampling,
 * then records shadow-mode telemetry (Tier 0) or actually prunes edges (Tier 1).</p>
 *
 * <h3>Behavior by Tier</h3>
 * <ul>
 *   <li><b>Tier 0 (default, {@code sparsification.enabled=false}):</b> Computes
 *       the plan and records {@code sparsificationCandidates}, {@code meanLeverageDropped},
 *       {@code meanLeverageKept} in {@link com.spectrayan.spector.memory.graph.GraphHealthMetrics}.
 *       No edges are actually removed.</li>
 *   <li><b>Tier 1 ({@code sparsification.enabled=true}):</b> Applies the plan,
 *       removing low-leverage non-bridge edges via concrete implementation methods.</li>
 * </ul>
 *
 * <p><b>Error handling:</b> Any exception is caught and logged — the relay degrades
 * gracefully and never blocks the rest of the reflect cycle.</p>
 *
 * @see SpectralSparsifier
 * @see BridgeDetector
 */
public final class SpectralSparsificationRelay implements SynapticRelay<ReflectSignal> {

    private static final Logger log = LoggerFactory.getLogger(SpectralSparsificationRelay.class);

    /** Bridge protection threshold — edges at or above are never sparsified. */
    private static final int BRIDGE_PROTECTION_THRESHOLD = 224;

    private final boolean pruningEnabled;
    private final float keepFloor;

    /**
     * Creates a sparsification relay with explicit configuration.
     *
     * @param pruningEnabled whether Tier 1 (actual pruning) is enabled
     * @param keepFloor      fractional leverage threshold [0.0, 1.0]; edges below are DROP candidates
     */
    public SpectralSparsificationRelay(boolean pruningEnabled, float keepFloor) {
        this.pruningEnabled = pruningEnabled;
        this.keepFloor = keepFloor;
    }

    /**
     * Creates a sparsification relay with default configuration from {@link SpectorPropertyConstants}.
     */
    public SpectralSparsificationRelay() {
        this(SpectorPropertyConstants.DEFAULT_MEMORY_SPARSIFICATION_ENABLED,
             SpectorPropertyConstants.DEFAULT_MEMORY_SPARSIFICATION_KEEP_FLOOR);
    }

    @Override
    public boolean transmit(final ReflectSignal signal) {
        try {
            sparsifyHebbianGraph(signal);
        } catch (Exception e) {
            log.warn("Spectral sparsification failed: {}", e.getMessage(), e);
        }
        return true;
    }

    private void sparsifyHebbianGraph(ReflectSignal signal) {
        if (signal.hebbianGraph() == null) return;

        var hebbianGraph = signal.hebbianGraph();
        int nodeCount = hebbianGraph.capacity();
        if (nodeCount == 0) return;

        // Build adjacency lists from the HebbianGraphBase interface
        int[][] adjacency = new int[nodeCount][];
        int activeNodes = 0;
        for (int n = 0; n < nodeCount; n++) {
            List<HebbianEdge> edges = hebbianGraph.neighbors(n);
            if (edges == null || edges.isEmpty()) {
                adjacency[n] = new int[0];
            } else {
                adjacency[n] = new int[edges.size()];
                for (int e = 0; e < edges.size(); e++) {
                    adjacency[n][e] = edges.get(e).neighborIndex();
                }
                activeNodes++;
            }
        }

        if (activeNodes < 2) {
            log.debug("Spectral sparsification: fewer than 2 active nodes, skipping");
            return;
        }

        // Compute bridge/leverage scores via UST sampling
        int[][] bridgeScores = BridgeDetector.computeBridgeScoresSpanningTree(
                adjacency, nodeCount,
                BridgeDetector.DEFAULT_SAMPLE_COUNT,
                BridgeDetector.DEFAULT_BUDGET_MS);

        if (bridgeScores == null) {
            log.debug("Spectral sparsification: UST sampling budget exceeded, skipping");
            return;
        }

        // Compute keep/drop plan
        int keepFloorScore = SpectralSparsifier.toKeepFloorScore(keepFloor);
        SparsificationPlan plan = SpectralSparsifier.plan(
                bridgeScores, nodeCount, keepFloorScore, BRIDGE_PROTECTION_THRESHOLD);

        // Record telemetry (always, Tier 0 and Tier 1)
        if (signal.graphMetrics() != null) {
            signal.graphMetrics().recordSparsification(
                    plan.candidateCount(),
                    0,  // pruned count: 0 for now (Tier 1 apply via concrete impl in future PR)
                    plan.meanLeverageDropped(),
                    plan.meanLeverageKept());
        }

        if (plan.isEmpty()) {
            log.debug("Spectral sparsification: no candidates (all edges above keep floor)");
            return;
        }

        if (pruningEnabled) {
            // Tier 1: actual pruning requires concrete HebbianGraphMemory methods
            // (applySparsificationPlan) — will be wired in follow-up PR.
            // For now, log what would happen.
            log.info("Spectral sparsification Tier 1: {} edges would be pruned " +
                    "(apply method pending integration, meanLeverageDropped={}, meanLeverageKept={})",
                    plan.candidateCount(),
                    String.format("%.2f", plan.meanLeverageDropped()),
                    String.format("%.2f", plan.meanLeverageKept()));
        } else {
            log.info("Spectral sparsification Tier 0 (shadow): {} candidates would be pruned " +
                    "(meanLeverageDropped={}, meanLeverageKept={})",
                    plan.candidateCount(),
                    String.format("%.2f", plan.meanLeverageDropped()),
                    String.format("%.2f", plan.meanLeverageKept()));
        }
    }
}
