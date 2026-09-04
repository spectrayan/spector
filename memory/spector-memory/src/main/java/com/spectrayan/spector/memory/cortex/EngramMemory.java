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
package com.spectrayan.spector.memory.cortex;

import java.lang.foreign.MemorySegment;
import java.nio.file.Path;

import com.spectrayan.spector.memory.kernel.RegionLayout;
import com.spectrayan.spector.memory.kernel.RegionPreamble;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeader;
import com.spectrayan.spector.memory.kernel.layout.FixedEngramLayout;
import com.spectrayan.spector.memory.model.MemoryType;

/**
 * Standardized interface for engram memory stores in Spector Memory (ADR-0030).
 *
 * <p>Unifies all four memory tiers (Semantic, Procedural, Working, Episodic)
 * under a single engram contract without constraining the physical stride or layout.</p>
 *
 * @since 1.5.0
 */
public interface EngramMemory extends AutoCloseable {

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
     * Returns the primary memory segment.
     *
     * @return primary memory segment
     */
    default MemorySegment primarySegment() {
        return segment();
    }

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
     * Returns the primary off-heap memory segment backing this store.
     *
     * @return the memory segment
     */
    MemorySegment segment();

    /**
     * Returns the header slab segment used for vectorized scanning.
     *
     * @return the header slab segment
     */
    MemorySegment headerSlab();

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
     * Returns the fixed engram layout if this store is fixed-stride.
     *
     * @return the layout as FixedEngramLayout, or null if variable-stride
     * @deprecated Use {@link #layout()} instead.
     */
    @Deprecated(since = "1.5.0", forRemoval = true)
    default FixedEngramLayout cognitiveLayout() {
        RegionLayout l = layout();
        return l instanceof FixedEngramLayout fel ? fel : null;
    }

    /**
     * Closes the memory store and releases off-heap resources.
     */
    @Override
    void close();
}
