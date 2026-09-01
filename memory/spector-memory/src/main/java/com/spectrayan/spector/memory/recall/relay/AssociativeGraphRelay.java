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
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pipeline.GraphExpansionStage;
import com.spectrayan.spector.memory.pipeline.graph.TemporalFactWeavingStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Expands results by following the Hebbian and temporal graphs.
 */
public final class AssociativeGraphRelay implements SynapticRelay<RecallSignal> {

    private static final Logger log = LoggerFactory.getLogger(AssociativeGraphRelay.class);

    private final GraphExpansionStage graphExpansionStage;
    private final TemporalFactWeavingStage temporalFactWeavingStage;

    /**
     * Constructs a new AssociativeGraphRelay.
     *
     * @param graphExpansionStage      the graph expansion stage
     * @param temporalFactWeavingStage the temporal fact weaving stage
     */
    public AssociativeGraphRelay(
            final GraphExpansionStage graphExpansionStage,
            final TemporalFactWeavingStage temporalFactWeavingStage) {
        this.graphExpansionStage = graphExpansionStage;
        this.temporalFactWeavingStage = temporalFactWeavingStage;
    }

    @Override
    public boolean transmit(final RecallSignal signal) {
        try {
            graphExpansionStage.expand(signal.candidates(), signal.queryVector(), signal.options(), signal.rawQuery());
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }

        temporalFactWeavingStage.weave(signal.candidates(), signal.queryVector(), signal.options(), signal.rawQuery());

        return true;
    }

    @Override
    public String relayName() {
        return RelayNames.GRAPH_EXPANSION;
    }
}
