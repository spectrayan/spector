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
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.SourceModality;

/**
 * Immutable record holding all engram encoding header fields across all layout versions.
 *
 * <p>Represents the physical 64-byte off-heap encoding header established during memory
 * ingestion, satisfying the pure-encoding identity requirements from MF-001 / ADR-0028.</p>
 *
 * <p>Under ADR-0028, mutable telemetry (dynamic recall counts, cooldowns, storage strength)
 * is authoritative in {@code StrengthMemory} (and modeled in {@code StrengthState}), while
 * {@code EncodingHeader} models the immutable/read-mostly engram identity.</p>
 *
 * <p><b>TODO (JDK 28+ / Project Valhalla):</b> Convert to {@code value record} once
 * JEP 401 (Value Classes) is available. As a value class, EncodingHeader would
 * be identity-free and scalarizable by the JIT — the 30 bytes of payload would
 * live in CPU registers instead of as a 48-byte heap object. When nested inside
 * {@code ScoredRecord} (also a value class), both would be flattened inline,
 * eliminating pointer indirection and reducing per-candidate cost from ~96B
 * heap to 0B (scalarized). Specialized generics (JEP 402) would further enable
 * flat storage in generic collections like {@code PriorityQueue}.</p>
 *
 * @param timestampMs        when the memory was formed (epoch millis)
 * @param synapticTags       64-bit Bloom filter of contextual markers
 * @param exactNorm          L2 norm for SIMD distance computation
 * @param importance         base importance (set by Prediction Error engine)
 * @param agentRecallCount   LTP reinforcement counter (re-homed to StrengthMemory in V2)
 * @param centroidId         IVF partition routing ID
 * @param valence            signed emotion/reward (-128 to +127)
 * @param flags              bit field (tombstone, type, consolidated, pinned, resolved)
 * @param arousal            emotional intensity (unsigned 0-255, V2+)
 * @param storageStrength    Two-Factor Memory storage strength (re-homed to StrengthMemory in V2)
 * @param encodingProfile    cognitive state at encoding (bit7=soul-derived, bits0-3=profile ordinal)
 * @param encodingAlpha      quantized alpha weight at encoding (0-255 → 0.0-1.0)
 * @param encodingBeta       quantized beta weight at encoding (0-255 → 0.0-1.0)
 * @param soulVersion        monotonic soul configuration version counter
 * @param encodingSurprise   surprise z-score from SurpriseDetector at ingestion
 * @param consolidationFlags provenance &amp; consolidation flags (V3+, offset 34; e.g., FLAG_SIMULATED, FLAG_CRYSTALLIZED)
 * @param source             trace provenance classification (NF7, offset 46; EXPERIENCED, DISTILLED, SIMULATED, REHEARSED)
 */
