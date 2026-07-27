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
import com.spectrayan.spector.memory.kernel.MemoryLayout;
import com.spectrayan.spector.memory.kernel.MemoryShape;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.lang.foreign.ValueLayout;

/**
 * Abstract base class for memory structures shaped as records.
 *
 * <p>Extends {@link AbstractMemory} to provide array-like, stride-based
 * indexed access to memory records.</p>
 *
 * @param <L> the type of memory layout used by this memory
 */
public abstract class AbstractRecordMemory<L extends MemoryLayout> extends AbstractMemory<L> implements RecordMemory<L> {

    /**
     * Volatile constructor — allocates memory off-heap without a backing file.
     *
     * @param id           the unique identifier for this memory
     * @param layout       the layout configuration
     * @param capacity     the maximum number of records
     * @param segmentBytes the total bytes to allocate
     */
    protected AbstractRecordMemory(MemoryId id, L layout, int capacity, long segmentBytes) {
        super(id, layout, capacity, segmentBytes);
    }

    /**
     * File-backed constructor — creates or opens a persistent memory-mapped file.
     *
     * @param id           the unique identifier for this memory
     * @param layout       the layout configuration
     * @param capacity     the maximum number of records
     * @param segmentBytes the total data bytes (excluding header)
     * @param filePath     the path to the backing file
     */
    protected AbstractRecordMemory(MemoryId id, L layout, int capacity, long segmentBytes, Path filePath) {
        super(id, layout, capacity, segmentBytes, filePath);
    }

    /**
     * Wrapping constructor — adopts a pre-made Arena and segment.
     *
     * @param id          the unique identifier for this memory
     * @param layout      the layout configuration
     * @param capacity    the maximum number of records
     * @param arena       the pre-made arena (caller transfers ownership)
     * @param segment     the pre-made segment (must belong to the arena)
     * @param count       the initial record count
     * @param persistent  whether this memory is file-backed
     * @param filePath    the file path (null for volatile)
     * @param fileChannel the file channel (null for volatile)
     */
    protected AbstractRecordMemory(MemoryId id, L layout, int capacity,
                                   Arena arena, MemorySegment segment, int count,
                                   boolean persistent, Path filePath,
                                   FileChannel fileChannel) {
        super(id, layout, capacity, arena, segment, count, persistent, filePath, fileChannel);
    }

    @Override
    public MemoryShape shape() {
        return MemoryShape.RECORD;
    }

    @Override
    public long recordOffset(long recordId) {
        return dataOffset() + recordId * layout.recordStride();
    }

    @Override
    public long write(long recordId, MemorySegment recordBytes) {
        if (recordId < 0 || recordId >= capacity) {
            throw new IndexOutOfBoundsException("Record ID out of bounds: " + recordId);
        }
        
        if (wal != null && !bypassWal) {
            byte[] bytes = new byte[layout.recordStride()];
            MemorySegment.copy(recordBytes, 0, MemorySegment.ofArray(bytes), 0, layout.recordStride());
            wal.appendRecordWrite(id.toString(), recordId, bytes);
        }

        long offset = recordOffset(recordId);
        MemorySegment.copy(recordBytes, 0, segment, offset, layout.recordStride());
        
        if (recordId >= count) {
            count = (int) recordId + 1;
            persistCount();
        }
        publishVisible();
        return offset;
    }

    @Override
    public void read(long recordId, MemorySegment dest) {
        if (recordId < 0 || recordId >= capacity) {
            throw new IndexOutOfBoundsException("Record ID out of bounds: " + recordId);
        }
        
        long offset = recordOffset(recordId);
        MemorySegment.copy(segment, offset, dest, 0, layout.recordStride());
    }
}
