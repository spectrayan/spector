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

import com.spectrayan.spector.kernel.id.MemoryId;

import com.spectrayan.spector.commons.concurrent.MemoryScope;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.model.RememberContext;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.memory.pathway.pipeline.PostIngestSync;
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

        // 2. Pre-computed edge hints from RememberContext
        final RememberContext context = signal.rememberContext();
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
