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
 * Shape interface for append-only log structures.
 * Backs TextDataStore, MemoryWal, temporal facts, etc.
 *
 * @param <L> the memory layout type
 */
public interface AppendMemory<L extends MemoryLayout> extends Memory<L> {
    /**
     * Appends bytes to the end of this memory.
     * @param bytes the data to append
     * @return the start offset of the appended data
     */
    long append(MemorySegment bytes);
    
    /**
     * Reads data at the given offset.
     * @param offset byte offset from the start of the data region
     * @param length number of bytes to read
     * @return a segment view of the requested range
     */
    MemorySegment read(long offset, int length);
    
    /**
     * Replays all records from the given offset.
     * @param fromOffset byte offset to start replaying from
     * @return iterator of record segments
     */
    java.util.Iterator<MemorySegment> replay(long fromOffset);
    
    /**
     * Current append cursor position (next write offset).
     * @return the append cursor offset
     */
    long appendCursor();
}
