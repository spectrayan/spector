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

/**
 * Constants and field definitions for the 64-byte engram encoding header.
 *
 * <p>This class realizes the <b>MF-001</b> pure encoding identity model (ADR-0028).
 * The 64-byte engram header is strictly immutable or read-mostly after formation.
 * All mutable telemetry (recall counters, storage strength, auto-LTP cooldowns) has
 * been relocated to {@code StrengthLayout} / {@code StrengthMemory} (Region 4).</p>
 *
 * <h3>Layout (64 bytes — V2 Pure Encoding Header, full cache line)</h3>
 * <pre>
 *   Offset  Size  Field                Type     Description
 *   ──────  ────  ─────────────────    ───────  ──────────────────────────────────────────
 *    0      1B    header_version       uint8    Format version (always 2)
 *    1      1B    flags                uint8    Tombstone, memory type, consolidated, pinned, resolved, modality
 *    2      1B    valence              int8     Initial signed emotional valence (-128 to +127)
 *    3      1B    arousal              uint8    Initial unsigned emotional arousal (0-255)
 *    4      4B    importance           float32  Initial base importance score
 *    8      8B    timestamp_ms         int64    Unix epoch ms when memory was formed
 *   16      4B    exact_norm           float32  L2 norm of unquantized vector
 *   20      2B    centroid_id          int16    IVF partition routing cluster ID
 *   22      2B    _pad0                bytes    Alignment padding
 *   24      8B    synaptic_tags_lo     uint64   128-bit Bloom filter low 64 bits
 *   32      8B    synaptic_tags_hi     uint64   128-bit Bloom filter high 64 bits
 *   40      1B    consolidation_flags  uint8    Provenance flags (contradicted, retracted, unverified, restricted, crystallized, simulated, dreamed)
 *   41      1B    encoding_profile     uint8    Cognitive state at ingestion (bit 7=soul-derived)
 *   42      1B    encoding_alpha       uint8    Quantized alpha weight at ingestion (0-255)
 *   43      1B    encoding_beta        uint8    Quantized beta weight at ingestion (0-255)
 *   44      2B    soul_version         uint16   Monotonic soul configuration generation counter
 *   46      1B    source               uint8    Trace source: experienced(0), distilled(1), simulated(2), rehearsed(3) (NF7)
 *   47      1B    _pad_source          bytes    Alignment padding
 *   48      4B    encoding_surprise    float32  Bayesian surprise z-score at ingestion
 *   52     12B    _reserved            bytes    Zero-padded reserved block for neural tensor invariants
 *   ── 64B total cache line ───────────────────────────────────────────────────────────────
 * </pre>
 *
 * <h3>Flags Bitfield (byte 1)</h3>
 * <pre>
 *   bit 0:   tombstone  (deleted / pruned by Deep Sleep)
 *   bit 1-2: memory_type (2 bits → 4 types: WORKING, EPISODIC, SEMANTIC, PROCEDURAL)
 *   bit 3:   consolidated (has been reflected into Semantic tier)
 *   bit 4:   pinned (exempt from decay/pruning)
 *   bit 5:   resolved (Zeigarnik Effect — unresolved tasks resist decay)
 *   bit 6-7: source_modality (2 bits → 4 modalities: TEXT, IMAGE, AUDIO, VIDEO)
 * </pre>
 *
 * <h3>Consolidation &amp; Governance Flags (byte 40)</h3>
 * <pre>
 *   bit 0: contradicted (CADP contradiction loser)
 *   bit 1: retracted (legally/explicitly retracted)
 *   bit 2: unverified (low-trust source)
 *   bit 3: restricted (RBAC or sovereign policy)
 *   bit 4: crystallized (skill synthesized from episodes)
 *   bit 5: simulated (counterfactual simulation, NF7)
 *   bit 7: dreamed (generated during sleep cycle)
 * </pre>
 *
 * @see EncodingHeaderLayout
 * @see StrengthLayout
 */
public final class EncodingHeaderFields {

    private EncodingHeaderFields() {}

    /** Header size in bytes (64B = full cache line). */
    public static final int HEADER_BYTES = 64;

    /** Legacy V1 header format version. */
    public static final int HEADER_VERSION_V1 = 1;

    /** V2 pure encoding header format version (ADR-0028). */
    public static final int HEADER_VERSION_V2 = 2;

