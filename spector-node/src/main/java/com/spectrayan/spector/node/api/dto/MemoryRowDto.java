package com.spectrayan.spector.node.api.dto;

/**
 * A single row in the memory table view.
 *
 * @param id              unique memory identifier
 * @param textPreview     truncated text content (max 200 chars)
 * @param tier            memory tier (WORKING, EPISODIC, SEMANTIC, PROCEDURAL)
 * @param source          provenance source (USER_STATED, OBSERVED, etc.)
 * @param importance      importance score (0.0 to 1.0)
 * @param valence         emotional valence (-128 to 127)
 * @param timestampMs     creation timestamp (epoch millis)
 * @param agentRecallCount number of times this memory was recalled
 * @param tombstoned      true if the record is tombstoned (forgotten)
 * @param pinned          true if the record is pinned
 * @param resolved        true if the record is resolved
 * @param consolidated    true if the record is consolidated
 * @param tags            synaptic tag strings
 */
public record MemoryRowDto(
    String id,
    String textPreview,
    String tier,
    String source,
    float importance,
    int valence,
    long timestampMs,
    int agentRecallCount,
    boolean tombstoned,
    boolean pinned,
    boolean resolved,
    boolean consolidated,
    String[] tags
) {}
