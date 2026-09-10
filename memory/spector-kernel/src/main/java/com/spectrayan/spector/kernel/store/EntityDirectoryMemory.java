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
package com.spectrayan.spector.kernel.store;

import com.spectrayan.spector.kernel.bundle.RegionRef;
import com.spectrayan.spector.kernel.region.RegionPreamble;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

/**
 * Composite memory spanning {@code ENTITY_DIRECTORY} and {@code ENTITY_NAMES} regions (R16.9).
 *
 * <p>Preserves the pairing invariant of the entity directory node slab and its accompanying
 * name index / adjacency slab under a single kernel accessor, preventing callers from having
 * to manage two separate region references.</p>
 */
public class EntityDirectoryMemory implements AutoCloseable {

    private final RegionRef directoryRegion;
    private final RegionRef namesRegion;

    public EntityDirectoryMemory(RegionRef directoryRegion, RegionRef namesRegion) {
        this.directoryRegion = Objects.requireNonNull(directoryRegion, "directoryRegion cannot be null");
        this.namesRegion = Objects.requireNonNull(namesRegion, "namesRegion cannot be null");
    }

    /**
     * Returns the region reference for {@code RegionId.ENTITY_DIRECTORY}.
     */
    public RegionRef directoryRegion() {
        return directoryRegion;
    }

    /**
     * Returns the region reference for {@code RegionId.ENTITY_NAMES}.
     */
    public RegionRef namesRegion() {
        return namesRegion;
    }

    /**
     * Returns the allocated entity capacity recorded in the directory preamble.
     */
    public int entityCapacity() {
        MemorySegment seg = directoryRegion.resolve();
        return seg != null ? (int) RegionPreamble.readCapacity(seg, 0L) : 0;
    }

    /**
     * Returns the count of active entities recorded in the directory preamble.
     */
    public int entityCount() {
        MemorySegment seg = directoryRegion.resolve();
        return seg != null ? (int) RegionPreamble.readCount(seg, 0L) : 0;
    }

    /**
     * Flushes changes to backing storage if applicable.
     */
    public void flush() {
        MemorySegment dir = directoryRegion.resolve();
        if (dir != null) {
            dir.force();
        }
        MemorySegment names = namesRegion.resolve();
        if (names != null) {
            names.force();
        }
    }

    @Override
    public void close() {
        flush();
    }
}
