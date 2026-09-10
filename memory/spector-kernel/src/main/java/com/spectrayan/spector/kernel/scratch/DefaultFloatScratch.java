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
package com.spectrayan.spector.kernel.scratch;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Default off-heap {@link FloatScratch} implementation backed by Panama {@link MemorySegment}.
 */
public final class DefaultFloatScratch implements FloatScratch {

    private final int capacity;
    private final long byteSize;
    private final Arena arena;
    private final MemorySegment segment;
    private final Consumer<DefaultFloatScratch> onCloseCallback;
    private volatile boolean closed;

    public DefaultFloatScratch(int capacity) {
        this(capacity, null);
    }

    public DefaultFloatScratch(int capacity, Consumer<DefaultFloatScratch> onCloseCallback) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be non-negative: " + capacity);
        }
        this.capacity = capacity;
        this.byteSize = (long) capacity * ValueLayout.JAVA_FLOAT.byteSize();
        this.arena = Arena.ofShared();
        this.segment = arena.allocate(byteSize, 8);
        this.onCloseCallback = onCloseCallback;
        this.closed = false;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("FloatScratch has already been closed");
        }
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= capacity) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for capacity " + capacity);
        }
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public float get(int index) {
        ensureOpen();
        checkIndex(index);
        return segment.get(ValueLayout.JAVA_FLOAT, (long) index * ValueLayout.JAVA_FLOAT.byteSize());
    }

    @Override
    public void set(int index, float value) {
        ensureOpen();
        checkIndex(index);
        segment.set(ValueLayout.JAVA_FLOAT, (long) index * ValueLayout.JAVA_FLOAT.byteSize(), value);
    }

    @Override
    public void fill(float value) {
        ensureOpen();
        for (int i = 0; i < capacity; i++) {
            segment.set(ValueLayout.JAVA_FLOAT, (long) i * ValueLayout.JAVA_FLOAT.byteSize(), value);
        }
    }

    @Override
    public void copyFrom(float[] src, int srcOffset, int destOffset, int length) {
        ensureOpen();
        Objects.requireNonNull(src, "src array must not be null");
        if (srcOffset < 0 || destOffset < 0 || length < 0
                || srcOffset + length > src.length
                || destOffset + length > capacity) {
            throw new IndexOutOfBoundsException("Invalid range for copyFrom: srcOffset=" + srcOffset
                    + ", destOffset=" + destOffset + ", length=" + length);
        }
        MemorySegment.copy(src, srcOffset, segment, ValueLayout.JAVA_FLOAT, (long) destOffset * ValueLayout.JAVA_FLOAT.byteSize(), length);
    }

    @Override
    public void copyTo(int srcOffset, float[] dest, int destOffset, int length) {
        ensureOpen();
        Objects.requireNonNull(dest, "dest array must not be null");
        if (srcOffset < 0 || destOffset < 0 || length < 0
                || srcOffset + length > capacity
                || destOffset + length > dest.length) {
            throw new IndexOutOfBoundsException("Invalid range for copyTo: srcOffset=" + srcOffset
                    + ", destOffset=" + destOffset + ", length=" + length);
        }
        MemorySegment.copy(segment, ValueLayout.JAVA_FLOAT, (long) srcOffset * ValueLayout.JAVA_FLOAT.byteSize(), dest, destOffset, length);
    }

    @Override
    public float[] toArray() {
        ensureOpen();
        float[] dest = new float[capacity];
        copyTo(0, dest, 0, capacity);
        return dest;
    }

    @Override
    public long byteSize() {
        return byteSize;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            try {
                arena.close();
            } finally {
                if (onCloseCallback != null) {
                    onCloseCallback.accept(this);
                }
            }
        }
    }
}
