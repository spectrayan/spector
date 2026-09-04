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
package com.spectrayan.spector.memory.kernel.layout.compat;

import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields.*;

/**
 * Read-only decoder for legacy V1 (64-byte mixed-tenancy) engram headers.
 *
 * <p>Quarantined in this {@code compat} package to isolate legacy V1 header field offsets
 * from live kernel layouts (spec task 3.4, requirement R2.1, R3.1). Provides read-only
 * access for data migration tools (such as {@code HeaderMigrator}) and backward-compatibility
 * readers.</p>
 */
public final class LegacyEncodingHeaderReader {

    /** Singleton instance. */
    public static final LegacyEncodingHeaderReader INSTANCE = new LegacyEncodingHeaderReader();

    private LegacyEncodingHeaderReader() {}

    /** Header size in bytes (64 bytes). */
    public int headerBytes() {
        return HEADER_BYTES;
    }

    /** Legacy layout version number (1). */
    public int version() {
        return HEADER_VERSION_V1;
    }

    public byte readVersion(MemorySegment seg, long off) {
        return seg.get(LAYOUT_HEADER_VERSION, off + OFFSET_HEADER_VERSION);
    }

    public byte readFlags(MemorySegment seg, long off) {
        return seg.get(LAYOUT_FLAGS, off + OFFSET_FLAGS);
    }

    public byte readValence(MemorySegment seg, long off) {
        return seg.get(LAYOUT_VALENCE, off + OFFSET_VALENCE);
    }

    public byte readArousal(MemorySegment seg, long off) {
        return seg.get(LAYOUT_AROUSAL, off + OFFSET_AROUSAL);
    }

    public float readImportance(MemorySegment seg, long off) {
        return seg.get(LAYOUT_IMPORTANCE, off + OFFSET_IMPORTANCE);
    }

    public long readTimestamp(MemorySegment seg, long off) {
        return seg.get(LAYOUT_TIMESTAMP, off + OFFSET_TIMESTAMP);
    }

    public int readAgentRecallCount(MemorySegment seg, long off) {
        return seg.get(LAYOUT_AGENT_RECALL_COUNT, off + OFFSET_AGENT_RECALL_COUNT);
    }

    public float readExactNorm(MemorySegment seg, long off) {
        return seg.get(LAYOUT_EXACT_NORM, off + OFFSET_EXACT_NORM);
    }

    public long readSynapticTags(MemorySegment seg, long off) {
        return seg.get(LAYOUT_SYNAPTIC_TAGS, off + OFFSET_SYNAPTIC_TAGS);
    }

    public short readCentroidId(MemorySegment seg, long off) {
        return seg.get(LAYOUT_CENTROID_ID, off + OFFSET_CENTROID_ID);
    }

    public byte readConsolidationFlags(MemorySegment seg, long off) {
        return seg.get(LAYOUT_CONSOLIDATION_FLAGS, off + OFFSET_CONSOLIDATION_FLAGS);
    }

    public byte readEncodingProfile(MemorySegment seg, long off) {
        return seg.get(LAYOUT_ENCODING_PROFILE, off + OFFSET_ENCODING_PROFILE);
    }

    public float readStorageStrength(MemorySegment seg, long off) {
        return seg.get(LAYOUT_STORAGE_STRENGTH, off + OFFSET_STORAGE_STRENGTH);
    }

    public int readSpectorRecallCount(MemorySegment seg, long off) {
        return seg.get(LAYOUT_SPECTOR_RECALL_COUNT, off + OFFSET_SPECTOR_RECALL_COUNT);
    }

    public byte readEncodingAlpha(MemorySegment seg, long off) {
        return seg.get(LAYOUT_ENCODING_ALPHA, off + OFFSET_ENCODING_ALPHA);
    }

    public byte readEncodingBeta(MemorySegment seg, long off) {
        return seg.get(LAYOUT_ENCODING_BETA, off + OFFSET_ENCODING_BETA);
    }

    public short readSoulVersion(MemorySegment seg, long off) {
        return seg.get(LAYOUT_SOUL_VERSION, off + OFFSET_SOUL_VERSION);
    }

    public long readLastAutoLtp(MemorySegment seg, long off) {
        return seg.get(LAYOUT_LAST_AUTO_LTP, off + OFFSET_LAST_AUTO_LTP);
    }

    public float readEncodingSurprise(MemorySegment seg, long off) {
        return seg.get(LAYOUT_ENCODING_SURPRISE, off + OFFSET_ENCODING_SURPRISE);
    }

    public byte readLastRecallProfile(MemorySegment seg, long off) {
        return seg.get(ValueLayout.JAVA_BYTE, off + OFFSET_LAST_RECALL_PROFILE);
    }

    /**
     * Reads all V1 header fields into an immutable {@link CognitiveHeader}.
     */
    public CognitiveHeader readHeader(MemorySegment seg, long off) {
        return new CognitiveHeader(
                readTimestamp(seg, off),
                readSynapticTags(seg, off),
                readExactNorm(seg, off),
                readImportance(seg, off),
                readAgentRecallCount(seg, off),
                readCentroidId(seg, off),
                readValence(seg, off),
                readFlags(seg, off),
                readArousal(seg, off),
                readStorageStrength(seg, off),
                readEncodingProfile(seg, off),
                readEncodingAlpha(seg, off),
                readEncodingBeta(seg, off),
                readSoulVersion(seg, off),
                readEncodingSurprise(seg, off),
                readConsolidationFlags(seg, off)
        );
    }
}
