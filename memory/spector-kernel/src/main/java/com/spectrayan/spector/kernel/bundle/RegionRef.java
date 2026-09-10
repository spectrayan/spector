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

import java.lang.foreign.MemorySegment;
import java.util.Objects;

/**
 * Dynamic reference to a region slice within a bundle.
 *
 * <p>Holds {@code (bundle, regionId, generation)} and resolves dynamically
 * via the bundle's optimistic-read fast path rather than capturing an
 * immutable, closeable {@link MemorySegment}. When a runtime region grows
 * or remaps, subsequent calls to {@link #resolve()} automatically return
 * the new live segment without caller action.</p>
 *
 * @see AbstractBundle
 */
public final class RegionRef {

    private final AbstractBundle bundle;
    private final RegionId id;
    private volatile int generation;

    public RegionRef(AbstractBundle bundle, RegionId id) {
        this.bundle = Objects.requireNonNull(bundle, "bundle");
        this.id = Objects.requireNonNull(id, "id");
        this.generation = bundle.generation(id);
    }

    /**
     * Resolves the current live memory segment for this region from the bundle.
     *
     * @return the live MemorySegment slice
     */
    public MemorySegment resolve() {
        return bundle.currentSlice(id);
    }

    /**
     * Returns the current generation of this region from the bundle.
     */
    public int generation() {
        return bundle.generation(id);
    }

    /**
     * Returns the region identifier.
     */
    public RegionId id() {
        return id;
    }

    /**
     * Returns the owning bundle.
     */
    public AbstractBundle bundle() {
        return bundle;
    }

    /**
     * Returns the owning bundle's file path.
     */
    public java.nio.file.Path bundlePath() {
        return bundle.bundlePath();
    }

    /**
     * Acquires an active lease on this region, preventing unmapping and arena closure
     * during the lease's lifetime.
     *
     * @return an AutoCloseable RegionLease
     */
    public RegionLease lease() {
        return bundle.lease(id);
    }

    /**
     * Ensures that this region has at least the required capacity in bytes.
     * If the current allocated capacity is less, the region is grown and remapped.
     *
     * @param requiredBytes minimum capacity in bytes
     */
    /**
     * Reads the checkpoint high-water mark if this region holds checkpoint metadata.
     * @return the checkpoint HWM, or -1 if invalid or not set
     */
    public long readCheckpointHwm() {
        MemorySegment slice = resolve();
        if (slice == null || slice.byteSize() < 16) return -1;
        int magic = slice.get(java.lang.foreign.ValueLayout.JAVA_INT, 0);
        if (magic != 0x434B5054) return -1;
        int version = slice.get(java.lang.foreign.ValueLayout.JAVA_INT, 4);
        if (version != 1) return -1;
        return slice.get(java.lang.foreign.ValueLayout.JAVA_LONG, 8);
    }

    /**
     * Writes the checkpoint high-water mark to this region.
     * @param hwm WAL high-water mark
     */
    public void writeCheckpointHwm(long hwm) {
        MemorySegment slice = resolve();
        if (slice != null && slice.byteSize() >= 16) {
            slice.set(java.lang.foreign.ValueLayout.JAVA_INT, 0, 0x434B5054);
            slice.set(java.lang.foreign.ValueLayout.JAVA_INT, 4, 1);
            slice.set(java.lang.foreign.ValueLayout.JAVA_LONG, 8, hwm);
            slice.force();
        }
    }

    public void ensureCapacity(long requiredBytes) {
        bundle.ensureCapacity(id, requiredBytes);
    }

    @Override
    public String toString() {
        return "RegionRef[" + id + "@gen" + generation() + "]";
    }
}
