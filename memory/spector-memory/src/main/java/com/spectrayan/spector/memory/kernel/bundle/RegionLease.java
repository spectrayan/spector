/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.kernel.bundle;

import com.spectrayan.spector.memory.kernel.region.RegionId;

import java.lang.foreign.MemorySegment;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An active lease on a memory region slice within a bundle, preventing unmapping
 * and arena closure while held.
 *
 * <p>Batch operations holding a resolved region (such as scans, cursors, checkpoints,
 * or compactions) acquire a lease to ensure the backing {@link java.lang.foreign.Arena}
 * is not closed concurrently by dynamic region growth ({@code growRegion}).</p>
 *
 * <p>Leases are acquired once per batch operation (not per record) and MUST be closed
 * upon completion, typically using a try-with-resources statement:</p>
 * <pre>{@code
 * try (RegionLease lease = regionRef.lease()) {
 *     MemorySegment slab = lease.slab();
 *     // scan records safely
 * }
 * }</pre>
 *
 * @see RegionRef#lease()
 * @see RuntimeBundle#lease(RegionId)
 */
public final class RegionLease implements AutoCloseable {

    private final MemorySegment slab;
    private final Runnable releaseAction;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public RegionLease(MemorySegment slab, Runnable releaseAction) {
        this.slab = Objects.requireNonNull(slab, "slab");
        this.releaseAction = Objects.requireNonNull(releaseAction, "releaseAction");
    }

    /**
     * Returns the memory segment slice held valid for the lifetime of this lease.
     *
     * @return the pinned MemorySegment slice
     * @throws IllegalStateException if this lease has already been closed
     */
    public MemorySegment slab() {
        if (closed.get()) {
            throw new IllegalStateException("Region lease has already been closed");
        }
        return slab;
    }

    /**
     * Alias for {@link #slab()} for segment-oriented callers.
     *
     * @return the pinned MemorySegment slice
     * @throws IllegalStateException if this lease has already been closed
     */
    public MemorySegment segment() {
        return slab();
    }

    /**
     * Returns true if this lease has been closed.
     */
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            releaseAction.run();
        }
    }
}
