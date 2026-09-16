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

import com.spectrayan.spector.commons.pathway.PathwayEngine;
import com.spectrayan.spector.commons.pathway.ErrorPolicy;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.commons.pathway.PathwayComposer;

/**
 * Factory for creating the remember / memory consolidation cognitive pathway.
 *
 * @deprecated Use {@link RememberRecipe} with {@link PathwayComposer} instead.
 */
@Deprecated(forRemoval = true, since = "1.5.0")
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
     * @deprecated Use {@link RememberRecipe} instead.
     */
    @Deprecated(forRemoval = true, since = "1.5.0")
    public static PathwayEngine<RememberSignal> create(
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
     * @deprecated Use {@link RememberRecipe} instead.
     */
    @Deprecated(forRemoval = true, since = "1.5.0")
    public static PathwayEngine<RememberSignal> create(
            final java.util.function.Function<SynapticRelay<RememberSignal>, SynapticRelay<RememberSignal>> interceptor,
            final DedupGuardRelay dedupGuardRelay,
            final SynapticTagTransductionRelay tagTransductionRelay,
            final DopaminergicSurpriseRelay surpriseRelay,
            final CorticalWriteTransactionRelay corticalWriteRelay,
            final SynapticGraphLinkingRelay graphLinkingRelay,
            final KnowledgeGraphEnrichmentRelay kgEnrichmentRelay) {

        final var composer = PathwayComposer.<RememberSignal>of("remember");
        if (interceptor != null) {
            composer.withInterceptor(interceptor);
        }
        new RememberRecipe(dedupGuardRelay, tagTransductionRelay, surpriseRelay,
                corticalWriteRelay, graphLinkingRelay, kgEnrichmentRelay).compose(composer);
        return composer.build();
    }
}
