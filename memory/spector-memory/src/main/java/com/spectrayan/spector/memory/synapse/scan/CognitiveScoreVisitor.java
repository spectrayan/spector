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
package com.spectrayan.spector.memory.synapse.scan;

import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.engram.HeaderBits;
import com.spectrayan.spector.kernel.scan.SlotVisitor;
import com.spectrayan.spector.kernel.score.CognitiveMass;
import com.spectrayan.spector.kernel.score.DecayStrategy;
import com.spectrayan.spector.kernel.score.SynapticTagEncoder;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoreFusionMode;
import com.spectrayan.spector.memory.model.ScoringMode;
import com.spectrayan.spector.memory.synapse.AssociativePriorProvider;
import com.spectrayan.spector.memory.synapse.CognitiveScorer.ScoredRecord;
import com.spectrayan.spector.memory.synapse.QueryAssociativeContext;

import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import static com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields.isPinned;
import static com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields.isResolved;

/**
 * Above-the-line visitor applying Phase 6 fused score composition and top-K heap management (R7.2, R7.3).
 */
public final class CognitiveScoreVisitor implements SlotVisitor {

    private final FlatMinHeap heap;
    private final PriorityQueue<ScoredRecord> lateralHeap;
    private final boolean lateralMode;
    private final float lateralDistanceThreshold;
    private final int lateralMaxResults;
    private final float lateralMinTagOverlap;

    private final RecallOptions options;
    private final long nowMs;
    private final long queryTagMask;
    private final long hyperfocusMask;
    private final float alpha;
    private final float beta;
    private final float tagRelevanceBoost;
    private final float strictness;
    private final boolean pureSimilarity;
    private final ScoreFusionMode fusionMode;
    private final boolean enableAssociativePrior;
    private final AssociativePriorProvider priorProvider;
    private final QueryAssociativeContext priorContext;
    private final float associativePriorDelta;
    private final boolean twoFactorEnabled;
    private final float sExponent;
    private final boolean valenceAlign;
    private final byte queryValence;
    private final float hyperfocusBoost;

    public CognitiveScoreVisitor(
            final RecallOptions options,
            final long nowMs,
            final AssociativePriorProvider priorProvider,
            final QueryAssociativeContext priorContext) {
        this.options = options;
        this.nowMs = nowMs;
        this.priorProvider = priorProvider;
        this.priorContext = priorContext;

        this.heap = new FlatMinHeap(options.topK());
        this.lateralMode = options.lateralMode();
        this.lateralDistanceThreshold = options.lateralDistanceThreshold();
        this.lateralMaxResults = options.lateralMaxResults();
        this.lateralMinTagOverlap = options.lateralMinTagOverlap();
        this.lateralHeap = lateralMode ? new PriorityQueue<>(lateralMaxResults + 1) : null;

        this.queryTagMask = options.synapticTagMask();
        this.hyperfocusMask = options.hyperfocusMask();
        this.alpha = options.alpha();
        this.beta = options.beta();
        this.tagRelevanceBoost = options.tagRelevanceBoost();
        this.strictness = options.strictnessCoefficient();
        this.pureSimilarity = options.scoringMode() == ScoringMode.SIMILARITY;
        this.fusionMode = options.scoreFusionMode() != null ? options.scoreFusionMode() : ScoreFusionMode.MULTIPLICATIVE;
        this.enableAssociativePrior = options.enableAssociativePrior() && priorProvider != null && priorContext != null;
        this.associativePriorDelta = options.associativePriorDelta();
        this.twoFactorEnabled = options.twoFactorConfig() != null && options.twoFactorConfig().enabled();
        this.sExponent = options.twoFactorConfig() != null ? options.twoFactorConfig().sExponent() : 0.3f;
        this.valenceAlign = options.enableValenceAlignment();
        this.queryValence = options.queryValence();
        this.hyperfocusBoost = options.hyperfocusBoost();
    }

    @Override
    public void accept(int slot, int partition, long offset, long headerBits, float rawScore) {
        accept(slot, partition, offset, headerBits, rawScore, nowMs, 0L);
    }

    @Override
    public void accept(int slot, int partition, long offset, long headerBits, float rawScore, long timestampMs, long tagsLo) {
        final byte flags = HeaderBits.flags(headerBits);
        final byte valence = HeaderBits.valence(headerBits);
        final byte arousal = HeaderBits.arousal(headerBits);
        final int agentRecallCount = HeaderBits.agentRecallCount(headerBits);
        final float importance = HeaderBits.importance(headerBits);
        final float storageStrength = HeaderBits.storageStrength(headerBits);

        final float cognitiveMass = CognitiveMass.computeCognitiveMass(importance, arousal, storageStrength);
        final float tagOverlap = queryTagMask != 0L ? SynapticTagEncoder.overlapRatio(tagsLo, queryTagMask) : 0.0f;

        final int rawBucket = DecayStrategy.ageToBucket(timestampMs, nowMs);
        final int adjustedBucket = DecayStrategy.adjustForReconsolidation(rawBucket, agentRecallCount);

        if (lateralMode && rawScore > lateralDistanceThreshold && tagOverlap >= lateralMinTagOverlap) {
            scoreLateral(offset, rawScore, tagOverlap, importance, adjustedBucket, arousal,
                    timestampMs, tagsLo, valence, flags, agentRecallCount, slot, storageStrength);
            return;
        }

        final boolean focusMatch = hyperfocusMask != 0 && (tagsLo & hyperfocusMask) == hyperfocusMask;
        final boolean zeroTimeDecay = focusMatch || (!isResolved(flags) && !isPinned(flags));

        final float finalScore = CognitiveScoreFusion.computeFusedScore(
                rawScore, strictness, pureSimilarity, timestampMs, nowMs, cognitiveMass,
                arousal, storageStrength, true, twoFactorEnabled, sExponent,
                agentRecallCount, importance, beta, alpha, tagOverlap, fusionMode,
                valenceAlign, queryValence, valence, tagRelevanceBoost, focusMatch,
                zeroTimeDecay, hyperfocusBoost, flags, enableAssociativePrior,
                priorProvider, offset, tagsLo, priorContext, associativePriorDelta);

        if (heap.shouldInsert(finalScore)) {
            final long synapticTags = queryTagMask != 0 || hyperfocusMask != 0 ? tagsLo : 0L;
            heap.insert(finalScore, offset, slot, timestampMs, synapticTags,
                    1.0f, importance, agentRecallCount, (short) 0, valence, flags);
        }
    }

    private void scoreLateral(
            final long offset, final float l2dist, final float tagOverlap, final float importance,
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

        final EncodingHeader header = new EncodingHeader(
                timestamp, recordTags, 1.0f, importance,
                agentRecallCount, (short) 0, valence, flags,
                arousal, storageStrength);

        if (lateralHeap.size() < lateralMaxResults) {
            lateralHeap.offer(new ScoredRecord(offset, lateralScore, recordIndex, header, true));
        } else if (lateralScore > lateralHeap.peek().score()) {
            lateralHeap.poll();
            lateralHeap.offer(new ScoredRecord(offset, lateralScore, recordIndex, header, true));
        }
    }

    public List<ScoredRecord> drain() {
        final List<ScoredRecord> results = heap.drain();
        if (lateralHeap != null && !lateralHeap.isEmpty()) {
            results.addAll(lateralHeap);
        }
        results.sort(Comparator.comparing(ScoredRecord::score).reversed().thenComparingLong(ScoredRecord::offset));
        return results;
    }
}
