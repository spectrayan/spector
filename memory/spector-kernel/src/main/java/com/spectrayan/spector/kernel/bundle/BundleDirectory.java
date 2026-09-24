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
package com.spectrayan.spector.kernel.bundle;

import com.spectrayan.spector.kernel.bundle.BundleFileLayout;

import com.spectrayan.spector.kernel.region.RegionId;
import com.spectrayan.spector.kernel.region.RegionEntry;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorServerException;
import com.spectrayan.spector.commons.error.SpectorStorageException;
import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.shape.MemoryShape;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages reading and writing the SMKM header, BundleSubHeader, and RegionEntry directory.
 */
public final class BundleDirectory {
    public static final long HEADER_OFFSET = 0;
    public static final long SUB_HEADER_OFFSET = RegionPreamble.PREAMBLE_BYTES;  // 64
    public static final long ENTRIES_OFFSET = SUB_HEADER_OFFSET + BundleSubHeader.SIZE;  // 128
    
    private static final int PAGE_SIZE = 4096;
    
    private final List<RegionEntry> entries;
    private final int maxRegions;
    private final int bundleMagic;  // SRTB or SPTB
    private final int schemaVersion;

    /**
     * Constructor from existing entries, at the current write version.
     */
    public BundleDirectory(int bundleMagic, int maxRegions, List<RegionEntry> entries) {
        this(bundleMagic, maxRegions, entries, BundleFileLayout.SCHEMA_VERSION);
    }

    /**
     * Constructor carrying the format version that was read from disk.
     *
     * @param schemaVersion the version recorded in the bundle, already validated as readable
     */
    public BundleDirectory(int bundleMagic, int maxRegions, List<RegionEntry> entries, int schemaVersion) {
        this.bundleMagic = bundleMagic;
        this.maxRegions = maxRegions;
        this.entries = List.copyOf(entries);
        this.schemaVersion = schemaVersion;
    }

    /**
     * The bundle format version recorded on disk, for tooling and compatibility assertions.
     *
     * <p>Always within {@code [BundleFileLayout.MIN_READABLE_SCHEMA_VERSION, SCHEMA_VERSION]} — a directory
     * outside that range is refused by {@link #read(MemorySegment, int)} and never constructed.</p>
     *
     * @return the recorded format version
     */
    public int schemaVersion() {
        return schemaVersion;
    }
    
    /**
     * Reads a BundleDirectory from a mapped memory segment.
     * Validates the SMKM header, sub-header, and reads all region entries.
     */
    public static BundleDirectory read(MemorySegment masterSegment) {
        return read(masterSegment, 0);
    }

    /**
     * Reads a BundleDirectory, additionally requiring a specific bundle magic.
     *
     * @param masterSegment       the mapped bundle
     * @param expectedBundleMagic {@code BundleSubHeader.MAGIC_PARTITION} or {@code MAGIC_RUNTIME}; pass
     *                            {@code 0} to accept either
     * @return the directory
     */
    public static BundleDirectory read(MemorySegment masterSegment, int expectedBundleMagic) {
        if (!RegionPreamble.isValid(masterSegment, HEADER_OFFSET)) {
            throw new SpectorServerException(ErrorCode.RECORD_CRC_CORRUPTED, "Invalid SMKM header");
        }
        if (RegionPreamble.readShape(masterSegment, HEADER_OFFSET) != MemoryShape.BUNDLE) {
            throw new SpectorServerException(ErrorCode.ARGUMENT_INVALID, "MemoryShape is not BUNDLE");
        }
        if (RegionPreamble.readLayoutId(masterSegment, HEADER_OFFSET) != BundleFileLayout.LAYOUT_ID) {
            throw new SpectorServerException(ErrorCode.ARGUMENT_INVALID, "LayoutId does not match BundleFileLayout");
        }
        if (!BundleSubHeader.isValid(masterSegment)) {
            throw new SpectorServerException(ErrorCode.RECORD_CRC_CORRUPTED, "Invalid BundleSubHeader CRC");
        }

        // The version gate. Until this existed, schemaVersion was written into two places per file and read
        // back by nothing — and because open() maps READ_WRITE and close()/flush() rewrite the directory with
        // the *current* SCHEMA_VERSION, an unfamiliar bundle was not merely accepted but relabelled as
        // current on close. That destroys the only evidence it was written by a different binary, so the next
        // open sees a well-formed current-version file whose regions were laid out by another writer.
        //
        // Checked here rather than in PartitionBundle.Init.open so that every path is covered — both bundle
        // types, the inspection CLI and RawBundleAccess — and so it runs before the directory escapes.
        requireReadableVersion(masterSegment);

        int bundleMagic = BundleSubHeader.readBundleMagic(masterSegment);
        if (expectedBundleMagic != 0 && bundleMagic != expectedBundleMagic) {
            // A runtime bundle opened as a partition bundle would otherwise be accepted, and simply fail to
            // find any partition region — a confusing absence rather than a clear refusal.
            throw new SpectorStorageException(ErrorCode.FILE_FORMAT_INVALID, String.format(
                    "bundle magic 0x%08X, expected 0x%08X — this is a %s bundle being opened as a %s bundle",
                    bundleMagic, expectedBundleMagic,
                    describeMagic(bundleMagic), describeMagic(expectedBundleMagic)));
        }
        int maxRegions = BundleSubHeader.readRegionCount(masterSegment);
        
        List<RegionEntry> entries = new ArrayList<>(maxRegions);
        for (int i = 0; i < maxRegions; i++) {
            entries.add(RegionEntry.read(masterSegment, ENTRIES_OFFSET + (long) i * RegionEntry.ENTRY_BYTES));
        }
        
        return new BundleDirectory(bundleMagic, maxRegions, entries,
                RegionPreamble.readSchemaVersion(masterSegment, HEADER_OFFSET));
    }
    
