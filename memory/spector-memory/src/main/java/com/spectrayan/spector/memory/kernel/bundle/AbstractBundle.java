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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;

/**
 * Common contract implemented by bundles ({@link RuntimeBundle} and {@link PartitionBundle}).
 *
 * <p>Provides package-private region slice resolution and generation tracking for {@link RegionRef},
 * as well as implementing {@link RegionOpener} to open typed shape memories.</p>
 */
public interface AbstractBundle extends RegionOpener, AutoCloseable {

    /**
     * Resolves the current live memory segment slice for the specified region.
     *
     * @param id the region identifier
     * @return live MemorySegment slice for the region
     */
    MemorySegment currentSlice(RegionId id);

    /**
     * Returns the current generation of the specified region.
     * Incremented whenever a region is grown or remapped.
     *
     * @param id the region identifier
     * @return current generation number
     */
    int generation(RegionId id);

    /**
     * Returns the bundle file path, or null for in-memory heap bundles.
     */
    Path bundlePath();

    /**
     * Returns a {@link RegionRef} bound to this bundle and the specified region.
     */
    default RegionRef regionRef(RegionId id) {
        return new RegionRef(this, id);
    }
}
