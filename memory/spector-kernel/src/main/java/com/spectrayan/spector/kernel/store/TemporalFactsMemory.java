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

import com.spectrayan.spector.kernel.id.MemoryId;
import com.spectrayan.spector.kernel.id.SystemMemoryId;
import com.spectrayan.spector.kernel.layout.TemporalFactLayout;
import com.spectrayan.spector.kernel.shape.AbstractAppendMemory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.shape.MemoryShape;
import java.nio.file.Path;

/**
 * Durable append-only memory kernel store for 64-byte temporal fact records,
 * extending {@link AbstractAppendMemory} directly.
 */
public final class TemporalFactsMemory extends AbstractAppendMemory<TemporalFactLayout> {

    private static final MemoryId MEMORY_ID = SystemMemoryId.TEMPORAL_FACTS.id();

    /**
     * Creates an in-memory (heap) TemporalFactsMemory store with default capacity (64 KB).
     */
    public TemporalFactsMemory() {
        this(64L * 1024);
    }

    /**
     * Creates an in-memory (heap) TemporalFactsMemory store.
     *
     * @param initialSize initial byte capacity
     */
    public TemporalFactsMemory(long initialSize) {
        super(MEMORY_ID, new TemporalFactLayout(), 0, initialSize);
    }

    /**
     * Creates a file-backed (mmap) TemporalFactsMemory store.
     *
     * @param filePath    path to the facts data file
     * @param initialSize byte capacity for new files
     */
    public TemporalFactsMemory(Path filePath, long initialSize) {
        super(MEMORY_ID, new TemporalFactLayout(), 0, initialSize, filePath);
    }

    public static TemporalFactsMemory fromBundle(Arena arena, MemorySegment regionSlice, Path bundlePath, boolean isNew) {
        return new TemporalFactsMemory(arena, regionSlice, bundlePath, isNew);
    }

    public static TemporalFactsMemory fromRegionRef(com.spectrayan.spector.kernel.bundle.RegionRef regionRef, Path bundlePath, boolean isNew) {
        return new TemporalFactsMemory(regionRef, bundlePath, isNew);
    }

    private TemporalFactsMemory(Arena arena, MemorySegment regionSlice, Path bundlePath, boolean isNew) {
        super(MEMORY_ID, new TemporalFactLayout(), 0, arena, regionSlice,
              isNew ? 0 : (int) RegionPreamble.readCount(regionSlice, 0L),
              true, bundlePath, null, true); // bundleManaged=true
        if (isNew) {
            long now = System.currentTimeMillis();
            RegionPreamble.write(segment(), 0L, new TemporalFactLayout().schemaVersion(), MemoryShape.APPEND, 0,
                    (int) segment().byteSize(), 0, 0, new TemporalFactLayout().layoutId(), now, now);
        }
    }

    private TemporalFactsMemory(com.spectrayan.spector.kernel.bundle.RegionRef regionRef, Path bundlePath, boolean isNew) {
        super(MEMORY_ID, new TemporalFactLayout(), 0, regionRef,
              isNew ? 0 : (int) RegionPreamble.readCount(regionRef.resolve(), 0L),
              true, bundlePath);
        if (isNew) {
            long now = System.currentTimeMillis();
            RegionPreamble.write(segment(), 0L, new TemporalFactLayout().schemaVersion(), MemoryShape.APPEND, 0,
                    (int) segment().byteSize(), 0, 0, new TemporalFactLayout().layoutId(), now, now);
        }
    }
}
