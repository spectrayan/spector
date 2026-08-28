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
package com.spectrayan.spector.memory.consolidation;

import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.RememberPathway;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.temporal.TemporalKnowledgeGraph;

import java.util.function.Function;

/**
 * Common interface for memory consolidation strategies in Spector.
 *
 * <p>Consolidation identifies near-duplicate memories, classifies contradictions,
 * applies CADP directional resolution to contradictory memories, and merges
 * non-contradictory duplicate memories.</p>
 */
public interface Consolidator extends AutoCloseable {

    /**
     * Executes consolidation across cognitive memory.
     *
     * @param cognitiveRouter        router accessing cognitive tier stores
     * @param index                  memory index
     * @param quantizer              scalar quantizer
     * @param entityDirectory        entity directory
     * @param hyperEntityGraph       hypergraph memory
     * @param temporalKnowledgeGraph temporal knowledge graph
     * @param ingestionTarget        ingestion target for merged memories
     * @param wal                    write-ahead log
     * @param inspectFunction        function to inspect full cognitive records by ID
     */
    void consolidate(
            CognitiveMemoryRouter cognitiveRouter,
            MemoryIndex index,
            ScalarQuantizer quantizer,
            EntityDirectory entityDirectory,
            HyperEntityGraphMemory hyperEntityGraph,
            TemporalKnowledgeGraph temporalKnowledgeGraph,
            RememberPathway ingestionTarget,
            MemoryWal wal,
            Function<String, CognitiveRecord> inspectFunction);

    @Override
    default void close() {
        // Default no-op for stateless consolidator implementations
    }
}