    /** Current default header format version. */
    public static final int HEADER_VERSION = HEADER_VERSION_V2;

    // ── V1 Field offsets (first 32 bytes) ──

    /** Offset of header_version byte (always byte 0). */
    public static final long OFFSET_HEADER_VERSION      = 0L;
    /** Offset of flags bitfield. */
    public static final long OFFSET_FLAGS               = 1L;
    /** Offset of valence byte (signed -128 to +127). */
    public static final long OFFSET_VALENCE             = 2L;
    /** Offset of arousal byte (unsigned 0-255). */
    public static final long OFFSET_AROUSAL             = 3L;
    /** Offset of importance float (4B-aligned). */
    public static final long OFFSET_IMPORTANCE          = 4L;
    /** Offset of timestamp_ms long (8B-aligned). */
    public static final long OFFSET_TIMESTAMP           = 8L;
    /** Offset of agent_recall_count int (4B-aligned, V1 only). */
    public static final long OFFSET_AGENT_RECALL_COUNT  = 16L;
    /** Offset of exact_norm float. */
    public static final long OFFSET_EXACT_NORM          = 20L;
    /** Offset of synaptic_tags long (8B-aligned). */
    public static final long OFFSET_SYNAPTIC_TAGS       = 24L;

    // ── V1 Field offsets (bytes 32-63) ──

    /** Offset of centroid_id short (IVF routing). */
    public static final long OFFSET_CENTROID_ID         = 32L;
    /** Offset of consolidation flags byte. */
    public static final long OFFSET_CONSOLIDATION_FLAGS = 34L;
    /** Offset of encoding-time cognitive profile byte. */
    public static final long OFFSET_ENCODING_PROFILE    = 35L;
    /** Offset of storage_strength float (Two-Factor scoring, V1 only). */
    public static final long OFFSET_STORAGE_STRENGTH    = 36L;
    /** Offset of spector-internal recall count int (auto-LTP, V1 only). */
    public static final long OFFSET_SPECTOR_RECALL_COUNT = 40L;
    /** Offset of quantized alpha weight at encoding time. */
    public static final long OFFSET_ENCODING_ALPHA      = 44L;
    /** Offset of quantized beta weight at encoding time. */
    public static final long OFFSET_ENCODING_BETA       = 45L;
    /** Offset of monotonic soul configuration version counter (2 bytes). */
    public static final long OFFSET_SOUL_VERSION        = 46L;
    /** Offset of last auto-LTP timestamp long (8B-aligned, V1 only). */
    public static final long OFFSET_LAST_AUTO_LTP       = 48L;
    /** Offset of surprise z-score at encoding time (float32). */
    public static final long OFFSET_ENCODING_SURPRISE   = 56L;
    /** Profile ordinal of the CognitiveProfile used during last recall (V1 only). */
    public static final long OFFSET_LAST_RECALL_PROFILE = 60L;

    // ── V2 Pure Encoding Field offsets (64B Cache-Line Aligned, ADR-0028) ──

    /** V2: Offset of exact_norm float (16-19). */
    public static final long OFFSET_V2_EXACT_NORM          = 16L;
    /** V2: Offset of centroid_id short (20-21). */
    public static final long OFFSET_V2_CENTROID_ID         = 20L;
    /** V2: Alignment padding (22-23). */
    public static final long OFFSET_V2_PAD0                = 22L;
    /** V2: 128-bit Bloom filter low 64-bits (24-31). */
    public static final long OFFSET_V2_SYNAPTIC_TAGS_LO    = 24L;
    /** V2: 128-bit Bloom filter high 64-bits (32-39). */
    public static final long OFFSET_V2_SYNAPTIC_TAGS_HI    = 32L;
    /** V2: Offset of consolidation & provenance flags (byte 40). */
    public static final long OFFSET_V2_CONSOLIDATION_FLAGS = 40L;
    /** V2: Offset of encoding-time cognitive profile (byte 41). */
    public static final long OFFSET_V2_ENCODING_PROFILE    = 41L;
    /** V2: Offset of quantized alpha weight (byte 42). */
    public static final long OFFSET_V2_ENCODING_ALPHA      = 42L;
    /** V2: Offset of quantized beta weight (byte 43). */
    public static final long OFFSET_V2_ENCODING_BETA       = 43L;
    /** V2: Offset of soul configuration version counter (bytes 44-45). */
    public static final long OFFSET_V2_SOUL_VERSION        = 44L;
    /** V2: Offset of trace source classification (byte 46, NF7). */
    public static final long OFFSET_V2_SOURCE              = 46L;
    /** V2: Reserved alignment padding (byte 47). */
    public static final long OFFSET_V2_PAD_SOURCE          = 47L;
    /** V2: Reserved for manifold geodesic coordinates (bytes 46-47, legacy alias). */
    public static final long OFFSET_V2_RESERVED_GEO        = 46L;
    /** V2: Offset of surprise z-score (float32, bytes 48-51). */
    public static final long OFFSET_V2_ENCODING_SURPRISE   = 48L;
    /** V2: Zero-padded reserved block for neural tensor invariants (bytes 52-63). */
    public static final long OFFSET_V2_RESERVED            = 52L;