public record EncodingHeader(
        long timestampMs,
        long synapticTags,
        float exactNorm,
        float importance,
        int agentRecallCount,
        short centroidId,
        byte valence,
        byte flags,
        // ── Extended fields (V2+) ──
        byte arousal,
        float storageStrength,
        // ── Encoding State ──
        byte encodingProfile,
        byte encodingAlpha,
        byte encodingBeta,
        short soulVersion,
        float encodingSurprise,
        // ── Provenance (V3+) ──
        byte consolidationFlags,
        // ── NF7 Source Honesty ──
        EngramSource source
) {
    /**
     * Compact constructor — defaults null source per NF7.
     */
    public EncodingHeader {
        if (source == null) {
            source = EncodingHeaderFields.isSimulated(consolidationFlags)
                    ? EngramSource.SIMULATED
                    : (EncodingHeaderFields.isCrystallized(consolidationFlags)
                            ? EngramSource.DISTILLED
                            : EngramSource.EXPERIENCED);
        }
    }

    /**
     * V1-compatible constructor — defaults for extended fields.
     *
     * <p>Provides backward compatibility for code that constructs headers
     * without arousal or storage strength fields.</p>
     */
    public EncodingHeader(long timestampMs, long synapticTags, float exactNorm,
                          float importance, int agentRecallCount, short centroidId,
                          byte valence, byte flags) {
        this(timestampMs, synapticTags, exactNorm, importance,
                agentRecallCount, centroidId, valence, flags,
                (byte) 0, 1.0f,
                (byte) 0, (byte) 0, (byte) 0, (short) 0, 0.0f,
                (byte) 0, EngramSource.EXPERIENCED);
    }

    /**
     * V2-compatible constructor — defaults for encoding state fields.
     */
    public EncodingHeader(long timestampMs, long synapticTags, float exactNorm,
                          float importance, int agentRecallCount, short centroidId,
                          byte valence, byte flags,
                          byte arousal, float storageStrength) {
        this(timestampMs, synapticTags, exactNorm, importance,
                agentRecallCount, centroidId, valence, flags,
                arousal, storageStrength,
                (byte) 0, (byte) 0, (byte) 0, (short) 0, 0.0f,
                (byte) 0, EngramSource.EXPERIENCED);
    }

    /**
     * Backward-compatible 16-parameter constructor without explicit source.
     */
    public EncodingHeader(long timestampMs, long synapticTags, float exactNorm,
                          float importance, int agentRecallCount, short centroidId,
                          byte valence, byte flags,
                          byte arousal, float storageStrength,
                          byte encodingProfile, byte encodingAlpha, byte encodingBeta,
                          short soulVersion, float encodingSurprise,
                          byte consolidationFlags) {
        this(timestampMs, synapticTags, exactNorm, importance,
                agentRecallCount, centroidId, valence, flags,
                arousal, storageStrength,
                encodingProfile, encodingAlpha, encodingBeta,
                soulVersion, encodingSurprise,
                consolidationFlags,
                EncodingHeaderFields.isSimulated(consolidationFlags)
                        ? EngramSource.SIMULATED
                        : (EncodingHeaderFields.isCrystallized(consolidationFlags)
                                ? EngramSource.DISTILLED
                                : EngramSource.EXPERIENCED));
    }

    /**
     * Creates a new header for initial ingestion with default recall count and valence.
     */
    public static EncodingHeader create(long timestampMs, long synapticTags, float exactNorm,
                                        float importance, short centroidId, MemoryType memoryType) {
        byte flags = EncodingHeaderFields.withMemoryType((byte) 0, memoryType.ordinal());
        return new EncodingHeader(timestampMs, synapticTags, exactNorm, importance,
                0, centroidId, (byte) 0, flags,
                (byte) 0, 1.0f,
                (byte) 0, (byte) 0, (byte) 0, (short) 0, 0.0f,
                (byte) 0, EngramSource.EXPERIENCED);
    }

    /**
     * Creates a new header with explicit source for initial ingestion.
     */
    public static EncodingHeader createWithSource(long timestampMs, long synapticTags, float exactNorm,
                                                float importance, short centroidId, MemoryType memoryType,
                                                EngramSource source) {
        byte flags = EncodingHeaderFields.withMemoryType((byte) 0, memoryType.ordinal());
        return new EncodingHeader(timestampMs, synapticTags, exactNorm, importance,
                0, centroidId, (byte) 0, flags,
                (byte) 0, 1.0f,
                (byte) 0, (byte) 0, (byte) 0, (short) 0, 0.0f,
                (byte) 0, source);
    }

    /**
     * Creates a new header with arousal for V2+ ingestion.
     */
    public static EncodingHeader createWithArousal(long timestampMs, long synapticTags,
                                                  float exactNorm, float importance,
                                                  short centroidId, MemoryType memoryType,
                                                  byte valence, byte arousal) {
        return createWithArousal(timestampMs, synapticTags, exactNorm, importance,
                centroidId, memoryType, valence, arousal, EngramSource.EXPERIENCED);
    }

    /**
     * Creates a new header with arousal and source for V2+ ingestion.
     */
    public static EncodingHeader createWithArousal(long timestampMs, long synapticTags,
                                                  float exactNorm, float importance,
                                                  short centroidId, MemoryType memoryType,
                                                  byte valence, byte arousal,
                                                  EngramSource source) {
        byte flags = EncodingHeaderFields.withMemoryType((byte) 0, memoryType.ordinal());
        return new EncodingHeader(timestampMs, synapticTags, exactNorm, importance,
                0, centroidId, valence, flags, arousal, 1.0f,
                (byte) 0, (byte) 0, (byte) 0, (short) 0, 0.0f,
                (byte) 0, source);
    }

    /**
     * Creates a new header with source modality for multimodal ingestion.
     *
     * <p>Encodes both the memory type (bits 1-2) and source modality (bits 6-7)
     * into the flags byte. Used by the ingestion pipeline when processing
     * non-text content (images, audio, video).</p>
     *
     * @param timestampMs  when the memory was formed (epoch millis)
     * @param synapticTags 64-bit Bloom filter of contextual markers
     * @param exactNorm    L2 norm for SIMD distance computation
     * @param importance   base importance (set by Prediction Error engine)
     * @param centroidId   IVF partition routing ID
     * @param memoryType   cognitive memory tier
     * @param modality     source modality (TEXT, IMAGE, AUDIO, VIDEO)
     * @param valence      signed emotion/reward (-128 to +127)
     * @param arousal      emotional intensity (unsigned 0-255)
     */
    public static EncodingHeader createWithModality(long timestampMs, long synapticTags,
                                                    float exactNorm, float importance,
                                                    short centroidId, MemoryType memoryType,
                                                    SourceModality modality,
                                                    byte valence, byte arousal) {
        return createWithModality(timestampMs, synapticTags, exactNorm, importance,
                centroidId, memoryType, modality, valence, arousal, EngramSource.EXPERIENCED);
    }

    /**
     * Creates a new header with source modality and explicit source for multimodal ingestion.
     */
    public static EncodingHeader createWithModality(long timestampMs, long synapticTags,
                                                    float exactNorm, float importance,
                                                    short centroidId, MemoryType memoryType,
                                                    SourceModality modality,
                                                    byte valence, byte arousal,
                                                    EngramSource source) {
        byte flags = EncodingHeaderFields.withMemoryType((byte) 0, memoryType.ordinal());
        if (modality != null && modality != SourceModality.TEXT) {
            flags = EncodingHeaderFields.withSourceModality(flags, modality.ordinal());
        }
        return new EncodingHeader(timestampMs, synapticTags, exactNorm, importance,
                0, centroidId, valence, flags, arousal, 1.0f,
                (byte) 0, (byte) 0, (byte) 0, (short) 0, 0.0f,
                (byte) 0, source);
    }

    /**
     * Creates a new header with full encoding state for V3+ ingestion.
     */
    public static EncodingHeader createWithEncodingState(
            long timestampMs, long synapticTags, float exactNorm, float importance,
            short centroidId, MemoryType memoryType, SourceModality modality,
            byte valence, byte arousal,
            byte encodingProfile, byte encodingAlpha, byte encodingBeta,
            short soulVersion, float encodingSurprise) {
        return createWithEncodingState(timestampMs, synapticTags, exactNorm, importance,
                centroidId, memoryType, modality, valence, arousal,
                encodingProfile, encodingAlpha, encodingBeta, soulVersion, encodingSurprise,
                EngramSource.EXPERIENCED);
    }

    /**
     * Creates a new header with full encoding state and source for V3+ ingestion.
     */
    public static EncodingHeader createWithEncodingState(
            long timestampMs, long synapticTags, float exactNorm, float importance,
            short centroidId, MemoryType memoryType, SourceModality modality,
            byte valence, byte arousal,
            byte encodingProfile, byte encodingAlpha, byte encodingBeta,
            short soulVersion, float encodingSurprise,
            EngramSource source) {
        byte flags = EncodingHeaderFields.withMemoryType((byte) 0, memoryType.ordinal());
        if (modality != null && modality != SourceModality.TEXT) {
            flags = EncodingHeaderFields.withSourceModality(flags, modality.ordinal());
        }
        return new EncodingHeader(timestampMs, synapticTags, exactNorm, importance,
                0, centroidId, valence, flags, arousal, 1.0f,
                encodingProfile, encodingAlpha, encodingBeta,
                soulVersion, encodingSurprise,
                (byte) 0, source);
    }

    /**
     * Creates a new header for synthetically generated memories (constructive simulation,
     * procedural crystallization) with explicit consolidation/provenance flags and current soul version.
     */
    public static EncodingHeader createSynthetic(
            long timestampMs, long synapticTags, float exactNorm, float importance,
            byte valence, byte arousal, byte flags,
            byte consolidationFlags, short soulVersion, float encodingSurprise) {
        EngramSource src = EncodingHeaderFields.isCrystallized(consolidationFlags)
                ? EngramSource.DISTILLED
                : EngramSource.SIMULATED;
        return createSynthetic(timestampMs, synapticTags, exactNorm, importance,
                valence, arousal, flags, consolidationFlags, soulVersion, encodingSurprise, src);
    }

    /**
     * Creates a new header for synthetically generated memories with explicit source classification.
     */
    public static EncodingHeader createSynthetic(
            long timestampMs, long synapticTags, float exactNorm, float importance,
            byte valence, byte arousal, byte flags,
            byte consolidationFlags, short soulVersion, float encodingSurprise,
            EngramSource source) {
        return new EncodingHeader(timestampMs, synapticTags, exactNorm, importance,
                0, (short) 0, valence, flags, arousal, 1.0f,
                (byte) 0, (byte) 0, (byte) 0, soulVersion, encodingSurprise,
                consolidationFlags, source);
    }
}
