package com.spectrayan.spector.node.api.dto;

import java.util.List;

/**
 * Top-level response DTO for the memory graph API.
 *
 * <p>Contains the full graph neighborhood for a single memory
 * ({@code memoryId} non-null) or a sampled overview of the entire
 * graph ({@code memoryId} null).</p>
 *
 * <p>The graph explorer renders nodes as spheres (sized by importance,
 * colored by tier) and edges as lines (styled by type). This DTO
 * provides all the data needed for that rendering.</p>
 *
 * @param memoryId the focal memory ID (null for overview queries)
 * @param nodes    graph nodes (memories)
 * @param edges    graph edges (Hebbian, Temporal, Entity)
 */
public record MemoryGraphDto(
    String memoryId,
    List<GraphNodeDto> nodes,
    List<GraphEdgeDto> edges
) {}
