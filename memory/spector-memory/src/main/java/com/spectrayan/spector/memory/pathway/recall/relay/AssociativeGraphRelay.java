/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.memory.pathway.recall.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pathway.pipeline.GraphExpansionStage;
import com.spectrayan.spector.memory.pathway.pipeline.graph.TemporalFactWeavingStage;
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
        if (!signal.candidates().isEmpty()) {
            signal.candidates().sort(java.util.Comparator.comparing(com.spectrayan.spector.memory.model.CognitiveResult::score).reversed()
                    .thenComparing(com.spectrayan.spector.memory.model.CognitiveResult::id));
        }
        log.info("AssociativeGraphRelay: candidates before expand={}, query='{}'", signal.candidates().size(), signal.rawQuery());
        try {
            graphExpansionStage.expand(signal.candidates(), signal.queryVector(), signal.options(), signal.rawQuery());
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
        log.info("AssociativeGraphRelay: candidates after expand={}", signal.candidates().size());

        temporalFactWeavingStage.weave(signal.candidates(), signal.queryVector(), signal.options(), signal.rawQuery(), signal.context());

        return true;
    }

    @Override
    public String relayName() {
        return RelayNames.GRAPH_EXPANSION;
    }
}
