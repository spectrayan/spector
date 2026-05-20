package com.spectrayan.spector.gpu;

import java.util.Map;

/**
 * Metrics reported by the {@link GpuMemoryManager} about current GPU memory usage.
 *
 * @param totalAllocatedBytes total number of bytes currently allocated on the device
 * @param activeSegments      number of active (not yet released) memory segments
 * @param segmentSizes        map of allocation ID to size in bytes for each active segment
 */
public record GpuMemoryMetrics(
        long totalAllocatedBytes,
        int activeSegments,
        Map<Long, Long> segmentSizes
) {
    /**
     * Creates a GpuMemoryMetrics with an unmodifiable copy of the segment sizes map.
     */
    public GpuMemoryMetrics {
        segmentSizes = Map.copyOf(segmentSizes);
    }
}
