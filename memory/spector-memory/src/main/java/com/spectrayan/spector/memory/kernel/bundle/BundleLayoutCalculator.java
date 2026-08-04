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

import com.spectrayan.spector.memory.kernel.MemoryHeader;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes region sizes from raw primitive sizes.
 */
public final class BundleLayoutCalculator {
    
    private BundleLayoutCalculator() {} // static utility
    
    /** Specification for a single region's size requirements. */
    public record RegionSizeSpec(
        RegionId regionId,
        long dataBytes,      // total data bytes (excluding region's own SMKM header)
        int capacity,        // max records
        int stride,          // record stride (from store's MemoryLayout)
        int layoutId,        // store's MemoryLayout.layoutId()
        int schemaVersion,   // store's MemoryLayout.schemaVersion()
        boolean growable     // whether region can grow via relocate-to-tail
    ) {}
    
    /**
     * Computes a complete bundle layout from region specs.
     * Returns (BundleDirectory, totalFileSize).
     */
    public static BundleComputedLayout compute(int bundleMagic, List<RegionSizeSpec> specs) {
        int maxRegions = specs.size();
        long dataStart = BundleDirectory.dataStartOffset(maxRegions);
        long cursor = dataStart;
        List<RegionEntry> entries = new ArrayList<>();
        for (RegionSizeSpec spec : specs) {
            long regionTotal = alignToPage(MemoryHeader.HEADER_BYTES + spec.dataBytes());
            short flags = (short) (RegionEntry.FLAG_LIVE | (spec.growable() ? RegionEntry.FLAG_GROWABLE : 0));
            entries.add(new RegionEntry(spec.regionId(), flags, cursor, regionTotal,
                    0, spec.capacity(), spec.stride(), spec.layoutId(), spec.schemaVersion()));
            cursor += regionTotal;
        }
        long totalFileSize = cursor;
        return new BundleComputedLayout(
            new BundleDirectory(bundleMagic, maxRegions, entries),
            totalFileSize
        );
    }
    
    public record BundleComputedLayout(BundleDirectory directory, long totalFileSize) {}
    
    /** Rounds up to the nearest 4KB page boundary. */
    public static long alignToPage(long bytes) {
        return (bytes + 4095L) & ~4095L;
    }
}
