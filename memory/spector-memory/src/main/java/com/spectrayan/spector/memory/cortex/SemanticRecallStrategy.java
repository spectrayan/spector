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
package com.spectrayan.spector.memory.cortex;

import com.spectrayan.spector.index.ScoredResult;
import com.spectrayan.spector.index.VectorIndex;
import com.spectrayan.spector.memory.index.IndexRecordMemory.MemoryLocation;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoreBreakdown;
import com.spectrayan.spector.memory.model.ScoringMode;
import com.spectrayan.spector.memory.model.SourceModality;
import com.spectrayan.spector.memory.synapse.DecayStrategy;
import com.spectrayan.spector.memory.synapse.SynapticTagEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Fused HNSW + Cognitive Scoring recall strategy for Semantic Memory (ADR-0009, #445).
 *
 * <h3>Architecture</h3>
 * <p>Semantic memory uses a unified {@link VectorIndex} (HNSW/IVF) for $O(\log N)$
 * approximate nearest neighbor retrieval across all partitions. When a recall query arrives,
 * the strategy:</p>
 * <ol>
 *   <li>Queries HNSW for {@code topK * semanticCandidateMultiplier} candidates</li>
 *   <li>Resolves each candidate ID to its physical partition and offset via {@link MemoryIndex#location(String)}</li>
 *   <li>Reads the 64-byte {@link CognitiveHeader} from the partition's off-heap slab segment</li>
 *   <li>Applies tombstones, contradiction gating, temporal bounds, synaptic tags / hyperfocus, valence, and importance filtering</li>
 *   <li>Computes the fused cognitive score (similarity + decay * importance * tag boost)</li>
 *   <li>Sorts and returns the top-K cognitive results</li>
 * </ol>
 *
 * <h3>Partition Awareness</h3>
 * <p>Unlike the legacy single-store implementation, this strategy resolves partition handles dynamically via
 * {@link PartitionRegistry#handleFor(int)}, guaranteeing zero offset collisions across partition rolls.</p>
 */
public final class SemanticRecallStrategy {

    private static final Logger log = LoggerFactory.getLogger(SemanticRecallStrategy.class);

    private final VectorIndex vectorIndex;
    private final PartitionRegistry partitionRegistry;
    private final MemoryIndex memoryIndex;

    /**
     * Creates a partition-aware fused semantic recall strategy (ADR-0009).
     *
     * @param vectorIndex       the HNSW/IVF index backing semantic memory
     * @param partitionRegistry the partition registry for partition resolution
     * @param memoryIndex       the ID → metadata index for location lookups
     */
    public SemanticRecallStrategy(VectorIndex vectorIndex,
                                  PartitionRegistry partitionRegistry,
                                  MemoryIndex memoryIndex) {
        this.vectorIndex = vectorIndex;
        this.partitionRegistry = partitionRegistry;
        this.memoryIndex = memoryIndex;
    }

    /**
     * Executes a fused semantic recall: HNSW search → partition-aware cognitive re-ranking.
     *
     * @param queryVector the embedded query vector
     * @param options     recall configuration
     * @param nowMs       current timestamp for decay computation
     * @return ranked list of cognitive results
     */
    public List<CognitiveResult> recall(float[] queryVector, RecallOptions options, long nowMs) {
        int candidateCount = options.topK() * options.semanticCandidateMultiplier();
        ScoredResult[] hnswResults = vectorIndex.search(queryVector, candidateCount);

        if (hnswResults == null || hnswResults.length == 0) {
            log.debug("Semantic HNSW search returned 0 results");
            return List.of();
        }

        // Extract filter parameters
        long queryTagMask = options.synapticTagMask();
        long hyperfocusMask = options.hyperfocusMask();
        byte minValence = options.minValence();
        byte maxValence = options.maxValence();
        float minImportance = options.minImportance();
        Long minTimestamp = options.minTimestamp();
        Long maxTimestamp = options.maxTimestamp();
        boolean pureSimilarity = options.scoringMode() == ScoringMode.SIMILARITY;

        // Cognitive scoring weights (ignored in SIMILARITY mode)
        float alpha = options.alpha();
        float beta = options.beta();
        float tagRelevanceBoost = options.tagRelevanceBoost();

        List<CognitiveResult> results = new ArrayList<>();

        for (ScoredResult sr : hnswResults) {
            String id = sr.id();
            if (id == null) continue;

            MemoryLocation loc = memoryIndex.location(id);
            if (loc == null || loc.type() != MemoryType.SEMANTIC) continue;

            int partitionSeq = loc.colocatedPartition();
            long headerOffset = loc.offset();

            if (partitionRegistry == null) continue;
            PartitionHandle handle = partitionRegistry.handleFor(partitionSeq);
            if (handle == null || handle.router() == null) continue;
            SemanticRecordMemory store = handle.router().semantic();

            if (store == null) continue;

            CognitiveRecordLayout layout = store.cognitiveLayout();
            MemorySegment headerSlab = store.primarySegment();

            // Bounds check: ensure we're within the slab
            if (headerSlab == null || headerOffset + layout.headerLayout().headerBytes() > headerSlab.byteSize()) {
                continue;
            }

            CognitiveHeader header = layout.readHeader(headerSlab, headerOffset);

            // Phase 1: Tombstone check (always applied)
            if (SynapticHeaderConstants.isTombstoned(header.flags())) continue;

            // Phase 1c: Contradiction Gating
            if (!options.includeContradictions()) {
                byte cFlags = layout.readConsolidationFlags(headerSlab, headerOffset);
                if (SynapticHeaderConstants.isContradicted(cFlags)) continue;
            }

            // Phase 1b: Temporal gating & Future causal horizon gate
            long timestamp = header.timestampMs();
            if (com.spectrayan.spector.memory.synapse.scan.RecordGates.isTemporalGated(
                    timestamp, minTimestamp, maxTimestamp, nowMs, options.allowFuture())) {
                continue;
            }

            // Phase 2: Synaptic tag gating
            long recordTags = header.synapticTags();
            if (hyperfocusMask != 0L) {
                if ((recordTags & hyperfocusMask) != hyperfocusMask) continue;
            } else if (queryTagMask != 0L) {
                if ((recordTags & queryTagMask) == 0L) continue;
            }

            // Phase 3: Valence filter
            byte valence = header.valence();
            if (valence < minValence || valence > maxValence) continue;

            // Phase 4: Importance threshold
            float importance = header.importance();
            if (importance < minImportance) continue;

            float finalScore;
            int agentRecallCount = header.agentRecallCount();
            float decay;
            float rawDecay;

            float similarity;
            if (sr.score() >= 0.0f && sr.score() <= 1.0f) {
                similarity = sr.score();
            } else {
                similarity = 1.0f / (1.0f + Math.max(0.0f, sr.score()));
            }

            if (pureSimilarity) {
                finalScore = similarity;
                decay = 1.0f;
                rawDecay = 1.0f;
            } else {
                int rawBucket = DecayStrategy.ageToBucket(timestamp, nowMs);
                int adjusted = DecayStrategy.adjustForReconsolidation(rawBucket, agentRecallCount);
                decay = DecayStrategy.decay(adjusted);
                rawDecay = DecayStrategy.decay(rawBucket);

                float baseScore = alpha * similarity + beta * importance * decay;
                float tagOverlap = SynapticTagEncoder.overlapRatio(recordTags, queryTagMask);
                finalScore = baseScore * (1.0f + tagOverlap * tagRelevanceBoost);
            }

            String text = memoryIndex.text(id);
            if (text == null) text = "";
            MemorySource source = memoryIndex.source(id);
            if (source == null) source = MemorySource.OBSERVED;
            String[] tags = memoryIndex.tags(id);
            if (tags == null) tags = new String[0];
            float ageDays = (nowMs - timestamp) / (1000f * 60f * 60f * 24f);

            ScoreBreakdown breakdown;
            if (pureSimilarity) {
                breakdown = new ScoreBreakdown(similarity, 0f, 1.0f, 1.0f, 1.0f, 1.0f, finalScore);
            } else {
                float importanceDecay = importance * decay;
                float tagOverlapForBd = SynapticTagEncoder.overlapRatio(recordTags, queryTagMask);
                float tagBoostFactor = 1.0f + tagOverlapForBd * tagRelevanceBoost;
                breakdown = new ScoreBreakdown(
                        similarity, importanceDecay, tagBoostFactor,
                        1.0f, 1.0f, 1.0f, finalScore);
            }

            results.add(new CognitiveResult(
                    id, text, finalScore, importance, ageDays,
                    agentRecallCount, valence, MemoryType.SEMANTIC, source,
                    tags, rawDecay, decay,
                    CognitiveResult.RetrievalMode.STANDARD, breakdown, null,
                    SourceModality.TEXT, Map.of(), (byte) 0, timestamp));
        }

        // Sort by fused score descending
        results.sort(Comparator.comparing(CognitiveResult::score).reversed());

        log.debug("Semantic partition-aware fused recall: {} HNSW candidates → {} after filtering",
                hnswResults.length, results.size());

        return results;
    }

    /**
     * Returns whether this strategy has a configured vector index.
     */
    public boolean isAvailable() {
        return vectorIndex != null;
    }
}
