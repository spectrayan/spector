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

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * 64-byte on-disk directory entry for a region within a Bundle.
 * 
 * @param regionId The stable ID of the region
 * @param flags Bitmask flags (FLAG_LIVE, FLAG_GROWABLE)
 * @param offset Byte offset in the bundle file
 * @param allocatedSize Total bytes allocated
 * @param usedSize Current used bytes
 * @param capacity Maximum records capacity
 * @param stride Record stride
 * @param layoutId Layout ID of the region store
 * @param schemaVersion Per-region schema version
 */
public record RegionEntry(
    RegionId regionId, 
    short flags, 
    long offset, 
    long allocatedSize, 
    long usedSize, 
    int capacity, 
    int stride, 
    int layoutId, 
    int schemaVersion
) {
    public static final int ENTRY_BYTES = 64;
    
    public static final short FLAG_LIVE = 0x01;
    public static final short FLAG_GROWABLE = 0x02;

    private static final long OFF_REGION_ID = 0;
    private static final long OFF_FLAGS = 2;
    private static final long OFF_RESERVED_1 = 4;
    private static final long OFF_OFFSET = 8;
    private static final long OFF_ALLOCATED_SIZE = 16;
    private static final long OFF_USED_SIZE = 24;
    private static final long OFF_CAPACITY = 32;
    private static final long OFF_STRIDE = 36;
    private static final long OFF_LAYOUT_ID = 40;
    private static final long OFF_SCHEMA_VERSION = 44;
    private static final long OFF_RESERVED_2 = 48;
    private static final long OFF_RESERVED_3 = 56;

    /**
     * @return true if the region is currently live
     */
    public boolean isLive() {
        return (flags & FLAG_LIVE) != 0;
    }

    /**
     * @return true if the region can grow its allocated size dynamically
     */
    public boolean isGrowable() {
        return (flags & FLAG_GROWABLE) != 0;
    }

    public RegionEntry withOffset(long newOffset) {
        return new RegionEntry(regionId, flags, newOffset, allocatedSize, usedSize, capacity, stride, layoutId, schemaVersion);
    }

    public RegionEntry withAllocatedSize(long newAllocatedSize) {
        return new RegionEntry(regionId, flags, offset, newAllocatedSize, usedSize, capacity, stride, layoutId, schemaVersion);
    }

    public RegionEntry withUsedSize(long newUsedSize) {
        return new RegionEntry(regionId, flags, offset, allocatedSize, newUsedSize, capacity, stride, layoutId, schemaVersion);
    }

    public RegionEntry withFlags(short newFlags) {
        return new RegionEntry(regionId, newFlags, offset, allocatedSize, usedSize, capacity, stride, layoutId, schemaVersion);
    }

    /**
     * Reads a RegionEntry from the provided memory segment at the given offset.
     * 
     * @param seg The memory segment to read from
     * @param entryOffset The byte offset within the segment where the entry starts
     * @return A parsed RegionEntry record
     */
    public static RegionEntry read(MemorySegment seg, long entryOffset) {
        short regionIdVal = seg.get(ValueLayout.JAVA_SHORT, entryOffset + OFF_REGION_ID);
        short flags = seg.get(ValueLayout.JAVA_SHORT, entryOffset + OFF_FLAGS);
        long offset = seg.get(ValueLayout.JAVA_LONG, entryOffset + OFF_OFFSET);
        long allocatedSize = seg.get(ValueLayout.JAVA_LONG, entryOffset + OFF_ALLOCATED_SIZE);
        long usedSize = seg.get(ValueLayout.JAVA_LONG, entryOffset + OFF_USED_SIZE);
        int capacity = seg.get(ValueLayout.JAVA_INT, entryOffset + OFF_CAPACITY);
        int stride = seg.get(ValueLayout.JAVA_INT, entryOffset + OFF_STRIDE);
        int layoutId = seg.get(ValueLayout.JAVA_INT, entryOffset + OFF_LAYOUT_ID);
        int schemaVersion = seg.get(ValueLayout.JAVA_INT, entryOffset + OFF_SCHEMA_VERSION);

        return new RegionEntry(
            RegionId.fromId(regionIdVal),
            flags,
            offset,
            allocatedSize,
            usedSize,
            capacity,
            stride,
            layoutId,
            schemaVersion
        );
    }

    /**
     * Writes a RegionEntry to the provided memory segment at the given offset.
     * 
     * @param seg The memory segment to write to
     * @param entryOffset The byte offset within the segment where the entry starts
     * @param entry The RegionEntry to write
     */
    public static void write(MemorySegment seg, long entryOffset, RegionEntry entry) {
        seg.set(ValueLayout.JAVA_SHORT, entryOffset + OFF_REGION_ID, (short) entry.regionId().id());
        seg.set(ValueLayout.JAVA_SHORT, entryOffset + OFF_FLAGS, entry.flags());
        seg.set(ValueLayout.JAVA_INT, entryOffset + OFF_RESERVED_1, 0);
        seg.set(ValueLayout.JAVA_LONG, entryOffset + OFF_OFFSET, entry.offset());
        seg.set(ValueLayout.JAVA_LONG, entryOffset + OFF_ALLOCATED_SIZE, entry.allocatedSize());
        seg.set(ValueLayout.JAVA_LONG, entryOffset + OFF_USED_SIZE, entry.usedSize());
        seg.set(ValueLayout.JAVA_INT, entryOffset + OFF_CAPACITY, entry.capacity());
        seg.set(ValueLayout.JAVA_INT, entryOffset + OFF_STRIDE, entry.stride());
        seg.set(ValueLayout.JAVA_INT, entryOffset + OFF_LAYOUT_ID, entry.layoutId());
        seg.set(ValueLayout.JAVA_INT, entryOffset + OFF_SCHEMA_VERSION, entry.schemaVersion());
        seg.set(ValueLayout.JAVA_LONG, entryOffset + OFF_RESERVED_2, 0L);
        seg.set(ValueLayout.JAVA_LONG, entryOffset + OFF_RESERVED_3, 0L);
    }
}
