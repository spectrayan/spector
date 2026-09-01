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

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;

import java.lang.foreign.MemorySegment;

/**
 * Polymorphic accessor for synaptic memory record headers.
 *
 * <h3>Design: Strategy Pattern with Sealed Interface</h3>
 * <p>The header format is a 64-byte cache-line-aligned binary layout.
 * The sealed interface enables future layout versions to be added
 * without modifying existing code. The JIT devirtualizes the sealed
 * hierarchy into direct method calls — zero overhead vs. hardcoded
 * constants.</p>
 *
 * <h3>Current Implementation</h3>
 * <ul>
 *   <li><b>{@link HeaderLayout64V2} (64B)</b> — Full cache-line-aligned pure encoding format (V2, default).</li>
 *   <li><b>{@link HeaderLayout64} (64B)</b> — Legacy mixed-tenancy format (V1).</li>
 * </ul>
 *
 * @see HeaderLayout64V2
 * @see HeaderLayout64
 * @see CognitiveRecordLayout
 */
public sealed interface HeaderLayout
        permits HeaderLayout64, HeaderLayout64V2 {

    // ── Layout metadata ──

    /** Header size in bytes for this layout version. */
    int headerBytes();

    /** Layout version number. */
    int version();

    // ── Field reads ──

    /** Reads the timestamp (epoch millis) at the given record offset. */
    long readTimestamp(MemorySegment seg, long off);

    /** Reads the 64-bit Bloom filter of contextual synaptic tags. */
    long readSynapticTags(MemorySegment seg, long off);

    /** Reads the L2 norm of the original float32 vector. */
    float readExactNorm(MemorySegment seg, long off);

    /** Reads the base importance score. */
    float readImportance(MemorySegment seg, long off);

    /** Reads the LTP reinforcement counter. */
    int readAgentRecallCount(MemorySegment seg, long off);

    /** Reads the IVF centroid routing ID. */
    short readCentroidId(MemorySegment seg, long off);

    /** Reads the signed valence byte (-128 to +127). */
    byte readValence(MemorySegment seg, long off);

    /** Reads the flags bitfield. */
    byte readFlags(MemorySegment seg, long off);

    // ── Extended field reads ──

    /**
     * Reads the arousal byte (emotional intensity, unsigned 0-255).
     *
     * <p>Use {@code Byte.toUnsignedInt()} for arithmetic. Higher arousal
     * modulates decay — emotionally intense memories resist forgetting.</p>
     *
     * @return arousal value
     */
    byte readArousal(MemorySegment seg, long off);

    /**
     * Reads the storage strength for Two-Factor Memory (Bjork &amp; Bjork).
     *
     * <p>Storage strength determines how resistant a memory is to decay.
     * It increases most when retrieval occurs right before forgetting
     * (the spacing effect).</p>
     *
     * @return storage strength
     */
    float readStorageStrength(MemorySegment seg, long off);

    // ── Extended field writes ──

    /** Writes the arousal byte. */
    void writeArousal(MemorySegment seg, long off, byte arousal);

    /** Writes the storage strength. */
    void writeStorageStrength(MemorySegment seg, long off, float strength);

    // ── Full header read/write ──

    /** Reads all header fields into an immutable {@link CognitiveRecordLayout.CognitiveHeader}. */
    CognitiveRecordLayout.CognitiveHeader readHeader(MemorySegment seg, long off);

    /** Writes all header fields from a {@link CognitiveRecordLayout.CognitiveHeader}. */
    void writeHeader(MemorySegment seg, long off, CognitiveRecordLayout.CognitiveHeader header);

    // ── Mutation helpers ──

    /** Updates the importance field. */
    void writeImportance(MemorySegment seg, long off, float importance);

    /** Updates the timestamp field. */
    void writeTimestamp(MemorySegment seg, long off, long timestampMs);

    /** Merges synaptic tags via atomic bitwise OR. */
    void mergeSynapticTags(MemorySegment seg, long off, long additionalTags);

    /** Sets the tombstone flag (logical deletion). Atomic. */
    void markTombstoned(MemorySegment seg, long off);

    /** Sets the consolidated flag (reflected from Episodic → Semantic). Atomic. */
    void markConsolidated(MemorySegment seg, long off);

    /**
     * Sets the pinned flag (exempt from decay and pruning). Atomic.
     * Used by neurodivergent lossless consolidation (SYSTEMATIZER profile).
     */
    void markPinned(MemorySegment seg, long off);

    /**
     * Sets the resolved flag (Zeigarnik Effect — task is done). Atomic.
     * The memory succumbs to normal time-decay.
     */
    void markResolved(MemorySegment seg, long off);

    /**
     * Clears the resolved flag (Zeigarnik Effect — task re-opened). Atomic.
     * The memory re-enters the Zeigarnik loop and resists decay.
     */
    void markUnresolved(MemorySegment seg, long off);

    /** Reads the consolidation flags byte (offset 34). */
    byte readConsolidationFlags(MemorySegment seg, long off);

    /** Writes the consolidation flags byte (offset 34). */
    void writeConsolidationFlags(MemorySegment seg, long off, byte consolidationFlags);

    /** Sets the contradicted flag (bit 0 of consolidation flags byte). Atomic/CAS. */
    void markContradicted(MemorySegment seg, long off);

    /**
     * Atomically increments the recall count (LTP reinforcement).
     *
     * @return the previous recall count value
     */
    int incrementAgentRecallCount(MemorySegment seg, long off);

    /**
     * Atomically updates the storage strength using the given update function.
     *
     * @param seg the memory segment
     * @param off the record offset
     * @param updateFn the function to compute the new value
     * @return the newly updated storage strength
     */
    float casStorageStrength(MemorySegment seg, long off, FloatUnaryOperator updateFn);

    /**
     * Atomically updates the importance using the given update function.
     *
     * @param seg the memory segment
     * @param off the record offset
     * @param updateFn the function to compute the new value
     * @return the newly updated importance
     */
    float casImportance(MemorySegment seg, long off, FloatUnaryOperator updateFn);

    /**
     * Directly writes the valence release value.
     *
     * @param seg the memory segment
     * @param off the record offset
     * @param valence the valence value to write
     */
    void writeValenceRelease(MemorySegment seg, long off, byte valence);

    // ── Auto-LTP fields ──

    /**
     * Reads the spector-internal recall count (passive retrieval counter).
     *
     * <p>Unlike {@code agentRecallCount} (agent-explicit reinforcement),
     * this counter tracks how many times Spector's own recall pipeline
     * has surfaced this memory. Used for a gentler decay adjustment.</p>
     *
     * @return spector recall count
     */
    int readSpectorRecallCount(MemorySegment seg, long off);

    /**
     * Atomically increments the spector-internal recall count.
     *
     * @return the previous spector recall count value
     */
    int incrementSpectorRecallCount(MemorySegment seg, long off);

    /**
     * Reads the last auto-LTP timestamp (epoch millis).
     *
     * <p>Used to enforce a cooldown between passive recall reinforcements,
     * preventing runaway LTP from repeated queries.</p>
     *
     * @return last auto-LTP timestamp
     */
    long readLastAutoLtp(MemorySegment seg, long off);

    /**
     * Writes the last auto-LTP timestamp.
     */
    void writeLastAutoLtp(MemorySegment seg, long off, long timestampMs);

    // ── Encoding State fields ──

    /** Reads the encoding-time cognitive profile byte. Returns 0 (BALANCED) for pre-V3 records. */
    default byte readEncodingProfile(MemorySegment seg, long off) { return 0; }

    /** Writes the encoding-time cognitive profile byte. */
    default void writeEncodingProfile(MemorySegment seg, long off, byte profile) {}

    /** Reads the quantized alpha weight at encoding time. Returns 0 for pre-V3 records. */
    default byte readEncodingAlpha(MemorySegment seg, long off) { return 0; }

    /** Writes the quantized alpha weight at encoding time. */
    default void writeEncodingAlpha(MemorySegment seg, long off, byte alpha) {}

    /** Reads the quantized beta weight at encoding time. Returns 0 for pre-V3 records. */
    default byte readEncodingBeta(MemorySegment seg, long off) { return 0; }

    /** Writes the quantized beta weight at encoding time. */
    default void writeEncodingBeta(MemorySegment seg, long off, byte beta) {}

    /** Reads the soul version counter. Returns 0 (no soul) for pre-V3 records. */
    default short readSoulVersion(MemorySegment seg, long off) { return 0; }

    /** Writes the soul version counter. */
    default void writeSoulVersion(MemorySegment seg, long off, short version) {}

    /** Reads the surprise z-score at encoding time. Returns 0.0f for pre-V3 records. */
    default float readEncodingSurprise(MemorySegment seg, long off) { return 0f; }

    /** Writes the surprise z-score at encoding time. */
    default void writeEncodingSurprise(MemorySegment seg, long off, float surprise) {}

    /** Reads the low 64 bits of the 128-bit synaptic Bloom filter. */
    default long readSynapticTagsLo(MemorySegment seg, long off) {
        return readSynapticTags(seg, off);
    }

    /** Reads the high 64 bits of the 128-bit synaptic Bloom filter. */
    default long readSynapticTagsHi(MemorySegment seg, long off) {
        return 0L;
    }

    /** Writes a 128-bit synaptic tag filter (low and high 64 bits). */
    default void writeSynapticTags(MemorySegment seg, long off, long tagsLo, long tagsHi) {
        mergeSynapticTags(seg, off, tagsLo);
    }

    /** Merges 128-bit synaptic tags via atomic bitwise OR. */
    default void mergeSynapticTags128(MemorySegment seg, long off, long additionalLo, long additionalHi) {
        mergeSynapticTags(seg, off, additionalLo);
    }

    // ── Factory methods ──

    /**
     * Returns the layout for the given version number.
     *
     * @param version layout version (1 for V1 legacy, 2 for V2 pure encoding)
     * @return the corresponding layout instance (singleton)
     * @throws SpectorValidationException if version is unknown
     */
    static HeaderLayout forVersion(int version) {
        return switch (version) {
            case 1 -> HeaderLayout64.INSTANCE;
            case 2 -> HeaderLayout64V2.INSTANCE;
            default -> throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID, "header layout version", version + " (supported: 1, 2)");
        };
    }

    /** Default layout for all new stores (V1, 64 bytes). */
    static HeaderLayout defaultLayout() {
        return HeaderLayout64.INSTANCE;
    }

    /**
     * Detects the layout version from a store's metadata segment.
     *
     * @param metadataVersion the version byte from the store metadata
     * @return the corresponding layout
     */
    static HeaderLayout detect(int metadataVersion) {
        if (metadataVersion <= 0) {
            return HeaderLayout64.INSTANCE; // default to V1
        }
        return forVersion(metadataVersion);
    }
}
