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
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

import static com.spectrayan.spector.memory.kernel.layout.EpisodicHeaderFields.OFFSET_EPISODIC_TAGS_HI;
import static com.spectrayan.spector.memory.kernel.layout.EpisodicHeaderFields.OFFSET_EPISODIC_TAGS_LO;
import static com.spectrayan.spector.memory.kernel.layout.EpisodicHeaderFields.OFFSET_MODEL_ID;
import static com.spectrayan.spector.memory.kernel.layout.EpisodicHeaderFields.OFFSET_ROLE;
import static com.spectrayan.spector.memory.kernel.layout.EpisodicHeaderFields.OFFSET_SESSION_ID;
import static com.spectrayan.spector.memory.kernel.layout.EpisodicHeaderFields.VAR_HANDLE_EPISODIC_TAGS;

/**
 * Dedicated encoding header layout for the Episodic memory tier (ADR-0030).
 *
 * <p>Extends {@link EncodingHeaderLayout} with honest episodic fields (session ID, model ID,
 * conversation role, episodic context tags) and absorbs record prefix and status mutations
 * previously housed in {@code EpisodicHeaderAccessor}.</p>
 *
 * @since 1.5.0
 * @see EncodingHeaderLayout
 * @see EpisodicHeaderFields
 * @see EpisodeLayout
 */
public class EpisodicHeaderLayout extends EncodingHeaderLayout {

    public static final EpisodicHeaderLayout INSTANCE = new EpisodicHeaderLayout();

    public static final VarHandle VAR_HANDLE_EPISODIC_TAGS_LO = VAR_HANDLE_EPISODIC_TAGS;
    public static final VarHandle VAR_HANDLE_EPISODIC_TAGS_HI = VAR_HANDLE_EPISODIC_TAGS;

    public EpisodicHeaderLayout() {
        super();
    }

    public static EpisodicHeaderLayout defaultLayout() {
        return INSTANCE;
    }

    // ── Honest Episodic Header Fields (Offset relative to header start) ──

    public long readSessionId(MemorySegment seg, long headerOff) {
        return seg.get(ValueLayout.JAVA_LONG_UNALIGNED, headerOff + OFFSET_SESSION_ID);
    }

