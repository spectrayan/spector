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

import com.spectrayan.spector.commons.pathway.ErrorPolicy;
import com.spectrayan.spector.commons.pathway.PathwayComposer;
import com.spectrayan.spector.commons.pathway.PathwayRecipe;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.pathway.RelayNames;

import java.util.Objects;

/**
 * Declarative recipe for composing the Remember cognitive pathway (ADR-0035 §7.3).
 *
 * <p>Replaces positional factory overloads in {@link RememberPathwayFactory}. Composes the
 * standard deduplication, tag transduction, surprise, cortical write, and background graph/KG
 * linking relays in dependency order.</p>
 */
public final class RememberRecipe implements PathwayRecipe<RememberSignal> {

    private final SynapticRelay<RememberSignal> dedup;
    private final SynapticRelay<RememberSignal> tags;
    private final SynapticRelay<RememberSignal> surprise;
    private final SynapticRelay<RememberSignal> write;
    private final SynapticRelay<RememberSignal> graph;
    private final SynapticRelay<RememberSignal> kg;

    /**
     * Creates a new Remember recipe from its constituent relays.
     *
     * @param dedup    deduplication guard relay
     * @param tags     synaptic tag transduction relay
     * @param surprise dopaminergic surprise and novelty relay
     * @param write    transactional cortical write and index sync relay
     * @param graph    associative graph and temporal chain linking relay
     * @param kg       knowledge graph and entity enrichment relay
     */
    public RememberRecipe(
            final SynapticRelay<RememberSignal> dedup,
            final SynapticRelay<RememberSignal> tags,
            final SynapticRelay<RememberSignal> surprise,
            final SynapticRelay<RememberSignal> write,
            final SynapticRelay<RememberSignal> graph,
            final SynapticRelay<RememberSignal> kg) {
        this.dedup = Objects.requireNonNull(dedup, "dedup cannot be null");
        this.tags = Objects.requireNonNull(tags, "tags cannot be null");
        this.surprise = Objects.requireNonNull(surprise, "surprise cannot be null");
        this.write = Objects.requireNonNull(write, "write cannot be null");
        this.graph = Objects.requireNonNull(graph, "graph cannot be null");
        this.kg = Objects.requireNonNull(kg, "kg cannot be null");
    }

    @Override
    public void compose(final PathwayComposer<RememberSignal> composer) {
        composer.relay(RelayNames.DEDUP_GUARD,           dedup,    ErrorPolicy.FAIL_FAST)
                .relay(RelayNames.TAG_TRANSDUCTION,      tags,     ErrorPolicy.FAIL_FAST)
                .relay(RelayNames.DOPAMINERGIC_SURPRISE, surprise, ErrorPolicy.FAIL_FAST)
                .relay(RelayNames.CORTICAL_WRITE,        write,    ErrorPolicy.FAIL_FAST)
                .relay(RelayNames.GRAPH_LINKING,         graph,    ErrorPolicy.DEGRADE_GRACEFULLY)
                .relay(RelayNames.KG_ENRICHMENT,         kg,       ErrorPolicy.DEGRADE_GRACEFULLY);
    }
}
