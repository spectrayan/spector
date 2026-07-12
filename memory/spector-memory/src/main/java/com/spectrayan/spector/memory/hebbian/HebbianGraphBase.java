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
package com.spectrayan.spector.memory.hebbian;

import com.spectrayan.spector.memory.graph.GraphHealthMetrics;
import com.spectrayan.spector.memory.hebbian.HebbianGraph.DecayModulator;
import com.spectrayan.spector.memory.hebbian.HebbianGraph.HebbianEdge;

import java.nio.file.Path;
import java.util.List;

/**
 * Common interface for Hebbian graph implementations — both the legacy fixed-width
 * layout ({@link HebbianGraph}, V2) and the sparse CSR layout ({@link HebbianGraphCsr}, V3).
 *
 * <h3>Biological Analog</h3>
 * <p>In the cortex, neurons form association networks where activating one memory
 * spreads activation to connected memories. This graph models those co-recall
 * edges with bounded degree and decaying weights.</p>
 *
 * <p>All implementations are thread-safe for concurrent reads. Structural mutations
 * (strengthen, decay) are synchronized via {@link java.util.concurrent.locks.ReentrantLock}.</p>
 *
 * @see HebbianGraph The original V2 fixed-width implementation (deprecated)
 * @see HebbianGraphCsr The CSR V3 sparse implementation (preferred)
 */
public sealed interface HebbianGraphBase extends AutoCloseable
        permits HebbianGraph, HebbianGraphCsr {

    // ── Capacity ──

    /** Maximum number of nodes (memories) this graph can hold. */
    int capacity();

    // ── Edge Operations ──

    /**
     * Strengthens or creates the bidirectional edge between two nodes.
     *
     * @param nodeA      first memory index
     * @param nodeB      second memory index
     * @param weightDelta weight increment (typically 1.0 for co-recall)
     */
    void strengthen(int nodeA, int nodeB, float weightDelta);

    /**
     * Returns all neighbors of the given node, sorted by weight descending.
     *
     * @param node memory index
     * @return list of edges (never null, may be empty)
     */
    List<HebbianEdge> neighbors(int node);

    /**
     * Returns the degree (number of edges) for the given node.
     *
     * @param node memory index
     * @return edge count
     */
    int degree(int node);

    /** Returns the total number of edges across all nodes. */
    int totalEdges();

    // ── Decay ──

    /**
     * Sets the per-node decay modulator for arousal-modulated edge decay.
     *
     * @param modulator per-node modifier (null = uniform decay)
     */
    void setDecayModulator(DecayModulator modulator);

    /**
     * Decays all edges by the given factor and prunes edges below threshold.
     *
     * @param decayFactor multiplicative factor (e.g., 0.9 for 10% decay)
     * @return number of edges removed
     */
    int decayEdges(float decayFactor);

    /**
     * Decays all edges and collects graph health metrics.
     *
     * @param decayFactor multiplicative factor
     * @param metrics     collector for health statistics
     * @return number of edges removed
     */
    int decayEdges(float decayFactor, GraphHealthMetrics metrics);

    // ── Session Tracking ──

    /**
     * Marks a session boundary to trigger bridge score recalculation on next decay.
     *
     * @param durationMs session duration in milliseconds
     */
    void setSessionBoundary(long durationMs);

    /** Whether a new session has started since the last decay cycle. */
    boolean isNewSession();

    // ── Spreading Activation ──

    /**
     * BFS spreading activation from a seed node up to the given depth.
     *
     * @param node  seed memory index
     * @param depth maximum BFS depth
     * @return list of reachable edges (weight-decayed by depth)
     */
    List<HebbianEdge> activateNeighbors(int node, int depth);

    // ── Persistence ──

    /**
     * Saves the graph to a file.
     *
     * @param filePath output file path
     */
    void save(Path filePath);

    // ── Lifecycle ──

    /**
     * Resets all edges by zero-filling. The graph remains usable after reset.
     *
     * @return total edges that existed before reset
     */
    int reset();

    /** Returns the off-heap memory footprint in bytes. */
    long memoryUsageBytes();

    /** Releases all off-heap resources. */
    @Override
    void close();
}
