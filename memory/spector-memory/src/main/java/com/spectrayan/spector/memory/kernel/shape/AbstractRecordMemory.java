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

import com.spectrayan.spector.memory.kernel.AbstractMemory;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryLayout;
import com.spectrayan.spector.memory.kernel.MemoryShape;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.zip.CRC32C;

/**
 * Abstract base class for memory structures shaped as records.
 *
 * <p>Extends {@link AbstractMemory} to provide array-like, stride-based
 * indexed access to memory records with optional per-record CRC32C verification.</p>
 *
 * @param <L> the type of memory layout used by this memory
 */
public abstract class AbstractRecordMemory<L extends MemoryLayout> extends AbstractMemory<L> implements RecordMemory<L> {

    protected AbstractRecordMemory(MemoryId id, L layout, int capacity, long segmentBytes) {
        super(id, layout, capacity, segmentBytes);
    }

    protected AbstractRecordMemory(MemoryId id, L layout, int capacity, long segmentBytes, Path filePath) {
        super(id, layout, capacity, segmentBytes, filePath);
    }

    protected AbstractRecordMemory(MemoryId id, L layout, int capacity,
                                   Arena arena, MemorySegment segment, int count,
                                   boolean persistent, Path filePath,
                                   FileChannel fileChannel) {
        super(id, layout, capacity, arena, segment, count, persistent, filePath, fileChannel);
    }

    @Override
    public MemoryShape shape() {
        return MemoryShape.RECORD;
    }

    @Override
    public long recordOffset(long recordId) {
        return dataOffset() + recordId * layout.recordStride();
    }

    @Override
    public long write(long recordId, MemorySegment recordBytes) {
        if (recordId < 0 || recordId >= capacity) {
            throw new IndexOutOfBoundsException("Record ID out of bounds: " + recordId);
        }
        
        if (wal != null && !bypassWal) {
            byte[] bytes = new byte[layout.recordStride()];
            MemorySegment.copy(recordBytes, 0, MemorySegment.ofArray(bytes), 0, layout.recordStride());
            wal.appendRecordWrite(id.toString(), recordId, bytes);
        }

        long offset = recordOffset(recordId);
        MemorySegment.copy(recordBytes, 0, segment, offset, layout.recordStride());

        if (layout.crcEnabled() && layout.recordStride() >= 4) {
            int payloadLen = layout.recordStride() - 4;
            byte[] payload = new byte[payloadLen];
            MemorySegment.copy(segment, offset, MemorySegment.ofArray(payload), 0, payloadLen);
            CRC32C crc32c = new CRC32C();
            crc32c.update(payload);
            int checksum = (int) crc32c.getValue();
            segment.set(ValueLayout.JAVA_INT_UNALIGNED, offset + payloadLen, checksum);
        }

        if (recordId >= count) {
            count = (int) recordId + 1;
            persistCount();
        }
        publishVisible();
        return offset;
    }

    @Override
    public void read(long recordId, MemorySegment dest) {
        if (recordId < 0 || recordId >= capacity) {
            throw new IndexOutOfBoundsException("Record ID out of bounds: " + recordId);
        }
        
        long offset = recordOffset(recordId);

        if (layout.crcEnabled() && layout.recordStride() >= 4) {
            int payloadLen = layout.recordStride() - 4;
            byte[] payload = new byte[payloadLen];
            MemorySegment.copy(segment, offset, MemorySegment.ofArray(payload), 0, payloadLen);
            CRC32C crc32c = new CRC32C();
            crc32c.update(payload);
            int expectedChecksum = (int) crc32c.getValue();
            int actualChecksum = segment.get(ValueLayout.JAVA_INT_UNALIGNED, offset + payloadLen);
            if (expectedChecksum != actualChecksum) {
                throw new IllegalStateException("Record CRC32C corruption detected at recordId " + recordId);
            }
        }

        MemorySegment.copy(segment, offset, dest, 0, layout.recordStride());
    }
}
