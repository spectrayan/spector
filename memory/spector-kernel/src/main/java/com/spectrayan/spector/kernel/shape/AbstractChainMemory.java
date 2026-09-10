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
package com.spectrayan.spector.kernel.shape;

import com.spectrayan.spector.kernel.shape.AbstractMemory;
import com.spectrayan.spector.kernel.id.MemoryId;
import com.spectrayan.spector.kernel.shape.MemoryShape;
import com.spectrayan.spector.kernel.layout.RegionLayout;

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
                                   com.spectrayan.spector.kernel.bundle.RegionRef regionRef, int count,
                                   boolean persistent, Path filePath) {
        super(id, layout, capacity, regionRef, count, persistent, filePath);
    }

    @Override
    public MemoryShape shape() {
        return MemoryShape.CHAIN;
    }
}
