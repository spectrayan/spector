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
import com.spectrayan.spector.kernel.api.MemoryType;

import com.spectrayan.spector.commons.concurrent.MemoryScope;
import com.spectrayan.spector.commons.pathway.IdempotentRelay;
import com.spectrayan.spector.commons.pathway.InterruptibleRelay;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.graph.EntityExtractor;
import com.spectrayan.spector.memory.graph.ExtractedEntity;
import com.spectrayan.spector.memory.model.RememberContext;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pathway.pipeline.AsyncEntityExtractionQueue;
import com.spectrayan.spector.memory.pathway.pipeline.PostIngestSync;

import java.util.List;
import java.util.Objects;

/**
 * Enriches the cognitive knowledge graph and temporal facts with extracted entities and relations.
 */
/*
 * Deliberately NOT IdempotentRelay: ADR-0036 §14 tabled this stage as
 * "retry TRANSIENT ×2", but the relay does more than an LLM call — it also runs
 * syncEntityExtraction/syncTemporalFacts, which mutate the entity and temporal
 * graphs. Re-running after a partial failure can duplicate edges. It therefore
 * gets a timeout and a breaker (both safe) but no retry. Documented deviation.
 */
public final class KnowledgeGraphEnrichmentRelay
        implements SynapticRelay<RememberSignal>, InterruptibleRelay {

    private final PostIngestSync postIngestSync;
    private final AsyncEntityExtractionQueue asyncEntityExtractionQueue;
    private final EntityExtractor entityExtractor;

    public KnowledgeGraphEnrichmentRelay(
            final PostIngestSync postIngestSync,
            final AsyncEntityExtractionQueue asyncEntityExtractionQueue,
            final EntityExtractor entityExtractor) {
        this.postIngestSync = Objects.requireNonNull(postIngestSync, "postIngestSync cannot be null");
        this.asyncEntityExtractionQueue = asyncEntityExtractionQueue;
        this.entityExtractor = entityExtractor;
    }

    @Override
    public boolean transmit(final RememberSignal signal) {
        final int memoryIdx = signal.graphSlot();
        if (memoryIdx < 0) {
            return true;
        }

        // Episodic memories are raw event logs and should not generate entity graph links or KG extractions
        if (signal.type() == com.spectrayan.spector.kernel.api.MemoryType.EPISODIC) {
            return true;
        }

        final RememberContext context = signal.rememberContext();
        final long epochSeconds = signal.timestampMs() / 1000;
        final String tsid = MemoryScope.sessionId();
        final String nsid = MemoryScope.namespaceId();

        if (context != null && context.hasEntities()) {
            // Pre-extracted entities from RememberContext
            postIngestSync.syncPreExtractedEntities(context.entities(), memoryIdx, signal.id());
            postIngestSync.syncTemporalFacts(context.entities(), memoryIdx, signal.id(), epochSeconds);
        } else if (asyncEntityExtractionQueue != null && entityExtractor != null && entityExtractor.isAvailable()) {
            // Asynchronous queue submission
            asyncEntityExtractionQueue.submit(signal.id(), signal.text(), memoryIdx, epochSeconds, tsid, nsid);
        } else if (entityExtractor != null && entityExtractor.isAvailable()) {
            // Synchronous extraction fallback
            final List<ExtractedEntity> extractedEntities = postIngestSync.syncEntityExtraction(signal.id(), signal.text(), memoryIdx);
            postIngestSync.syncTemporalFacts(extractedEntities, memoryIdx, signal.id(), epochSeconds);
        }

        return true;
    }

    @Override
    public String relayName() {
        return RelayNames.KG_ENRICHMENT;
    }
}
