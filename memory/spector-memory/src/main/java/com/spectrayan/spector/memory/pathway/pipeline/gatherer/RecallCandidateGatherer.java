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
package com.spectrayan.spector.memory.pathway.pipeline.gatherer;

import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.MemoryBM25Index;
import com.spectrayan.spector.memory.cortex.MemoryBM25Index.BM25Candidate;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.cortex.PartitionRegistry;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeader;
import com.spectrayan.spector.memory.kernel.layout.EngramLayout;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields;
import com.spectrayan.spector.memory.kernel.layout.EpisodicHeaderAccessor;
import com.spectrayan.spector.memory.cortex.EpisodicMemory;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.SourceModality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.memory.synapse.SynapticTagEncoder;

import java.lang.foreign.MemorySegment;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles candidate retrieval across vector (HNSW/SVASQ), sparse (SPLADE),
 * and keyword (BM25) search indexes.
 */
public class RecallCandidateGatherer {

    private static final Logger log = LoggerFactory.getLogger(RecallCandidateGatherer.class);

    private final MemoryIndex index;
    private final MemoryBM25Index bm25Index;

    public RecallCandidateGatherer(MemoryIndex index, MemoryBM25Index bm25Index) {
        this.index = index;
        this.bm25Index = bm25Index;
    }

    public MemoryIndex index() {
        return index;
    }

    public MemoryBM25Index bm25Index() {
        return bm25Index;
    }

