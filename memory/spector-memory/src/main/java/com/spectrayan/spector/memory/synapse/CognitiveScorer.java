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
package com.spectrayan.spector.memory.synapse;

import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.score.RecordGates;
import com.spectrayan.spector.kernel.store.EngramRegion;
import com.spectrayan.spector.kernel.store.StrengthMemory;
import com.spectrayan.spector.memory.model.RecallOptions;

import java.util.List;

/**
 * Fused SIMD cognitive scoring loop — the heart of Spector Memory's performance.
 *
 * <h3>6-Phase Modular Scan (ADR-0030 v1)</h3>
 * <pre>
 *   Phase 1 &amp; 1c: Tombstone &amp; contradiction check (~1 cycle)  — {@link RecordGates#isDeletedOrContradicted}
 *   Phase 1b:    Temporal &amp; future causal gate   (~1 cycle)  — {@link RecordGates#isTemporalGated}
 *   Phase 2:     Synaptic tag &amp; hyperfocus gate  (~1 cycle)  — {@link RecordGates#isTagGated}
 *   Phase 3:     Valence range filter            (~2 cycles) — {@link RecordGates#isValenceGated}
 *   Phase 4:     Age decay with high-mass exempt (~2 cycles) — {@link RecordGates#isStaleAndWeak}
 *   Phase 5:     Zero-copy SIMD L2 distance      (~200 cyc)  — {@link com.spectrayan.spector.core.similarity.SimilarityFunction#computeQuantizedFromSegment}
 *   Phase 6:     Fused mass-dilated score        (~7 cycles) — {@link com.spectrayan.spector.memory.synapse.scan.CognitiveScoreFusion#computeFusedScore}
 * </pre>
 */
public final class CognitiveScorer {

    private CognitiveScorer() {
        // utility class
    }

    /**
     * Represents a scored record for the priority queue.
     *
     * @param lateral true if this record came from the lateral retrieval heap
     */
    public record ScoredRecord(long offset, float score, int index, EncodingHeader header, boolean lateral)
            implements Comparable<ScoredRecord> {

        /** Standard (non-lateral) constructor for backward compatibility. */
        public ScoredRecord(final long offset, final float score, final int index, final EncodingHeader header) {
            this(offset, score, index, header, false);
        }

        @Override
        public int compareTo(final ScoredRecord other) {
            return Float.compare(this.score, other.score); // min-heap for top-K
        }
    }

    /**
     * Scans an engram region and returns the top-K scored records.
     */
    public static List<ScoredRecord> score(
            final EngramRegion region,
            final float[] queryVector, final RecallOptions options, final long nowMs) {
        return score(region, queryVector, options, nowMs, null, null, null, null, null);
    }

    /**
     * Scans an engram region using calibrated scalar quantization parameters.
     */
    public static List<ScoredRecord> score(
            final EngramRegion region,
            final float[] queryVector, final RecallOptions options, final long nowMs,
            final float[] mins, final float[] scales) {
        return score(region, queryVector, options, nowMs, mins, scales, null, null, null);
    }

    /**
     * Full scan entrypoint with calibrated distance and early associative prior (MR-06).
     */
    public static List<ScoredRecord> score(
            final EngramRegion region,
            final float[] queryVector, final RecallOptions options, final long nowMs,
            final float[] mins, final float[] scales,
            final AssociativePriorProvider priorProvider,
            final QueryAssociativeContext priorContext) {
        return score(region, queryVector, options, nowMs, mins, scales, priorProvider, priorContext, null);
    }

    /**
     * Full scan entrypoint with calibrated distance, early associative prior, and authoritative strength region.
     */
    public static List<ScoredRecord> score(
            final EngramRegion region,
            final float[] queryVector, final RecallOptions options, final long nowMs,
            final float[] mins, final float[] scales,
            final AssociativePriorProvider priorProvider,
            final QueryAssociativeContext priorContext,
            final StrengthMemory strengthStore) {

        final com.spectrayan.spector.kernel.scan.ScanFilter filter = createScanFilter(options, nowMs);
        final com.spectrayan.spector.memory.synapse.scan.CognitiveScoreVisitor visitor =
                new com.spectrayan.spector.memory.synapse.scan.CognitiveScoreVisitor(options, nowMs, priorProvider, priorContext);

        region.scan(queryVector, mins, scales, filter, strengthStore, 0, visitor);

        return visitor.drain();
    }

    public static com.spectrayan.spector.kernel.scan.ScanFilter createScanFilter(final RecallOptions options, final long nowMs) {
        final long minTimestamp = options.minTimestamp() != null ? options.minTimestamp() : 0L;
        final long maxTimestamp = options.maxTimestamp() != null ? options.maxTimestamp() : Long.MAX_VALUE;
        return new com.spectrayan.spector.kernel.scan.ScanFilter(
                options.synapticTagMask(), 0L,
                options.hyperfocusMask(), 0L,
                minTimestamp, maxTimestamp,
                nowMs,
                options.allowFuture(),
                options.minValence(), options.maxValence(),
                options.minImportance(),
                (byte) 0, (byte) 0,
                options.includeContradictions(), options.allowSimulated(),
                RecordGates.DEFAULT_STALE_BUCKET_THRESHOLD,
                RecordGates.DEFAULT_WEAK_MASS_THRESHOLD
        );
    }
}
