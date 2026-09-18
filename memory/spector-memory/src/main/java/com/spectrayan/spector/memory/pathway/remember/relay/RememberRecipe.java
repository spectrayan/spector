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
package com.spectrayan.spector.memory.pathway.remember.relay;

import com.spectrayan.spector.commons.pathway.ErrorPolicy;
import com.spectrayan.spector.commons.pathway.PathwayComposer;
import com.spectrayan.spector.commons.pathway.PathwayRecipe;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.pathway.PathwayResilience;
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
        // CORTICAL_WRITE deliberately carries NO timeout and NO retry (ADR-0036 §7.3):
        // it writes to mmap'd MemorySegment regions plus the WAL, neither of which can
        // observe an interrupt. A budget would report a timeout while the write ran to
        // completion on a detached thread, leaving the outcome saying "skipped" and the
        // bundle saying "written". CorticalWriteTransactionRelay implements neither
        // IdempotentRelay nor InterruptibleRelay, so the composer rejects both at build
        // time — this comment documents the intent, the type system enforces it.
        composer.relay(RelayNames.DEDUP_GUARD,           dedup,    ErrorPolicy.FAIL_FAST)
                .relay(RelayNames.TAG_TRANSDUCTION,      tags,     ErrorPolicy.FAIL_FAST)
                .relay(RelayNames.DOPAMINERGIC_SURPRISE, surprise, ErrorPolicy.FAIL_FAST)
                .relay(RelayNames.CORTICAL_WRITE,        write,    ErrorPolicy.FAIL_FAST)
                .relay(RelayNames.GRAPH_LINKING,         graph,    ErrorPolicy.DEGRADE_GRACEFULLY);

        // KG enrichment may invoke the LLM for entity extraction: 8s budget, shared
        // llm-provider breaker (BYPASS — enrichment is optional), and a 2-permit
        // bulkhead so a burst of ingests cannot saturate the provider. No retry: the
        // relay also mutates the entity/temporal graphs, so re-running after a partial
        // failure can duplicate edges (see the note on KnowledgeGraphEnrichmentRelay).
        composer.stage(RelayNames.KG_ENRICHMENT)
                .relay(kg)
                .policy(ErrorPolicy.DEGRADE_GRACEFULLY)
                .timeoutIfInterruptible(PathwayResilience.LLM_TIMEOUT)
                .breaker(PathwayResilience.llmProvider())
                .bulkhead(PathwayResilience.LLM_PROVIDER, PathwayResilience.llmBulkhead())
                .add();
    }
}
