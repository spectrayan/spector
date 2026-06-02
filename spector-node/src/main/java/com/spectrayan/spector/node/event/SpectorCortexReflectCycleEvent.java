package com.spectrayan.spector.node.event;

import java.time.Instant;

/** Cortex reflect cycle event — emitted after memory consolidation. */
public record SpectorCortexReflectCycleEvent(
        String nodeId, Instant timestamp,
        int hebbianEdgesDecayed, int hebbianEdgesRemoved,
        double decayFactor, long durationMs
) implements SpectorEvent {
    @Override public String eventType() { return "cortex.reflect.cycle"; }
}
