package com.spectrayan.spector.gpu;

import java.time.Duration;
import java.time.Instant;

/**
 * Represents a potential memory leak — a tracked MemorySegment that has remained
 * allocated beyond the configured lifetime threshold.
 *
 * @param segmentId      unique identifier for the tracked segment
 * @param sizeBytes      size of the allocation in bytes
 * @param allocatedAt    timestamp when the segment was created
 * @param elapsedTime    how long the segment has been alive
 * @param allocationSite stack trace captured at the time of allocation
 */
public record LeakCandidate(
        long segmentId,
        long sizeBytes,
        Instant allocatedAt,
        Duration elapsedTime,
        StackTraceElement[] allocationSite
) {}
