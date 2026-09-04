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

import com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields;
import com.spectrayan.spector.memory.model.ConversationRole;
import com.spectrayan.spector.memory.model.EngramSource;
import com.spectrayan.spector.memory.model.EpisodeRecord;
import com.spectrayan.spector.memory.model.SourceModality;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Read-only compatibility reader for pre-existing episodic records written in the legacy
 * punned header format (Task 5.4, R4.1, R4.4).
 *
 * <h3>Legacy Format (V1 punned 64B header)</h3>
 * <p>In legacy episodic logs prior to unification, conversation metadata was punned directly
 * into cognitive header fields (e.g. role in valence, sequence ID in importance, latency in storage strength).
 * This class is strictly read-only and preserved exclusively for dual-read backwards compatibility.</p>
 *
 * @since 1.4.0
 */
public final class LegacyEpisodeHeaderReader {

    private LegacyEpisodeHeaderReader() {} // static utility

    /** Reads conversation role from legacy valence byte (offset 2). */
    public static ConversationRole readRole(MemorySegment segment, long offset) {
        byte ordinal = segment.get(ValueLayout.JAVA_BYTE, offset + EncodingHeaderFields.OFFSET_VALENCE);
        return ConversationRole.fromOrdinal(ordinal & 0xFF);
    }

    /** Reads sequence ID from legacy importance field (offset 4). */
    public static int readSequenceId(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_INT_UNALIGNED, offset + EncodingHeaderFields.OFFSET_IMPORTANCE);
    }

    /** Reads creation timestamp in ms (offset 8). */
    public static long readTimestamp(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_LONG_UNALIGNED, offset + EncodingHeaderFields.OFFSET_TIMESTAMP);
    }

    /** Reads session ID from legacy synaptic_tags field (offset 24). */
    public static long readSessionId(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_LONG_UNALIGNED, offset + EncodingHeaderFields.OFFSET_SYNAPTIC_TAGS);
    }

    /** Reads token in count from legacy agent_recall_count (offset 16). */
    public static int readTokenInCount(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_INT_UNALIGNED, offset + EncodingHeaderFields.OFFSET_AGENT_RECALL_COUNT);
    }

    /** Reads token out count from legacy exact_norm (offset 20). */
    public static int readTokenOutCount(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_INT_UNALIGNED, offset + EncodingHeaderFields.OFFSET_EXACT_NORM);
    }

    /** Reads model ID from legacy centroid_id (offset 32). */
    public static short readModelId(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_SHORT_UNALIGNED, offset + EncodingHeaderFields.OFFSET_CENTROID_ID);
    }

    /** Reads generation latency in ms from legacy storage_strength (offset 36). */
    public static int readLatencyMs(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_INT_UNALIGNED, offset + EncodingHeaderFields.OFFSET_STORAGE_STRENGTH);
    }

    /** Reads user/tenant ID from legacy last_auto_ltp (offset 48). */
    public static long readUserId(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_LONG_UNALIGNED, offset + EncodingHeaderFields.OFFSET_LAST_AUTO_LTP);
    }

    /** Reads CBOR body length from legacy encoding_surprise (offset 56). */
    public static int readBodyLength(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_INT_UNALIGNED, offset + EncodingHeaderFields.OFFSET_ENCODING_SURPRISE);
    }

    /** Reads raw flags byte (offset 1). */
    public static byte readFlags(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_BYTE, offset + EncodingHeaderFields.OFFSET_FLAGS);
    }

    /** Reads conversation flags (offset 34). */
    public static byte readConversationFlags(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_BYTE, offset + EncodingHeaderFields.OFFSET_CONSOLIDATION_FLAGS);
    }

    /** Reads soul version (offset 46). */
    public static short readSoulVersion(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_SHORT_UNALIGNED, offset + EncodingHeaderFields.OFFSET_SOUL_VERSION);
    }

    /** Extracts source modality from flags. */
    public static SourceModality readModality(MemorySegment segment, long offset) {
        byte flags = readFlags(segment, offset);
        return SourceModality.fromOrdinal(EncodingHeaderFields.sourceModalityOrdinal(flags));
    }

    /** Checks if legacy record is tombstoned. */
    public static boolean isTombstoned(MemorySegment segment, long offset) {
        return EncodingHeaderFields.isTombstoned(readFlags(segment, offset));
    }

    /** Checks if legacy record is consolidated. */
    public static boolean isConsolidated(MemorySegment segment, long offset) {
        return EncodingHeaderFields.isConsolidated(readFlags(segment, offset));
    }

    /**
     * Reads a complete legacy episodic record from the given segment at the specified offset.
     *
     * @param segment     mmap segment
     * @param offset      offset where the 64B legacy header begins
     * @param includeBody if true, reads body bytes
     * @return decoded {@link EpisodeRecord}
     */
    public static EpisodeRecord readRecord(MemorySegment segment, long offset, boolean includeBody) {
        byte flags = readFlags(segment, offset);
        int bodyLength = readBodyLength(segment, offset);

        byte[] body = null;
        if (includeBody && bodyLength > 0) {
            long bodyOffset = offset + EncodingHeaderFields.HEADER_BYTES;
            body = segment.asSlice(bodyOffset, bodyLength).toArray(ValueLayout.JAVA_BYTE);
        }

        return new EpisodeRecord(
                readRole(segment, offset),
                readSequenceId(segment, offset),
                readTimestamp(segment, offset),
                readSessionId(segment, offset),
                bodyLength,
                body,
                readModelId(segment, offset),
                readTokenInCount(segment, offset),
                readTokenOutCount(segment, offset),
                readLatencyMs(segment, offset),
                readUserId(segment, offset),
                readSoulVersion(segment, offset),
                SourceModality.fromOrdinal(EncodingHeaderFields.sourceModalityOrdinal(flags)),
                flags,
                0.0f,
                (byte) 0,
                (byte) 0,
                EngramSource.EXPERIENCED
        );
    }
}
