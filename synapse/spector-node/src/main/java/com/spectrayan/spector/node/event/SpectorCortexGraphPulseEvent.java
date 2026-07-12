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
) implements SpectorNodeEvent {
    @Override public String eventType() { return "cortex.graph.pulse"; }
}
