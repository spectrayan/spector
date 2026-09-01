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

import com.spectrayan.spector.memory.kernel.Memory;
import com.spectrayan.spector.memory.kernel.MemoryLayout;
import java.lang.foreign.MemorySegment;

/**
 * Shape interface for fixed-stride record storage (like database tables).
 * Backs structures like IndexRecordMemory, SymbolTable, etc.
 *
 * @param <L> the memory layout type
 */
public interface RecordMemory<L extends MemoryLayout> extends Memory<L> {
    /**
     * Writes a record at the given slot index.
     * @param recordId slot index (0-based)
     * @param recordBytes the record data to write
     * @return the byte offset of the written record within the segment
     */
    long write(long recordId, MemorySegment recordBytes);
    
    /**
     * Reads a record at the given slot index into the destination segment.
     * @param recordId slot index (0-based)
     * @param dest destination segment to copy record data into
     */
    void read(long recordId, MemorySegment dest);
    
    /**
     * Returns the byte offset of a record within the segment.
     * @param recordId slot index (0-based)
     * @return byte offset
     */
    long recordOffset(long recordId);
}
