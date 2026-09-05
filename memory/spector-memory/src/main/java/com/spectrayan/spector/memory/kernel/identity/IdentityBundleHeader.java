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
package com.spectrayan.spector.memory.kernel.identity;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorServerException;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.RegionPreamble;

/**
 * Constants and layout utilities for the {@link IdentityBundle} header and sub-header.
 *
 * <pre>
 * ┌──────────────────────────────────────────────────┐ offset 0
 * │ 64B RegionPreamble (SMKM, BUNDLE, layout=IDNT)   │
 * ├──────────────────────────────────────────────────┤ offset 64
 * │ 64B IdentitySubHeader (SIDB, version, regions)   │
 * ├──────────────────────────────────────────────────┤ offset 128
 * │ 16 × 64B IdentityRegionEntry directory           │ ends at 1152
 * ├──────────────────────────────────────────────────┤ offset 4096 (page aligned)
 * │ Region 0..15 data payloads (64KB default each)   │
 * └──────────────────────────────────────────────────┘
 * </pre>
 */
public final class IdentityBundleHeader {

    public static final int LAYOUT_ID = 0x49444E54; // 'IDNT'
    public static final int SUB_MAGIC = 0x53494442; // 'SIDB'
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_REGIONS = 16;
    public static final long DATA_START_OFFSET = 4096L;
    public static final long DEFAULT_REGION_ALLOCATION = 64 * 1024L; // 64KB per region
    public static final long TOTAL_INITIAL_SIZE = DATA_START_OFFSET + MAX_REGIONS * DEFAULT_REGION_ALLOCATION; // 1,052,672 B

    public static final long OFF_SMKM_HEADER = 0;
    public static final long OFF_SUB_HEADER = RegionPreamble.PREAMBLE_BYTES; // 64
    public static final long OFF_ENTRIES = OFF_SUB_HEADER + 64; // 128

    // Sub-header internal field offsets (from OFF_SUB_HEADER)
    private static final long OFF_SUB_MAGIC = 0;
    private static final long OFF_SUB_VERSION = 4;
    private static final long OFF_SUB_REGIONS = 8;
    private static final long OFF_SUB_DATA_START = 12;
    private static final long OFF_SUB_TOTAL_SIZE = 16;

    private IdentityBundleHeader() {
    }

    /**
     * Initializes the SMKM header, sub-header, and 16 region entries on a newly created segment.
     */
    public static void initialize(MemorySegment segment) {
        long now = System.currentTimeMillis();
        RegionPreamble.write(
                segment,
                OFF_SMKM_HEADER,
                SCHEMA_VERSION,
                MemoryShape.BUNDLE,
                1, // persistent
                MAX_REGIONS,
                0, // initial active count
                IdentityRegionEntry.ENTRY_BYTES,
                LAYOUT_ID,
                now,
                now
        );

        // Write SubHeader
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, OFF_SUB_HEADER + OFF_SUB_MAGIC, SUB_MAGIC);
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, OFF_SUB_HEADER + OFF_SUB_VERSION, SCHEMA_VERSION);
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, OFF_SUB_HEADER + OFF_SUB_REGIONS, MAX_REGIONS);
        segment.set(ValueLayout.JAVA_INT_UNALIGNED, OFF_SUB_HEADER + OFF_SUB_DATA_START, (int) DATA_START_OFFSET);
        segment.set(ValueLayout.JAVA_LONG_UNALIGNED, OFF_SUB_HEADER + OFF_SUB_TOTAL_SIZE, TOTAL_INITIAL_SIZE);

        // Write 16 Region Entries
        for (int i = 0; i < MAX_REGIONS; i++) {
            IdentityRegionId regionId = IdentityRegionId.fromId(i);
            long offset = DATA_START_OFFSET + (long) i * DEFAULT_REGION_ALLOCATION;
            IdentityRegionEntry entry = new IdentityRegionEntry(
                    regionId,
                    IdentityRegionEntry.FLAG_EMPTY,
                    offset,
                    DEFAULT_REGION_ALLOCATION,
                    0L,
                    0,
                    0,
                    now
            );
            IdentityRegionEntry.write(segment, OFF_ENTRIES + (long) i * IdentityRegionEntry.ENTRY_BYTES, entry);
        }
    }

    /**
     * Validates that the mapped memory segment has valid headers for an IdentityBundle.
     */
    public static void validate(MemorySegment segment) {
        if (!RegionPreamble.isValid(segment, OFF_SMKM_HEADER)) {
            throw new SpectorServerException(ErrorCode.RECORD_CRC_CORRUPTED, "Invalid SMKM RegionPreamble on IdentityBundle");
        }
        if (RegionPreamble.readLayoutId(segment, OFF_SMKM_HEADER) != LAYOUT_ID) {
            throw new SpectorServerException(ErrorCode.ARGUMENT_INVALID, "IdentityBundle layoutId mismatch");
        }
        int subMagic = segment.get(ValueLayout.JAVA_INT_UNALIGNED, OFF_SUB_HEADER + OFF_SUB_MAGIC);
        if (subMagic != SUB_MAGIC) {
            throw new SpectorServerException(ErrorCode.ARGUMENT_INVALID, "IdentityBundle sub-header magic mismatch: 0x" + Integer.toHexString(subMagic));
        }
    }
}
