package com.spectrayan.spector.gpu;

import java.lang.foreign.Arena;
import java.time.Instant;

/**
 * Represents a single GPU device memory allocation tracked by {@link GpuMemoryManager}.
 *
 * @param devicePointer the CUDA device pointer for this allocation
 * @param sizeBytes     size of the allocation in bytes
 * @param arena         the Arena scope that owns this allocation's lifetime
 * @param allocatedAt   timestamp when this allocation was made
 */
public record GpuAllocation(
        long devicePointer,
        long sizeBytes,
        Arena arena,
        Instant allocatedAt
) {}
