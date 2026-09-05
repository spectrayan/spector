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
package com.spectrayan.spector.memory.graph.temporal;

import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.SystemMemoryId;
import com.spectrayan.spector.memory.kernel.layout.TemporalFactLayout;
import com.spectrayan.spector.memory.kernel.shape.AbstractAppendMemory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import com.spectrayan.spector.memory.kernel.RegionPreamble;
import com.spectrayan.spector.memory.kernel.MemoryShape;
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
}
