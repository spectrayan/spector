package com.spectrayan.spector.gpu;

/**
 * Metrics exposed by {@link PanamaMemoryDetector} via the monitoring API.
 *
 * @param totalSegments           total number of currently tracked segments
 * @param totalBytes              total bytes across all tracked segments
 * @param thresholdExceedingCount number of segments that have exceeded the lifetime threshold
 * @param untrackedSegmentCount   number of segments that could not be tracked (hook attachment failed)
 */
public record AllocationMetrics(
        int totalSegments,
        long totalBytes,
        int thresholdExceedingCount,
        long untrackedSegmentCount
) {}
