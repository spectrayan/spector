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


import com.spectrayan.spector.kernel.shape.Memory;
import com.spectrayan.spector.kernel.layout.RegionLayout;
import java.lang.foreign.MemorySegment;

/**
 * Shape interface for fixed-stride record storage (like database tables).
 * Backs structures like IndexEntryMemory, SymbolTable, etc.
 *
 * @param <L> the memory layout type
 */
public interface RecordMemory<L extends RegionLayout> extends Memory<L> {
    /**
     * Writes a record at the given slot index.
     * @param recordId slot index (0-based)
     * @param recordBytes the record data to write
     * @return the byte offset of the written record within the segment
     */
    long write(long recordId, MemorySegment recordBytes);
    
    /**
     * Writes a record at the given slot index from a byte array.
     * @param recordId slot index (0-based)
     * @param recordBytes the record data to write
     * @return the byte offset of the written record within the segment
     */
    default long write(long recordId, byte[] recordBytes) {
        return write(recordId, MemorySegment.ofArray(recordBytes));
    }
    
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
