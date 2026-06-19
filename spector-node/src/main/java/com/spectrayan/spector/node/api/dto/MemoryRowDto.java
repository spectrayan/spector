package com.spectrayan.spector.node.api.dto;

import java.util.Map;

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
 * @param arousal         emotional intensity (unsigned 0-255)
 * @param sourceModality  source modality (TEXT, IMAGE, AUDIO, VIDEO)
 * @param l2Norm          L2 norm of the embedding vector
 * @param storageStrength Two-Factor Memory storage strength (1.0 = baseline)
 * @param synapticTagsHex 64-bit Bloom filter as hex string (e.g., "0x00A4F2...")
 * @param metadata        multimodal metadata map (nullable — null for text-only memories)
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
    String[] tags,
    // ── Extended fields (Profile card) ──
    int arousal,
    String sourceModality,
    float l2Norm,
    float storageStrength,
    String synapticTagsHex,
    Map<String, String> metadata
) {
    /**
     * Backward-compatible constructor for code that doesn't supply extended fields.
     */
    public MemoryRowDto(String id, String textPreview, String tier, String source,
                        float importance, int valence, long timestampMs,
                        int agentRecallCount, boolean tombstoned, boolean pinned,
                        boolean resolved, boolean consolidated, String[] tags) {
        this(id, textPreview, tier, source, importance, valence, timestampMs,
             agentRecallCount, tombstoned, pinned, resolved, consolidated, tags,
             0, "TEXT", 0f, 1.0f, "0x0000000000000000", null);
    }
}
