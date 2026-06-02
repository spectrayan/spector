package com.spectrayan.spector.node.event;

import java.time.Instant;

/**
 * Cortex SIMD lane event — emitted per-query with aggregate SIMD execution stats.
 *
 * <p>Reports which SIMD kernel was used, the lane width of the hardware,
 * the number of vectors processed, and total execution time.</p>
 *
 * @param nodeId            node that executed the operation
 * @param timestamp         when the event was generated
 * @param kernelName        similarity kernel used (e.g., "CosineSimilarity")
 * @param laneWidth         SIMD lane width in floats (e.g., 8 for AVX2, 16 for AVX-512)
 * @param vectorsProcessed  total vectors compared during the query
 * @param durationMicros    wall-clock time of the SIMD work in microseconds
 * @param fallbackNanos     time spent in scalar fallback (tail lanes) — 0 if fully vectorized
 */
public record SpectorCortexSimdLaneEvent(
        String nodeId, Instant timestamp,
        String kernelName, int laneWidth,
        int vectorsProcessed, long durationMicros,
        long fallbackNanos
) implements SpectorEvent {
    @Override public String eventType() { return "cortex.simd.lane"; }
}
