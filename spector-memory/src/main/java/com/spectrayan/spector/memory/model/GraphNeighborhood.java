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
package com.spectrayan.spector.memory.model;

import java.util.List;

/**
 * Graph neighborhood result — nodes and edges from a BFS traversal or sampled overview.
 *
 * <p>Returned by {@link com.spectrayan.spector.memory.graph.CognitiveGraphFacade} for
 * both single-memory neighborhood queries and full-graph overview sampling.</p>
 *
 * @param centerId the memory ID at the center of the traversal (null for overview)
 * @param nodes    the discovered graph nodes
 * @param edges    the discovered graph edges
 * @param error    error message if the query failed (null on success)
 * @since 1.1.0
 */
public record GraphNeighborhood(
        String centerId,
        List<GraphNode> nodes,
        List<GraphEdge> edges,
        String error
) {

    /** Returns an empty neighborhood with an optional error message. */
    public static GraphNeighborhood empty(String error) {
        return new GraphNeighborhood(null, List.of(), List.of(), error);
    }

    /**
     * A node in the cognitive graph, representing a single memory.
     *
     * @param id          the memory ID
     * @param tier        the memory tier name (WORKING, EPISODIC, SEMANTIC, PROCEDURAL)
     * @param textPreview truncated text preview
     * @param importance  importance score
     * @param valence     emotional valence
     * @param timestampMs creation timestamp in epoch milliseconds
     * @param entityNames entity names associated with this memory
     */
    public record GraphNode(
            String id,
            String tier,
            String textPreview,
            float importance,
            byte valence,
            long timestampMs,
            List<String> entityNames
    ) {}

    /**
     * An edge in the cognitive graph, connecting two memories.
     *
     * @param sourceId         source memory ID
     * @param targetId         target memory ID
     * @param type             edge type: "HEBBIAN", "TEMPORAL", or "ENTITY"
     * @param relationType     relation type for ENTITY edges (null for others)
     * @param weight           edge weight (0.0 to 1.0)
     * @param sourceEntityType entity type of source (for ENTITY edges)
     * @param targetEntityType entity type of target (for ENTITY edges)
     */
    public record GraphEdge(
            String sourceId,
            String targetId,
            String type,
            String relationType,
            double weight,
            String sourceEntityType,
            String targetEntityType
    ) {}
}
