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
package com.spectrayan.spector.inspect;

import com.spectrayan.spector.memory.kernel.RegionPreamble;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.bundle.BundleDirectory;
import com.spectrayan.spector.memory.kernel.bundle.BundleSubHeader;
import com.spectrayan.spector.memory.kernel.bundle.RegionEntry;
import com.spectrayan.spector.memory.kernel.bundle.RegionId;
import com.spectrayan.spector.memory.kernel.bundle.BundleLayout;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Date;

/**
 * CLI implementation for spector-inspect module.
 * Supports "header" subcommand for single SMK files, and "bundle" subcommand for V4 bundles.
 */
public class SpectorInspectCli {

    public static void main(String[] args) {
        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }

        String subcommand = args[0];
        String filePathStr = args[1];
        Path path = Paths.get(filePathStr);

        if (!Files.exists(path)) {
            System.err.println("Error: File does not exist: " + path.toAbsolutePath());
            System.exit(1);
        }

        if (subcommand.equalsIgnoreCase("header")) {
            inspectHeader(path);
        } else if (subcommand.equalsIgnoreCase("bundle")) {
            inspectBundle(path);
        } else {
            System.err.println("Unknown subcommand: " + subcommand);
            printUsage();
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("  spector-inspect header <file-path>  - Inspect standard SMKM header");
        System.err.println("  spector-inspect bundle <file-path>  - Inspect V4 partition/runtime bundle");
    }