    // ── Value layouts ──

    public static final ValueLayout.OfByte  LAYOUT_HEADER_VERSION     = ValueLayout.JAVA_BYTE;
    public static final ValueLayout.OfByte  LAYOUT_FLAGS              = ValueLayout.JAVA_BYTE;
    public static final ValueLayout.OfByte  LAYOUT_VALENCE            = ValueLayout.JAVA_BYTE;
    public static final ValueLayout.OfByte  LAYOUT_AROUSAL            = ValueLayout.JAVA_BYTE;
    public static final ValueLayout.OfFloat LAYOUT_IMPORTANCE         = ValueLayout.JAVA_FLOAT;
    public static final ValueLayout.OfLong  LAYOUT_TIMESTAMP          = ValueLayout.JAVA_LONG;
    public static final ValueLayout.OfInt   LAYOUT_AGENT_RECALL_COUNT = ValueLayout.JAVA_INT;
    public static final ValueLayout.OfFloat LAYOUT_EXACT_NORM         = ValueLayout.JAVA_FLOAT;
    public static final ValueLayout.OfLong  LAYOUT_SYNAPTIC_TAGS      = ValueLayout.JAVA_LONG;
    public static final ValueLayout.OfShort LAYOUT_CENTROID_ID        = ValueLayout.JAVA_SHORT;
    public static final ValueLayout.OfByte  LAYOUT_CONSOLIDATION_FLAGS = ValueLayout.JAVA_BYTE;
    public static final ValueLayout.OfFloat LAYOUT_STORAGE_STRENGTH   = ValueLayout.JAVA_FLOAT;
    public static final ValueLayout.OfInt   LAYOUT_SPECTOR_RECALL_COUNT = ValueLayout.JAVA_INT;
    public static final ValueLayout.OfLong  LAYOUT_LAST_AUTO_LTP      = ValueLayout.JAVA_LONG;
    public static final ValueLayout.OfByte  LAYOUT_ENCODING_PROFILE  = ValueLayout.JAVA_BYTE;
    public static final ValueLayout.OfByte  LAYOUT_ENCODING_ALPHA    = ValueLayout.JAVA_BYTE;
    public static final ValueLayout.OfByte  LAYOUT_ENCODING_BETA     = ValueLayout.JAVA_BYTE;
    public static final ValueLayout.OfShort LAYOUT_SOUL_VERSION      = ValueLayout.JAVA_SHORT;
    public static final ValueLayout.OfByte  LAYOUT_SOURCE            = ValueLayout.JAVA_BYTE;
    public static final ValueLayout.OfFloat LAYOUT_ENCODING_SURPRISE = ValueLayout.JAVA_FLOAT;

    // ── VarHandle views for atomic access ──

    /** VarHandle for atomic updates to the agent_recall_count field. */
    public static final java.lang.invoke.VarHandle VAR_HANDLE_AGENT_RECALL_COUNT = LAYOUT_AGENT_RECALL_COUNT.varHandle();
    /** VarHandle for atomic updates to the spector_recall_count field. */
    public static final java.lang.invoke.VarHandle VAR_HANDLE_SPECTOR_RECALL_COUNT = LAYOUT_SPECTOR_RECALL_COUNT.varHandle();
    /** VarHandle for atomic bitwise synaptic tag merging (getAndBitwiseOr). */
    public static final java.lang.invoke.VarHandle VAR_HANDLE_SYNAPTIC_TAGS = LAYOUT_SYNAPTIC_TAGS.varHandle();
    /** VarHandle for atomic updates to the storage_strength field. */
    public static final java.lang.invoke.VarHandle VAR_HANDLE_STORAGE_STRENGTH = LAYOUT_STORAGE_STRENGTH.varHandle();
    /** VarHandle for atomic updates to the importance field. */
    public static final java.lang.invoke.VarHandle VAR_HANDLE_IMPORTANCE = LAYOUT_IMPORTANCE.varHandle();

