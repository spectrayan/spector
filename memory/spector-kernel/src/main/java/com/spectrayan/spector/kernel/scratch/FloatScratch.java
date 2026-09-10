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

/**
 * Off-heap reusable scratch buffer for floating-point calculations (R8.1).
 *
 * <p>Exposes typed primitive float operations without leaking Panama {@code MemorySegment}
 * or {@code Arena} references outside the kernel.</p>
 */
public interface FloatScratch extends AutoCloseable {

    /**
     * Number of float elements this scratch buffer can hold.
     *
     * @return buffer capacity in elements
     */
    int capacity();

    /**
     * Gets the float value at the given element index.
     *
     * @param index zero-based element index
     * @return float value
     */
    float get(int index);

    /**
     * Sets the float value at the given element index.
     *
     * @param index zero-based element index
     * @param value float value to store
     */
    void set(int index, float value);

    /**
     * Fills the entire buffer with the specified value.
     *
     * @param value float value to broadcast
     */
    void fill(float value);

    /**
     * Copies elements from a source float array into this scratch buffer.
     *
     * @param src source array
     * @param srcOffset starting offset in source array
     * @param destOffset starting offset in this scratch buffer
     * @param length number of elements to copy
     */
    void copyFrom(float[] src, int srcOffset, int destOffset, int length);

    /**
     * Copies elements from this scratch buffer into a destination float array.
     *
     * @param srcOffset starting offset in this scratch buffer
     * @param dest destination array
     * @param destOffset starting offset in destination array
     * @param length number of elements to copy
     */
    void copyTo(int srcOffset, float[] dest, int destOffset, int length);

    /**
     * Copies the entire buffer contents into a newly allocated float array.
     *
     * @return float array containing the buffer's contents
     */
    float[] toArray();

    /**
     * Memory footprint of this buffer in bytes.
     *
     * @return allocated byte count
     */
    long byteSize();

    /**
     * Releases the underlying off-heap memory back to the allocator.
     */
    @Override
    void close();
}
