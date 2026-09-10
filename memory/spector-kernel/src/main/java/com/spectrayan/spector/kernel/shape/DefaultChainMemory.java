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

import com.spectrayan.spector.kernel.id.MemoryId;
import com.spectrayan.spector.kernel.layout.RegionLayout;
import com.spectrayan.spector.kernel.bundle.RegionRef;

import java.nio.file.Path;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import com.spectrayan.spector.kernel.region.RegionPreamble;

/**
 * Standard default implementation of {@link ChainMemory} backed by a {@link RegionRef}.
 *
 * @param <L> the layout type
 */
public class DefaultChainMemory<L extends RegionLayout> extends AbstractChainMemory<L> {

    public DefaultChainMemory(MemoryId id, L layout, int capacity,
                              RegionRef regionRef, int count,
                              boolean persistent, Path filePath) {
        super(id, layout, capacity, regionRef, count, persistent, filePath);
    }

    public DefaultChainMemory(MemoryId id, L layout, int capacity, long segmentBytes, Path filePath) {
        super(id, layout, capacity, segmentBytes, filePath);
    }

    private int stride() {
        return Math.max(8, layout().recordStride());
    }

    private long nodeOffset(int nodeId) {
        return RegionPreamble.PREAMBLE_BYTES + (long) nodeId * stride();
    }

    @Override
    public void link(int nodeId, int nextId) {
        if (nodeId < 0 || nodeId >= capacity()) {
            throw new IndexOutOfBoundsException("Node " + nodeId + " out of capacity " + capacity());
        }
        if (nextId < 0 || nextId >= capacity()) {
            throw new IndexOutOfBoundsException("Next " + nextId + " out of capacity " + capacity());
        }
        MemorySegment seg = segment();
        seg.set(ValueLayout.JAVA_INT, nodeOffset(nodeId) + 4, nextId);
        seg.set(ValueLayout.JAVA_INT, nodeOffset(nextId), nodeId);
    }

    @Override
    public int next(int nodeId) {
        if (nodeId < 0 || nodeId >= capacity()) return -1;
        return segment().get(ValueLayout.JAVA_INT, nodeOffset(nodeId) + 4);
    }

    @Override
    public int prev(int nodeId) {
        if (nodeId < 0 || nodeId >= capacity()) return -1;
        return segment().get(ValueLayout.JAVA_INT, nodeOffset(nodeId));
    }

    @Override
    public int head() {
        int cap = capacity();
        for (int i = 0; i < cap; i++) {
            if (isLinked(i) && prev(i) == -1) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int tail() {
        int cap = capacity();
        for (int i = 0; i < cap; i++) {
            if (isLinked(i) && next(i) == -1) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int chainLength() {
        int cap = capacity();
        int count = 0;
        for (int i = 0; i < cap; i++) {
            if (isLinked(i)) count++;
        }
        return count;
    }

    private boolean isLinked(int nodeId) {
        return prev(nodeId) != -1 || next(nodeId) != -1;
    }
}
