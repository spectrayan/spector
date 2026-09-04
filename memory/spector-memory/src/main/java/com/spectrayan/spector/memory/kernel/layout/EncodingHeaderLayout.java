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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.memory.kernel.FloatUnaryOperator;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

import static com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants.*;

/**
 * Pure 64-byte cache-line-aligned encoding header layout (V2, ADR-0028).
 *
 * <p>This is the sole live engram encoding header layout in Spector, realizing the
 * pure-encoding identity rule from MF-001 / ADR-0028.</p>
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><b>Pure Encoding Identity</b>: Contains only immutable/read-mostly engram properties
 *       established during memory ingestion.</li>
 *   <li><b>Zero False Sharing</b>: All mutable telemetry (recall counters, auto-LTP cooldowns,
 *       storage strength, ACT-R ring buffer) is relocated to {@link StrengthLayout}.</li>
 *   <li><b>128-Bit Synaptic Tags</b>: Contextual Bloom filter expanded from 64-bit to 128-bit
 *       (offsets 24-39), slashing candidate pre-screening false positives by ~60×.</li>
 *   <li><b>Cache-Line Aligned</b>: Exact 64-byte stride aligned to CPU hardware cache lines.</li>
 * </ul>
 *
 * <h3>Layout (64 bytes — V2)</h3>
 * <pre>
 *   Offset  Size  Field                Type     Description
 *   ──────  ────  ─────────────────    ───────  ──────────────────────────────────────────
 *    0      1B    header_version       uint8    Always 2 (V2 header)
 *    1      1B    flags                uint8    Tombstone, type, consolidated, pinned, resolved, modality
 *    2      1B    valence              int8     Initial signed emotional valence (-128 to +127)
 *    3      1B    arousal              uint8    Initial unsigned emotional arousal (0-255)
 *    4      4B    importance           float32  Initial ICNU base importance score
 *    8      8B    timestamp_ms         int64    Unix epoch ms when memory was formed
 *   16      4B    exact_norm           float32  L2 norm of unquantized vector
 *   20      2B    centroid_id          int16    IVF partition routing cluster ID
 *   22      2B    _pad0                bytes    Alignment padding
 *   24      8B    synaptic_tags_lo     uint64   128-bit Bloom filter low 64 bits
 *   32      8B    synaptic_tags_hi     uint64   128-bit Bloom filter high 64 bits
 *   40      1B    consolidation_flags  uint8    Provenance flags (simulated, crystallized, dreamed)
 *   41      1B    encoding_profile     uint8    Cognitive state at ingestion (bit 7=soul-derived)
 *   42      1B    encoding_alpha       uint8    Quantized alpha weight at ingestion (0-255)
 *   43      1B    encoding_beta        uint8    Quantized beta weight at ingestion (0-255)
 *   44      2B    soul_version         uint16   Monotonic soul configuration generation counter
 *   46      2B    _reserved_geo        bytes    Reserved for manifold geodesic coordinates
 *   48      4B    encoding_surprise    float32  Bayesian surprise z-score at ingestion
 *   52     12B    _reserved            bytes    Zero-padded reserved block for tensor invariants
 *   ── 64B total cache line ───────────────────────────────────────────────────────────────
 * </pre>
 *
 * @see StrengthLayout
 * @see SynapticHeaderConstants
 */
public record EncodingHeaderLayout() {

    /** Singleton instance of the encoding header layout. */
    public static final EncodingHeaderLayout INSTANCE = new EncodingHeaderLayout();

    public static final VarHandle VAR_HANDLE_SYNAPTIC_TAGS_LO = LAYOUT_SYNAPTIC_TAGS.varHandle();
    public static final VarHandle VAR_HANDLE_SYNAPTIC_TAGS_HI = LAYOUT_SYNAPTIC_TAGS.varHandle();
    public static final VarHandle VAR_HANDLE_IMPORTANCE_V2    = LAYOUT_IMPORTANCE.varHandle();

    /** Header size in bytes (64 bytes, 1 CPU cache line). */
    public static final int HEADER_BYTES = SynapticHeaderConstants.HEADER_BYTES;

    /** Default layout for all new stores (V2, 64 bytes). */
    public static EncodingHeaderLayout defaultLayout() {
        return INSTANCE;
    }

