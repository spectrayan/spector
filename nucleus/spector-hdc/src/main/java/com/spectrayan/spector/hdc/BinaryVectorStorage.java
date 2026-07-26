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
package com.spectrayan.spector.hdc;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/**
 * Off-heap binary vector storage using Panama FFM.
 * Provides cache-line aligned storage for high-performance SIMD access.
 */
public final class BinaryVectorStorage implements AutoCloseable {

    private static final int CACHE_LINE_SIZE = 64;
    
    private final int capacity;
    private final int dimensions;
    private final long bytesPerVector;
    private final Arena arena;
    private final MemorySegment segment;

    /**
     * Creates a new BinaryVectorStorage.
     * @param capacity the maximum number of vectors to store
     * @param dimensions the dimensionality of each vector
     */
    public BinaryVectorStorage(int capacity, int dimensions) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        if (dimensions <= 0) {
            throw new IllegalArgumentException("Dimensions must be positive");
        }
        
        this.capacity = capacity;
        this.dimensions = dimensions;
        
        // bytesPerVector = ((dimensions + 63) / 64) * 8 (round to long boundary)
        this.bytesPerVector = ((dimensions + 63) / 64) * 8L;
        
        this.arena = Arena.ofShared();
        
        long totalBytes = capacity * bytesPerVector;
        this.segment = arena.allocate(totalBytes, CACHE_LINE_SIZE);
    }

    /**
     * Stores a Hypervector at the given index.
     * @param index the index to store the vector at
     * @param v the Hypervector to store
     */
    public void putVector(int index, Hypervector v) {
        checkIndex(index);
        Objects.requireNonNull(v, "Vector cannot be null");
        if (v.dimensions() != dimensions) {
            throw new IllegalArgumentException("Vector dimensions do not match storage dimensions");
        }
        
        long[] words = v.words();
        long offset = index * bytesPerVector;
        MemorySegment.copy(MemorySegment.ofArray(words), 0, segment, offset, words.length * 8L);
    }

    /**
     * Gets a zero-copy slice of the stored vector.
     * @param index the index of the vector
     * @return a MemorySegment slice
     */
    public MemorySegment getSlice(int index) {
        checkIndex(index);
        long offset = index * bytesPerVector;
        return segment.asSlice(offset, bytesPerVector);
    }

    /**
     * Reads a stored vector back as a Hypervector.
     * @param index the index of the vector
     * @return a new Hypervector instance
     */
    public Hypervector getVector(int index) {
        checkIndex(index);
        long offset = index * bytesPerVector;
        
        int wordCount = (int) (bytesPerVector / 8);
        long[] words = new long[wordCount];
        MemorySegment.copy(segment, offset, MemorySegment.ofArray(words), 0, wordCount * 8L);
        
        return Hypervector.fromBits(words, dimensions);
    }

    public int capacity() {
        return capacity;
    }

    public int dimensions() {
        return dimensions;
    }

    @Override
    public void close() {
        arena.close();
    }
    
    private void checkIndex(int index) {
        if (index < 0 || index >= capacity) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for capacity " + capacity);
        }
    }
}
