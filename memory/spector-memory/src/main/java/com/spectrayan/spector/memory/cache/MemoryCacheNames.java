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
package com.spectrayan.spector.memory.cache;

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
     * All managed cache names in the memory engine.
     */
    public static final String[] ALL = {
            GRAPH_OVERVIEW,
            TOPOLOGY_STATS,
            MEMORY_STATS,
            SCORING_STATS
    };
}