    // ── Flags bitmasks ──

    /** Bit 0: Record has been logically deleted (tombstoned). */
    public static final byte FLAG_TOMBSTONE    = 0x01;
    /** Bits 1-2: Memory type (2 bits → 4 types). */
    public static final byte FLAG_TYPE_MASK    = 0x06;
    /** Number of bits to shift to read/write memory type from flags. */
    public static final int  FLAG_TYPE_SHIFT   = 1;
    /** Bit 3: Memory has been consolidated (reflected from Episodic → Semantic). */
    public static final byte FLAG_CONSOLIDATED = 0x08;
    /** Bit 4: Memory is pinned (exempt from decay and pruning). */
    public static final byte FLAG_PINNED       = 0x10;
    /** Bit 5: Memory is resolved (Zeigarnik Effect — unresolved memories resist time-decay). */
    public static final byte FLAG_RESOLVED     = 0x20;
    /** Bits 6-7: Source modality (2 bits → 4 modalities: TEXT=0, IMAGE=1, AUDIO=2, VIDEO=3). */
    public static final byte FLAG_MODALITY_MASK  = (byte) 0xC0;
    /** Number of bits to shift to read/write source modality from flags. */
    public static final int  FLAG_MODALITY_SHIFT = 6;

    // ── Consolidation & Governance Flags bitmasks (offset 34) ──

    /** Bit 0: Memory has a conflicting near-duplicate in the tier. */
    public static final byte FLAG_CONTRADICTED = 0x01;
    /** Bit 1: Memory has been explicitly or legally retracted (fail-closed release). */
    public static final byte FLAG_RETRACTED    = 0x02;
    /** Bit 2: Memory was ingested from an unverified or low-trust source. */
    public static final byte FLAG_UNVERIFIED   = 0x04;
    /** Bit 3: Memory access is restricted by sovereign policy or RBAC. */
    public static final byte FLAG_RESTRICTED   = 0x08;
    /** Bit 4: Memory represents a crystallized procedural skill synthesized from episodic traces. */
    public static final byte FLAG_CRYSTALLIZED = 0x10;
    /** Bit 5: Memory was generated by constructive simulation (counterfactual recombination, not lived experience). */
    public static final byte FLAG_SIMULATED = 0x20;
    
    /** Memory was generated during a dream/thought experiment cycle. Byte 34, bit 7. */
    public static final byte FLAG_DREAMED = (byte) 0x80;

    // ── NF7 Source Honesty constants (offset 46) ──

    /** Source code 0: experienced trace (from physical world or user; NF7). */
    public static final byte SOURCE_EXPERIENCED = 0;
    /** Source code 1: distilled trace (reflection, crystallization, commit_simulation; NF7). */
    public static final byte SOURCE_DISTILLED   = 1;
    /** Source code 2: simulated trace (constructive simulation, dream, counterfactual; NF7). */
    public static final byte SOURCE_SIMULATED   = 2;
    /** Source code 3: rehearsed trace (cross-agent transfer, replay; NF7). */
    public static final byte SOURCE_REHEARSED   = 3;

    // ── Encoding Profile bitmasks (offset 35) ──

    /** Bit 7: Memory was ingested under a soul-derived configuration (not a preset CognitiveProfile). */
    public static final byte ENCODING_FLAG_SOUL_DERIVED = (byte) 0x80;
    /** Bits 0-3: CognitiveProfile ordinal when not soul-derived (0-15). */
    public static final byte ENCODING_PROFILE_MASK = 0x0F;

    // ── Convenience methods ──

    /**
     * Checks if the tombstone flag is set in the given flags byte.
     */
    public static boolean isTombstoned(byte flags) {
        return (flags & FLAG_TOMBSTONE) != 0;
    }

