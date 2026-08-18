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
package com.spectrayan.spector.memory.remember.relay;

import com.spectrayan.spector.commons.concurrent.MemoryScope;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.graph.EntityExtractor;
import com.spectrayan.spector.memory.graph.ExtractedEntity;
import com.spectrayan.spector.memory.model.IngestionContext;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pipeline.AsyncEntityExtractionQueue;
import com.spectrayan.spector.memory.pipeline.PostIngestSync;

import java.util.List;
import java.util.Objects;

/**
 * Enriches the cognitive knowledge graph and temporal facts with extracted entities and relations.
 */
public final class KnowledgeGraphEnrichmentRelay implements SynapticRelay<RememberSignal> {

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

        final IngestionContext context = signal.context();
        final long epochSeconds = signal.timestampMs() / 1000;
        final String tsid = MemoryScope.sessionId();
        final String nsid = MemoryScope.namespaceId();

        if (context != null && context.hasEntities()) {
            // Pre-extracted entities from IngestionContext
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