    /**
     * Fuses BM25 text search candidates with existing vector recall results using Reciprocal Rank Fusion (RRF).
     */
    public void fuseBM25Candidates(List<CognitiveResult> vectorResults,
                                   List<BM25Candidate> bm25Hits,
                                   RecallOptions options,
                                   PartitionRegistry partitionRegistry) {
        if (bm25Hits == null || bm25Hits.isEmpty()) return;
        final int RRF_K = 60;
        final long nowMs = System.currentTimeMillis();

        Map<String, CognitiveResult> existingById = new LinkedHashMap<>(vectorResults.size());
        Map<String, Float> rrfScores = new LinkedHashMap<>(vectorResults.size() + bm25Hits.size());

        for (int i = 0; i < vectorResults.size(); i++) {
            CognitiveResult r = vectorResults.get(i);
            String id = r.id();
            if (id != null && !existingById.containsKey(id)) {
                existingById.put(id, r);
                rrfScores.put(id, 1.0f / (RRF_K + (i + 1)));
            }
        }

        float bm25Weight = 1.5f;
        Set<String> bm25Seen = new java.util.HashSet<>(bm25Hits.size());
        for (int i = 0; i < bm25Hits.size(); i++) {
            String id = bm25Hits.get(i).id();
            if (id != null && bm25Seen.add(id)) {
                float rrfContribution = (1.0f / (RRF_K + (i + 1))) * bm25Weight;
                rrfScores.merge(id, rrfContribution, Float::sum);
            }
        }

        vectorResults.clear();
        for (Map.Entry<String, Float> entry : rrfScores.entrySet()) {
            String id = entry.getKey();
            float rrfScore = entry.getValue();
            CognitiveResult existing = existingById.get(id);

            if (existing != null) {
                float tierBoost = (existing.memoryType() == MemoryType.SEMANTIC || existing.memoryType() == MemoryType.PROCEDURAL) ? 2.0f : 1.0f;
                float provenanceBoost = existing.source() != null ? (0.8f + 0.2f * existing.source().confidenceWeight()) : 1.0f;
                vectorResults.add(existing.withScore(rrfScore * tierBoost * provenanceBoost));
            } else if (index != null) {
                MemoryIndex.MemoryLocation loc = index.locate(id);
                if (loc == null) continue;

                MemoryType type = loc.type();
                if (!CognitiveMemoryRouter.shouldScan(type, options.memoryTypes())) continue;

                float importance = 0f;
                byte valence = 0;
                float ageDays = 0f;
                short recallCount = 0;
                long ts = 0L;

                if (partitionRegistry != null) {
                    CognitiveMemoryRouter router = partitionRegistry.routerFor(loc.colocatedPartition());
                    if (router != null) {
                        if (type == MemoryType.EPISODIC) {
                            EpisodicMemory episodic = router.episodic();
                            if (episodic != null) {
                                MemorySegment segment = episodic.segment();
                                if (segment != null) {
                                    if (EpisodicHeaderAccessor.isTombstoned(segment, loc.offset())) continue;
                                    var header = EpisodicHeaderAccessor.readHeader(segment, loc.offset());
                                    importance = header.importance();
                                    valence = header.valence();
                                    recallCount = (short) header.agentRecallCount();
                                    ts = header.timestampMs();
                                    if (options.minTimestamp() != null && ts < options.minTimestamp()) continue;
                                    if (options.maxTimestamp() != null && ts > options.maxTimestamp()) continue;
                                    if (ts > 0) {
                                        ageDays = (float) ((nowMs - ts) / (double) (24 * 60 * 60 * 1000));
                                    }
                                }
                            }
                        } else {
                            MemorySegment segment = router.segmentFor(type);
                            if (segment != null) {
                                EngramLayout layout = router.layoutFor(type);
                                byte cFlags = layout.readConsolidationFlags(segment, loc.offset());
                                if (!options.includeContradictions() && EncodingHeaderFields.isContradicted(cFlags)) continue;

                                var header = layout.readHeader(segment, loc.offset());
                                importance = header.importance();
                                valence = header.valence();
                                recallCount = (short) header.agentRecallCount();
                                ts = header.timestampMs();
                                if (options.minTimestamp() != null && ts < options.minTimestamp()) continue;
                                if (options.maxTimestamp() != null && ts > options.maxTimestamp()) continue;
                                if (ts > 0) {
                                    ageDays = (float) ((nowMs - ts) / (double) (24 * 60 * 60 * 1000));
                                }
                            }
                        }
                    }
                }

                // Check valence & importance filters
                if (valence < options.minValence() || valence > options.maxValence()) continue;
                if (options.minImportance() > 0 && importance < options.minImportance()) continue;

                // Check tag filters
                String[] tags = index.tags(id);
                if (options.hyperfocusMask() != 0L) {
                    long recTags = SynapticTagEncoder.encode(tags);
                    if ((recTags & options.hyperfocusMask()) != options.hyperfocusMask()) continue;
                } else if (options.synapticTagMask() != 0L) {
                    long recTags = SynapticTagEncoder.encode(tags);
                    if ((recTags & options.synapticTagMask()) == 0L) continue;
                }

                String text = index.text(id);
                if (text == null || text.isEmpty()) continue;

                MemorySource source = index.source(id);
                Map<String, String> bm25Meta = index.metadata(id);
                SourceModality bm25Modality = bm25Meta != null
                        ? SourceModality.fromName(bm25Meta.get(SourceModality.METADATA_KEY))
                        : SourceModality.TEXT;

                float tierBoost = (type == MemoryType.SEMANTIC || type == MemoryType.PROCEDURAL) ? 2.0f : 1.0f;
                float provenanceBoost = source != null ? (0.8f + 0.2f * source.confidenceWeight()) : 1.0f;
                vectorResults.add(new CognitiveResult(
                        id, text, rrfScore * tierBoost * provenanceBoost, importance, ageDays,
                        recallCount, valence, type, source,
                        tags, 1.0f, 1.0f, CognitiveResult.RetrievalMode.STANDARD, null, null,
                        bm25Modality, bm25Meta, (byte) 0, ts));
            }
        }

        vectorResults.sort(Comparator.comparing(CognitiveResult::score).reversed().thenComparing(CognitiveResult::id));

        log.debug("RRF fused {} vector + {} BM25 candidates -> {} unique results",
                existingById.size(), bm25Hits.size(), vectorResults.size());
    }
}
