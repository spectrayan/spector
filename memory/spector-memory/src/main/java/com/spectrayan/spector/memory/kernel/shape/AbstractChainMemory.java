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
package com.spectrayan.spector.memory.kernel.shape;

import com.spectrayan.spector.memory.kernel.AbstractMemory;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.RegionLayout;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

/**
 * Abstract base class for memory structures shaped as sequential linked prev/next chains.
 *
 * <p>Extends {@link AbstractMemory} and implements {@link ChainMemory} to provide standard
 * off-heap lifecycle, memory segments, and header management for chain-shaped memories.</p>
 *
 * @param <L> the type of memory layout used by this memory
 * @see ChainMemory
 * @see MemoryShape#CHAIN
 */
public abstract class AbstractChainMemory<L extends RegionLayout>
        extends AbstractMemory<L> implements ChainMemory<L> {

    protected AbstractChainMemory(MemoryId id, L layout, int capacity, long segmentBytes) {
        super(id, layout, capacity, segmentBytes);
    }

    protected AbstractChainMemory(MemoryId id, L layout, int capacity, long segmentBytes, Path filePath) {
        super(id, layout, capacity, segmentBytes, filePath);
    }

    protected AbstractChainMemory(MemoryId id, L layout, int capacity,
                                  Arena arena, MemorySegment segment, int count,
                                  boolean persistent, Path filePath,
                                  FileChannel fileChannel) {
        super(id, layout, capacity, arena, segment, count, persistent, filePath, fileChannel);
    }

    protected AbstractChainMemory(MemoryId id, L layout, int capacity,
                                  Arena arena, MemorySegment segment, int count,
                                  boolean persistent, Path filePath,
                                  FileChannel fileChannel, boolean bundleManaged) {
        super(id, layout, capacity, arena, segment, count, persistent, filePath, fileChannel, bundleManaged);
    }

    protected AbstractChainMemory(MemoryId id, L layout, int capacity,
                                   com.spectrayan.spector.memory.kernel.bundle.RegionRef regionRef, int count,
                                   boolean persistent, Path filePath) {
        super(id, layout, capacity, regionRef, count, persistent, filePath);
    }

    @Override
    public MemoryShape shape() {
        return MemoryShape.CHAIN;
    }
}
