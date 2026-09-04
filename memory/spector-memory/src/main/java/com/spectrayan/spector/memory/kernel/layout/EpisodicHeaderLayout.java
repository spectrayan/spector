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
import static com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields.*;
import static com.spectrayan.spector.memory.kernel.layout.EpisodicHeaderFields.*;

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

    /**
     * Discriminates between legacy punned Option B records (where exactNorm at +16 is 0.0f
     * and sessionId was punned at +24) and new honest Option B records (ADR-0030).
     */
    public boolean isLegacyPunnedHeader(MemorySegment seg, long headerOff) {
        int intAt16 = seg.get(ValueLayout.JAVA_INT_UNALIGNED, headerOff + 16);
        long tagsLoAt24 = seg.get(ValueLayout.JAVA_LONG_UNALIGNED, headerOff + EncodingHeaderFields.OFFSET_V2_SYNAPTIC_TAGS_LO);
        if (intAt16 == 0 && tagsLoAt24 != 0L) {
            short soulAt32 = seg.get(ValueLayout.JAVA_SHORT_UNALIGNED, headerOff + EpisodicHeaderFields.OFFSET_SOUL_VERSION);
            if (soulAt32 != 0) {
                return false; // Honest record with soulVersion at +32
            }
            short soulAt44 = seg.get(ValueLayout.JAVA_SHORT_UNALIGNED, headerOff + EncodingHeaderFields.OFFSET_V2_SOUL_VERSION);
            if (soulAt44 != 0) {
                return true; // Legacy record with soulVersion at +44
            }
            short centroidAt20 = seg.get(ValueLayout.JAVA_SHORT_UNALIGNED, headerOff + EncodingHeaderFields.OFFSET_V2_CENTROID_ID);
            if (centroidAt20 != 0) {
                return true; // Legacy record with modelId in centroidId at +20
            }
            return true;
        }
        return false;
    }

    public long readSessionId(MemorySegment seg, long headerOff) {
        if (isLegacyPunnedHeader(seg, headerOff)) {
            return seg.get(ValueLayout.JAVA_LONG_UNALIGNED, headerOff + EncodingHeaderFields.OFFSET_V2_SYNAPTIC_TAGS_LO);
        }
        return seg.get(ValueLayout.JAVA_LONG_UNALIGNED, headerOff + OFFSET_SESSION_ID);
    }

    public void writeSessionId(MemorySegment seg, long headerOff, long sessionId) {
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED, headerOff + OFFSET_SESSION_ID, sessionId);
    }

    public short readModelId(MemorySegment seg, long headerOff) {
        if (isLegacyPunnedHeader(seg, headerOff)) {
            return seg.get(ValueLayout.JAVA_SHORT_UNALIGNED, headerOff + EncodingHeaderFields.OFFSET_V2_CENTROID_ID);
        }
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

    @Override
    public short readSoulVersion(MemorySegment seg, long off) {
        if (isLegacyPunnedHeader(seg, off)) {
            return seg.get(ValueLayout.JAVA_SHORT_UNALIGNED, off + EncodingHeaderFields.OFFSET_V2_SOUL_VERSION);
        }
        return seg.get(ValueLayout.JAVA_SHORT_UNALIGNED, off + EpisodicHeaderFields.OFFSET_SOUL_VERSION);
    }

    @Override
    public void writeSoulVersion(MemorySegment seg, long off, short version) {
        seg.set(ValueLayout.JAVA_SHORT_UNALIGNED, off + EpisodicHeaderFields.OFFSET_SOUL_VERSION, version);
    }

    @Override
    public byte readConsolidationFlags(MemorySegment seg, long off) {
        if (isLegacyPunnedHeader(seg, off)) {
            return seg.get(LAYOUT_CONSOLIDATION_FLAGS, off + EncodingHeaderFields.OFFSET_V2_CONSOLIDATION_FLAGS);
        }
        return seg.get(LAYOUT_CONSOLIDATION_FLAGS, off + EpisodicHeaderFields.OFFSET_CONSOLIDATION_FLAGS);
    }

    @Override
    public void writeConsolidationFlags(MemorySegment seg, long off, byte flags) {
        seg.set(LAYOUT_CONSOLIDATION_FLAGS, off + EpisodicHeaderFields.OFFSET_CONSOLIDATION_FLAGS, flags);
    }

    @Override
    public byte readEncodingProfile(MemorySegment seg, long off) {
        if (isLegacyPunnedHeader(seg, off)) {
            return seg.get(LAYOUT_ENCODING_PROFILE, off + EncodingHeaderFields.OFFSET_V2_ENCODING_PROFILE);
        }
        return seg.get(LAYOUT_ENCODING_PROFILE, off + EpisodicHeaderFields.OFFSET_ENCODING_PROFILE);
    }

    @Override
    public void writeEncodingProfile(MemorySegment seg, long off, byte profile) {
        seg.set(LAYOUT_ENCODING_PROFILE, off + EpisodicHeaderFields.OFFSET_ENCODING_PROFILE, profile);
    }

    @Override
    public byte readEncodingAlpha(MemorySegment seg, long off) {
        if (isLegacyPunnedHeader(seg, off)) {
            return seg.get(LAYOUT_ENCODING_ALPHA, off + EncodingHeaderFields.OFFSET_V2_ENCODING_ALPHA);
        }
        return seg.get(LAYOUT_ENCODING_ALPHA, off + EpisodicHeaderFields.OFFSET_ENCODING_ALPHA);
    }

    @Override
    public void writeEncodingAlpha(MemorySegment seg, long off, byte alpha) {
        seg.set(LAYOUT_ENCODING_ALPHA, off + EpisodicHeaderFields.OFFSET_ENCODING_ALPHA, alpha);
    }

    @Override
    public byte readEncodingBeta(MemorySegment seg, long off) {
        if (isLegacyPunnedHeader(seg, off)) {
            return seg.get(LAYOUT_ENCODING_BETA, off + EncodingHeaderFields.OFFSET_V2_ENCODING_BETA);
        }
        return seg.get(LAYOUT_ENCODING_BETA, off + EpisodicHeaderFields.OFFSET_ENCODING_BETA);
    }

    @Override
    public void writeEncodingBeta(MemorySegment seg, long off, byte beta) {
        seg.set(LAYOUT_ENCODING_BETA, off + EpisodicHeaderFields.OFFSET_ENCODING_BETA, beta);
    }

    @Override
    public float readEncodingSurprise(MemorySegment seg, long off) {
        if (isLegacyPunnedHeader(seg, off)) {
            return seg.get(ValueLayout.JAVA_FLOAT_UNALIGNED, off + EncodingHeaderFields.OFFSET_V2_ENCODING_SURPRISE);
        }
        return seg.get(ValueLayout.JAVA_FLOAT_UNALIGNED, off + EpisodicHeaderFields.OFFSET_ENCODING_SURPRISE);
    }

    @Override
    public void writeEncodingSurprise(MemorySegment seg, long off, float surprise) {
        seg.set(ValueLayout.JAVA_FLOAT_UNALIGNED, off + EpisodicHeaderFields.OFFSET_ENCODING_SURPRISE, surprise);
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

    public long readSessionIdRecord(MemorySegment segment, long recordOffset) {
        return readSessionId(segment, headerOffset(recordOffset));
    }

    public void writeSessionIdRecord(MemorySegment segment, long recordOffset, long sessionId) {
        writeSessionId(segment, headerOffset(recordOffset), sessionId);
    }

    public short readModelIdRecord(MemorySegment segment, long recordOffset) {
        return readModelId(segment, headerOffset(recordOffset));
    }

    public void writeModelIdRecord(MemorySegment segment, long recordOffset, short modelId) {
        writeModelId(segment, headerOffset(recordOffset), modelId);
    }

    public byte readRoleRecord(MemorySegment segment, long recordOffset) {
        return readRole(segment, headerOffset(recordOffset));
    }

    public void writeRoleRecord(MemorySegment segment, long recordOffset, byte role) {
        writeRole(segment, headerOffset(recordOffset), role);
    }

    public long readEpisodicTagsLoRecord(MemorySegment segment, long recordOffset) {
        return readEpisodicTagsLo(segment, headerOffset(recordOffset));
    }

    public long readEpisodicTagsHiRecord(MemorySegment segment, long recordOffset) {
        return readEpisodicTagsHi(segment, headerOffset(recordOffset));
    }

    public void writeEpisodicTagsRecord(MemorySegment segment, long recordOffset, long tagsLo, long tagsHi) {
        writeEpisodicTags(segment, headerOffset(recordOffset), tagsLo, tagsHi);
    }

    public void writeEpisodicHeader(MemorySegment seg, long headerOff,
                                    long timestampMs, byte flags, byte valence, byte arousal, float importance,
                                    long sessionId, short modelId, byte role, short soulVersion, EngramSource source,
                                    long episodicTagsLo, long episodicTagsHi) {
        // Cognitive substrate (0–15)
        seg.set(LAYOUT_HEADER_VERSION, headerOff + EncodingHeaderFields.OFFSET_HEADER_VERSION, (byte) EncodingHeaderFields.HEADER_VERSION_V2);
        seg.set(LAYOUT_FLAGS,         headerOff + EncodingHeaderFields.OFFSET_FLAGS,          flags);
        seg.set(LAYOUT_VALENCE,       headerOff + EncodingHeaderFields.OFFSET_VALENCE,        valence);
        seg.set(LAYOUT_AROUSAL,       headerOff + EncodingHeaderFields.OFFSET_AROUSAL,        arousal);
        seg.set(ValueLayout.JAVA_FLOAT_UNALIGNED, headerOff + EncodingHeaderFields.OFFSET_IMPORTANCE, importance);
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED,  headerOff + EncodingHeaderFields.OFFSET_TIMESTAMP,  timestampMs);

        // Tier-specific honest episodic fields (16–63)
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED,  headerOff + OFFSET_SESSION_ID, sessionId);
        seg.set(ValueLayout.JAVA_SHORT_UNALIGNED, headerOff + OFFSET_MODEL_ID,   modelId);
        seg.set(ValueLayout.JAVA_BYTE,            headerOff + OFFSET_ROLE,       role);
        seg.set(LAYOUT_CONSOLIDATION_FLAGS,       headerOff + EpisodicHeaderFields.OFFSET_CONSOLIDATION_FLAGS, (byte) 0);
        seg.set(LAYOUT_ENCODING_PROFILE,          headerOff + EpisodicHeaderFields.OFFSET_ENCODING_PROFILE,    (byte) 0);
        seg.set(LAYOUT_ENCODING_ALPHA,            headerOff + EpisodicHeaderFields.OFFSET_ENCODING_ALPHA,      (byte) 0);
        seg.set(LAYOUT_ENCODING_BETA,             headerOff + EpisodicHeaderFields.OFFSET_ENCODING_BETA,       (byte) 0);
        seg.set(ValueLayout.JAVA_BYTE,            headerOff + OFFSET_PAD1,                (byte) 0);
        seg.set(ValueLayout.JAVA_SHORT_UNALIGNED, headerOff + EpisodicHeaderFields.OFFSET_SOUL_VERSION, soulVersion);
        seg.set(ValueLayout.JAVA_SHORT_UNALIGNED, headerOff + OFFSET_RESERVED_GEO, (short) 0);
        seg.set(ValueLayout.JAVA_FLOAT_UNALIGNED, headerOff + EpisodicHeaderFields.OFFSET_ENCODING_SURPRISE, 0.0f);
        seg.set(ValueLayout.JAVA_INT_UNALIGNED,   headerOff + OFFSET_RESERVED0, 0);
        seg.set(ValueLayout.JAVA_INT_UNALIGNED,   headerOff + OFFSET_RESERVED1, 0);
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED,  headerOff + OFFSET_EPISODIC_TAGS_LO, episodicTagsLo);
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED,  headerOff + OFFSET_EPISODIC_TAGS_HI, episodicTagsHi);
    }

    public void writeEpisodicHeaderRecord(MemorySegment seg, long recordOffset,
                                          long timestampMs, byte flags, byte valence, byte arousal, float importance,
                                          long sessionId, short modelId, byte role, short soulVersion, EngramSource source,
                                          long episodicTagsLo, long episodicTagsHi) {
        writeEpisodicHeader(seg, headerOffset(recordOffset), timestampMs, flags, valence, arousal, importance,
                sessionId, modelId, role, soulVersion, source, episodicTagsLo, episodicTagsHi);
    }

    @Override
    public void writeHeader(MemorySegment seg, long off, EncodingHeader header) {
        // Cognitive substrate (0–15)
        seg.set(LAYOUT_HEADER_VERSION, off + EncodingHeaderFields.OFFSET_HEADER_VERSION, (byte) EncodingHeaderFields.HEADER_VERSION_V2);
        seg.set(LAYOUT_FLAGS,         off + EncodingHeaderFields.OFFSET_FLAGS,          header.flags());
        seg.set(LAYOUT_VALENCE,       off + EncodingHeaderFields.OFFSET_VALENCE,        header.valence());
        seg.set(LAYOUT_AROUSAL,       off + EncodingHeaderFields.OFFSET_AROUSAL,        header.arousal());
        seg.set(ValueLayout.JAVA_FLOAT_UNALIGNED, off + EncodingHeaderFields.OFFSET_IMPORTANCE, header.importance());
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED,  off + EncodingHeaderFields.OFFSET_TIMESTAMP,  header.timestampMs());

        // Tier-specific honest episodic fields (16–63)
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED,  off + OFFSET_SESSION_ID, header.synapticTags());
        seg.set(ValueLayout.JAVA_SHORT_UNALIGNED, off + OFFSET_MODEL_ID,   header.centroidId());
        seg.set(ValueLayout.JAVA_BYTE,            off + OFFSET_ROLE,       (byte) 0);
        seg.set(LAYOUT_CONSOLIDATION_FLAGS,       off + EpisodicHeaderFields.OFFSET_CONSOLIDATION_FLAGS, header.consolidationFlags());
        seg.set(LAYOUT_ENCODING_PROFILE,          off + EpisodicHeaderFields.OFFSET_ENCODING_PROFILE,    header.encodingProfile());
        seg.set(LAYOUT_ENCODING_ALPHA,            off + EpisodicHeaderFields.OFFSET_ENCODING_ALPHA,      header.encodingAlpha());
        seg.set(LAYOUT_ENCODING_BETA,             off + EpisodicHeaderFields.OFFSET_ENCODING_BETA,       header.encodingBeta());
        seg.set(ValueLayout.JAVA_BYTE,            off + OFFSET_PAD1,                (byte) 0);
        seg.set(ValueLayout.JAVA_SHORT_UNALIGNED, off + EpisodicHeaderFields.OFFSET_SOUL_VERSION, header.soulVersion());
        seg.set(ValueLayout.JAVA_SHORT_UNALIGNED, off + OFFSET_RESERVED_GEO, (short) 0);
        seg.set(ValueLayout.JAVA_FLOAT_UNALIGNED, off + EpisodicHeaderFields.OFFSET_ENCODING_SURPRISE, header.encodingSurprise());
        seg.set(ValueLayout.JAVA_INT_UNALIGNED,   off + OFFSET_RESERVED0, 0);
        seg.set(ValueLayout.JAVA_INT_UNALIGNED,   off + OFFSET_RESERVED1, 0);
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED,  off + OFFSET_EPISODIC_TAGS_LO, 0L);
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED,  off + OFFSET_EPISODIC_TAGS_HI, 0L);
    }

    @Override
    public EncodingHeader readHeader(MemorySegment seg, long off) {
        return new EncodingHeader(
                readTimestamp(seg, off),
                readSessionId(seg, off),
                0.0f,
                readImportance(seg, off),
                0,
                readModelId(seg, off),
                readValence(seg, off),
                readFlags(seg, off),
                readArousal(seg, off),
                1.0f,
                readEncodingProfile(seg, off),
                readEncodingAlpha(seg, off),
                readEncodingBeta(seg, off),
                readSoulVersion(seg, off),
                readEncodingSurprise(seg, off),
                readConsolidationFlags(seg, off),
                readSource(seg, off)
        );
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
