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
package com.spectrayan.spector.memory.model;

import com.spectrayan.spector.memory.cortex.MemorySource;

import java.util.Map;

/**
 * Immutable result record returned by {@link com.spectrayan.spector.memory.SpectorMemory#recall}.
 *
 * <p>Contains the memory text, cognitive scoring metadata, provenance information,
 * and biological state (recall count, valence, decay factor, timestampMs). Designed to give
 * the LLM maximum contextual grounding for reasoning about memory reliability.</p>
 *
 * @param id              unique memory identifier
 * @param text            the memory content text
 * @param score           final fused cognitive score (similarity × decay × importance)
 * @param importance      base importance weight (auto-set by Prediction Error engine)
 * @param ageDays         age of the memory in days
 * @param agentRecallCount number of times this memory has been recalled (LTP/reconsolidation)
 * @param valence         emotional valence (-128 to +127)
 * @param memoryType      cognitive memory tier (Working, Episodic, Semantic, Procedural)
 * @param source          provenance source (Observed, UserStated, Reflected, etc.)
 * @param synapticTags    decoded synaptic tag labels
 * @param decayFactor     raw decay multiplier (before reconsolidation adjustment)
 * @param ltpAdjustedDecay decay multiplier after reconsolidation adjustment
 * @param retrievalMode   how this result was retrieved (Standard, Lateral, Hyperfocus)
 * @param breakdown       decomposed scoring trace (nullable for backward compat)
 * @param trace           per-step pipeline scoring trace (nullable — only populated when enableTrace=true)
 * @param sourceModality  what the memory originally was before ingestion (TEXT, IMAGE, AUDIO, VIDEO)
 * @param metadata        multimodal metadata (source_uri, etc.) — empty map for text-only memories
 * @param consolidationFlags consolidation provenance flags
 * @param timestampMs     epoch millisecond timestamp when the memory was formed (ADR-0030 v1)
 */
