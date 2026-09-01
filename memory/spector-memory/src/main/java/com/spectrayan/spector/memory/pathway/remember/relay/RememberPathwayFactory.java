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
package com.spectrayan.spector.memory.pathway.remember.relay;

import com.spectrayan.spector.commons.pathway.CognitivePathway;
import com.spectrayan.spector.commons.pathway.ErrorPolicy;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.pathway.RelayNames;

/**
 * Factory for creating the remember / memory consolidation cognitive pathway.
 */
public final class RememberPathwayFactory {

    private RememberPathwayFactory() {}

    /**
     * Creates the remember cognitive pathway from its constituent relays.
     *
     * @param dedupGuardRelay      the deduplication guard relay
     * @param tagTransductionRelay the synaptic tag transduction relay
     * @param surpriseRelay        the dopaminergic surprise and novelty relay
     * @param corticalWriteRelay   the transactional cortical write and index sync relay
     * @param graphLinkingRelay    the associative graph and temporal chain linking relay
     * @param kgEnrichmentRelay    the knowledge graph and entity enrichment relay
     * @return the constructed remember pathway
     */
    public static CognitivePathway<RememberSignal> create(
            final DedupGuardRelay dedupGuardRelay,
            final SynapticTagTransductionRelay tagTransductionRelay,
            final DopaminergicSurpriseRelay surpriseRelay,
            final CorticalWriteTransactionRelay corticalWriteRelay,
            final SynapticGraphLinkingRelay graphLinkingRelay,
            final KnowledgeGraphEnrichmentRelay kgEnrichmentRelay) {
        return create(null, dedupGuardRelay, tagTransductionRelay, surpriseRelay,
                corticalWriteRelay, graphLinkingRelay, kgEnrichmentRelay);
    }

    /**
     * Creates the remember cognitive pathway with an interceptor/decorator.
     *
     * @param interceptor          optional interceptor/decorator function
     * @param dedupGuardRelay      the deduplication guard relay
     * @param tagTransductionRelay the synaptic tag transduction relay
     * @param surpriseRelay        the dopaminergic surprise and novelty relay
     * @param corticalWriteRelay   the transactional cortical write and index sync relay
     * @param graphLinkingRelay    the associative graph and temporal chain linking relay
     * @param kgEnrichmentRelay    the knowledge graph and entity enrichment relay
     * @return the constructed remember pathway
     */
    public static CognitivePathway<RememberSignal> create(
            final java.util.function.Function<SynapticRelay<RememberSignal>, SynapticRelay<RememberSignal>> interceptor,
            final DedupGuardRelay dedupGuardRelay,
            final SynapticTagTransductionRelay tagTransductionRelay,
            final DopaminergicSurpriseRelay surpriseRelay,
            final CorticalWriteTransactionRelay corticalWriteRelay,
            final SynapticGraphLinkingRelay graphLinkingRelay,
            final KnowledgeGraphEnrichmentRelay kgEnrichmentRelay) {

        final var builder = CognitivePathway.<RememberSignal>pathway("remember");
        if (interceptor != null) {
            builder.withInterceptor(interceptor);
        }
        return builder
                .relay(RelayNames.DEDUP_GUARD, dedupGuardRelay, ErrorPolicy.FAIL_FAST)
                .relay(RelayNames.TAG_TRANSDUCTION, tagTransductionRelay, ErrorPolicy.FAIL_FAST)
                .relay(RelayNames.DOPAMINERGIC_SURPRISE, surpriseRelay, ErrorPolicy.FAIL_FAST)
                .relay(RelayNames.CORTICAL_WRITE, corticalWriteRelay, ErrorPolicy.FAIL_FAST)
                .relay(RelayNames.GRAPH_LINKING, graphLinkingRelay, ErrorPolicy.DEGRADE_GRACEFULLY)
                .relay(RelayNames.KG_ENRICHMENT, kgEnrichmentRelay, ErrorPolicy.DEGRADE_GRACEFULLY)
                .build();
    }
}
