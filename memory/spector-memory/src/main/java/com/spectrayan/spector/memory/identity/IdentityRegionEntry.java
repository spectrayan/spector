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
package com.spectrayan.spector.memory.identity;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * 64-byte on-disk directory entry for a region within an {@link IdentityBundle}.
 *
 * @param regionId      the identity region identifier
 * @param flags         bitmask flags (FLAG_EMPTY, FLAG_PRESENT, FLAG_DIRTY)
 * @param offset        byte offset in the identity bundle file
 * @param allocatedSize total bytes allocated for this region
 * @param usedSize      current bytes used by the payload
 * @param version       incrementing mutation version
 * @param checksum      CRC32C checksum of the payload bytes
 * @param updatedAt     epoch millisecond timestamp of last update
 */
public record IdentityRegionEntry(
        IdentityRegionId regionId,
        short flags,
        long offset,
        long allocatedSize,
        long usedSize,
        int version,
        int checksum,
        long updatedAt
) {
    public static final int ENTRY_BYTES = 64;

    public static final short FLAG_EMPTY = 0x00;
    public static final short FLAG_PRESENT = 0x01;
    public static final short FLAG_DIRTY = 0x02;

    /**
     * @return {@code true} if this region currently stores a valid payload
     */
    public boolean isPresent() {
        return (flags & FLAG_PRESENT) != 0;
    }

    /**
     * Reads an {@link IdentityRegionEntry} from a mapped memory segment.
     *
     * @param segment    the mapped segment
     * @param baseOffset byte offset of the 64-byte entry
     * @return the deserialized entry
     */
    public static IdentityRegionEntry read(MemorySegment segment, long baseOffset) {
        short regionCode = segment.get(ValueLayout.JAVA_SHORT_UNALIGNED, baseOffset);
        short flags = segment.get(ValueLayout.JAVA_SHORT_UNALIGNED, baseOffset + 2);
        long offset = segment.get(ValueLayout.JAVA_LONG_UNALIGNED, baseOffset + 8);
        long allocatedSize = segment.get(ValueLayout.JAVA_LONG_UNALIGNED, baseOffset + 16);
        long usedSize = segment.get(ValueLayout.JAVA_LONG_UNALIGNED, baseOffset + 24);
        int version = segment.get(ValueLayout.JAVA_INT_UNALIGNED, baseOffset + 32);
        int checksum = segment.get(ValueLayout.JAVA_INT_UNALIGNED, baseOffset + 36);
        long updatedAt = segment.get(ValueLayout.JAVA_LONG_UNALIGNED, baseOffset + 40);

        return new IdentityRegionEntry(
                IdentityRegionId.fromId(regionCode),
                flags,
                offset,
                allocatedSize,
                usedSize,
                version,
                checksum,
                updatedAt
        );
    }

    /**
     * Writes this {@link IdentityRegionEntry} to a mapped memory segment.
     *
     * @param segment    the mapped segment
     * @param baseOffset byte offset of the 64-byte entry
     * @param entry      the entry to serialize
     */
    public static void write(MemorySegment segment, long baseOffset, IdentityRegionEntry entry) {
        segment.set(ValueLayout.JAVA_SHORT_UNALIGNED, baseOffset, (short) entry.regionId().id());
        segment.set(ValueLayout.JAVA_SHORT_UNALIGNED, baseOffset + 2, entry.flags());
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, baseOffset + 4, 0); // reserved
        segment.set(ValueLayout.JAVA_LONG_UNALIGNED, baseOffset + 8, entry.offset());
        segment.set(ValueLayout.JAVA_LONG_UNALIGNED, baseOffset + 16, entry.allocatedSize());
        segment.set(ValueLayout.JAVA_LONG_UNALIGNED, baseOffset + 24, entry.usedSize());
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, baseOffset + 32, entry.version());
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, baseOffset + 36, entry.checksum());
        segment.set(ValueLayout.JAVA_LONG_UNALIGNED, baseOffset + 40, entry.updatedAt());
        segment.set(ValueLayout.JAVA_LONG_UNALIGNED, baseOffset + 48, 0L); // reserved
        segment.set(ValueLayout.JAVA_LONG_UNALIGNED, baseOffset + 56, 0L); // reserved
    }
}
