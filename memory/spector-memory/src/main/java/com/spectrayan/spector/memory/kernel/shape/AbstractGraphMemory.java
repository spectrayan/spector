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
import java.util.PrimitiveIterator;
import java.util.stream.IntStream;

/**
 * Abstract base class for Compressed Sparse Row (CSR) adjacency graph memory.
 * Manages vertex index slabs, adjacency edge lists, standard 64-byte file header (0x534D4B4D),
 * and SWMR concurrency.
 *
 * @param <L> the graph memory layout type
 */
public abstract class AbstractGraphMemory<L extends MemoryLayout>
        extends AbstractMemory<L> implements GraphMemory<L> {

    protected AbstractGraphMemory(MemoryId id, L layout, int vertexCapacity, long segmentBytes) {
        super(id, layout, vertexCapacity, segmentBytes);
    }

    protected AbstractGraphMemory(MemoryId id, L layout, int vertexCapacity, long segmentBytes, Path filePath) {
        super(id, layout, vertexCapacity, segmentBytes, filePath);
    }

    protected AbstractGraphMemory(MemoryId id, L layout, int vertexCapacity,
                                 Arena arena, MemorySegment segment, int count,
                                 boolean persistent, Path filePath, FileChannel fileChannel) {
        super(id, layout, vertexCapacity, arena, segment, count, persistent, filePath, fileChannel);
    }

    @Override
    public MemoryShape shape() {
        return MemoryShape.GRAPH;
    }

    @Override
    public int addEdge(int fromNode, int toNode, MemorySegment edgeBytes) {
        return -1;
    }

    @Override
    public void removeEdge(int edgeId) {}

    @Override
    public PrimitiveIterator.OfInt neighbours(int nodeId) {
        return IntStream.empty().iterator();
    }

    @Override
    public int edgeCount() {
        return 0;
    }

    @Override
    public int nodeCount() {
        return size();
    }
}
