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
package com.spectrayan.spector.kernel.api;

import com.spectrayan.spector.kernel.engram.EncodingHeader;

/**
 * Kernel dispatching facade for multi-tier engram record storage (R16.3, R16.4).
 *
 * <p>Polymorphically dispatches writes and record lifecycle mutations across
 * cognitive memory tiers without exposing internal region segments or per-tier handles.</p>
 */
public interface EngramMemory extends AutoCloseable {

    /**
     * Appends a pre-quantized engram record with its encoding header to the specified tier.
     *
     * @param tier       target memory tier (SEMANTIC, PROCEDURAL, WORKING)
     * @param header     encoding header with metadata and synaptic flags
     * @param quantized  pre-quantized embedding vector bytes
     * @return byte offset where the record was written
     */
    long write(MemoryType tier, EncodingHeader header, byte[] quantized);

    /**
     * Reads the encoding header for the memory record at the given location.
     */
    EncodingHeader readHeader(MemoryLocation loc);

    /**
     * Reads the quantized vector bytes for the memory record into {@code dest}.
     */
    void readVector(MemoryLocation loc, byte[] dest);

    /**
     * Returns the record count for the given memory tier.
     */
    int countFor(MemoryType tier);

    /**
     * Returns the total record count across all registered memory tiers.
     */
    int totalCount();

    /**
     * Sets the tombstone flag (logical deletion) for the record at the given location.
     */
    void tombstone(MemoryLocation loc);

    /**
     * Returns whether the record at the given location is tombstoned.
     */
    boolean isTombstoned(MemoryLocation loc);

    /**
     * Sets the resolved flag (Zeigarnik effect) for the record at the given location.
     */
    void markResolved(MemoryLocation loc);

    /**
     * Clears the resolved flag (Zeigarnik effect) for the record at the given location.
     */
    void markUnresolved(MemoryLocation loc);

    /**
     * Marks the record at the given location as contradicted.
     */
    void markContradicted(MemoryLocation loc);

    /**
     * Returns the nearest distance between candidate vector and existing records in working memory,
     * or -1.0f if working memory is empty or unavailable.
     */
    float nearestWorkingDistance(float[] vector, float[] mins, float[] scales);

    /**
     * Flushes all registered engram stores to disk.
     */
    void force();

    @Override
    void close();
}
