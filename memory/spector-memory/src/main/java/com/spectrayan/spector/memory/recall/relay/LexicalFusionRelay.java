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
package com.spectrayan.spector.memory.recall.relay;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.config.model.TextSearchMode;
import com.spectrayan.spector.memory.cortex.MemoryBM25Index;
import com.spectrayan.spector.memory.cortex.MemoryBM25Index.BM25Candidate;
import com.spectrayan.spector.memory.cortex.MemorySpladeIndex;
import com.spectrayan.spector.memory.cortex.MemorySpladeIndex.SpladeCandidate;
import com.spectrayan.spector.memory.cortex.PartitionRegistry;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pipeline.gatherer.RecallCandidateGatherer;
import com.spectrayan.spector.provider.embedding.SparseEmbeddingProvider;
import com.spectrayan.spector.provider.embedding.SparseEmbeddingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Fuses keyword (BM25) and sparse (SPLADE) search results into the recall candidates using RRF.
 */
public final class LexicalFusionRelay implements SynapticRelay<RecallSignal> {

    private static final Logger log = LoggerFactory.getLogger(LexicalFusionRelay.class);

    private final MemoryBM25Index bm25Index;
    private final MemorySpladeIndex spladeIndex;
    private final SparseEmbeddingProvider spladeProvider;
    private final RecallCandidateGatherer gatherer;
    private final PartitionRegistry partitionRegistry;

    private boolean spladeWarnLogged = false;

    /**
     * Constructs a new LexicalFusionRelay.
     *
     * @param bm25Index         the BM25 index
     * @param spladeIndex       the SPLADE index (nullable)
     * @param spladeProvider    the SPLADE sparse embedding provider (nullable)
     * @param gatherer          the candidate gatherer
     * @param partitionRegistry the partition registry
     */
    public LexicalFusionRelay(
            final MemoryBM25Index bm25Index,
            final MemorySpladeIndex spladeIndex,
            final SparseEmbeddingProvider spladeProvider,
            final RecallCandidateGatherer gatherer,
            final PartitionRegistry partitionRegistry) {
        this.bm25Index = bm25Index;
        this.spladeIndex = spladeIndex;
        this.spladeProvider = spladeProvider;
        this.gatherer = gatherer;
        this.partitionRegistry = partitionRegistry;
    }

    @Override
    public boolean transmit(final RecallSignal signal) {
        boolean rrfFused = false;

        // Sort vector candidates by cognitive score descending before RRF rank assignment
        signal.candidates().sort(java.util.Comparator.comparing(com.spectrayan.spector.memory.model.CognitiveResult::score).reversed());

        // BM25 Text Search
        if (bm25Index != null && signal.options().enableTextSearch()
                && signal.options().textSearchMode() != TextSearchMode.VECTOR_ONLY) {
            try {
                final List<BM25Candidate> bm25Hits = bm25Index.search(signal.rawQuery(), signal.options().topK() * 2);
                if (bm25Hits != null && !bm25Hits.isEmpty()) {
                    gatherer.fuseBM25Candidates(signal.candidates(), bm25Hits, signal.options(), partitionRegistry);
                    rrfFused = true;
                }
            } catch (final RuntimeException e) {
                log.warn("BM25 search failed, continuing with vector-only results", e);
            }
        }

        // SPLADE Learned Sparse Search
        if (signal.options().enableTextSearch() && signal.options().textSearchMode().usesSPLADE()) {
            if (spladeIndex != null && spladeProvider != null) {
                try {
                    final SparseEmbeddingResult querySparse = spladeProvider.encode(signal.rawQuery());
                    final List<SpladeCandidate> spladeHits = spladeIndex.search(querySparse.weights(), signal.options().topK() * 2);
                    if (spladeHits != null && !spladeHits.isEmpty()) {
                        final List<BM25Candidate> asBm25 = spladeHits.stream()
                                .map(sc -> new BM25Candidate(sc.id(), sc.spladeScore(), sc.partitionIndex()))
                                .toList();
                        gatherer.fuseBM25Candidates(signal.candidates(), asBm25, signal.options(), partitionRegistry);
                        rrfFused = true;
                    }
                } catch (final RuntimeException e) {
                    log.warn("SPLADE search failed, continuing without", e);
                }
            } else if (!spladeWarnLogged) {
                log.warn("SPLADE search requested (mode={}) but SparseEmbeddingProvider/SpladeIndex " +
                         "not configured  --  degrading to BM25", signal.options().textSearchMode());
                spladeWarnLogged = true;
            }
        }

        if (rrfFused) {
            signal.setRrfFused(true);
        }

        return true;
    }

    @Override
    public String relayName() {
        return RelayNames.BM25_SEARCH;
    }
}
