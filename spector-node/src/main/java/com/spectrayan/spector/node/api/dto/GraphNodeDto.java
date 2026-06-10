package com.spectrayan.spector.node.api.dto;

/**
 * A node in the memory graph response.
 *
 * <p>Represents a single cognitive memory in the graph visualization,
 * with enough metadata for rendering (tier color, node size by importance,
 * tooltip text).</p>
 *
 * @param id          unique memory identifier
 * @param tier        cognitive tier (WORKING, EPISODIC, SEMANTIC, PROCEDURAL)
 * @param textPreview truncated text content (max 120 chars)
 * @param importance  importance score (0.0 to 1.0) — controls node size
 * @param valence     emotional valence (-128 to 127) — controls node hue shift
 */
public record GraphNodeDto(
    String id,
    String tier,
    String textPreview,
    float importance,
    int valence
) {}
