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

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.pipeline.reranker.ColBERTReranker;
import com.spectrayan.spector.memory.pipeline.reranker.ColBERTReranker.RerankCandidate;
import com.spectrayan.spector.memory.pipeline.reranker.ColBERTReranker.RerankResult;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.pathway.RelayNames;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Relay that uses ColBERT v2 to rerank candidate results.
 */
public final class CognitiveRerankRelay implements SynapticRelay<RecallSignal> {

    private static final Logger log = LoggerFactory.getLogger(CognitiveRerankRelay.class);
    private final ColBERTReranker colbertReranker;
    private boolean colbertWarnLogged = false;

    public CognitiveRerankRelay(final ColBERTReranker colbertReranker) {
        this.colbertReranker = colbertReranker;
    }

    @Override
    public boolean transmit(final RecallSignal signal) {
        final RecallOptions options = signal.options();
        List<CognitiveResult> allResults = signal.candidates();

        if (options.enableReranker() && options.textSearchMode().usesColBERT()) {
            if (colbertReranker != null) {
                try {
                    final int rerankerDepth = Math.min(options.rerankerDepth(), allResults.size());
                    if (rerankerDepth > 0) {
                        final List<CognitiveResult> toRerank = allResults.subList(0, rerankerDepth);

                        final List<RerankCandidate> candidates = toRerank.stream()
                                .map(r -> new RerankCandidate(
                                        r.id(), r.text() != null ? r.text() : "", r.score()))
                                .toList();

                        final List<RerankResult> reranked =
                                colbertReranker.rerank(signal.rawQuery(), candidates, options.topK());

                        final Map<String, Float> rerankScores = new HashMap<>();
                        for (final RerankResult rr : reranked) {
                            rerankScores.put(rr.id(), rr.combinedScore());
                        }

                        for (int i = 0; i < toRerank.size(); i++) {
                            final CognitiveResult r = toRerank.get(i);
                            final Float newScore = rerankScores.get(r.id());
                            if (newScore != null) {
                                allResults.set(i, r.withScore(newScore));
                            }
                        }

                        allResults.sort(Comparator.comparing(CognitiveResult::score).reversed().thenComparing(CognitiveResult::id));
                        if (allResults.size() > options.topK()) {
                            allResults = new ArrayList<>(allResults.subList(0, options.topK()));
                            signal.setCandidates(allResults);
                        }

                        log.debug("ColBERT reranked {} candidates  ->  {} results",
                                rerankerDepth, allResults.size());
                    }
                } catch (final RuntimeException e) {
                    log.warn("ColBERT reranking failed, keeping first-stage order", e);
                }
            } else if (!colbertWarnLogged) {
                log.warn("ColBERT reranking requested (mode={}) but ColBERTReranker " +
                         "not configured  --  skipping rerank step", options.textSearchMode());
                colbertWarnLogged = true;
            }
        }
        return true;
    }

    @Override
    public String relayName() {
        return RelayNames.COLBERT_RERANK;
    }
}
