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
package com.spectrayan.spector.node.event;

import com.spectrayan.spector.events.NotificationScope;
import java.time.Instant;

/**
 * Cortex query trace event — emitted after each recall pipeline execution.
 * Shows per-phase record survival counts for the scoring funnel visualization.
 *
 * <p>User-scoped: when {@code targetUserId} is set, only that user's SSE
 * connection receives this event via {@link NotificationScope.User}.</p>
 */
public record SpectorCortexQueryTraceEvent(
        String nodeId, Instant timestamp,
        String queryText, String cognitiveProfile,
        long synapticTagMask,
        int totalRecords, int afterTombstone, int afterTagGate,
        int afterValence, int afterDecay, int afterVectorDistance, int finalTopK,
        int hebbianActivated, int temporalLinked, int entityDiscovered,
        long latencyMicros,
        String targetUserId
) implements SpectorNodeEvent {

    /** Backwards-compatible constructor — broadcasts to all SSE clients. */
    public SpectorCortexQueryTraceEvent(
            String nodeId, Instant timestamp,
            String queryText, String cognitiveProfile,
            long synapticTagMask,
            int totalRecords, int afterTombstone, int afterTagGate,
            int afterValence, int afterDecay, int afterVectorDistance, int finalTopK,
            int hebbianActivated, int temporalLinked, int entityDiscovered,
            long latencyMicros) {
        this(nodeId, timestamp, queryText, cognitiveProfile, synapticTagMask,
                totalRecords, afterTombstone, afterTagGate, afterValence,
                afterDecay, afterVectorDistance, finalTopK,
                hebbianActivated, temporalLinked, entityDiscovered,
                latencyMicros, null);
    }

    @Override public String eventType() { return "cortex.query.trace"; }
    @Override public NotificationScope scope() {
        return targetUserId != null ? NotificationScope.user(targetUserId) : NotificationScope.BROADCAST;
    }
}
