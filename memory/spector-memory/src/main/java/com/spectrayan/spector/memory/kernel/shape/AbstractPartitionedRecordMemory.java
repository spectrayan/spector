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

import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstract base class for partitioned record storage structures.
 * Encapsulates active partition tracking, historical partition freezing,
 * segment delegation, and multi-partition iteration.
 *
 * @param <L> the record layout type
 */
public abstract class AbstractPartitionedRecordMemory<L extends MemoryLayout>
        extends AbstractMemory<L> implements PartitionedRecordMemory<L> {

    protected volatile RecordMemory<L> activePartition;
    protected final List<RecordMemory<L>> historicalPartitions = new ArrayList<>();

    protected AbstractPartitionedRecordMemory(MemoryId id, L layout, int capacity, long segmentBytes) {
        super(id, layout, capacity, segmentBytes);
    }

    protected AbstractPartitionedRecordMemory(MemoryId id, L layout, int capacity, long segmentBytes, Path filePath) {
        super(id, layout, capacity, segmentBytes, filePath);
    }

    protected AbstractPartitionedRecordMemory(MemoryId id, L layout, int capacity,
                                              Arena arena, MemorySegment segment, int count,
                                              boolean persistent, Path filePath, FileChannel fileChannel) {
        super(id, layout, capacity, arena, segment, count, persistent, filePath, fileChannel);
    }

    @Override
    public MemoryShape shape() {
        return MemoryShape.PARTITIONED;
    }

    @Override
    public RecordMemory<L> activePartition() {
        return activePartition;
    }

    @Override
    public List<RecordMemory<L>> historicalPartitions() {
        return Collections.unmodifiableList(historicalPartitions);
    }

    @Override
    public long write(long recordId, MemorySegment recordBytes) {
        RecordMemory<L> active = activePartition();
        if (active != null) {
            return active.write(recordId, recordBytes);
        }
        return -1L;
    }

    @Override
    public void read(long recordId, MemorySegment dest) {
        RecordMemory<L> active = activePartition();
        if (active != null) {
            active.read(recordId, dest);
        }
    }

    @Override
    public long recordOffset(long recordId) {
        RecordMemory<L> active = activePartition();
        if (active != null) {
            return active.recordOffset(recordId);
        }
        return -1L;
    }
}
