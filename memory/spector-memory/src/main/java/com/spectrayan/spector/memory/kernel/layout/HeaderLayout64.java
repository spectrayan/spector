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

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants.*;

/**
 * The 64-byte cache-line-aligned header layout.
 *
 * <p>This is the sole header layout shipped in Spector. The 64-byte size
 * matches exactly one CPU cache line, eliminating split-line reads during
 * sequential scans. The on-disk version byte is {@code 1}.</p>
 *
 * <h3>Layout (64 bytes)</h3>
 * <pre>
 *   Offset  Size  Field                Notes
 *   ──────  ────  ─────────────────    ──────
 *    0      1B    header_version       Always 1
 *    1      1B    flags                Tombstone, type, consolidated, pinned, resolved, modality
 *    2      1B    valence              Signed emotion (-128 to +127)
 *    3      1B    arousal              Unsigned intensity (0-255)
 *    4      4B    importance           Base importance score (ICNU-fused)
 *    8      8B    timestamp_ms         When the memory was formed
 *   16      4B    agent_recall_count   LTP reinforcement counter
 *   20      4B    exact_norm           L2 norm for cosine normalization
 *   24      8B    synaptic_tags        64-bit Bloom filter
 *   32      2B    centroid_id          IVF partition routing ID
 *   34      1B    consolidation_flags  bit0=contradicted, bits 1-7 reserved
 *   35      1B    encoding_profile     Cognitive state at write time (bit7=soul-derived)
 *   36      4B    storage_strength     Two-Factor Memory S(t)
 *   40      4B    spector_recall_cnt   Auto-LTP passive counter
 *   44      1B    encoding_alpha       Quantized alpha at encoding (0-255)
 *   45      1B    encoding_beta        Quantized beta at encoding (0-255)
 *   46      2B    soul_version         Monotonic soul config version counter
 *   48      8B    last_auto_ltp        Auto-LTP timestamp
 *   56      4B    encoding_surprise    Surprise z-score at encoding (float32)
 *   60      1B    last_recall_profile  CognitiveProfile ordinal from last recall
 *   61-63   3B    _reserved            Future use
 * </pre>
 *
 * @see HeaderLayout
 * @see SynapticHeaderConstants
 */
public record HeaderLayout64() implements HeaderLayout {

    /** Singleton instance. */
    public static final HeaderLayout64 INSTANCE = new HeaderLayout64();

    @Override public int headerBytes() { return HEADER_BYTES; }
    @Override public int version() { return HEADER_VERSION; }

    // ── Field reads ──

    @Override public long readTimestamp(MemorySegment seg, long off) {
        return seg.get(LAYOUT_TIMESTAMP, off + OFFSET_TIMESTAMP);
    }

    @Override public long readSynapticTags(MemorySegment seg, long off) {
        return seg.get(LAYOUT_SYNAPTIC_TAGS, off + OFFSET_SYNAPTIC_TAGS);
    }

    @Override public float readExactNorm(MemorySegment seg, long off) {
        return seg.get(LAYOUT_EXACT_NORM, off + OFFSET_EXACT_NORM);
    }

    @Override public float readImportance(MemorySegment seg, long off) {
        return seg.get(LAYOUT_IMPORTANCE, off + OFFSET_IMPORTANCE);
    }

    @Override public int readAgentRecallCount(MemorySegment seg, long off) {
        return seg.get(LAYOUT_AGENT_RECALL_COUNT, off + OFFSET_AGENT_RECALL_COUNT);
    }

    @Override public short readCentroidId(MemorySegment seg, long off) {
        return seg.get(LAYOUT_CENTROID_ID, off + OFFSET_CENTROID_ID);
    }

    @Override public byte readValence(MemorySegment seg, long off) {
        return seg.get(LAYOUT_VALENCE, off + OFFSET_VALENCE);
    }

    @Override public byte readFlags(MemorySegment seg, long off) {
        return seg.get(LAYOUT_FLAGS, off + OFFSET_FLAGS);
    }

    @Override public byte readArousal(MemorySegment seg, long off) {
        return seg.get(LAYOUT_AROUSAL, off + OFFSET_AROUSAL);
    }

    @Override public float readStorageStrength(MemorySegment seg, long off) {
        return seg.get(LAYOUT_STORAGE_STRENGTH, off + OFFSET_STORAGE_STRENGTH);
    }

    @Override public byte readEncodingProfile(MemorySegment seg, long off) {
        return seg.get(LAYOUT_ENCODING_PROFILE, off + OFFSET_ENCODING_PROFILE);
    }

    @Override public byte readEncodingAlpha(MemorySegment seg, long off) {
        return seg.get(LAYOUT_ENCODING_ALPHA, off + OFFSET_ENCODING_ALPHA);
    }

    @Override public byte readEncodingBeta(MemorySegment seg, long off) {
        return seg.get(LAYOUT_ENCODING_BETA, off + OFFSET_ENCODING_BETA);
    }

    @Override public short readSoulVersion(MemorySegment seg, long off) {
        return seg.get(LAYOUT_SOUL_VERSION, off + OFFSET_SOUL_VERSION);
    }

