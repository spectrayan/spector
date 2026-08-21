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

import com.spectrayan.spector.commons.pathway.CognitivePathway;
import com.spectrayan.spector.commons.pathway.ConsolidationRelay;
import com.spectrayan.spector.commons.pathway.ErrorPolicy;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.pathway.RelayNames;

/**
 * Factory for creating the recall cognitive pathway.
 */
public final class RecallPathwayFactory {

    private RecallPathwayFactory() {}

    /**
     * Creates the recall cognitive pathway.
     */
    public static CognitivePathway<RecallSignal> create(
            final SynapticRelay<RecallSignal> transductionRelay,
            final SynapticRelay<RecallSignal> prospectiveRelay,
            final SynapticRelay<RecallSignal> governedReleaseGateRelay,
            final SynapticRelay<RecallSignal> vectorSearchRelay,
            final SynapticRelay<RecallSignal> scoringRelay,
            final SynapticRelay<RecallSignal> graphExpansionRelay,
            final SynapticRelay<RecallSignal> evidenceFusionRelay,
            final SynapticRelay<RecallSignal> bm25SearchRelay,
            final RrfRescoreRelay rrfRescoreRelay,
            final SortAndTruncateRelay sortAndTruncateRelay,
            final CognitiveRerankRelay cognitiveRerankRelay,
            final MmrDiversityRelay mmrDiversityRelay,
            final TemperatureSoftmaxRelay temperatureSoftmaxRelay,
            final ConsolidationRelay<RecallSignal> consolidationRelay) {
        return create(null, transductionRelay, prospectiveRelay, governedReleaseGateRelay,
                vectorSearchRelay, scoringRelay, graphExpansionRelay, evidenceFusionRelay,
                bm25SearchRelay, rrfRescoreRelay, sortAndTruncateRelay,
                cognitiveRerankRelay, mmrDiversityRelay, temperatureSoftmaxRelay, consolidationRelay);
    }

    /**
     * Creates the recall cognitive pathway with an interceptor/decorator.
     */
    public static CognitivePathway<RecallSignal> create(
            final java.util.function.Function<SynapticRelay<RecallSignal>, SynapticRelay<RecallSignal>> interceptor,
            final SynapticRelay<RecallSignal> transductionRelay,
            final SynapticRelay<RecallSignal> prospectiveRelay,
            final SynapticRelay<RecallSignal> governedReleaseGateRelay,
            final SynapticRelay<RecallSignal> vectorSearchRelay,
            final SynapticRelay<RecallSignal> scoringRelay,
            final SynapticRelay<RecallSignal> graphExpansionRelay,
            final SynapticRelay<RecallSignal> evidenceFusionRelay,
            final SynapticRelay<RecallSignal> bm25SearchRelay,
            final RrfRescoreRelay rrfRescoreRelay,
            final SortAndTruncateRelay sortAndTruncateRelay,
            final CognitiveRerankRelay cognitiveRerankRelay,
            final MmrDiversityRelay mmrDiversityRelay,
            final TemperatureSoftmaxRelay temperatureSoftmaxRelay,
            final ConsolidationRelay<RecallSignal> consolidationRelay) {

        final var builder = CognitivePathway.<RecallSignal>pathway("recall");
        if (interceptor != null) {
            builder.withInterceptor(interceptor);
        }
        return builder
                .relay(RelayNames.TRANSDUCTION, transductionRelay)
                .relay(RelayNames.PROSPECTIVE, prospectiveRelay)
                .relay(RelayNames.GOVERNED_RELEASE_GATE, governedReleaseGateRelay)
                .relay(RelayNames.VECTOR_SEARCH, vectorSearchRelay)
                .relay(RelayNames.SCORING, scoringRelay)
                .relay(RelayNames.GRAPH_EXPANSION, graphExpansionRelay)
                .relay(RelayNames.EVIDENCE_FUSION, evidenceFusionRelay)
                .gated(RelayNames.BM25_SEARCH, RecallGates.TEXT_SEARCH_ENABLED, bm25SearchRelay, ErrorPolicy.DEGRADE_GRACEFULLY)
                .gated(RelayNames.RRF_RESCORE, RecallGates.RRF_FUSED, rrfRescoreRelay, ErrorPolicy.DEGRADE_GRACEFULLY)
                .relay(RelayNames.SORT_TRUNCATE, sortAndTruncateRelay)
                .circuitBreaker(RelayNames.COLBERT_RERANK,
                        new com.spectrayan.spector.commons.pathway.GatedRelay<>(
                                RelayNames.COLBERT_RERANK, RecallGates.RERANK_CONFIGURED, cognitiveRerankRelay),
                        5, 30_000L, ErrorPolicy.DEGRADE_GRACEFULLY)
                .gated(RelayNames.MMR_RERANK, RecallGates.MMR_ENABLED, mmrDiversityRelay, ErrorPolicy.DEGRADE_GRACEFULLY)
                .relay(RelayNames.TEMPERATURE, temperatureSoftmaxRelay)
                .relay(RelayNames.CONSOLIDATION, consolidationRelay, ErrorPolicy.DEGRADE_GRACEFULLY)
                .build();
    }
}
