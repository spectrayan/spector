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
import com.spectrayan.spector.kernel.score.DecayStrategy;
import com.spectrayan.spector.kernel.score.SynapticTagEncoder;
import com.spectrayan.spector.kernel.score.Valence;

import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;

import com.spectrayan.spector.core.similarity.SimilarityFunction;
import com.spectrayan.spector.kernel.layout.EngramLayout;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.layout.FixedEngramLayout;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoreFusionMode;
import com.spectrayan.spector.memory.model.ScoringMode;
import com.spectrayan.spector.kernel.store.StrengthMemory;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.synapse.scan.CognitiveScoreFusion;
import com.spectrayan.spector.memory.synapse.scan.FlatMinHeap;
import com.spectrayan.spector.kernel.score.RecordGates;

import java.lang.foreign.MemorySegment;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import static com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields.*;

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
 *   Phase 5:     Zero-copy SIMD L2 distance      (~200 cyc)  — {@link SimilarityFunction#computeQuantizedFromSegment}
 *   Phase 6:     Fused mass-dilated score        (~7 cycles) — {@link CognitiveScoreFusion#computeFusedScore}
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
     * Scans a memory segment and returns the top-K scored records.
     */
    public static List<ScoredRecord> score(
            final MemorySegment segment, final int recordCount, final FixedEngramLayout layout,
            final float[] queryVector, final RecallOptions options, final long nowMs) {
        return score(segment, recordCount, layout, queryVector, options, nowMs, 0L, null, null);
    }

    /**
     * Scans a memory segment and returns the top-K scored records with base offset.
     */
    public static List<ScoredRecord> score(
            final MemorySegment segment, final int recordCount, final FixedEngramLayout layout,
            final float[] queryVector, final RecallOptions options, final long nowMs, final long baseOffset) {
        return score(segment, recordCount, layout, queryVector, options, nowMs, baseOffset, null, null);
    }

    /**
     * Scans a memory segment using calibrated scalar quantization parameters.
     */
    public static List<ScoredRecord> score(
            final MemorySegment segment, final int recordCount, final FixedEngramLayout layout,
            final float[] queryVector, final RecallOptions options, final long nowMs, final long baseOffset,
            final float[] mins, final float[] scales) {
        return score(segment, recordCount, layout, queryVector, options, nowMs, baseOffset, mins, scales, null, null);
    }

    /**
     * Full scan entrypoint with calibrated distance and early associative prior (MR-06).
     */
    public static List<ScoredRecord> score(
            final MemorySegment segment, final int recordCount, final FixedEngramLayout layout,
            final float[] queryVector, final RecallOptions options, final long nowMs, final long baseOffset,
            final float[] mins, final float[] scales,
            final AssociativePriorProvider priorProvider,
            final QueryAssociativeContext priorContext) {
        return score(segment, recordCount, layout, queryVector, options, nowMs, baseOffset,
                mins, scales, priorProvider, priorContext, null, null);
    }

    /**
     * Full scan entrypoint with calibrated distance, early associative prior, and authoritative strength region.
     */
    public static List<ScoredRecord> score(
            final MemorySegment segment, final int recordCount, final FixedEngramLayout layout,
            final float[] queryVector, final RecallOptions options, final long nowMs, final long baseOffset,
            final float[] mins, final float[] scales,
            final AssociativePriorProvider priorProvider,
            final QueryAssociativeContext priorContext,
            final StrengthMemory strengthStore,
            final MemoryType tier) {

        final com.spectrayan.spector.kernel.scan.ScanFilter filter = createScanFilter(options, nowMs);
        final com.spectrayan.spector.memory.synapse.scan.CognitiveScoreVisitor visitor =
                new com.spectrayan.spector.memory.synapse.scan.CognitiveScoreVisitor(options, nowMs, priorProvider, priorContext);

        com.spectrayan.spector.kernel.scan.SlabScanner.scan(
                segment, recordCount, layout, queryVector, mins, scales, filter, strengthStore, tier, baseOffset, 0, visitor);

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
