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

import com.spectrayan.spector.core.similarity.SimilarityFunction;
import com.spectrayan.spector.memory.kernel.layout.EngramLayout;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeader;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoreFusionMode;
import com.spectrayan.spector.memory.model.ScoringMode;
import com.spectrayan.spector.memory.cortex.StrengthMemory;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.synapse.scan.CognitiveScoreFusion;
import com.spectrayan.spector.memory.synapse.scan.FlatMinHeap;
import com.spectrayan.spector.memory.synapse.scan.RecordGates;

import java.lang.foreign.MemorySegment;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import static com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields.*;

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
            final MemorySegment segment, final int recordCount, final EngramLayout layout,
            final float[] queryVector, final RecallOptions options, final long nowMs) {
        return score(segment, recordCount, layout, queryVector, options, nowMs, 0L, null, null);
    }

    /**
     * Scans a memory segment and returns the top-K scored records with base offset.
     */
    public static List<ScoredRecord> score(
            final MemorySegment segment, final int recordCount, final EngramLayout layout,
            final float[] queryVector, final RecallOptions options, final long nowMs, final long baseOffset) {
        return score(segment, recordCount, layout, queryVector, options, nowMs, baseOffset, null, null);
    }

    /**
     * Scans a memory segment using calibrated scalar quantization parameters.
     */
    public static List<ScoredRecord> score(
            final MemorySegment segment, final int recordCount, final EngramLayout layout,
            final float[] queryVector, final RecallOptions options, final long nowMs, final long baseOffset,
            final float[] mins, final float[] scales) {
        return score(segment, recordCount, layout, queryVector, options, nowMs, baseOffset, mins, scales, null, null);
    }

    /**
     * Full scan entrypoint with calibrated distance and early associative prior (MR-06).
     */
    public static List<ScoredRecord> score(
            final MemorySegment segment, final int recordCount, final EngramLayout layout,
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
            final MemorySegment segment, final int recordCount, final EngramLayout layout,
            final float[] queryVector, final RecallOptions options, final long nowMs, final long baseOffset,
            final float[] mins, final float[] scales,
            final AssociativePriorProvider priorProvider,
            final QueryAssociativeContext priorContext,
            final StrengthMemory strengthStore,
            final MemoryType tier) {

        final int topK = options.topK();
        final long queryTagMask = options.synapticTagMask();
        final float minImportance = options.minImportance();
        final byte minValence = options.minValence();
        final byte maxValence = options.maxValence();
        final float alpha = options.alpha();
        final float beta = options.beta();
        final float tagRelevanceBoost = options.tagRelevanceBoost();
        final float strictness = options.strictnessCoefficient();
        final Long minTimestamp = options.minTimestamp();
        final Long maxTimestamp = options.maxTimestamp();
        final boolean allowFuture = options.allowFuture();
        final boolean pureSimilarity = options.scoringMode() == ScoringMode.SIMILARITY;
        final ScoreFusionMode fusionMode = options.scoreFusionMode() != null
                ? options.scoreFusionMode()
                : ScoreFusionMode.MULTIPLICATIVE;
        final boolean enableAssociativePrior = options.enableAssociativePrior() && priorProvider != null && priorContext != null;
        final float associativePriorDelta = options.associativePriorDelta();

        final boolean twoFactorEnabled = options.twoFactorConfig() != null && options.twoFactorConfig().enabled();
        final float sExponent = options.twoFactorConfig() != null ? options.twoFactorConfig().sExponent() : 0.3f;

        final boolean valenceAlign = options.enableValenceAlignment();
        final byte queryValence = options.queryValence();

        final long hyperfocusMask = options.hyperfocusMask();
        final float hyperfocusBoost = options.hyperfocusBoost();

        final boolean lateralMode = options.lateralMode();
        final float lateralDistanceThreshold = options.lateralDistanceThreshold();
        final int lateralMaxResults = options.lateralMaxResults();
        final float lateralMinTagOverlap = options.lateralMinTagOverlap();

        final int dims = queryVector.length;
        final float[] effectiveMins = mins != null ? mins : IdentityCalibration.mins(dims);
        final float[] effectiveScales = scales != null ? scales : IdentityCalibration.scales(dims);

        final int stride = layout.stride();
        final boolean hasArousal = layout.headerLayout().version() >= 2;
        final boolean hasStorageStrength = hasArousal;
        final boolean useStrength = strengthStore != null && tier != null && tier != MemoryType.WORKING;

        final FlatMinHeap heap = new FlatMinHeap(topK);
        final PriorityQueue<ScoredRecord> lateralHeap = lateralMode
                ? new PriorityQueue<>(lateralMaxResults + 1)
                : null;

        for (int i = 0; i < recordCount; i++) {
            final long offset = baseOffset + (long) i * stride;
            if (offset + stride > segment.byteSize()) {
                break;
            }

            // Phase 1: Tombstone check (~1 cycle)
            final byte flags = layout.readFlags(segment, offset);
            if (isTombstoned(flags)) {
                continue;
            }

            // Phase 1c: Contradiction & simulation gating (NF7: default recall hard-gates simulated records)
            if (!options.includeContradictions() || !options.allowSimulated()) {
                final byte cFlags = layout.readConsolidationFlags(segment, offset);
                if (!options.includeContradictions() && isContradicted(cFlags)) {
                    continue;
                }
                if (!options.allowSimulated() && com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields.isSimulated(cFlags)) {
                    continue;
                }
            }

            // Phase 1b: Temporal gating & Future causal horizon gate
            final long timestamp = layout.readTimestamp(segment, offset);
            if (RecordGates.isTemporalGated(timestamp, minTimestamp, maxTimestamp, nowMs, allowFuture)) {
                continue;
            }

            // Phase 2: Synaptic tag gating
            long recordTags = 0;
            if (hyperfocusMask != 0 || queryTagMask != 0) {
                recordTags = layout.readSynapticTags(segment, offset);
                if (RecordGates.isTagGated(recordTags, queryTagMask, hyperfocusMask)) {
                    continue;
                }
            }

            // Phase 3: Valence filter
            final byte valence = layout.readValence(segment, offset);
            if (RecordGates.isValenceGated(valence, minValence, maxValence)) {
                continue;
            }

            // Phase 4: Temporal / importance pre-screen with reconsolidation & high-mass exemption
            final float rawImportance = layout.readImportance(segment, offset);
            final float importance;
            if (useStrength) {
                float eff = strengthStore.readEffectiveImportance(tier, i);
                importance = eff != 0.0f ? eff : rawImportance;
            } else {
                importance = rawImportance;
            }
            if (importance < minImportance) {
                continue;
            }

            final int agentRecallCount = useStrength
                    ? strengthStore.readAgentRecallCount(tier, i)
                    : layout.readAgentRecallCount(segment, offset);
            final int rawBucket = DecayStrategy.ageToBucket(timestamp, nowMs);
            int adjustedBucket = DecayStrategy.adjustForReconsolidation(rawBucket, agentRecallCount);

            if (hasStorageStrength || useStrength) {
                final int spectorRecallCount = useStrength
                        ? strengthStore.readSpectorRecallCount(tier, i)
                        : layout.readSpectorRecallCount(segment, offset);
                adjustedBucket = DecayStrategy.adjustForAutoRecall(adjustedBucket, spectorRecallCount);
            }

            final boolean focusMatch = hyperfocusMask != 0 && (recordTags & hyperfocusMask) == hyperfocusMask;
            final boolean zeroTimeDecay = focusMatch || (!isResolved(flags) && !isPinned(flags));

            final byte arousal = hasArousal ? layout.readArousal(segment, offset) : (byte) 0;
            final float storageStrength;
            if (useStrength) {
                float s = strengthStore.readStorageStrength(tier, i);
                storageStrength = s > 0.0f ? s : 1.0f;
            } else if (hasStorageStrength) {
                storageStrength = layout.readStorageStrength(segment, offset);
            } else {
                storageStrength = 1.0f;
            }
            final float cognitiveMass = CognitiveScoreFusion.computeCognitiveMass(importance, arousal, storageStrength);

            if (RecordGates.isStaleAndWeak(adjustedBucket, importance, flags, cognitiveMass)) {
                continue;
            }

            // Phase 5: Calibrated SIMD L2 distance
            final float l2dist = SimilarityFunction.EUCLIDEAN.computeQuantizedFromSegment(
                    queryVector, segment, layout.vectorOffset(offset),
                    effectiveMins, effectiveScales, layout.quantizedVecBytes());

            // Phase 6: Fused score composition with mass-dilated continuous log recency
            final float tagOverlap = SynapticTagEncoder.overlapRatio(recordTags, queryTagMask);

            if (lateralMode && l2dist > lateralDistanceThreshold && tagOverlap >= lateralMinTagOverlap) {
                scoreLateral(lateralHeap, lateralMaxResults, segment, offset, layout,
                        l2dist, tagOverlap, importance, adjustedBucket, arousal,
                        timestamp, recordTags, valence, flags, agentRecallCount, i, storageStrength);
                continue;
            }

            final float finalScore = CognitiveScoreFusion.computeFusedScore(
                    l2dist, strictness, pureSimilarity, timestamp, nowMs, cognitiveMass,
                    arousal, storageStrength, hasStorageStrength || useStrength, twoFactorEnabled, sExponent,
                    agentRecallCount, importance, beta, alpha, tagOverlap, fusionMode,
                    valenceAlign, queryValence, valence, tagRelevanceBoost, focusMatch,
                    zeroTimeDecay, hyperfocusBoost, flags, enableAssociativePrior,
                    priorProvider, offset, recordTags, priorContext, associativePriorDelta);

            // Top-K insertion
            if (heap.shouldInsert(finalScore)) {
                final long synapticTags = queryTagMask != 0 || hyperfocusMask != 0 ? recordTags : 0;
                final float exactNorm = layout.readExactNorm(segment, offset);
                final short centroidId = layout.readCentroidId(segment, offset);
                heap.insert(finalScore, offset, i, timestamp, synapticTags,
                        exactNorm, importance, agentRecallCount, centroidId, valence, flags);
            }
        }

        final List<ScoredRecord> results = heap.drain();
        if (lateralHeap != null && !lateralHeap.isEmpty()) {
            results.addAll(lateralHeap);
        }
        results.sort(Comparator.comparing(ScoredRecord::score).reversed().thenComparingLong(ScoredRecord::offset));
        return results;
    }

    private static void scoreLateral(
            final PriorityQueue<ScoredRecord> lateralHeap, final int lateralMaxResults,
            final MemorySegment segment, final long offset, final EngramLayout layout,
            final float l2dist, final float tagOverlap, final float importance,
            final int adjustedBucket, final byte arousal,
            final long timestamp, final long recordTags, final byte valence, final byte flags,
            final int agentRecallCount, final int recordIndex, final float storageStrength) {

        final float l2sq = l2dist * l2dist;
        final float diff = l2sq - 2.0f;
        final float lateralSimilarity = Math.max(0.0f, 1.0f - 0.25f * diff * diff);
        float decay = DecayStrategy.decay(adjustedBucket) * DecayStrategy.arousalModifier(arousal);
        decay = Math.min(1.0f, decay);
        final float importanceNorm = importance / 10.0f;
        final float lateralScore = lateralSimilarity * tagOverlap * (1.0f + importanceNorm * decay);

        final float exactNorm = layout.readExactNorm(segment, offset);
        final short centroidId = layout.readCentroidId(segment, offset);
        final EncodingHeader header = new EncodingHeader(
                timestamp, recordTags, exactNorm, importance,
                agentRecallCount, centroidId, valence, flags,
                arousal, storageStrength);

        if (lateralHeap.size() < lateralMaxResults) {
            lateralHeap.offer(new ScoredRecord(offset, lateralScore, recordIndex, header, true));
        } else if (lateralScore > lateralHeap.peek().score()) {
            lateralHeap.poll();
            lateralHeap.offer(new ScoredRecord(offset, lateralScore, recordIndex, header, true));
        }
    }
}
