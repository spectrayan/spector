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
) implements SpectorEvent {
    @Override public String eventType() { return "cortex.memory.diagnostic"; }
}
