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

import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.synapse.SynapticHeaderConstants;

import java.time.Instant;
import java.util.Arrays;

/**
 * Complete cognitive snapshot of a single memory — the "X-ray" view.
 *
 * <p>Combines data from three subsystems into a single record:</p>
 * <ul>
 *   <li><b>MemoryIndex</b>: id, text, source, tags, location</li>
 *   <li><b>CognitiveHeader</b> (64-byte off-heap): timestamp, synaptic tags bloom,
 *       importance, recall counts, valence, arousal, storage strength, flags</li>
 *   <li><b>Vector payload</b>: quantized INT8 vector bytes</li>
 * </ul>
 *
 * <p>This closes the gap where no single API call could return the full
 * text ↔ cognitive header ↔ vector correlation for a given memory.</p>
 *
 * <h3>Usage via MCP</h3>
 * <pre>{@code
 *   // Java API
 *   CognitiveRecord record = memory.inspect("mem-42");
 *
 *   // MCP tool
 *   { "tool": "memory_inspect", "arguments": { "id": "mem-42" } }
 * }</pre>
 *
 * @param id                 unique memory identifier
 * @param text               raw memory text content
 * @param memoryType         cognitive tier (WORKING, EPISODIC, SEMANTIC, PROCEDURAL)
 * @param source             provenance (USER_STATED, OBSERVED, INFERRED, PROCEDURAL)
 * @param tags               synaptic tag strings (human-readable labels)
 * @param timestampMs        when the memory was created (epoch millis)
 * @param synapticTags       64-bit Bloom filter of encoded tag hashes
 * @param exactNorm          L2 norm of the vector (for distance computation)
 * @param importance         importance score (0.0–10.0, set by Prediction Error engine)
 * @param agentRecallCount   times the agent explicitly reinforced this memory
 * @param spectorRecallCount times the memory appeared in recall results (auto-LTP)
 * @param centroidId         IVF partition routing ID
 * @param valence            emotional coloring (-128 to +127)
 * @param arousal            emotional intensity (unsigned 0-255)
 * @param storageStrength    Two-Factor Memory storage strength (≥1.0)
 * @param flags              raw flags byte (tombstone, consolidated, pinned, resolved)
 * @param quantizedVector    quantized INT8 vector bytes (nullable if not requested)
 * @param partitionIndex     partition where this memory is stored (-1 for non-partitioned)
 * @param byteOffset         byte offset within the tier's MemorySegment
 *
 * @see com.spectrayan.spector.memory.SpectorMemory#inspect(String)
 */
public record CognitiveRecord(
        // ── Identity ──
        String id,
        String text,
        MemoryType memoryType,
        MemorySource source,
        String[] tags,

        // ── Cognitive Header (decoded from 64-byte off-heap) ──
        long timestampMs,
        long synapticTags,
        float exactNorm,
        float importance,
        int agentRecallCount,
        int spectorRecallCount,
        short centroidId,
        byte valence,
        byte arousal,
        float storageStrength,
        byte flags,

        // ── Vector ──
        byte[] quantizedVector,

        // ── Physical Location ──
        int partitionIndex,
        long byteOffset
) {

    // ══════════════════════════════════════════════════════════════
    // FLAG INTROSPECTION HELPERS
    // ══════════════════════════════════════════════════════════════

    /** Returns true if this memory has been logically deleted. */
    public boolean isTombstoned() {
        return SynapticHeaderConstants.isTombstoned(flags);
    }

    /** Returns true if this memory has been consolidated (reflected into Semantic tier). */
    public boolean isConsolidated() {
        return SynapticHeaderConstants.isConsolidated(flags);
    }

    /** Returns true if this memory is pinned (exempt from decay and pruning). */
    public boolean isPinned() {
        return SynapticHeaderConstants.isPinned(flags);
    }

    /** Returns true if this memory is resolved (Zeigarnik Effect — task completed). */
    public boolean isResolved() {
        return SynapticHeaderConstants.isResolved(flags);
    }

    /** Returns the creation timestamp as an Instant. */
    public Instant createdAt() {
        return Instant.ofEpochMilli(timestampMs);
    }

    /** Returns the age of the memory in days (from now). */
    public float ageDays() {
        long nowMs = System.currentTimeMillis();
        return (nowMs - timestampMs) / (1000f * 60 * 60 * 24);
    }

    /** Returns the total recall count (agent + spector). */
    public int totalRecallCount() {
        return agentRecallCount + spectorRecallCount;
    }

    /** Returns true if the quantized vector is available. */
    public boolean hasVector() {
        return quantizedVector != null && quantizedVector.length > 0;
    }

    // ══════════════════════════════════════════════════════════════
    // JSON EXPORT
    // ══════════════════════════════════════════════════════════════

    /**
     * Returns a JSON-compatible string representation of this cognitive record.
     *
     * <p>Suitable for MCP tool responses and memory export. The quantized vector
     * is represented as a hex string to avoid JSON array verbosity.</p>
     */
    public String toJson() {
        var sb = new StringBuilder(512);
        sb.append("{");
        sb.append("\"id\":\"").append(escapeJson(id)).append("\",");
        sb.append("\"text\":\"").append(escapeJson(text)).append("\",");
        sb.append("\"memoryType\":\"").append(memoryType).append("\",");
        sb.append("\"source\":\"").append(source).append("\",");
        sb.append("\"tags\":").append(tagsToJson()).append(",");
        sb.append("\"createdAt\":\"").append(createdAt()).append("\",");
        sb.append("\"ageDays\":").append(String.format("%.2f", ageDays())).append(",");
        sb.append("\"synapticTags\":\"0x").append(Long.toHexString(synapticTags)).append("\",");
        sb.append("\"exactNorm\":").append(exactNorm).append(",");
        sb.append("\"importance\":").append(String.format("%.4f", importance)).append(",");
        sb.append("\"agentRecallCount\":").append(agentRecallCount).append(",");
        sb.append("\"spectorRecallCount\":").append(spectorRecallCount).append(",");
        sb.append("\"centroidId\":").append(centroidId).append(",");
        sb.append("\"valence\":").append(valence).append(",");
        sb.append("\"arousal\":").append(Byte.toUnsignedInt(arousal)).append(",");
        sb.append("\"storageStrength\":").append(String.format("%.4f", storageStrength)).append(",");
        sb.append("\"tombstoned\":").append(isTombstoned()).append(",");
        sb.append("\"consolidated\":").append(isConsolidated()).append(",");
        sb.append("\"pinned\":").append(isPinned()).append(",");
        sb.append("\"resolved\":").append(isResolved()).append(",");
        sb.append("\"partitionIndex\":").append(partitionIndex).append(",");
        sb.append("\"byteOffset\":").append(byteOffset);
        if (quantizedVector != null) {
            sb.append(",\"quantizedVectorHex\":\"").append(bytesToHex(quantizedVector)).append("\"");
            sb.append(",\"vectorDimensions\":").append(quantizedVector.length);
        }
        sb.append("}");
        return sb.toString();
    }

    // ── Internal helpers ──

    private String tagsToJson() {
        if (tags == null || tags.length == 0) return "[]";
        var sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < tags.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(tags[i])).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String bytesToHex(byte[] bytes) {
        var sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }
}
