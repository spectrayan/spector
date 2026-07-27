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

import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Iterator;
import java.util.NoSuchElementException;

import com.spectrayan.spector.memory.kernel.AbstractMemory;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryLayout;
import com.spectrayan.spector.memory.kernel.MemoryShape;

/**
 * Abstract base class for append-only log structures.
 */
public abstract class AbstractAppendMemory<L extends MemoryLayout>
        extends AbstractMemory<L> implements AppendMemory<L> {

    protected AbstractAppendMemory(MemoryId id, L layout, int capacity, long segmentBytes) {
        super(id, layout, capacity, segmentBytes);
    }

    protected AbstractAppendMemory(MemoryId id, L layout, int capacity, long segmentBytes, Path filePath) {
        super(id, layout, capacity, segmentBytes, filePath);
    }

    protected AbstractAppendMemory(MemoryId id, L layout, int capacity,
                                   Arena arena, MemorySegment segment, int count,
                                   boolean persistent, Path filePath, FileChannel fileChannel) {
        super(id, layout, capacity, arena, segment, count, persistent, filePath, fileChannel);
    }

    @Override
    public MemoryShape shape() {
        return MemoryShape.APPEND;
    }

    @Override
    public synchronized long append(MemorySegment bytes) {
        long len = bytes.byteSize();
        // Check capacity bounds (here count stores the append cursor position in bytes)
        if (dataOffset() + count + 4 + len > segment().byteSize()) {
            throw new IndexOutOfBoundsException("Append memory full: cursor=" + count + ", request=" + (4 + len));
        }

        if (wal != null && !bypassWal) {
            byte[] rawBytes = new byte[(int) len];
            MemorySegment.copy(bytes, 0, MemorySegment.ofArray(rawBytes), 0, len);
            wal.appendAppend(id.toString(), rawBytes);
        }

        long writeOffset = dataOffset() + count;
        // Write 4B length prefix
        segment().set(ValueLayout.JAVA_INT_UNALIGNED, writeOffset, (int) len);
        // Copy payload
        MemorySegment.copy(bytes, 0, segment(), writeOffset + 4, len);

        long payloadOffset = count + 4;
        count += (int) (4 + len);
        persistCount();
        return payloadOffset;
    }

    @Override
    public MemorySegment read(long offset, int length) {
        if (dataOffset() + offset + length > segment().byteSize()) {
            throw new IndexOutOfBoundsException("Read out of bounds: offset=" + offset + ", len=" + length);
        }
        return segment().asSlice(dataOffset() + offset, length);
    }

    @Override
    public long appendCursor() {
        return count;
    }

    @Override
    public Iterator<MemorySegment> replay(long fromOffset) {
        return new Iterator<MemorySegment>() {
            private long cursor = fromOffset;

            @Override
            public boolean hasNext() {
                // Since we store length prefix (4B) before the record, there must be at least 4 bytes left
                return cursor + 4 <= count;
            }

            @Override
            public MemorySegment next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                int len = segment().get(ValueLayout.JAVA_INT_UNALIGNED, dataOffset() + cursor);
                MemorySegment slice = segment().asSlice(dataOffset() + cursor + 4, len);
                cursor += 4 + len;
                return slice;
            }
        };
    }
}