    /**
     * Refuses a bundle whose format version this binary does not claim to read.
     *
     * <p>Fails closed, matching the discipline already applied elsewhere in the kernel —
     * {@code HebbianGraphMemory.load} throws on unrecognised magic with an explicit "never silently drop"
     * comment, {@code DreamJournalMemory} throws on a bad magic, and {@code RegionPreamble.readShape} throws
     * {@code FILE_FORMAT_INVALID} on an unknown shape ordinal. This structure describes where every region
     * lives, so accepting one we cannot interpret is the worst place to guess.</p>
     *
     * <p>Both persisted copies of the version are checked. They are written together and should agree; a
     * disagreement means one of them was rewritten independently, which is itself corruption worth refusing
     * rather than silently preferring one copy.</p>
     */
    private static void requireReadableVersion(MemorySegment masterSegment) {
        int preambleVersion = RegionPreamble.readSchemaVersion(masterSegment, HEADER_OFFSET);
        int subHeaderVersion = BundleSubHeader.readBundleVersion(masterSegment);

        if (preambleVersion != subHeaderVersion) {
            throw new SpectorStorageException(ErrorCode.FILE_FORMAT_INVALID, String.format(
                    "bundle format version disagrees between its two recorded copies: preamble says %d, "
                            + "sub-header says %d. They are written together, so a mismatch means one was "
                            + "rewritten independently. Refusing to guess which is authoritative.",
                    preambleVersion, subHeaderVersion));
        }

        if (preambleVersion < BundleFileLayout.MIN_READABLE_SCHEMA_VERSION
                || preambleVersion > BundleFileLayout.SCHEMA_VERSION) {
            throw new SpectorStorageException(ErrorCode.FILE_FORMAT_INVALID, String.format(
                    "bundle format version %d is outside the range this binary can read [%d, %d]. %s "
                            + "Refusing to open it: the directory describes where every region begins, so "
                            + "misreading it would surface as corrupt records rather than as a format error.",
                    preambleVersion,
                    BundleFileLayout.MIN_READABLE_SCHEMA_VERSION, BundleFileLayout.SCHEMA_VERSION,
                    preambleVersion > BundleFileLayout.SCHEMA_VERSION
                            ? "The bundle was written by a newer Spector; upgrade this binary to read it."
                            : "The bundle predates the oldest format this binary supports; migrate it with "
                                    + "an older release first."));
        }
    }

    private static String describeMagic(int magic) {
        if (magic == BundleSubHeader.MAGIC_PARTITION) {
            return "partition";
        }
        if (magic == BundleSubHeader.MAGIC_RUNTIME) {
            return "runtime";
        }
        return "unrecognised";
    }

    /**
     * Writes the SMKM header, sub-header, and all entries to the mapped segment.
     */
    public void write(MemorySegment masterSegment) {
        long now = System.currentTimeMillis();
        RegionPreamble.write(masterSegment, HEADER_OFFSET, BundleFileLayout.SCHEMA_VERSION, MemoryShape.BUNDLE, 
                           0, maxRegions, entries.size(), BundleFileLayout.REGION_ENTRY_STRIDE, BundleFileLayout.LAYOUT_ID, 
                           now, now);
        
        long maxEnd = directorySize();
        for (RegionEntry entry : entries) {
            long end = entry.offset() + entry.allocatedSize();
            if (end > maxEnd) {
                maxEnd = end;
            }
        }
        
        BundleSubHeader.write(masterSegment, bundleMagic, BundleFileLayout.SCHEMA_VERSION, maxEnd, 
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
