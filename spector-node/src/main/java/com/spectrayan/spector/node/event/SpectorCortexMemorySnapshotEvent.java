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
 * Cortex memory snapshot event — emitted before and after each reflect()
 * consolidation cycle, enabling the memory diff view.
 *
 * <p>A pair of pre/post events share the same {@code reflectCycleId}
 * so the dashboard can compute deltas (edges removed, nodes pruned, etc.).</p>
 *
 * @param nodeId              node that performed the reflection
 * @param timestamp           when the snapshot was taken
 * @param phase               "pre-reflect" or "post-reflect"
 * @param reflectCycleId      unique ID linking the pre/post pair
 * @param hebbianEdgeCount    current count of Hebbian association edges
 * @param temporalLinkCount   current count of temporal chain links
 * @param entityNodeCount     current count of entity graph nodes
 * @param entityEdgeCount     current count of entity graph edges
 * @param offHeapBytes        off-heap memory usage in bytes
 * @param tombstoneCount      current count of tombstoned (soft-deleted) memories
 * @param coActivationPairs   current co-activation pair count
 * @param stdpEdges           current STDP (spike-timing-dependent plasticity) edge count
 */
public record SpectorCortexMemorySnapshotEvent(
        String nodeId, Instant timestamp,
        String phase, String reflectCycleId,
        int hebbianEdgeCount, int temporalLinkCount,
        int entityNodeCount, int entityEdgeCount,
        long offHeapBytes, int tombstoneCount,
        int coActivationPairs, int stdpEdges
) implements SpectorNodeEvent {
    @Override public String eventType() { return "cortex.memory.snapshot"; }
}
