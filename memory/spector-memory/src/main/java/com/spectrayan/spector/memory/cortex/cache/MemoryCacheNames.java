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
package com.spectrayan.spector.memory.cortex.cache;

/**
 * Central registry of standard cache names used across the {@code spector-memory} engine.
 */
public final class MemoryCacheNames {

    private MemoryCacheNames() {}

    /**
     * Cache for graph neighborhood overviews returned by {@code GET /api/v1/memory/graph/overview}.
     */
    public static final String GRAPH_OVERVIEW = "memory-graph-overview";

    /**
     * Cache for entity and relationship topology statistics returned by {@code GET /api/v1/memory/graph/topology-stats}.
     */
    public static final String TOPOLOGY_STATS = "memory-topology-stats";

    /**
     * Cache for overall memory tier statistics returned by {@code GET /api/v1/memory/stats}.
     */
    public static final String MEMORY_STATS = "memory-stats";

    /**
     * Cache for cognitive scoring and salience calibration statistics returned by {@code GET /api/v1/memory/stats/scoring}.
     */
    public static final String SCORING_STATS = "memory-scoring-stats";

    /**
     * Cache for text embeddings computed during ingestion and recall.
     */
    public static final String EMBEDDINGS = "spector-embeddings";

    /**
     * All managed cache names in the memory engine.
     */
    public static final String[] ALL = {
            GRAPH_OVERVIEW,
            TOPOLOGY_STATS,
            MEMORY_STATS,
            SCORING_STATS,
            EMBEDDINGS
    };
}