    @Override public float readEncodingSurprise(MemorySegment seg, long off) {
        return seg.get(LAYOUT_ENCODING_SURPRISE, off + OFFSET_ENCODING_SURPRISE);
    }

    @Override public void writeArousal(MemorySegment seg, long off, byte arousal) {
        seg.set(LAYOUT_AROUSAL, off + OFFSET_AROUSAL, arousal);
    }

    @Override public void writeStorageStrength(MemorySegment seg, long off, float strength) {
        seg.set(LAYOUT_STORAGE_STRENGTH, off + OFFSET_STORAGE_STRENGTH, strength);
    }

    @Override public void writeEncodingProfile(MemorySegment seg, long off, byte profile) {
        seg.set(LAYOUT_ENCODING_PROFILE, off + OFFSET_ENCODING_PROFILE, profile);
    }

    @Override public void writeEncodingAlpha(MemorySegment seg, long off, byte alpha) {
        seg.set(LAYOUT_ENCODING_ALPHA, off + OFFSET_ENCODING_ALPHA, alpha);
    }

    @Override public void writeEncodingBeta(MemorySegment seg, long off, byte beta) {
        seg.set(LAYOUT_ENCODING_BETA, off + OFFSET_ENCODING_BETA, beta);
    }

    @Override public void writeSoulVersion(MemorySegment seg, long off, short version) {
        seg.set(LAYOUT_SOUL_VERSION, off + OFFSET_SOUL_VERSION, version);
    }

    @Override public void writeEncodingSurprise(MemorySegment seg, long off, float surprise) {
        seg.set(LAYOUT_ENCODING_SURPRISE, off + OFFSET_ENCODING_SURPRISE, surprise);
    }

    // ── Full header read/write ──

    @Override
    public CognitiveRecordLayout.CognitiveHeader readHeader(MemorySegment seg, long off) {
        return new CognitiveRecordLayout.CognitiveHeader(
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
                readEncodingSurprise(seg, off)
        );
    }

    @Override
    public void writeHeader(MemorySegment seg, long off, CognitiveRecordLayout.CognitiveHeader header) {
        seg.set(LAYOUT_HEADER_VERSION, off + OFFSET_HEADER_VERSION, (byte) HEADER_VERSION);
        seg.set(LAYOUT_FLAGS,         off + OFFSET_FLAGS,          header.flags());
        seg.set(LAYOUT_VALENCE,       off + OFFSET_VALENCE,        header.valence());
        seg.set(LAYOUT_AROUSAL,       off + OFFSET_AROUSAL,        header.arousal());
        seg.set(LAYOUT_IMPORTANCE,    off + OFFSET_IMPORTANCE,     header.importance());
        seg.set(LAYOUT_TIMESTAMP,     off + OFFSET_TIMESTAMP,      header.timestampMs());
        seg.set(LAYOUT_AGENT_RECALL_COUNT, off + OFFSET_AGENT_RECALL_COUNT, header.agentRecallCount());
        seg.set(LAYOUT_EXACT_NORM,    off + OFFSET_EXACT_NORM,     header.exactNorm());
        seg.set(LAYOUT_SYNAPTIC_TAGS, off + OFFSET_SYNAPTIC_TAGS,  header.synapticTags());
        seg.set(LAYOUT_CENTROID_ID,   off + OFFSET_CENTROID_ID,    header.centroidId());
        seg.set(LAYOUT_CONSOLIDATION_FLAGS, off + OFFSET_CONSOLIDATION_FLAGS, (byte) 0);
        seg.set(LAYOUT_ENCODING_PROFILE, off + OFFSET_ENCODING_PROFILE, header.encodingProfile());
        seg.set(LAYOUT_STORAGE_STRENGTH, off + OFFSET_STORAGE_STRENGTH, header.storageStrength());
        // Zero auto-LTP and reserved fields (ensure clean state)
        seg.set(LAYOUT_SPECTOR_RECALL_COUNT, off + OFFSET_SPECTOR_RECALL_COUNT, 0);
        seg.set(LAYOUT_ENCODING_ALPHA, off + OFFSET_ENCODING_ALPHA, header.encodingAlpha());
        seg.set(LAYOUT_ENCODING_BETA, off + OFFSET_ENCODING_BETA, header.encodingBeta());
        seg.set(LAYOUT_SOUL_VERSION, off + OFFSET_SOUL_VERSION, header.soulVersion());
        seg.set(LAYOUT_LAST_AUTO_LTP, off + OFFSET_LAST_AUTO_LTP, 0L);
        seg.set(LAYOUT_ENCODING_SURPRISE, off + OFFSET_ENCODING_SURPRISE, header.encodingSurprise());
        seg.set(ValueLayout.JAVA_BYTE, off + OFFSET_LAST_RECALL_PROFILE, (byte) 0);
        seg.set(ValueLayout.JAVA_BYTE, off + 61L, (byte) 0); // reserved
        seg.set(ValueLayout.JAVA_SHORT, off + 62L, (short) 0); // reserved
    }

    // ── Mutation helpers ──

