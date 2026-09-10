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
package com.spectrayan.spector.kernel.store;

import java.nio.file.Path;
import java.util.List;

/**
 * Common interface for Hebbian graph operations.
 */
public interface HebbianGraphBase extends AutoCloseable {

    /** Maximum number of nodes (memories) this graph can hold. */
    int capacity();

    /**
     * Strengthens or creates the bidirectional edge between two nodes.
     *
     * @param nodeA       first memory index
     * @param nodeB       second memory index
     * @param weightDelta weight increment
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

    /**
     * Sets the per-node decay modulator for arousal-modulated edge decay.
     *
     * @param modulator per-node modifier
     */
    void setDecayModulator(DecayModulator modulator);

    /**
     * Decays all edges by the given factor and prunes edges below threshold.
     *
     * @param decayFactor multiplicative factor
     * @return number of edges removed
     */
    int decayEdges(float decayFactor);

    /**
     * Decays all edges and collects graph health metrics.
     *
     * @param decayFactor multiplicative factor
     * @param metrics     sink for health statistics
     * @return number of edges removed
     */
    int decayEdges(float decayFactor, GraphHealthSink metrics);

    /**
     * Marks a session boundary to trigger bridge score recalculation on next decay.
     *
     * @param durationMs session duration in milliseconds
     */
    void setSessionBoundary(long durationMs);

    /** Whether a new session has started since the last decay cycle. */
    boolean isNewSession();

    /**
     * BFS spreading activation from a seed node up to the given depth.
     *
     * @param node  seed memory index
     * @param depth maximum BFS depth
     * @return list of reachable edges (weight-decayed by depth)
     */
    List<HebbianEdge> activateNeighbors(int node, int depth);

    /**
     * Saves the graph to a file.
     *
     * @param filePath output file path
     */
    void save(Path filePath);

    /**
     * Resets all edges by zero-filling.
     *
     * @return total edges that existed before reset
     */
    int reset();

    @Override
    void close();
}
