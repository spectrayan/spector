package com.spectrayan.spector.node.event;

import java.time.Instant;

/**
 * Cortex graph pulse event — emitted per-query with aggregate spreading
 * activation, temporal chain traversal, and entity BFS statistics.
 *
 * @param nodeId            node that executed the operation
 * @param timestamp         when the event was generated
 * @param nodesVisited      total graph nodes visited during activation
 * @param edgesTraversed    total edges traversed across all graph layers
 * @param maxDepth          maximum activation depth reached
 * @param durationMicros    wall-clock time of the graph scoring phase in microseconds
 */
public record SpectorCortexGraphPulseEvent(
        String nodeId, Instant timestamp,
        int nodesVisited, int edgesTraversed,
        int maxDepth, long durationMicros
) implements SpectorEvent {
    @Override public String eventType() { return "cortex.graph.pulse"; }
}
