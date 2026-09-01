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
import com.spectrayan.spector.memory.model.IngestionContext;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pipeline.PostIngestSync;
import com.spectrayan.spector.memory.session.SessionRegistry;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Links synaptic associative graphs (Hebbian co-activation and temporal sequences) during memory consolidation.
 */
public final class SynapticGraphLinkingRelay implements SynapticRelay<RememberSignal> {

    private final PostIngestSync postIngestSync;
    private final AtomicInteger lastIngestedMemoryIdx;
    private final SessionRegistry sessionRegistry;

    public SynapticGraphLinkingRelay(
            final PostIngestSync postIngestSync,
            final AtomicInteger lastIngestedMemoryIdx,
            final SessionRegistry sessionRegistry) {
        this.postIngestSync = Objects.requireNonNull(postIngestSync, "postIngestSync cannot be null");
        this.lastIngestedMemoryIdx = Objects.requireNonNull(lastIngestedMemoryIdx, "lastIngestedMemoryIdx cannot be null");
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public boolean transmit(final RememberSignal signal) {
        final int memoryIdx = signal.graphSlot();
        if (memoryIdx < 0) {
            return true; // No graph slot allocated
        }

        final String tsid = MemoryScope.sessionId();
        final int sessionIntId = sessionRegistry != null ? sessionRegistry.resolve(tsid) : 0;
        final int previousIdx = lastIngestedMemoryIdx.getAndSet(memoryIdx);

        // 1. Session co-ingestion Hebbian + Temporal linking
        postIngestSync.syncGraphEdges(memoryIdx, previousIdx, sessionIntId);

        // 2. Pre-computed edge hints from IngestionContext
        final IngestionContext context = signal.context();
        if (context != null) {
            if (context.hasHebbianEdges()) {
                postIngestSync.syncHebbianEdgeHints(memoryIdx, signal.id(), context.hebbianEdges());
            }
            if (context.hasTemporalLinks()) {
                postIngestSync.syncTemporalLinkHints(memoryIdx, signal.id(), context.temporalLinks());
            }
        }

        return true;
    }

    @Override
    public String relayName() {
        return RelayNames.GRAPH_LINKING;
    }
}
