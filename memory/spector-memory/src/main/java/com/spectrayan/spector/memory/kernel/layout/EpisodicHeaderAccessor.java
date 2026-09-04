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
package com.spectrayan.spector.memory.kernel.layout;

import com.spectrayan.spector.memory.model.EngramSource;
import com.spectrayan.spector.memory.model.SourceModality;

import java.lang.foreign.MemorySegment;

/**
 * Accessor for the 64-byte {@link EncodingHeaderLayout encoding header} embedded within
 * variable-length episodic records (ADR-0010 / ADR-0030, D2 Option B).
 *
 * <p>Addresses the header at {@code recordOffset + 16}, delegating directly to
 * {@link EncodingHeaderLayout} and {@link EncodingHeaderFields}.
 * Enables salience walks to read the 64B header at {@code +16} and jump {@code 80 + payloadBytes}
 * without decoding the variable conversation payload.</p>
 *
 * @since 1.4.0
 * @deprecated Use {@link EpisodicHeaderLayout} or {@link EpisodeLayout#headerLayout()} instead (ADR-0030).
 * @see EpisodicHeaderLayout
 * @see EpisodeLayout
 * @see EpisodeCodec
 */
@Deprecated(since = "1.5.0", forRemoval = true)
public final class EpisodicHeaderAccessor {

    private static final EpisodicHeaderLayout LAYOUT = EpisodicHeaderLayout.INSTANCE;

    private EpisodicHeaderAccessor() {} // static utility

    // ── Prefix Readers ──

    /**
     * Reads the payload byte length from the prefix (offset 0).
     */
    public static int readPayloadBytes(MemorySegment segment, long recordOffset) {
        return LAYOUT.readPayloadBytes(segment, recordOffset);
    }

    /**
     * Reads the monotonic sequence ID from the prefix (offset 4).
     */
    public static int readSequenceId(MemorySegment segment, long recordOffset) {
        return LAYOUT.readSequenceId(segment, recordOffset);
    }

    /**
     * Reads the CRC32C checksum from the prefix (offset 8).
     */
    public static int readChecksum(MemorySegment segment, long recordOffset) {
        return LAYOUT.readChecksum(segment, recordOffset);
    }

    /**
     * Reads the magic marker from the prefix (offset 12).
     */
    public static int readMagic(MemorySegment segment, long recordOffset) {
        return LAYOUT.readMagic(segment, recordOffset);
    }

    /**
     * Checks if the record at the given offset matches Option B format ('EPIS' magic marker at offset +12).
     */
    public static boolean isOptionBRecord(MemorySegment segment, long recordOffset) {
        return LAYOUT.isOptionBRecord(segment, recordOffset);
    }

    // ── Header Delegations (Offset +16) ──

    /**
     * Reads the complete 64-byte {@link EncodingHeader} at {@code recordOffset + 16}.
     */
    public static EncodingHeader readHeader(MemorySegment segment, long recordOffset) {
        return LAYOUT.readHeaderRecord(segment, recordOffset);
    }

    /**
     * Writes the complete 64-byte {@link EncodingHeader} at {@code recordOffset + 16}.
     */
    public static void writeHeader(MemorySegment segment, long recordOffset, EncodingHeader header) {
        LAYOUT.writeHeaderRecord(segment, recordOffset, header);
    }

    /**
     * Reads the true floating-point salience importance from the header (offset 16 + 4 = 20).
     */
    public static float readImportance(MemorySegment segment, long recordOffset) {
        return LAYOUT.readImportanceRecord(segment, recordOffset);
    }

    /**
     * Writes the floating-point salience importance into the header (offset 16 + 4 = 20).
     */
    public static void writeImportance(MemorySegment segment, long recordOffset, float importance) {
        LAYOUT.writeImportanceRecord(segment, recordOffset, importance);
    }

    /**
     * Reads the emotional valence byte [-128, 127] from the header (offset 16 + 2 = 18).
     */
    public static byte readValence(MemorySegment segment, long recordOffset) {
        return LAYOUT.readValenceRecord(segment, recordOffset);
    }

    /**
     * Writes the emotional valence byte [-128, 127] into the header (offset 16 + 2 = 18).
     */
    public static void writeValence(MemorySegment segment, long recordOffset, byte valence) {
        LAYOUT.writeValenceRecord(segment, recordOffset, valence);
    }

    /**
     * Reads the emotional arousal byte [-128, 127] from the header (offset 16 + 3 = 19).
     */
    public static byte readArousal(MemorySegment segment, long recordOffset) {
        return LAYOUT.readArousalRecord(segment, recordOffset);
    }

    /**
     * Writes the emotional arousal byte [-128, 127] into the header (offset 16 + 3 = 19).
     */
    public static void writeArousal(MemorySegment segment, long recordOffset, byte arousal) {
        LAYOUT.writeArousalRecord(segment, recordOffset, arousal);
    }

    /**
     * Reads the creation timestamp in epoch milliseconds (offset 16 + 8 = 24).
     */
    public static long readTimestamp(MemorySegment segment, long recordOffset) {
        return LAYOUT.readTimestampRecord(segment, recordOffset);
    }

    /**
     * Reads the header flags byte (offset 16 + 1 = 17).
     */
    public static byte readFlags(MemorySegment segment, long recordOffset) {
        return LAYOUT.readFlagsRecord(segment, recordOffset);
    }

    /**
     * Reads the engram provenance source from the header (offset 16 + 46 = 62).
     */
    public static EngramSource readSource(MemorySegment segment, long recordOffset) {
        return LAYOUT.readSourceRecord(segment, recordOffset);
    }

    /**
     * Extracts the source modality from the flags byte.
     */
    public static SourceModality readModality(MemorySegment segment, long recordOffset) {
        return LAYOUT.readModalityRecord(segment, recordOffset);
    }

    /**
     * Reads the agent soul configuration version (offset 16 + 46 = 62 or 47).
     */
    public static short readSoulVersion(MemorySegment segment, long recordOffset) {
        return LAYOUT.readSoulVersionRecord(segment, recordOffset);
    }

    /**
     * Checks if this record has been tombstoned.
     */
    public static boolean isTombstoned(MemorySegment segment, long recordOffset) {
        return LAYOUT.isTombstonedRecord(segment, recordOffset);
    }

    /**
     * Checks if this record has been consolidated into semantic memory.
     */
    public static boolean isConsolidated(MemorySegment segment, long recordOffset) {
        return LAYOUT.isConsolidatedRecord(segment, recordOffset);
    }

    /**
     * Logically tombstones the episodic record by setting the tombstone flag in the header.
     */
    public static void tombstone(MemorySegment segment, long recordOffset) {
        LAYOUT.tombstoneRecord(segment, recordOffset);
    }

    /**
     * Marks the episodic record as consolidated by setting the consolidated flag in the header.
     */
    public static void markConsolidated(MemorySegment segment, long recordOffset) {
        LAYOUT.markConsolidatedRecord(segment, recordOffset);
    }

    /**
     * Marks the episodic record as resolved (Zeigarnik Effect) by setting the resolved flag in the header.
     */
    public static void markResolved(MemorySegment segment, long recordOffset) {
        LAYOUT.markResolvedRecord(segment, recordOffset);
    }

    /**
     * Marks the episodic record as unresolved (Zeigarnik Effect) by clearing the resolved flag in the header.
     */
    public static void markUnresolved(MemorySegment segment, long recordOffset) {
        LAYOUT.markUnresolvedRecord(segment, recordOffset);
    }
}
