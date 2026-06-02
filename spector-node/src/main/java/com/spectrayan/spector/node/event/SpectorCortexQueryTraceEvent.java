package com.spectrayan.spector.node.event;

import java.time.Instant;

/**
 * Cortex query trace event — emitted after each recall pipeline execution.
 * Shows per-phase record survival counts for the scoring funnel visualization.
 */
public record SpectorCortexQueryTraceEvent(
        String nodeId, Instant timestamp,
        String queryText, String cognitiveProfile,
        long synapticTagMask,
        int totalRecords, int afterTombstone, int afterTagGate,
        int afterValence, int afterDecay, int afterVectorDistance, int finalTopK,
        int hebbianActivated, int temporalLinked, int entityDiscovered,
        long latencyMicros
) implements SpectorEvent {
    @Override public String eventType() { return "cortex.query.trace"; }
}