    private static void inspectHeader(Path path) {
        try {
            long size = Files.size(path);
            if (size < RegionPreamble.PREAMBLE_BYTES) {
                System.err.println("Error: File is too small to contain a valid Spector Memory Kernel header (size: " + size + " bytes).");
                System.exit(1);
            }
        } catch (IOException e) {
            System.err.println("Error reading file size: " + e.getMessage());
            System.exit(1);
        }

        try (Arena arena = Arena.ofShared();
             FileChannel fc = FileChannel.open(path, StandardOpenOption.READ)) {
            
            MemorySegment segment = fc.map(FileChannel.MapMode.READ_ONLY, 0, RegionPreamble.PREAMBLE_BYTES, arena);
            
            if (!RegionPreamble.isValid(segment, 0)) {
                System.err.println("Error: Invalid or corrupted Spector Memory Kernel header.");
                System.exit(1);
            }

            int schemaVersion = RegionPreamble.readSchemaVersion(segment, 0);
            MemoryShape shape = RegionPreamble.readShape(segment, 0);
            long capacity = RegionPreamble.readCapacity(segment, 0);
            long count = RegionPreamble.readCount(segment, 0);
            int recordStride = RegionPreamble.readRecordStride(segment, 0);
            int layoutId = RegionPreamble.readLayoutId(segment, 0);
            long createdAt = RegionPreamble.readCreatedAt(segment, 0);
            long lastFlush = RegionPreamble.readLastFlush(segment, 0);
            int flags = RegionPreamble.readFlags(segment, 0);

            String layoutIdStr = decodeLayoutId(layoutId);

            System.out.println("==================================================");
            System.out.println("Spector Memory Kernel Header: " + path.getFileName());
            System.out.println("==================================================");
            System.out.printf("Magic:            0x%08X (SMKM)\n", RegionPreamble.MAGIC);
            System.out.printf("Schema Version:   %d\n", schemaVersion);
            System.out.printf("Memory Shape:     %s\n", shape);
            System.out.printf("Flags:            0x%08X\n", flags);
            System.out.printf("Capacity:         %d\n", capacity);
            System.out.printf("Count:            %d\n", count);
            System.out.printf("Record Stride:    %d bytes\n", recordStride);
            System.out.printf("Layout ID:        0x%08X (\"%s\")\n", layoutId, layoutIdStr);
            System.out.printf("Created At:       %s (%d)\n", new Date(createdAt), createdAt);
            System.out.printf("Last Flush At:    %s (%d)\n", new Date(lastFlush), lastFlush);
            System.out.println("==================================================");

            if (shape == MemoryShape.BUNDLE) {
                System.out.println("\n[Note] This file is a BUNDLE shape.");
                System.out.println("       Run 'spector-inspect bundle <file-path>' to view full region directory listing.");
            }

        } catch (IOException e) {
            System.err.println("IO Error reading header: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void inspectBundle(Path path) {
        long size;
        try {
            size = Files.size(path);
            if (size < RegionPreamble.PREAMBLE_BYTES) {
                System.err.println("Error: File is too small to contain a valid Spector Memory Kernel header (size: " + size + " bytes).");
                System.exit(1);
            }
        } catch (IOException e) {
            System.err.println("Error reading file size: " + e.getMessage());
            System.exit(1);
            return;
        }

        try (Arena arena = Arena.ofShared();
             FileChannel fc = FileChannel.open(path, StandardOpenOption.READ)) {

            // Step 1: Read the bundle magic shape and max capacity from the main header
            MemorySegment initialSegment = fc.map(FileChannel.MapMode.READ_ONLY, 0, RegionPreamble.PREAMBLE_BYTES, arena);
            if (!RegionPreamble.isValid(initialSegment, 0)) {
                System.err.println("Error: Invalid or corrupted Spector Memory Kernel header.");
                System.exit(1);
            }

            MemoryShape shape = RegionPreamble.readShape(initialSegment, 0);
            if (shape != MemoryShape.BUNDLE) {
                System.err.println("Error: Specified file shape is " + shape + ", not BUNDLE.");
                System.err.println("       Use 'spector-inspect header <file-path>' for standard SMK files.");
                System.exit(1);
            }

            int maxRegions = (int) RegionPreamble.readCapacity(initialSegment, 0);
            long dirBytes = BundleDirectory.dataStartOffset(maxRegions);

            if (size < dirBytes) {
                System.err.println("Error: File size (" + size + " bytes) is smaller than required directory layout (" + dirBytes + " bytes).");
                System.exit(1);
            }

            // Step 2: Map the entire directory area to parse BundleSubHeader and RegionEntries
            MemorySegment dirSegment = fc.map(FileChannel.MapMode.READ_ONLY, 0, dirBytes, arena);
            BundleDirectory directory = BundleDirectory.read(dirSegment);

            int bundleMagic = directory.bundleMagic();
            String bundleMagicStr = decodeLayoutId(bundleMagic);
            int version = BundleSubHeader.readBundleVersion(dirSegment);
            long totalFileSize = BundleSubHeader.readTotalFileSize(dirSegment);
            long checksum = BundleSubHeader.readDirChecksum(dirSegment);
            long dataStartOffset = BundleSubHeader.readDataStartOffset(dirSegment);
            long createdAt = BundleSubHeader.readCreatedAtEpochMs(dirSegment);
            long lastModified = BundleSubHeader.readLastModifiedEpochMs(dirSegment);

            System.out.println("==================================================================================");
            System.out.println("Spector Memory Bundle Diagnostics: " + path.getFileName());
            System.out.println("==================================================================================");
            System.out.printf("Magic:              0x%08X (SMKM)\n", RegionPreamble.MAGIC);
            System.out.printf("Layout ID:          0x%08X (\"%s\")\n", BundleLayout.LAYOUT_ID, decodeLayoutId(BundleLayout.LAYOUT_ID));
            System.out.printf("Bundle Type:        0x%08X (\"%s\")\n", bundleMagic, bundleMagicStr);
            System.out.printf("Bundle Version:     %d\n", version);
            System.out.printf("File Size (Header): %d bytes\n", totalFileSize);
            System.out.printf("File Size (Actual): %d bytes\n", size);
            System.out.printf("Directory Checksum: 0x%016X\n", checksum);
            System.out.printf("Data Start Offset:  %d bytes (page-aligned)\n", dataStartOffset);
            System.out.printf("Created At:         %s (%d)\n", new Date(createdAt), createdAt);
            System.out.printf("Last Modified:      %s (%d)\n", new Date(lastModified), lastModified);
            System.out.printf("Regions Capacity:   %d max, %d live\n", maxRegions, directory.liveRegionCount());
            System.out.println("================================================----------------------------------");

            // Step 3: Print region entry statistics
            System.out.printf("%-2s %-16s %-6s %-12s %-12s %-12s %-8s %-8s %-10s %-7s\n",
                    "#", "Region ID", "Status", "Offset", "Allocated", "Used", "Capacity", "Stride", "Layout ID", "Version");
            System.out.println("----------------------------------------------------------------------------------");

            long totalAllocated = 0;
            long totalUsed = 0;
            long deadAllocated = 0;

            for (int i = 0; i < maxRegions; i++) {
                RegionEntry entry = RegionEntry.read(dirSegment, BundleDirectory.ENTRIES_OFFSET + (long) i * RegionEntry.ENTRY_BYTES);
                
                // Unused directory slot (skipped/placeholder)
                if (entry.regionId() == null) {
                    continue;
                }

                String statusStr = entry.isLive() ? "LIVE" : "DEAD";
                if (entry.isGrowable()) {
                    statusStr += "+G";
                }

                // Note: STRENGTH regions (RegionId.STRENGTH(4)) maintain persisted Layout ID 0x41554454 ('AUDT')
                // for bundle backward compatibility (ADR-0028), so layoutStr decodes as "AUDT" while region name is STRENGTH.
                String layoutStr = decodeLayoutId(entry.layoutId());

                System.out.printf("%-2d %-16s %-6s 0x%08X %-12d %-12d %-8d %-8d 0x%08X %-7d\n",
                        i,
                        entry.regionId().name(),
                        statusStr,
                        entry.offset(),
                        entry.allocatedSize(),
                        entry.usedSize(),
                        entry.capacity(),
                        entry.stride(),
                        entry.layoutId(),
                        entry.schemaVersion());

                if (entry.isLive()) {
                    totalAllocated += entry.allocatedSize();
                    totalUsed += entry.usedSize();
                } else {
                    deadAllocated += entry.allocatedSize();
                }

                // If region is live, read the inner region header if available
                if (entry.isLive() && entry.offset() > 0 && entry.allocatedSize() >= RegionPreamble.PREAMBLE_BYTES) {
                    long regionEnd = entry.offset() + entry.allocatedSize();
                    if (size >= regionEnd) {
                        try {
                            MemorySegment regionSegment = fc.map(FileChannel.MapMode.READ_ONLY, entry.offset(), RegionPreamble.PREAMBLE_BYTES, arena);
                            int magicVal = regionSegment.get(ValueLayout.JAVA_INT, 0);
                            
                            // If it starts with SMKM magic, print parsed stats
                            if (magicVal == RegionPreamble.MAGIC && RegionPreamble.isValid(regionSegment, 0)) {
                                long innerCount = RegionPreamble.readCount(regionSegment, 0);
                                long innerCapacity = RegionPreamble.readCapacity(regionSegment, 0);
                                MemoryShape innerShape = RegionPreamble.readShape(regionSegment, 0);
                                System.out.printf("  ↳ Inner Store: count=%d, capacity=%d, shape=%s, layout=\"%s\"\n",
                                        innerCount, innerCapacity, innerShape, layoutStr);
                            }
                        } catch (Exception ignored) {
                            // Suppress errors during inner store diagnostics
                        }
                    }
                }
            }

            System.out.println("==================================================================================");
            System.out.println("Fragmentation & Compaction Metrics");
            System.out.println("==================================================================================");
            System.out.printf("Live Regions Allocated Space:  %d bytes (%.2f MB)\n", totalAllocated, totalAllocated / (1024.0 * 1024.0));
            System.out.printf("Live Regions Used space:       %d bytes (%.2f MB)\n", totalUsed, totalUsed / (1024.0 * 1024.0));
            System.out.printf("Wasted Space (DEAD Regions):   %d bytes (%.2f MB)\n", deadAllocated, deadAllocated / (1024.0 * 1024.0));
            
            double fragPercent = 0.0;
            long totalSpace = totalAllocated + deadAllocated;
            if (totalSpace > 0) {
                fragPercent = (double) deadAllocated / totalSpace * 100.0;
            }
            System.out.printf("Fragmentation Ratio:           %.1f%%\n", fragPercent);

            if (deadAllocated > 0) {
                System.out.println("\n[Compaction Required] Wasted space detected from region relocation.");
                System.out.println("                      Compact the bundle offline to recover space.");
            } else {
                System.out.println("\n[Optimal] No fragmented or DEAD regions detected. Bundle is compact.");
            }
            System.out.println("==================================================================================");

        } catch (IOException e) {
            System.err.println("IO Error reading bundle: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String decodeLayoutId(int layoutId) {
        return String.format("%c%c%c%c",
                (char) (layoutId & 0xFF),
                (char) ((layoutId >> 8) & 0xFF),
                (char) ((layoutId >> 16) & 0xFF),
                (char) ((layoutId >> 24) & 0xFF));
    }
}
