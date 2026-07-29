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

import com.spectrayan.spector.memory.kernel.MemoryHeader;
import com.spectrayan.spector.memory.kernel.shape.RecordMemory;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.synapse.CognitiveRecordLayout;

import java.lang.foreign.MemorySegment;
import java.nio.file.Path;

/**
 * Standardized interface for cognitive record memory stores in Spector Memory.
 *
 * <p>Extends {@link RecordMemory} to provide full type safety and contracts
 * for cognitive memory record operations, SWMR visibility, and persistence,
 * eliminating downcasting anti-patterns.</p>
 */
public interface CognitiveRecordMemory extends RecordMemory<CognitiveRecordLayout>, AutoCloseable {

    /** Metadata header size in bytes. */
    int METADATA_HEADER_BYTES = MemoryHeader.HEADER_BYTES;

    /**
     * Writes a cognitive record (header + quantized vector payload).
     *
     * @param header cognitive header
     * @param quantized quantized vector bytes
     * @return byte offset where record was written
     */
    long write(CognitiveRecordLayout.CognitiveHeader header, byte[] quantized);

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
     * Returns the layout for cognitive record reads and writes.
     *
     * @return the cognitive record layout
     */
    CognitiveRecordLayout cognitiveLayout();

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
     * Closes the memory store and releases off-heap resources.
     */
    @Override
    void close();
}
