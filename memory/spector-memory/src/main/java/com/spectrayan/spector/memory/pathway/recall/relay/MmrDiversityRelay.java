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
package com.spectrayan.spector.memory.pathway.recall.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pathway.pipeline.reranker.MmrReranker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Relay that applies Maximal Marginal Relevance diversity reranking.
 */
public final class MmrDiversityRelay implements SynapticRelay<RecallSignal> {

    private static final Logger log = LoggerFactory.getLogger(MmrDiversityRelay.class);
    private final MmrReranker mmrReranker;
    private boolean mmrWarnLogged = false;

    public MmrDiversityRelay(final MmrReranker mmrReranker) {
        this.mmrReranker = mmrReranker;
    }

    @Override
    public boolean transmit(final RecallSignal signal) {
        final RecallOptions options = signal.options();
        List<CognitiveResult> allResults = signal.candidates();

        if (options.enableMmr()) {
            if (mmrReranker != null) {
                allResults = mmrReranker.rerank(allResults, signal.queryVector(), options.mmrLambda(), options.topK());
                signal.setCandidates(allResults);
            } else if (!mmrWarnLogged) {
                log.warn("MMR reranking requested but MmrReranker not configured");
                mmrWarnLogged = true;
            }
        }

        return true;
    }

    @Override
    public String relayName() {
        return RelayNames.MMR_RERANK;
    }
}