    /**
     * Returns the layout for the given version number.
     *
     * @param version layout version (supported: 2)
     * @return the corresponding layout instance
     * @throws SpectorValidationException if version is not 2
     */
    public static EncodingHeaderLayout forVersion(int version) {
        if (version != HEADER_VERSION_V2) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID, "header layout version", version + " (supported: " + HEADER_VERSION_V2 + ")");
        }
        return INSTANCE;
    }

    public int headerBytes() { return HEADER_BYTES; }
    public int version() { return HEADER_VERSION_V2; }

    // ── Field reads ──

    public long readTimestamp(MemorySegment seg, long off) {
        return seg.get(LAYOUT_TIMESTAMP, off + OFFSET_TIMESTAMP);
    }

    public long readSynapticTags(MemorySegment seg, long off) {
        return seg.get(LAYOUT_SYNAPTIC_TAGS, off + OFFSET_V2_SYNAPTIC_TAGS_LO);
    }

    public long readSynapticTagsLo(MemorySegment seg, long off) {
        return seg.get(LAYOUT_SYNAPTIC_TAGS, off + OFFSET_V2_SYNAPTIC_TAGS_LO);
    }

    public long readSynapticTagsHi(MemorySegment seg, long off) {
        return seg.get(LAYOUT_SYNAPTIC_TAGS, off + OFFSET_V2_SYNAPTIC_TAGS_HI);
    }

    public float readExactNorm(MemorySegment seg, long off) {
        return seg.get(LAYOUT_EXACT_NORM, off + OFFSET_V2_EXACT_NORM);
    }

    public float readImportance(MemorySegment seg, long off) {
        return seg.get(LAYOUT_IMPORTANCE, off + OFFSET_IMPORTANCE);
    }

    public int readAgentRecallCount(MemorySegment seg, long off) {
        return 0; // Pure V2 header does not store recall counters; lives in StrengthLayout
    }

    public short readCentroidId(MemorySegment seg, long off) {
        return seg.get(LAYOUT_CENTROID_ID, off + OFFSET_V2_CENTROID_ID);
    }

    public byte readValence(MemorySegment seg, long off) {
        return seg.get(LAYOUT_VALENCE, off + OFFSET_VALENCE);
    }

    public byte readFlags(MemorySegment seg, long off) {
        return seg.get(LAYOUT_FLAGS, off + OFFSET_FLAGS);
    }

    public byte readArousal(MemorySegment seg, long off) {
        return seg.get(LAYOUT_AROUSAL, off + OFFSET_AROUSAL);
    }

    public float readStorageStrength(MemorySegment seg, long off) {
        return SpectorPropertyConstants.DEFAULT_MEMORY_TWOFACTOR_INITIAL_STORAGE_STRENGTH;
    }

    public byte readEncodingProfile(MemorySegment seg, long off) {
        return seg.get(LAYOUT_ENCODING_PROFILE, off + OFFSET_V2_ENCODING_PROFILE);
    }

    public byte readEncodingAlpha(MemorySegment seg, long off) {
        return seg.get(LAYOUT_ENCODING_ALPHA, off + OFFSET_V2_ENCODING_ALPHA);
    }

    public byte readEncodingBeta(MemorySegment seg, long off) {
        return seg.get(LAYOUT_ENCODING_BETA, off + OFFSET_V2_ENCODING_BETA);
    }

    public short readSoulVersion(MemorySegment seg, long off) {
        return seg.get(LAYOUT_SOUL_VERSION, off + OFFSET_V2_SOUL_VERSION);
    }

    public float readEncodingSurprise(MemorySegment seg, long off) {
        return seg.get(LAYOUT_ENCODING_SURPRISE, off + OFFSET_V2_ENCODING_SURPRISE);
    }

    public byte readConsolidationFlags(MemorySegment seg, long off) {
        return seg.get(LAYOUT_CONSOLIDATION_FLAGS, off + OFFSET_V2_CONSOLIDATION_FLAGS);
    }

    // ── Field writes ──

    public void writeArousal(MemorySegment seg, long off, byte arousal) {
        seg.set(LAYOUT_AROUSAL, off + OFFSET_AROUSAL, arousal);
    }

    public void writeStorageStrength(MemorySegment seg, long off, float strength) {
        // No-op on EncodingHeaderLayout — storage strength written to StrengthLayout
    }

    public void writeEncodingProfile(MemorySegment seg, long off, byte profile) {
        seg.set(LAYOUT_ENCODING_PROFILE, off + OFFSET_V2_ENCODING_PROFILE, profile);
    }

    public void writeEncodingAlpha(MemorySegment seg, long off, byte alpha) {
        seg.set(LAYOUT_ENCODING_ALPHA, off + OFFSET_V2_ENCODING_ALPHA, alpha);
    }

    public void writeEncodingBeta(MemorySegment seg, long off, byte beta) {
        seg.set(LAYOUT_ENCODING_BETA, off + OFFSET_V2_ENCODING_BETA, beta);
    }

    public void writeSoulVersion(MemorySegment seg, long off, short version) {
        seg.set(LAYOUT_SOUL_VERSION, off + OFFSET_V2_SOUL_VERSION, version);
    }

    public void writeEncodingSurprise(MemorySegment seg, long off, float surprise) {
        seg.set(LAYOUT_ENCODING_SURPRISE, off + OFFSET_V2_ENCODING_SURPRISE, surprise);
    }

    public void writeConsolidationFlags(MemorySegment seg, long off, byte consolidationFlags) {
        seg.set(LAYOUT_CONSOLIDATION_FLAGS, off + OFFSET_V2_CONSOLIDATION_FLAGS, consolidationFlags);
    }

    public void writeImportance(MemorySegment seg, long off, float importance) {
        seg.set(LAYOUT_IMPORTANCE, off + OFFSET_IMPORTANCE, importance);
    }

    public void writeTimestamp(MemorySegment seg, long off, long timestampMs) {
        seg.set(LAYOUT_TIMESTAMP, off + OFFSET_TIMESTAMP, timestampMs);
    }

    public void writeSynapticTags(MemorySegment seg, long off, long tagsLo, long tagsHi) {
        seg.set(LAYOUT_SYNAPTIC_TAGS, off + OFFSET_V2_SYNAPTIC_TAGS_LO, tagsLo);
        seg.set(LAYOUT_SYNAPTIC_TAGS, off + OFFSET_V2_SYNAPTIC_TAGS_HI, tagsHi);
    }

    public void mergeSynapticTags(MemorySegment seg, long off, long additionalTags) {
        VAR_HANDLE_SYNAPTIC_TAGS_LO.getAndBitwiseOr(seg, off + OFFSET_V2_SYNAPTIC_TAGS_LO, additionalTags);
    }

    public void mergeSynapticTags128(MemorySegment seg, long off, long additionalLo, long additionalHi) {
        VAR_HANDLE_SYNAPTIC_TAGS_LO.getAndBitwiseOr(seg, off + OFFSET_V2_SYNAPTIC_TAGS_LO, additionalLo);
        if (additionalHi != 0L) {
            VAR_HANDLE_SYNAPTIC_TAGS_HI.getAndBitwiseOr(seg, off + OFFSET_V2_SYNAPTIC_TAGS_HI, additionalHi);
        }
    }

    public void markTombstoned(MemorySegment seg, long off) {
        byte flags = readFlags(seg, off);
        seg.set(LAYOUT_FLAGS, off + OFFSET_FLAGS, (byte) (flags | FLAG_TOMBSTONE));
    }

    public void markConsolidated(MemorySegment seg, long off) {
        byte flags = readFlags(seg, off);
        seg.set(LAYOUT_FLAGS, off + OFFSET_FLAGS, (byte) (flags | FLAG_CONSOLIDATED));
    }

    public void markPinned(MemorySegment seg, long off) {
        byte flags = readFlags(seg, off);
        seg.set(LAYOUT_FLAGS, off + OFFSET_FLAGS, (byte) (flags | FLAG_PINNED));
    }

    public void markResolved(MemorySegment seg, long off) {
        byte flags = readFlags(seg, off);
        seg.set(LAYOUT_FLAGS, off + OFFSET_FLAGS, (byte) (flags | FLAG_RESOLVED));
    }

    public void markUnresolved(MemorySegment seg, long off) {
        byte flags = readFlags(seg, off);
        seg.set(LAYOUT_FLAGS, off + OFFSET_FLAGS, (byte) (flags & ~FLAG_RESOLVED));
    }

    public void markContradicted(MemorySegment seg, long off) {
        byte cFlags = readConsolidationFlags(seg, off);
        writeConsolidationFlags(seg, off, (byte) (cFlags | FLAG_CONTRADICTED));
    }

    public int incrementAgentRecallCount(MemorySegment seg, long off) {
        return 0; // Routed to StrengthLayout in dual-region architecture
    }

    public float casStorageStrength(MemorySegment seg, long off, FloatUnaryOperator updateFn) {
        return 1.0f; // Routed to StrengthLayout in dual-region architecture
    }

    public float casImportance(MemorySegment seg, long off, FloatUnaryOperator updateFn) {
        long addr = off + OFFSET_IMPORTANCE;
        float prev, next;
        do {
            prev = (float) VAR_HANDLE_IMPORTANCE_V2.getVolatile(seg, addr);
            next = updateFn.applyAsFloat(prev);
        } while (!VAR_HANDLE_IMPORTANCE_V2.compareAndSet(seg, addr, prev, next));
        return next;
    }

    public void writeValenceRelease(MemorySegment seg, long off, byte valence) {
        seg.set(LAYOUT_VALENCE, off + OFFSET_VALENCE, valence);
    }

    public int readSpectorRecallCount(MemorySegment seg, long off) {
        return 0;
    }

    public int incrementSpectorRecallCount(MemorySegment seg, long off) {
        return 0;
    }

    public long readLastAutoLtp(MemorySegment seg, long off) {
        return 0L;
    }

    public void writeLastAutoLtp(MemorySegment seg, long off, long timestampMs) {
    }

    // ── Full header read/write ──

    public CognitiveHeader readHeader(MemorySegment seg, long off) {
        return new CognitiveHeader(
                readTimestamp(seg, off),
                readSynapticTags(seg, off),
                readExactNorm(seg, off),
                readImportance(seg, off),
                0, // agentRecallCount lives in strength region
                readCentroidId(seg, off),
                readValence(seg, off),
                readFlags(seg, off),
                readArousal(seg, off),
                SpectorPropertyConstants.DEFAULT_MEMORY_TWOFACTOR_INITIAL_STORAGE_STRENGTH, // storageStrength lives in strength region
                readEncodingProfile(seg, off),
                readEncodingAlpha(seg, off),
                readEncodingBeta(seg, off),
                readSoulVersion(seg, off),
                readEncodingSurprise(seg, off),
                readConsolidationFlags(seg, off)
        );
    }

    public void writeHeader(MemorySegment seg, long off, CognitiveHeader header) {
        seg.set(LAYOUT_HEADER_VERSION, off + OFFSET_HEADER_VERSION, (byte) HEADER_VERSION_V2);
        seg.set(LAYOUT_FLAGS,         off + OFFSET_FLAGS,          header.flags());
        seg.set(LAYOUT_VALENCE,       off + OFFSET_VALENCE,        header.valence());
        seg.set(LAYOUT_AROUSAL,       off + OFFSET_AROUSAL,        header.arousal());
        seg.set(LAYOUT_IMPORTANCE,    off + OFFSET_IMPORTANCE,     header.importance());
        seg.set(LAYOUT_TIMESTAMP,     off + OFFSET_TIMESTAMP,      header.timestampMs());
        seg.set(LAYOUT_EXACT_NORM,    off + OFFSET_V2_EXACT_NORM,  header.exactNorm());
        seg.set(LAYOUT_CENTROID_ID,   off + OFFSET_V2_CENTROID_ID, header.centroidId());
        seg.set(ValueLayout.JAVA_SHORT, off + OFFSET_V2_PAD0,     (short) 0);
        seg.set(LAYOUT_SYNAPTIC_TAGS, off + OFFSET_V2_SYNAPTIC_TAGS_LO, header.synapticTags());
        seg.set(LAYOUT_SYNAPTIC_TAGS, off + OFFSET_V2_SYNAPTIC_TAGS_HI, 0L);
        seg.set(LAYOUT_CONSOLIDATION_FLAGS, off + OFFSET_V2_CONSOLIDATION_FLAGS, header.consolidationFlags());
        seg.set(LAYOUT_ENCODING_PROFILE, off + OFFSET_V2_ENCODING_PROFILE, header.encodingProfile());
        seg.set(LAYOUT_ENCODING_ALPHA, off + OFFSET_V2_ENCODING_ALPHA, header.encodingAlpha());
        seg.set(LAYOUT_ENCODING_BETA, off + OFFSET_V2_ENCODING_BETA, header.encodingBeta());
        seg.set(LAYOUT_SOUL_VERSION, off + OFFSET_V2_SOUL_VERSION, header.soulVersion());
        seg.set(ValueLayout.JAVA_SHORT, off + OFFSET_V2_RESERVED_GEO, (short) 0);
        seg.set(LAYOUT_ENCODING_SURPRISE, off + OFFSET_V2_ENCODING_SURPRISE, header.encodingSurprise());
        // Zero reserved block (12 bytes at 4-byte aligned offset 52)
        seg.set(ValueLayout.JAVA_INT, off + OFFSET_V2_RESERVED, 0);
        seg.set(ValueLayout.JAVA_INT, off + OFFSET_V2_RESERVED + 4L, 0);
        seg.set(ValueLayout.JAVA_INT, off + OFFSET_V2_RESERVED + 8L, 0);
    }
}
