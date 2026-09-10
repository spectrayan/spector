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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorStorageException;
import com.spectrayan.spector.kernel.shape.AbstractMemory;
import com.spectrayan.spector.kernel.id.MemoryId;
import com.spectrayan.spector.kernel.layout.RegionLayout;
import com.spectrayan.spector.kernel.shape.MemoryShape;

import com.spectrayan.spector.kernel.bundle.RegionRef;

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
public abstract class AbstractRecordMemory<L extends RegionLayout> extends AbstractMemory<L> implements RecordMemory<L> {

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

    protected AbstractRecordMemory(MemoryId id, L layout, int capacity,
                                   Arena arena, MemorySegment segment, int count,
                                   boolean persistent, Path filePath,
                                   FileChannel fileChannel, boolean bundleManaged) {
        super(id, layout, capacity, arena, segment, count, persistent, filePath, fileChannel, bundleManaged);
    }

    protected AbstractRecordMemory(MemoryId id, L layout, int capacity,
                                   RegionRef regionRef, int count,
                                   boolean persistent, Path filePath) {
        super(id, layout, capacity, regionRef, count, persistent, filePath);
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
        MemorySegment.copy(recordBytes, 0, segment(), offset, layout.recordStride());

        if (layout.crcEnabled() && layout.recordStride() >= 4) {
            int payloadLen = layout.recordStride() - 4;
            byte[] payload = new byte[payloadLen];
            MemorySegment.copy(segment(), offset, MemorySegment.ofArray(payload), 0, payloadLen);
            CRC32C crc32c = new CRC32C();
            crc32c.update(payload);
            int checksum = (int) crc32c.getValue();
            segment().set(ValueLayout.JAVA_INT_UNALIGNED, offset + payloadLen, checksum);
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
            MemorySegment.copy(segment(), offset, MemorySegment.ofArray(payload), 0, payloadLen);
            CRC32C crc32c = new CRC32C();
            crc32c.update(payload);
            int expectedChecksum = (int) crc32c.getValue();
            int actualChecksum = segment().get(ValueLayout.JAVA_INT_UNALIGNED, offset + payloadLen);
            if (expectedChecksum != actualChecksum) {
                throw new SpectorStorageException(ErrorCode.RECORD_CRC_CORRUPTED, recordId);
            }
        }

        MemorySegment.copy(segment(), offset, dest, 0, layout.recordStride());
    }
}