    @Override public void writeImportance(MemorySegment seg, long off, float importance) {
        seg.set(LAYOUT_IMPORTANCE, off + OFFSET_IMPORTANCE, importance);
    }

    @Override public void writeTimestamp(MemorySegment seg, long off, long timestampMs) {
        seg.set(LAYOUT_TIMESTAMP, off + OFFSET_TIMESTAMP, timestampMs);
    }

    @Override public void mergeSynapticTags(MemorySegment seg, long off, long additionalTags) {
        VAR_HANDLE_SYNAPTIC_TAGS.getAndBitwiseOr(seg, off + OFFSET_SYNAPTIC_TAGS, additionalTags);
    }

    @Override public void markTombstoned(MemorySegment seg, long off) {
        byte flags = readFlags(seg, off);
        seg.set(LAYOUT_FLAGS, off + OFFSET_FLAGS, (byte) (flags | FLAG_TOMBSTONE));
    }

    @Override public void markConsolidated(MemorySegment seg, long off) {
        byte flags = readFlags(seg, off);
        seg.set(LAYOUT_FLAGS, off + OFFSET_FLAGS, (byte) (flags | FLAG_CONSOLIDATED));
    }

    @Override public void markPinned(MemorySegment seg, long off) {
        byte flags = readFlags(seg, off);
        seg.set(LAYOUT_FLAGS, off + OFFSET_FLAGS, (byte) (flags | FLAG_PINNED));
    }

    @Override public void markResolved(MemorySegment seg, long off) {
        byte flags = readFlags(seg, off);
        seg.set(LAYOUT_FLAGS, off + OFFSET_FLAGS, (byte) (flags | FLAG_RESOLVED));
    }

    @Override public void markUnresolved(MemorySegment seg, long off) {
        byte flags = readFlags(seg, off);
        seg.set(LAYOUT_FLAGS, off + OFFSET_FLAGS, (byte) (flags & ~FLAG_RESOLVED));
    }

    @Override public byte readConsolidationFlags(MemorySegment seg, long off) {
        return seg.get(LAYOUT_CONSOLIDATION_FLAGS, off + OFFSET_CONSOLIDATION_FLAGS);
    }

    @Override public void writeConsolidationFlags(MemorySegment seg, long off, byte consolidationFlags) {
        seg.set(LAYOUT_CONSOLIDATION_FLAGS, off + OFFSET_CONSOLIDATION_FLAGS, consolidationFlags);
    }

    @Override public void markContradicted(MemorySegment seg, long off) {
        byte cFlags = readConsolidationFlags(seg, off);
        writeConsolidationFlags(seg, off, (byte) (cFlags | FLAG_CONTRADICTED));
    }

    @Override public int incrementAgentRecallCount(MemorySegment seg, long off) {
        return (int) VAR_HANDLE_AGENT_RECALL_COUNT.getAndAdd(seg, off + OFFSET_AGENT_RECALL_COUNT, 1);
    }

    @Override
    public float casStorageStrength(MemorySegment seg, long off, FloatUnaryOperator updateFn) {
        long addr = off + OFFSET_STORAGE_STRENGTH;
        float prev, next;
        do {
            prev = (float) VAR_HANDLE_STORAGE_STRENGTH.getVolatile(seg, addr);
            next = updateFn.applyAsFloat(prev);
        } while (!VAR_HANDLE_STORAGE_STRENGTH.compareAndSet(seg, addr, prev, next));
        return next;
    }

    @Override
    public float casImportance(MemorySegment seg, long off, FloatUnaryOperator updateFn) {
        long addr = off + OFFSET_IMPORTANCE;
        float prev, next;
        do {
            prev = (float) VAR_HANDLE_IMPORTANCE.getVolatile(seg, addr);
            next = updateFn.applyAsFloat(prev);
        } while (!VAR_HANDLE_IMPORTANCE.compareAndSet(seg, addr, prev, next));
        return next;
    }

    @Override
    public void writeValenceRelease(MemorySegment seg, long off, byte valence) {
        seg.set(LAYOUT_VALENCE, off + OFFSET_VALENCE, valence);
    }

    // ── Auto-LTP field implementations ──

    @Override public int readSpectorRecallCount(MemorySegment seg, long off) {
        return seg.get(LAYOUT_SPECTOR_RECALL_COUNT, off + OFFSET_SPECTOR_RECALL_COUNT);
    }

    @Override public int incrementSpectorRecallCount(MemorySegment seg, long off) {
        return (int) VAR_HANDLE_SPECTOR_RECALL_COUNT.getAndAdd(seg, off + OFFSET_SPECTOR_RECALL_COUNT, 1);
    }

    @Override public long readLastAutoLtp(MemorySegment seg, long off) {
        return seg.get(LAYOUT_LAST_AUTO_LTP, off + OFFSET_LAST_AUTO_LTP);
    }

    @Override public void writeLastAutoLtp(MemorySegment seg, long off, long timestampMs) {
        seg.set(LAYOUT_LAST_AUTO_LTP, off + OFFSET_LAST_AUTO_LTP, timestampMs);
    }
}
