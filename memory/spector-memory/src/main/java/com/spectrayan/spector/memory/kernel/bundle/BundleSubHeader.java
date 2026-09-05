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
package com.spectrayan.spector.memory.kernel.bundle;

import com.spectrayan.spector.memory.kernel.RegionPreamble;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.zip.CRC32C;

/**
 * 64-byte sub-header at offset {@link RegionPreamble#PREAMBLE_BYTES} (64).
 * Uses explicit segment reads and writes with FFM API ValueLayouts.
 */
public final class BundleSubHeader {

    public static final long OFFSET = RegionPreamble.PREAMBLE_BYTES;
    public static final long SIZE = 64;
    
    public static final int MAGIC_RUNTIME = 0x53525442; // 'SRTB'
    public static final int MAGIC_PARTITION = 0x53505442; // 'SPTB'

    // Offsets within the sub-header
    private static final long VH_BUNDLE_MAGIC = 0;
    private static final long VH_BUNDLE_VERSION = 4;
    private static final long VH_TOTAL_FILE_SIZE = 8;
    private static final long VH_DIR_CHECKSUM = 16;
    private static final long VH_CAPACITY_CONFIG = 24;
    private static final long VH_REGION_COUNT = 28;
    private static final long VH_DATA_START = 32;
    private static final long VH_CREATED_AT = 40;
    private static final long VH_LAST_MODIFIED = 48;
    private static final long VH_CRC32C = 56;
    private static final long VH_RESERVED = 60;

    private BundleSubHeader() {
        // Utility class
    }

    /**
     * Writes all fields to the segment and computes the CRC32C over the first 56 bytes.
     */
    public static void write(MemorySegment seg, int bundleMagic, int version, long totalFileSize, 
                             long checksum, int capacityConfig, int regionCount, long dataStart) {
        
        long now = System.currentTimeMillis();
        
        seg.set(ValueLayout.JAVA_INT, OFFSET + VH_BUNDLE_MAGIC, bundleMagic);
        seg.set(ValueLayout.JAVA_INT, OFFSET + VH_BUNDLE_VERSION, version);
        seg.set(ValueLayout.JAVA_LONG, OFFSET + VH_TOTAL_FILE_SIZE, totalFileSize);
        seg.set(ValueLayout.JAVA_LONG, OFFSET + VH_DIR_CHECKSUM, checksum);
        seg.set(ValueLayout.JAVA_INT, OFFSET + VH_CAPACITY_CONFIG, capacityConfig);
        seg.set(ValueLayout.JAVA_INT, OFFSET + VH_REGION_COUNT, regionCount);
        seg.set(ValueLayout.JAVA_LONG, OFFSET + VH_DATA_START, dataStart);
        seg.set(ValueLayout.JAVA_LONG, OFFSET + VH_CREATED_AT, now);
        seg.set(ValueLayout.JAVA_LONG, OFFSET + VH_LAST_MODIFIED, now);
        seg.set(ValueLayout.JAVA_INT, OFFSET + VH_RESERVED, 0);

        int crc = computeCrc(seg);
        seg.set(ValueLayout.JAVA_INT, OFFSET + VH_CRC32C, crc);
    }

    /**
     * Checks if the sub-header has a valid CRC32C.
     */
    public static boolean isValid(MemorySegment seg) {
        int expectedCrc = computeCrc(seg);
        int actualCrc = seg.get(ValueLayout.JAVA_INT, OFFSET + VH_CRC32C);
        return expectedCrc == actualCrc;
    }

    public static int readBundleMagic(MemorySegment seg) {
        return seg.get(ValueLayout.JAVA_INT, OFFSET + VH_BUNDLE_MAGIC);
    }

    public static int readBundleVersion(MemorySegment seg) {
        return seg.get(ValueLayout.JAVA_INT, OFFSET + VH_BUNDLE_VERSION);
    }

    public static long readTotalFileSize(MemorySegment seg) {
        return seg.get(ValueLayout.JAVA_LONG, OFFSET + VH_TOTAL_FILE_SIZE);
    }

    public static long readDirChecksum(MemorySegment seg) {
        return seg.get(ValueLayout.JAVA_LONG, OFFSET + VH_DIR_CHECKSUM);
    }

    public static int readCapacityConfig(MemorySegment seg) {
        return seg.get(ValueLayout.JAVA_INT, OFFSET + VH_CAPACITY_CONFIG);
    }

    public static int readRegionCount(MemorySegment seg) {
        return seg.get(ValueLayout.JAVA_INT, OFFSET + VH_REGION_COUNT);
    }

    public static long readDataStartOffset(MemorySegment seg) {
        return seg.get(ValueLayout.JAVA_LONG, OFFSET + VH_DATA_START);
    }

    public static long readCreatedAtEpochMs(MemorySegment seg) {
        return seg.get(ValueLayout.JAVA_LONG, OFFSET + VH_CREATED_AT);
    }

    public static long readLastModifiedEpochMs(MemorySegment seg) {
        return seg.get(ValueLayout.JAVA_LONG, OFFSET + VH_LAST_MODIFIED);
    }

    private static int computeCrc(MemorySegment seg) {
        CRC32C crc32c = new CRC32C();
        crc32c.update(seg.asSlice(OFFSET, 56).asByteBuffer());
        return (int) crc32c.getValue();
    }
}