    public void writeSessionId(MemorySegment seg, long headerOff, long sessionId) {
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED, headerOff + OFFSET_SESSION_ID, sessionId);
    }

    public short readModelId(MemorySegment seg, long headerOff) {
        return seg.get(ValueLayout.JAVA_SHORT_UNALIGNED, headerOff + OFFSET_MODEL_ID);
    }

    public void writeModelId(MemorySegment seg, long headerOff, short modelId) {
        seg.set(ValueLayout.JAVA_SHORT_UNALIGNED, headerOff + OFFSET_MODEL_ID, modelId);
    }

    public byte readRole(MemorySegment seg, long headerOff) {
        return seg.get(ValueLayout.JAVA_BYTE, headerOff + OFFSET_ROLE);
    }

    public void writeRole(MemorySegment seg, long headerOff, byte role) {
        seg.set(ValueLayout.JAVA_BYTE, headerOff + OFFSET_ROLE, role);
    }

    public long readEpisodicTagsLo(MemorySegment seg, long headerOff) {
        return seg.get(ValueLayout.JAVA_LONG_UNALIGNED, headerOff + OFFSET_EPISODIC_TAGS_LO);
    }

    public long readEpisodicTagsHi(MemorySegment seg, long headerOff) {
        return seg.get(ValueLayout.JAVA_LONG_UNALIGNED, headerOff + OFFSET_EPISODIC_TAGS_HI);
    }

    public void writeEpisodicTags(MemorySegment seg, long headerOff, long tagsLo, long tagsHi) {
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED, headerOff + OFFSET_EPISODIC_TAGS_LO, tagsLo);
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED, headerOff + OFFSET_EPISODIC_TAGS_HI, tagsHi);
    }

    public void mergeEpisodicTags(MemorySegment seg, long headerOff, long additionalTags) {
        VAR_HANDLE_EPISODIC_TAGS.getAndBitwiseOr(seg, headerOff + OFFSET_EPISODIC_TAGS_LO, additionalTags);
    }

    public void mergeEpisodicTags128(MemorySegment seg, long headerOff, long tagsLo, long tagsHi) {
        VAR_HANDLE_EPISODIC_TAGS.getAndBitwiseOr(seg, headerOff + OFFSET_EPISODIC_TAGS_LO, tagsLo);
        VAR_HANDLE_EPISODIC_TAGS.getAndBitwiseOr(seg, headerOff + OFFSET_EPISODIC_TAGS_HI, tagsHi);
    }

    // ── Prefix Readers (Offset relative to record start) ──

    public int readPayloadBytes(MemorySegment segment, long recordOffset) {
        return segment.get(ValueLayout.JAVA_INT_UNALIGNED, recordOffset);
    }

    public int readSequenceId(MemorySegment segment, long recordOffset) {
        return segment.get(ValueLayout.JAVA_INT_UNALIGNED, recordOffset + 4);
    }

    public int readChecksum(MemorySegment segment, long recordOffset) {
        return segment.get(ValueLayout.JAVA_INT_UNALIGNED, recordOffset + 8);
    }

    public int readMagic(MemorySegment segment, long recordOffset) {
        return segment.get(ValueLayout.JAVA_INT_UNALIGNED, recordOffset + 12);
    }

    public boolean isOptionBRecord(MemorySegment segment, long recordOffset) {
        if (segment.byteSize() < recordOffset + EpisodeLayout.FIXED_OVERHEAD_BYTES) {
            return false;
        }
        return segment.get(ValueLayout.JAVA_INT_UNALIGNED, recordOffset + 12) == EpisodeLayout.MAGIC;
    }

    // ── Record-Level Convenience Methods (Translating recordOffset -> headerOffset) ──

    private static long headerOffset(long recordOffset) {
        return recordOffset + EpisodeLayout.PREFIX_BYTES;
    }

    public EncodingHeader readHeaderRecord(MemorySegment segment, long recordOffset) {
        return readHeader(segment, headerOffset(recordOffset));
    }

    public void writeHeaderRecord(MemorySegment segment, long recordOffset, EncodingHeader header) {
        writeHeader(segment, headerOffset(recordOffset), header);
    }

    public float readImportanceRecord(MemorySegment segment, long recordOffset) {
        return readImportance(segment, headerOffset(recordOffset));
    }

    public void writeImportanceRecord(MemorySegment segment, long recordOffset, float importance) {
        writeImportance(segment, headerOffset(recordOffset), importance);
    }

    public byte readValenceRecord(MemorySegment segment, long recordOffset) {
        return readValence(segment, headerOffset(recordOffset));
    }

    public void writeValenceRecord(MemorySegment segment, long recordOffset, byte valence) {
        writeValenceRelease(segment, headerOffset(recordOffset), valence);
    }

    public byte readArousalRecord(MemorySegment segment, long recordOffset) {
        return readArousal(segment, headerOffset(recordOffset));
    }

    public void writeArousalRecord(MemorySegment segment, long recordOffset, byte arousal) {
        writeArousal(segment, headerOffset(recordOffset), arousal);
    }

    public long readTimestampRecord(MemorySegment segment, long recordOffset) {
        return readTimestamp(segment, headerOffset(recordOffset));
    }

    public byte readFlagsRecord(MemorySegment segment, long recordOffset) {
        return readFlags(segment, headerOffset(recordOffset));
    }

    public EngramSource readSourceRecord(MemorySegment segment, long recordOffset) {
        return readSource(segment, headerOffset(recordOffset));
    }

    public SourceModality readModalityRecord(MemorySegment segment, long recordOffset) {
        byte flags = readFlagsRecord(segment, recordOffset);
        return SourceModality.fromOrdinal(EncodingHeaderFields.sourceModalityOrdinal(flags));
    }

    public short readSoulVersionRecord(MemorySegment segment, long recordOffset) {
        return readSoulVersion(segment, headerOffset(recordOffset));
    }

    public boolean isTombstonedRecord(MemorySegment segment, long recordOffset) {
        return EncodingHeaderFields.isTombstoned(readFlagsRecord(segment, recordOffset));
    }

    public boolean isConsolidatedRecord(MemorySegment segment, long recordOffset) {
        return EncodingHeaderFields.isConsolidated(readFlagsRecord(segment, recordOffset));
    }

    public void tombstoneRecord(MemorySegment segment, long recordOffset) {
        markTombstoned(segment, headerOffset(recordOffset));
    }

    public void markConsolidatedRecord(MemorySegment segment, long recordOffset) {
        markConsolidated(segment, headerOffset(recordOffset));
    }

    public void markResolvedRecord(MemorySegment segment, long recordOffset) {
        markResolved(segment, headerOffset(recordOffset));
    }

    public void markUnresolvedRecord(MemorySegment segment, long recordOffset) {
        markUnresolved(segment, headerOffset(recordOffset));
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof EpisodicHeaderLayout;
    }

    @Override
    public int hashCode() {
        return EpisodicHeaderLayout.class.hashCode();
    }

    @Override
    public String toString() {
        return "EpisodicHeaderLayout[]";
    }
}
