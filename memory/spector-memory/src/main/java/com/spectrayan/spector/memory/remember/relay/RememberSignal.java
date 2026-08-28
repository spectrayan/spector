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
package com.spectrayan.spector.memory.remember.relay;

import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.model.IngestionContext;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.neurodivergent.IngestionHints;

import java.util.Objects;

/**
 * Mutable context payload propagating through the memory consolidation / remember pathway.
 */
public final class RememberSignal {

    // ── Immutable Ingestion Request ────────────────────────────────
    private final String id;
    private final String text;
    private final float[] vector;
    private final MemoryType type;
    private final MemorySource source;
    private final IngestionHints hints;
    private final IngestionContext context;
    private final SalienceProfile salienceProfile;
    private final short soulVersion;
    private final long timestampMs;

    // ── Mutable Pipeline Working State ─────────────────────────────
    private String sanitizedText;
    private String[] tags;
    private long synapticTags;
    private float[] normalizedVector;
    private float[] privacyPerturbedVector;
    private byte[] quantizedVector;
    private float nearestDist;
    private float importance;
    private boolean flashbulb;
    private CognitiveHeader header;
    private long offset = -1L;
    private int graphSlot = -1;
    private boolean duplicate = false;
    private boolean successful = false;
    private com.spectrayan.spector.memory.aisme.fegr.EventDensityMetrics eventDensityMetrics;
    private boolean gated = false;
    private com.spectrayan.spector.memory.aisme.segmentation.EpisodicSegment episodicSegment;

    private RememberSignal(
            final String id,
            final String text,
            final float[] vector,
            final MemoryType type,
            final String[] tags,
            final MemorySource source,
            final IngestionHints hints,
            final IngestionContext context,
            final SalienceProfile salienceProfile,
            final short soulVersion,
            final long timestampMs) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.text = Objects.requireNonNull(text, "text cannot be null");
        this.vector = vector;
        this.type = type != null ? type : MemoryType.SEMANTIC;
        this.tags = tags;
        this.source = source != null ? source : MemorySource.OBSERVED;
        this.hints = hints;
        this.context = context;
        this.salienceProfile = salienceProfile != null ? salienceProfile : SalienceProfile.NEUTRAL;
        this.soulVersion = soulVersion;
        this.timestampMs = timestampMs > 0 ? timestampMs : System.currentTimeMillis();
    }

    /**
     * Factory for standard cognitive remember requests.
     */
    public static RememberSignal forCognitive(
            final String id,
            final String text,
            final float[] vector,
            final MemoryType type,
            final String[] tags,
            final MemorySource source,
            final IngestionHints hints,
            final SalienceProfile salienceProfile,
            final short soulVersion) {
        return new RememberSignal(
                id, text, vector, type, tags, source, hints, null,
                salienceProfile, soulVersion, System.currentTimeMillis());
    }

    /**
     * Factory for rich context-aware cognitive remember requests.
     */
    public static RememberSignal forCognitiveWithContext(
            final String id,
            final String text,
            final float[] vector,
            final MemoryType type,
            final String[] tags,
            final MemorySource source,
            final IngestionContext context,
            final SalienceProfile salienceProfile,
            final short soulVersion) {
        final IngestionHints effectiveHints = context != null ? context.hints() : null;
        final long ts = (context != null && context.effectiveTimestampMs() > 0)
                ? context.effectiveTimestampMs()
                : System.currentTimeMillis();
        return new RememberSignal(
                id, text, vector, type, tags, source, effectiveHints, context,
                salienceProfile, soulVersion, ts);
    }

    /**
     * Factory for cognitive remember requests preserving an existing header.
     */
    public static RememberSignal forCognitiveWithHeader(
            final String id,
            final String text,
            final float[] vector,
            final MemoryType type,
            final String[] tags,
            final MemorySource source,
            final CognitiveHeader header) {
        final RememberSignal signal = new RememberSignal(
                id, text, vector, type, tags, source, null, null,
                SalienceProfile.NEUTRAL, header != null ? header.soulVersion() : 0,
                header != null ? header.timestampMs() : System.currentTimeMillis());
        signal.header(header);
        return signal;
    }

    // ── Getters & Setters ──────────────────────────────────────────

    public String id() { return id; }
    public String text() { return sanitizedText != null ? sanitizedText : text; }
    public String rawText() { return text; }
    public String sanitizedText() { return sanitizedText; }
    public void sanitizedText(final String sanitizedText) { this.sanitizedText = sanitizedText; }

    public float[] vector() {
        if (privacyPerturbedVector != null) {
            return privacyPerturbedVector;
        }
        return normalizedVector != null ? normalizedVector : vector;
    }
    public MemoryType type() { return type; }
    public MemorySource source() { return source; }
    public IngestionHints hints() { return hints; }
    public IngestionContext context() { return context; }
    public SalienceProfile salienceProfile() { return salienceProfile; }
    public short soulVersion() { return soulVersion; }
    public long timestampMs() { return timestampMs; }

    public String[] tags() { return tags; }
    public void tags(final String[] tags) { this.tags = tags; }

    public long synapticTags() { return synapticTags; }
    public void synapticTags(final long synapticTags) { this.synapticTags = synapticTags; }

    public float[] normalizedVector() { return normalizedVector; }
    public void normalizedVector(final float[] normalizedVector) { this.normalizedVector = normalizedVector; }

    public float[] privacyPerturbedVector() { return privacyPerturbedVector; }
    public void privacyPerturbedVector(final float[] privacyPerturbedVector) { this.privacyPerturbedVector = privacyPerturbedVector; }

    public byte[] quantizedVector() { return quantizedVector; }
    public void quantizedVector(final byte[] quantizedVector) { this.quantizedVector = quantizedVector; }

    public float nearestDist() { return nearestDist; }
    public void nearestDist(final float nearestDist) { this.nearestDist = nearestDist; }

    public float importance() { return importance; }
    public void importance(final float importance) { this.importance = importance; }

    public boolean isFlashbulb() { return flashbulb; }
    public void flashbulb(final boolean flashbulb) { this.flashbulb = flashbulb; }

    public CognitiveHeader header() { return header; }
    public void header(final CognitiveHeader header) { this.header = header; }

    public long offset() { return offset; }
    public void offset(final long offset) { this.offset = offset; }

    public int graphSlot() { return graphSlot; }
    public void graphSlot(final int graphSlot) { this.graphSlot = graphSlot; }

    public boolean isDuplicate() { return duplicate; }
    public void duplicate(final boolean duplicate) { this.duplicate = duplicate; }

    public boolean isSuccessful() { return successful; }
    public void successful(final boolean successful) { this.successful = successful; }

    public com.spectrayan.spector.memory.aisme.fegr.EventDensityMetrics eventDensityMetrics() { return eventDensityMetrics; }
    public void eventDensityMetrics(final com.spectrayan.spector.memory.aisme.fegr.EventDensityMetrics metrics) { this.eventDensityMetrics = metrics; }

    public boolean isGated() { return gated; }
    public void gated(final boolean gated) { this.gated = gated; }

    public com.spectrayan.spector.memory.aisme.segmentation.EpisodicSegment episodicSegment() { return episodicSegment; }
    public void episodicSegment(final com.spectrayan.spector.memory.aisme.segmentation.EpisodicSegment segment) { this.episodicSegment = segment; }
}