public record CognitiveResult(
        String id,
        String text,
        float score,
        float importance,
        float ageDays,
        int agentRecallCount,
        byte valence,
        MemoryType memoryType,
        MemorySource source,
        String[] synapticTags,
        float decayFactor,
        float ltpAdjustedDecay,
        RetrievalMode retrievalMode,
        ScoreBreakdown breakdown,
        RecallTrace trace,
        SourceModality sourceModality,
        Map<String, String> metadata,
        byte consolidationFlags,
        long timestampMs
) {

    /** Compact constructor — defaults null modality/metadata and derives timestampMs if omitted. */
    public CognitiveResult {
        if (sourceModality == null) sourceModality = SourceModality.TEXT;
        if (metadata == null) metadata = Map.of();
        if (timestampMs == 0L && ageDays > 0.0f) {
            timestampMs = System.currentTimeMillis() - (long) (ageDays * 86_400_000.0);
        }
    }

    /**
     * Backward-compatible 18-argument constructor.
     */
    public CognitiveResult(String id, String text, float score, float importance,
                           float ageDays, int agentRecallCount, byte valence,
                           MemoryType memoryType, MemorySource source,
                           String[] synapticTags, float decayFactor,
                           float ltpAdjustedDecay, RetrievalMode retrievalMode,
                           ScoreBreakdown breakdown, RecallTrace trace,
                           SourceModality sourceModality, Map<String, String> metadata,
                           byte consolidationFlags) {
        this(id, text, score, importance, ageDays, agentRecallCount, valence,
                memoryType, source, synapticTags, decayFactor, ltpAdjustedDecay,
                retrievalMode, breakdown, trace, sourceModality, metadata, consolidationFlags, 0L);
    }

    /**
     * Backward-compatible 17-argument constructor.
     */
    public CognitiveResult(String id, String text, float score, float importance,
                           float ageDays, int agentRecallCount, byte valence,
                           MemoryType memoryType, MemorySource source,
                           String[] synapticTags, float decayFactor,
                           float ltpAdjustedDecay, RetrievalMode retrievalMode,
                           ScoreBreakdown breakdown, RecallTrace trace,
                           SourceModality sourceModality, Map<String, String> metadata) {
        this(id, text, score, importance, ageDays, agentRecallCount, valence,
                memoryType, source, synapticTags, decayFactor, ltpAdjustedDecay,
                retrievalMode, breakdown, trace, sourceModality, metadata, (byte) 0, 0L);
    }

    /**
     * How a memory was retrieved — enables the LLM to reason about result provenance.
     */
    public enum RetrievalMode {
        /** Standard similarity-based retrieval. */
        STANDARD,
        /** Lateral/orthogonal retrieval — tag-matched but semantically distant. */
        LATERAL,
        /** Hyperfocus retrieval — zero time decay, strict tag matching. */
        HYPERFOCUS
    }

    /**
     * Compact constructor — defaults retrievalMode to STANDARD when not specified.
     */
    public CognitiveResult(String id, String text, float score, float importance,
                            float ageDays, int agentRecallCount, byte valence,
                            MemoryType memoryType, MemorySource source,
                            String[] synapticTags, float decayFactor,
                            float ltpAdjustedDecay) {
        this(id, text, score, importance, ageDays, agentRecallCount, valence,
                memoryType, source, synapticTags, decayFactor, ltpAdjustedDecay,
                RetrievalMode.STANDARD, null, null, null, null, (byte) 0, 0L);
    }

    /**
     * Constructor with retrieval mode but no breakdown (backward compat).
     */
    public CognitiveResult(String id, String text, float score, float importance,
                            float ageDays, int agentRecallCount, byte valence,
                            MemoryType memoryType, MemorySource source,
                            String[] synapticTags, float decayFactor,
                            float ltpAdjustedDecay, RetrievalMode retrievalMode) {
        this(id, text, score, importance, ageDays, agentRecallCount, valence,
                memoryType, source, synapticTags, decayFactor, ltpAdjustedDecay,
                retrievalMode, null, null, null, null, (byte) 0, 0L);
    }

    /**
     * Constructor with breakdown but no trace (backward compat).
     */
    public CognitiveResult(String id, String text, float score, float importance,
                            float ageDays, int agentRecallCount, byte valence,
                            MemoryType memoryType, MemorySource source,
                            String[] synapticTags, float decayFactor,
                            float ltpAdjustedDecay, RetrievalMode retrievalMode,
                            ScoreBreakdown breakdown) {
        this(id, text, score, importance, ageDays, agentRecallCount, valence,
                memoryType, source, synapticTags, decayFactor, ltpAdjustedDecay,
                retrievalMode, breakdown, null, null, null, (byte) 0, 0L);
    }

    /**
     * Returns the confidence weight based on source monitoring.
     */
    public float confidenceWeight() {
        return source != null ? source.confidenceWeight() : 0.5f;
    }

    /**
     * Returns true if this result has a decomposed score breakdown.
     */
    public boolean hasBreakdown() {
        return breakdown != null;
    }

    /**
     * Returns true if this result has a pipeline scoring trace.
     */
    public boolean hasTrace() {
        return trace != null;
    }

    /**
     * Returns a copy of this result with the given trace attached.
     */
    public CognitiveResult withTrace(RecallTrace trace) {
        return new CognitiveResult(id, text, score, importance, ageDays, agentRecallCount,
                valence, memoryType, source, synapticTags, decayFactor, ltpAdjustedDecay,
                retrievalMode, breakdown, trace, sourceModality, metadata, consolidationFlags, timestampMs);
    }

    /**
     * Returns a copy of this result with updated score.
     */
    public CognitiveResult withScore(float newScore) {
        return new CognitiveResult(id, text, newScore, importance, ageDays, agentRecallCount,
                valence, memoryType, source, synapticTags, decayFactor, ltpAdjustedDecay,
                retrievalMode, breakdown, trace, sourceModality, metadata, consolidationFlags, timestampMs);
    }

    /**
     * Returns a copy of this result with updated score and trace.
     */
    public CognitiveResult withScoreAndTrace(float newScore, RecallTrace newTrace) {
        return new CognitiveResult(id, text, newScore, importance, ageDays, agentRecallCount,
                valence, memoryType, source, synapticTags, decayFactor, ltpAdjustedDecay,
                retrievalMode, breakdown, newTrace, sourceModality, metadata, consolidationFlags, timestampMs);
    }

    /**
     * Returns a copy of this result with updated score and breakdown.
     */
    public CognitiveResult withScoreAndBreakdown(float newScore, ScoreBreakdown newBreakdown) {
        return new CognitiveResult(id, text, newScore, importance, ageDays, agentRecallCount,
                valence, memoryType, source, synapticTags, decayFactor, ltpAdjustedDecay,
                retrievalMode, newBreakdown, trace, sourceModality, metadata, consolidationFlags, timestampMs);
    }

    /**
     * Returns a copy of this result with updated timestampMs.
     */
    public CognitiveResult withTimestampMs(long timestampMs) {
        return new CognitiveResult(id, text, score, importance, ageDays, agentRecallCount,
                valence, memoryType, source, synapticTags, decayFactor, ltpAdjustedDecay,
                retrievalMode, breakdown, trace, sourceModality, metadata, consolidationFlags, timestampMs);
    }

    /**
     * Returns a copy of this result with updated text content.
     */
    public CognitiveResult withText(String newText) {
        return new CognitiveResult(id, newText, score, importance, ageDays, agentRecallCount,
                valence, memoryType, source, synapticTags, decayFactor, ltpAdjustedDecay,
                retrievalMode, breakdown, trace, sourceModality, metadata, consolidationFlags, timestampMs);
    }

    /**
     * Returns a copy of this result with the given modality and metadata.
     */
    public CognitiveResult withModality(SourceModality modality, Map<String, String> metadata) {
        return new CognitiveResult(id, text, score, importance, ageDays, agentRecallCount,
                valence, memoryType, source, synapticTags, decayFactor, ltpAdjustedDecay,
                retrievalMode, breakdown, trace, modality, metadata, consolidationFlags, timestampMs);
    }

    /**
     * Returns a copy of this result with updated consolidation flags.
     */
    public CognitiveResult withConsolidationFlags(byte flags) {
        return new CognitiveResult(id, text, score, importance, ageDays, agentRecallCount,
                valence, memoryType, source, synapticTags, decayFactor, ltpAdjustedDecay,
                retrievalMode, breakdown, trace, sourceModality, metadata, flags, timestampMs);
    }

    /**
     * Returns true if this memory is multimodal (non-text source).
     */
    public boolean isMultimodal() {
        return sourceModality != null && sourceModality != SourceModality.TEXT;
    }

    /**
     * Returns the source asset URI, or null if this is a text-only memory.
     */
    public String sourceUri() {
        return metadata != null ? metadata.get(SourceModality.URI_KEY) : null;
    }

    /**
     * Returns true if this memory has been positively reinforced (valence > 10).
     */
    public boolean isPositivelyReinforced() {
        return valence > 10;
    }

    /**
     * Returns true if this memory is associated with a negative outcome (valence &lt; -10).
     */
    public boolean isNegativeOutcome() {
        return valence < -10;
    }

    /**
     * Returns true if this result was retrieved via lateral/divergent retrieval.
     */
    public boolean isLateral() {
        return retrievalMode == RetrievalMode.LATERAL;
    }

    /**
     * Returns true if this result was retrieved via hyperfocus/zero-decay mode.
     */
    public boolean isHyperfocused() {
        return retrievalMode == RetrievalMode.HYPERFOCUS;
    }

    /**
     * Returns the engram source provenance category (NF7).
     */
    public EngramSource engramSource() {
        return source != null ? source.toEngramSource() : EngramSource.EXPERIENCED;
    }

    /**
     * Returns true if this result was generated via constructive simulation / thought experiment (ADR-0031 / NF7).
     */
    public boolean isSimulated() {
        return (source != null && source.toEngramSource() == EngramSource.SIMULATED)
                || com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields.isSimulated(consolidationFlags);
    }

    /**
     * Returns true if this result was ingested during a dream cycle (ADR-0031).
     */
    public boolean isDreamed() {
        return com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields.isDreamed(consolidationFlags);
    }
}