    /**
     * Checks if the pinned flag is set.
     */
    public static boolean isPinned(byte flags) {
        return (flags & FLAG_PINNED) != 0;
    }

    /**
     * Checks if the resolved flag is set (Zeigarnik Effect).
     *
     * <p>When {@code false} (default for new memories), the memory resists
     * time-decay — it floats to the top of recall like an unfinished task.
     * When the agent marks the task complete, this flips to {@code true}
     * and the memory succumbs to normal decay.</p>
     */
    public static boolean isResolved(byte flags) {
        return (flags & FLAG_RESOLVED) != 0;
    }

    /**
     * Checks if the consolidated flag is set.
     */
    public static boolean isConsolidated(byte flags) {
        return (flags & FLAG_CONSOLIDATED) != 0;
    }

    /**
     * Extracts the 2-bit memory type ordinal (0–3) from the flags byte.
     */
    public static int memoryTypeOrdinal(byte flags) {
        return (flags & FLAG_TYPE_MASK) >>> FLAG_TYPE_SHIFT;
    }

    /**
     * Extracts the MemoryType enum value from the flags byte.
     */
    public static com.spectrayan.spector.memory.model.MemoryType memoryTypeOf(byte flags) {
        int ord = memoryTypeOrdinal(flags);
        var values = com.spectrayan.spector.memory.model.MemoryType.values();
        return (ord >= 0 && ord < values.length) ? values[ord] : com.spectrayan.spector.memory.model.MemoryType.SEMANTIC;
    }

    /**
     * Encodes a memory type ordinal into a flags byte, preserving other bits.
     */
    public static byte withMemoryType(byte flags, int typeOrdinal) {
        return (byte) ((flags & ~FLAG_TYPE_MASK) | ((typeOrdinal << FLAG_TYPE_SHIFT) & FLAG_TYPE_MASK));
    }

    /**
     * Extracts the 2-bit source modality ordinal (0–3) from the flags byte.
     *
     * @see com.spectrayan.spector.memory.model.SourceModality
     */
    public static int sourceModalityOrdinal(byte flags) {
        return (flags & 0xFF & FLAG_MODALITY_MASK) >>> FLAG_MODALITY_SHIFT;
    }

    /**
     * Encodes a source modality ordinal into a flags byte, preserving other bits.
     *
     * @see com.spectrayan.spector.memory.model.SourceModality
     */
    public static byte withSourceModality(byte flags, int modalityOrdinal) {
        return (byte) ((flags & ~FLAG_MODALITY_MASK)
                | ((modalityOrdinal << FLAG_MODALITY_SHIFT) & (FLAG_MODALITY_MASK & 0xFF)));
    }

    /**
     * Checks if the contradicted flag is set in the given consolidation flags byte.
     */
    public static boolean isContradicted(byte consolidationFlags) {
        return (consolidationFlags & FLAG_CONTRADICTED) != 0;
    }

    /**
     * Checks if the retracted flag is set in the consolidation & governance flags byte.
     */
    public static boolean isRetracted(byte consolidationFlags) {
        return (consolidationFlags & FLAG_RETRACTED) != 0;
    }

    /**
     * Checks if the unverified flag is set in the consolidation & governance flags byte.
     */
    public static boolean isUnverified(byte consolidationFlags) {
        return (consolidationFlags & FLAG_UNVERIFIED) != 0;
    }

    /**
     * Checks if the restricted flag is set in the consolidation & governance flags byte.
     */
    public static boolean isRestricted(byte consolidationFlags) {
        return (consolidationFlags & FLAG_RESTRICTED) != 0;
    }

    /**
     * Checks if the crystallized skill flag is set in the consolidation & governance flags byte.
     */
    public static boolean isCrystallized(byte consolidationFlags) {
        return (consolidationFlags & FLAG_CRYSTALLIZED) != 0;
    }

    /**
     * Checks if the simulated flag is set in the consolidation & governance flags byte.
     */
    public static boolean isSimulated(byte consolidationFlags) {
        return (consolidationFlags & FLAG_SIMULATED) != 0;
    }

    /**
     * Checks if the dreamed flag is set in the consolidation & governance flags byte.
     */
    public static boolean isDreamed(byte consolidationFlags) {
        return (consolidationFlags & FLAG_DREAMED) != 0;
    }

