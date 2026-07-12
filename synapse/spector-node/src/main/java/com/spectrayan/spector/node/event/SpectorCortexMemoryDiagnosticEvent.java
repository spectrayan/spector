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

/** Cortex memory diagnostic event — periodic system health snapshot. */
public record SpectorCortexMemoryDiagnosticEvent(
        String nodeId, Instant timestamp,
        long offHeapBytes, long pinnedBytes,
        long jvmHeapUsed, long jvmHeapMax,
        long gpuAllocated, long gpuFree,
        long softPageFaults, long hardPageFaults,
        int workingCount, int episodicCount, int semanticCount, int proceduralCount,
        int hebbianEdges, int temporalLinks,
        int entityNodes, int entityEdges,
        int coActivationPairs, int stdpEdges
) implements SpectorNodeEvent {
    @Override public String eventType() { return "cortex.memory.diagnostic"; }
}
