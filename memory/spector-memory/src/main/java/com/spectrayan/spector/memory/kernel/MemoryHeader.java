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
package com.spectrayan.spector.memory.kernel;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.zip.CRC32C;

/**
 * Utility class for reading and writing the standardized 64-byte file header
 * for Spector Memory Kernel files.
 * <p>
 * The header layout is exactly 64 bytes (1 cache line):
 * Offset  Size  Field
 *   0      4    magic (0x534D4B4D = 'SMKM')
 *   4      4    schemaVersion
 *   8      4    shape (MemoryShape ordinal)
 *  12      4    flags (bit0=persistent, bit1=encrypted, bit2=dirty)
 *  16      8    capacity (long)
 *  24      8    count (long)
 *  32      4    recordStride
 *  36      4    layoutId
 *  40      8    createdAtEpochMs
 *  48      8    lastFlushEpochMs
 *  56      4    headerCrc32c (CRC32C over bytes [0..55])
 *  60      4    reserved (must be 0)
 */
public final class MemoryHeader {
    
    public static final int HEADER_BYTES = 64;
    public static final int MAGIC = 0x534D4B4D;

    private static final long OFFSET_MAGIC = 0;
    private static final long OFFSET_SCHEMA_VERSION = 4;
    private static final long OFFSET_SHAPE = 8;
    private static final long OFFSET_FLAGS = 12;
    private static final long OFFSET_CAPACITY = 16;
    private static final long OFFSET_COUNT = 24;
    private static final long OFFSET_RECORD_STRIDE = 32;
    private static final long OFFSET_LAYOUT_ID = 36;
    private static final long OFFSET_CREATED_AT = 40;
    private static final long OFFSET_LAST_FLUSH = 48;
    private static final long OFFSET_CRC = 56;
    private static final long OFFSET_RESERVED = 60;

    private MemoryHeader() {
        // Utility class
    }

    /**
     * Writes all fields to the segment and computes the CRC32C over the first 56 bytes.
     */
    public static void write(MemorySegment segment, long offset, int schemaVersion, MemoryShape shape, int flags,
                             long capacity, long count, int recordStride, int layoutId,
                             long createdAtEpochMs, long lastFlushEpochMs) {
        
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, offset + OFFSET_MAGIC, MAGIC);
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, offset + OFFSET_SCHEMA_VERSION, schemaVersion);
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, offset + OFFSET_SHAPE, shape.ordinal());
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, offset + OFFSET_FLAGS, flags);
        segment.set(ValueLayout.JAVA_LONG_UNALIGNED, offset + OFFSET_CAPACITY, capacity);
        segment.set(ValueLayout.JAVA_LONG_UNALIGNED, offset + OFFSET_COUNT, count);
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, offset + OFFSET_RECORD_STRIDE, recordStride);
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, offset + OFFSET_LAYOUT_ID, layoutId);
        segment.set(ValueLayout.JAVA_LONG_UNALIGNED, offset + OFFSET_CREATED_AT, createdAtEpochMs);
        segment.set(ValueLayout.JAVA_LONG_UNALIGNED, offset + OFFSET_LAST_FLUSH, lastFlushEpochMs);
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, offset + OFFSET_RESERVED, 0);

        int crc = computeCrc(segment, offset);
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, offset + OFFSET_CRC, crc);
    }

    /**
     * Checks if the header has a valid magic number and CRC.
     *
     * @param segment The memory segment.
     * @param offset  The offset where the header starts.
     * @return true if the magic matches and the CRC is valid, false otherwise.
     */
    public static boolean isValid(MemorySegment segment, long offset) {
        int magic = segment.get(ValueLayout.JAVA_INT_UNALIGNED, offset + OFFSET_MAGIC);
        if (magic != MAGIC) {
            return false;
        }
        int expectedCrc = computeCrc(segment, offset);
        int actualCrc = segment.get(ValueLayout.JAVA_INT_UNALIGNED, offset + OFFSET_CRC);
        return expectedCrc == actualCrc;
    }

    /**
     * Reads the schema version from the header.
     */
    public static int readSchemaVersion(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_INT_UNALIGNED, offset + OFFSET_SCHEMA_VERSION);
    }

    /**
     * Reads the count from the header.
     */
    public static long readCount(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_LONG_UNALIGNED, offset + OFFSET_COUNT);
    }

    /**
     * Updates the count in the header and recomputes the CRC.
     */
    public static void writeCount(MemorySegment segment, long offset, long count) {
        segment.set(ValueLayout.JAVA_LONG_UNALIGNED, offset + OFFSET_COUNT, count);
        int crc = computeCrc(segment, offset);
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, offset + OFFSET_CRC, crc);
    }

    /**
     * Reads the MemoryShape from the header.
     */
    public static MemoryShape readShape(MemorySegment segment, long offset) {
        int ordinal = segment.get(ValueLayout.JAVA_INT_UNALIGNED, offset + OFFSET_SHAPE);
        MemoryShape[] values = MemoryShape.values();
        if (ordinal >= 0 && ordinal < values.length) {
            return values[ordinal];
        }
        throw new IllegalStateException("Invalid shape ordinal in header: " + ordinal);
    }

    /**
     * Reads the capacity from the header.
     */
    public static long readCapacity(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_LONG_UNALIGNED, offset + OFFSET_CAPACITY);
    }

    /**
     * Reads the flags from the header.
     */
    public static int readFlags(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_INT_UNALIGNED, offset + OFFSET_FLAGS);
    }

    /**
     * Reads the record stride from the header.
     */
    public static int readRecordStride(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_INT_UNALIGNED, offset + OFFSET_RECORD_STRIDE);
    }

    /**
     * Reads the layout ID from the header.
     */
    public static int readLayoutId(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_INT_UNALIGNED, offset + OFFSET_LAYOUT_ID);
    }

    /**
     * Reads the creation timestamp from the header.
     */
    public static long readCreatedAt(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_LONG_UNALIGNED, offset + OFFSET_CREATED_AT);
    }

    /**
     * Reads the last flush timestamp from the header.
     */
    public static long readLastFlush(MemorySegment segment, long offset) {
        return segment.get(ValueLayout.JAVA_LONG_UNALIGNED, offset + OFFSET_LAST_FLUSH);
    }

    private static int computeCrc(MemorySegment segment, long offset) {
        CRC32C crc32c = new CRC32C();
        crc32c.update(segment.asSlice(offset, 56).asByteBuffer());
        return (int) crc32c.getValue();
    }
}