    /**
     * Sets or clears the retracted flag on the given consolidation & governance flags byte.
     */
    public static byte withRetracted(byte flags, boolean retracted) {
        return (byte) (retracted ? (flags | FLAG_RETRACTED) : (flags & ~FLAG_RETRACTED));
    }

    /**
     * Sets or clears the unverified flag on the given consolidation & governance flags byte.
     */
    public static byte withUnverified(byte flags, boolean unverified) {
        return (byte) (unverified ? (flags | FLAG_UNVERIFIED) : (flags & ~FLAG_UNVERIFIED));
    }

    /**
     * Sets or clears the restricted flag on the given consolidation & governance flags byte.
     */
    public static byte withRestricted(byte flags, boolean restricted) {
        return (byte) (restricted ? (flags | FLAG_RESTRICTED) : (flags & ~FLAG_RESTRICTED));
    }

    /**
     * Sets or clears the crystallized flag on the given consolidation & governance flags byte.
     */
    public static byte withCrystallized(byte flags, boolean crystallized) {
        return (byte) (crystallized ? (flags | FLAG_CRYSTALLIZED) : (flags & ~FLAG_CRYSTALLIZED));
    }

    /**
     * Sets or clears the simulated flag on the given consolidation & governance flags byte.
     */
    public static byte withSimulated(byte flags, boolean simulated) {
        return (byte) (simulated ? (flags | FLAG_SIMULATED) : (flags & ~FLAG_SIMULATED));
    }

    /**
     * Sets or clears the dreamed flag on the given consolidation & governance flags byte.
     */
    public static byte setDreamed(byte flags, boolean dreamed) {
        return (byte) (dreamed ? (flags | FLAG_DREAMED) : (flags & ~FLAG_DREAMED));
    }

    /**
     * Checks if the encoding profile byte indicates a soul-derived configuration.
     */
    public static boolean isSoulDerived(byte encodingProfile) {
        return (encodingProfile & ENCODING_FLAG_SOUL_DERIVED) != 0;
    }

    /**
     * Extracts the CognitiveProfile ordinal from the encoding profile byte.
     * Only meaningful when {@link #isSoulDerived(byte)} returns false.
     */
    public static int encodingProfileOrdinal(byte encodingProfile) {
        return encodingProfile & ENCODING_PROFILE_MASK;
    }

    /**
     * Constructs an encoding profile byte for a preset CognitiveProfile.
     */
    public static byte presetEncodingProfile(int profileOrdinal) {
        return (byte) (profileOrdinal & ENCODING_PROFILE_MASK);
    }

    /**
     * Constructs an encoding profile byte for a soul-derived configuration.
     * Sets bit7 (soul-derived flag) and bits0-3 to the {@code SOUL_DERIVED} ordinal.
     */
    public static byte soulDerivedEncodingProfile() {
        return (byte) (ENCODING_FLAG_SOUL_DERIVED
                | (com.spectrayan.spector.memory.model.CognitiveProfile.SOUL_DERIVED.ordinal() & ENCODING_PROFILE_MASK));
    }

    /**
     * Quantizes a scoring weight (alpha or beta) from {@code [0.0, 1.0]} to an
     * unsigned byte {@code [0, 255]} for storage in the encoding state header fields.
     *
     * <p>The quantization is linear: {@code byte = clamp(weight × 255, 0, 255)}.</p>
     *
     * @param weight the scoring weight in [0.0, 1.0]
     * @return the quantized unsigned byte value
     */
    public static byte quantizeWeight(float weight) {
        int v = Math.round(Math.clamp(weight, 0.0f, 1.0f) * 255.0f);
        return (byte) v;
    }

    /**
     * Dequantizes a scoring weight from unsigned byte {@code [0, 255]} back to
     * {@code [0.0, 1.0]} float.
     *
     * @param quantized the stored unsigned byte value
     * @return the dequantized weight in [0.0, 1.0]
     */
    public static float dequantizeWeight(byte quantized) {
        return (quantized & 0xFF) / 255.0f;
    }

    /**
     * Extracts the EngramSource enum value from a source code byte (NF7).
     *
     * @param code numeric source code
     * @return corresponding EngramSource
     */
    public static com.spectrayan.spector.memory.model.EngramSource sourceOf(byte code) {
        return com.spectrayan.spector.memory.model.EngramSource.fromCode(code);
    }
}
