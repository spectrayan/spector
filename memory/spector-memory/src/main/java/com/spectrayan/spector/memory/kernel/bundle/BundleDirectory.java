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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorServerException;
import com.spectrayan.spector.memory.kernel.MemoryHeader;
import com.spectrayan.spector.memory.kernel.MemoryShape;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages reading and writing the SMKM header, BundleSubHeader, and RegionEntry directory.
 */
public final class BundleDirectory {
    public static final long HEADER_OFFSET = 0;
    public static final long SUB_HEADER_OFFSET = MemoryHeader.HEADER_BYTES;  // 64
    public static final long ENTRIES_OFFSET = SUB_HEADER_OFFSET + BundleSubHeader.SIZE;  // 128
    
    private static final int PAGE_SIZE = 4096;
    
    private final List<RegionEntry> entries;
    private final int maxRegions;
    private final int bundleMagic;  // SRTB or SPTB
    
    /**
     * Constructor from existing entries.
     */
    public BundleDirectory(int bundleMagic, int maxRegions, List<RegionEntry> entries) {
        this.bundleMagic = bundleMagic;
        this.maxRegions = maxRegions;
        this.entries = List.copyOf(entries);
    }
    
    /**
     * Reads a BundleDirectory from a mapped memory segment.
     * Validates the SMKM header, sub-header, and reads all region entries.
     */
    public static BundleDirectory read(MemorySegment masterSegment) {
        if (!MemoryHeader.isValid(masterSegment, HEADER_OFFSET)) {
            throw new SpectorServerException(ErrorCode.RECORD_CRC_CORRUPTED, "Invalid SMKM header");
        }
        if (MemoryHeader.readShape(masterSegment, HEADER_OFFSET) != MemoryShape.BUNDLE) {
            throw new SpectorServerException(ErrorCode.ARGUMENT_INVALID, "MemoryShape is not BUNDLE");
        }
        if (MemoryHeader.readLayoutId(masterSegment, HEADER_OFFSET) != BundleLayout.LAYOUT_ID) {
            throw new SpectorServerException(ErrorCode.ARGUMENT_INVALID, "LayoutId does not match BundleLayout");
        }
        if (!BundleSubHeader.isValid(masterSegment)) {
            throw new SpectorServerException(ErrorCode.RECORD_CRC_CORRUPTED, "Invalid BundleSubHeader CRC");
        }
        
        int bundleMagic = BundleSubHeader.readBundleMagic(masterSegment);
        int maxRegions = BundleSubHeader.readRegionCount(masterSegment);
        
        List<RegionEntry> entries = new ArrayList<>(maxRegions);
        for (int i = 0; i < maxRegions; i++) {
            entries.add(RegionEntry.read(masterSegment, ENTRIES_OFFSET + (long) i * RegionEntry.ENTRY_BYTES));
        }
        
        return new BundleDirectory(bundleMagic, maxRegions, entries);
    }
    
    /**
     * Writes the SMKM header, sub-header, and all entries to the mapped segment.
     */
    public void write(MemorySegment masterSegment) {
        long now = System.currentTimeMillis();
        MemoryHeader.write(masterSegment, HEADER_OFFSET, BundleLayout.SCHEMA_VERSION, MemoryShape.BUNDLE, 
                           0, maxRegions, entries.size(), BundleLayout.REGION_ENTRY_STRIDE, BundleLayout.LAYOUT_ID, 
                           now, now);
        
        long maxEnd = directorySize();
        for (RegionEntry entry : entries) {
            long end = entry.offset() + entry.allocatedSize();
            if (end > maxEnd) {
                maxEnd = end;
            }
        }
        
        BundleSubHeader.write(masterSegment, bundleMagic, BundleLayout.SCHEMA_VERSION, maxEnd, 
                              0L, 0, maxRegions, dataStartOffset(maxRegions));
                              
        for (int i = 0; i < maxRegions; i++) {
            if (i < entries.size()) {
                RegionEntry.write(masterSegment, ENTRIES_OFFSET + (long) i * RegionEntry.ENTRY_BYTES, entries.get(i));
            }
        }
    }
    
    /**
     * Finds a region entry by its ID.
     */
    public RegionEntry findRegion(RegionId id) {
        for (RegionEntry entry : entries) {
            if (entry.regionId() == id) {
                return entry;
            }
        }
        return null;
    }
    
    /**
     * Returns a list of all live regions.
     */
    public List<RegionEntry> liveRegions() {
        List<RegionEntry> live = new ArrayList<>();
        for (RegionEntry entry : entries) {
            if (entry.isLive()) {
                live.add(entry);
            }
        }
        return live;
    }
    
    /**
     * Returns the count of live regions.
     */
    public int liveRegionCount() {
        int count = 0;
        for (RegionEntry entry : entries) {
            if (entry.isLive()) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Returns a new BundleDirectory instance with the updated region entry.
     */
    public BundleDirectory withUpdatedRegion(RegionId id, RegionEntry newEntry) {
        List<RegionEntry> newEntries = new ArrayList<>(entries.size());
        for (RegionEntry entry : entries) {
            if (entry.regionId() == id) {
                newEntries.add(newEntry);
            } else {
                newEntries.add(entry);
            }
        }
        return new BundleDirectory(bundleMagic, maxRegions, newEntries);
    }
    
    /**
     * Computes the page-aligned start offset for data blocks.
     */
    public static long dataStartOffset(int maxRegions) {
        long rawEnd = ENTRIES_OFFSET + (long) maxRegions * RegionEntry.ENTRY_BYTES;
        return (rawEnd + PAGE_SIZE - 1) / PAGE_SIZE * PAGE_SIZE;  // round up to 4KB
    }
    
    /**
     * Returns the total header and directory size.
     */
    public long directorySize() { 
        return dataStartOffset(maxRegions); 
    }
    
    public int maxRegions() { 
        return maxRegions; 
    }
    
    public int bundleMagic() { 
        return bundleMagic; 
    }
}
