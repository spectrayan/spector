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
package com.spectrayan.spector.kernel.bundle;

import com.spectrayan.spector.kernel.region.RegionId;

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

    /**
     * Acquires an active {@link RegionLease} on the specified region slice,
     * ensuring the backing arena is not closed concurrently.
     *
     * @param id the region identifier
     * @return an AutoCloseable RegionLease
     */
    RegionLease lease(RegionId id);

    /**
     * Ensures that the specified region has at least the required capacity in bytes.
     *
     * @param id the region identifier
     * @param requiredBytes minimum capacity in bytes
     */
    default void ensureCapacity(RegionId id, long requiredBytes) {
        // Default no-op for fixed bundles
    }
}
