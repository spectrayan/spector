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
package com.spectrayan.spector.kernel.store;

import java.nio.file.Path;

import com.spectrayan.spector.kernel.layout.RegionLayout;
import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.layout.FixedEngramLayout;
import com.spectrayan.spector.kernel.api.MemoryType;

/**
 * Standardized interface for engram memory stores in Spector Memory (ADR-0030).
 *
 * <p>Unifies all four memory tiers (Semantic, Procedural, Working, Episodic)
 * under a single engram contract without constraining the physical stride or layout.</p>
 *
 * @since 1.5.0
 */
public sealed interface EngramRegion extends AutoCloseable permits AbstractEngramMemory, EpisodicMemory {

    /** Size of the {@link RegionPreamble} region prologue in bytes. */
    int METADATA_PREAMBLE_BYTES = RegionPreamble.PREAMBLE_BYTES;

    /**
     * Returns the offset of the first data record, skipping the region preamble if persistent.
     */
    default long dataOffset() {
        return isPersistent() ? METADATA_PREAMBLE_BYTES : 0L;
    }

    /**
     * Writes a cognitive record (header + quantized vector payload).
     *
     * @param header cognitive header
     * @param quantized quantized vector bytes
     * @return byte offset where record was written
     */
    long write(EncodingHeader header, byte[] quantized);

    /**
     * Returns the memory tier type (WORKING, SEMANTIC, PROCEDURAL, EPISODIC).
     *
     * @return the cognitive memory type
     */
    MemoryType type();

    /**
     * Returns the maximum record index readable by concurrent readers (SWMR barrier).
     *
     * @return the visible record count
     */
    int visibleCount();

    /**
     * Returns the ratio of tombstoned records to total records (0.0 to 1.0).
     *
     * @return the tombstone ratio
     */
    float tombstoneRatio();

    /**
     * Returns whether this store is backed by an mmap persistent file.
     *
     * @return true if file-backed and persistent, false if in-memory
     */
    boolean isPersistent();

    /**
     * Returns the path to the backing file, or null if in-memory.
     *
     * @return the file path or null
     */
    Path filePath();

    /**
     * Reads the encoding header flags for the record at the given byte offset.
     */
    byte readFlags(long offset);

    /**
     * Returns true if the record at the given byte offset is tombstoned.
     */
    boolean isTombstoned(long offset);

    /**
     * Returns true if the record at the given byte offset is marked contradicted.
     */
    boolean isContradicted(long offset);

    /**
     * Reads the decoded encoding header for the record at the given byte offset.
     */
    EncodingHeader readHeader(long offset);

    /**
     * Reads the quantized vector payload for the record at the given byte offset,
     * or null if not present or unsupported.
     */
    byte[] readVector(long offset);

    /**
     * Marks the record at the given byte offset as tombstoned.
     */
    void tombstone(long offset);

    /**
     * Marks the record at the given byte offset as contradicted.
     */
    void markContradicted(long offset);

    /**
     * Marks the record at the given byte offset as resolved.
     */
    void markResolved(long offset);

    /**
     * Marks the record at the given byte offset as unresolved.
     */
    void markUnresolved(long offset);

    /**
     * Forces all pending writes to disk if persistent.
     */
    void force();

    /**
     * Returns true if this memory store is frozen (read-only / older partition).
     */
    default boolean isFrozen() {
        return false;
    }

    /**
     * Returns the number of active records.
     *
     * @return record count
     */
    int size();

    /**
     * Returns the maximum record capacity of this store.
     *
     * @return maximum capacity
     */
    int capacity();

    /**
     * Returns the memory region layout.
     *
     * @return the region layout
     */
    RegionLayout layout();

    /**
     * Calculates the byte offset of a record slot.
     *
     * @param index zero-based record index
     * @return byte offset of the record
     */
    default long recordOffset(long index) {
        return dataOffset() + index * layout().recordStride();
    }



    /**
     * Closes the memory store and releases off-heap resources.
     */
    @Override
    void close();
}
