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

/**
 * Abstract base class for memory structures shaped as compound hash tables.
 *
 * <p>Unlike {@link AbstractRecordMemory}, which stores a uniform array of fixed-stride
 * records, a hash-table memory hosts one or more heterogeneous open-addressing hash
 * tables with independent slot sizes behind a sub-header. There is no
 * {@code recordOffset()} or {@code read(recordId)} — access is through raw
 * segment slicing into sub-table regions.</p>
 *
 * <h3>Biological Analog</h3>
 * <p>In Spector Memory, the co-activation tracker stores synaptic tag co-occurrence
 * counts (undirected Hebbian) and STDP directed edges in two separate hash tables
 * within a single off-heap region. This mirrors how the brain maintains both
 * association strength and temporal prediction in the same synaptic complex.</p>
 *
 * <p>Introduced as part of ADR-0009 (Cross-Capture Graph &amp; CoActivation Kernel
 * Integration) to replace the {@code stride=1} hack in {@code CoActivationRecordMemory}
 * with an honest kernel shape.</p>
 *
 * @param <L> the type of memory layout used by this memory
 * @see MemoryShape#HASHTABLE
 */
public abstract class AbstractHashTableMemory<L extends MemoryLayout> extends AbstractMemory<L> {

    protected AbstractHashTableMemory(MemoryId id, L layout, int capacity, long segmentBytes) {
        super(id, layout, capacity, segmentBytes);
    }

    protected AbstractHashTableMemory(MemoryId id, L layout, int capacity, long segmentBytes, Path filePath) {
        super(id, layout, capacity, segmentBytes, filePath);
    }

    protected AbstractHashTableMemory(MemoryId id, L layout, int capacity,
                                      Arena arena, MemorySegment segment, int count,
                                      boolean persistent, Path filePath,
                                      FileChannel fileChannel) {
        super(id, layout, capacity, arena, segment, count, persistent, filePath, fileChannel);
    }

    protected AbstractHashTableMemory(MemoryId id, L layout, int capacity,
                                      Arena arena, MemorySegment segment, int count,
                                      boolean persistent, Path filePath,
                                      FileChannel fileChannel, boolean bundleManaged) {
        super(id, layout, capacity, arena, segment, count, persistent, filePath, fileChannel, bundleManaged);
    }

    @Override
    public MemoryShape shape() {
        return MemoryShape.HASHTABLE;
    }

    /**
     * Returns a sub-slice of the data region for a hash table at the given offset and size.
     *
     * <p>This is the primary access pattern for hash-table memories: the caller
     * knows the byte offset and length of each sub-table within the data region
     * (typically computed from the layout) and carves out a slice for the
     * hash table implementation to operate on.</p>
     *
     * @param offset byte offset from the start of the data region
     * @param size   number of bytes in the sub-table slice
     * @return a {@link MemorySegment} view of the sub-table region
     */
    protected MemorySegment tableSlice(long offset, long size) {
        return segment.asSlice(dataOffset() + offset, size);
    }
}
