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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * A V4 partition bundle — packs 4 cognitive tier regions (Semantic, Episodic,
 * Procedural, Text) into a single mmap'd file with one shared {@link Arena}.
 *
 * <h3>On-Disk Format</h3>
 * <pre>
 * ┌─────────────────────────────────────┐  offset 0
 * │ 64B MemoryHeader (SMKM)            │  shape=BUNDLE, layoutId=BUND
 * ├─────────────────────────────────────┤  offset 64
 * │ 64B BundleSubHeader (SPTB)         │  magic=SPTB, totalFileSize, etc.
 * ├─────────────────────────────────────┤  offset 128
 * │ RegionEntry[0..3] (64B × 4)        │  SEMANTIC, EPISODIC, PROCEDURAL, TEXT
 * ├─────────────────────────────────────┤  page-aligned (4096B)
 * │ Region 0: SEMANTIC data            │
 * │ Region 1: EPISODIC data            │
 * │ Region 2: PROCEDURAL data          │
 * │ Region 3: TEXT data                │
 * └─────────────────────────────────────┘
 * </pre>
 *
 * <p>Follows the {@code EntityDirectory.Init} factory pattern:
 * {@code Init.mmap()} to create, {@code Init.open()} to load,
 * {@code Init.heap()} for tests.</p>
 *
 * <p>Partition regions are <b>fixed-size</b> — they never grow in place.
 * When a partition overflows, the system rolls to a new partition directory
 * with a fresh bundle file.</p>
 *
 * @since 1.2.0
 * @see BundleDirectory
 * @see BundleLayoutCalculator
 */
public final class PartitionBundle implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PartitionBundle.class);

    private final Arena arena;
    private final MemorySegment masterSegment;
    private final BundleDirectory directory;
    private final Path bundlePath;
    private final boolean isNew;

    private PartitionBundle(Arena arena, MemorySegment masterSegment,
                             BundleDirectory directory, Path bundlePath, boolean isNew) {
        this.arena = arena;
        this.masterSegment = masterSegment;
        this.directory = directory;
        this.bundlePath = bundlePath;
        this.isNew = isNew;
    }

    // ── Init factory (follows EntityDirectory.Init pattern) ──

    /**
     * Factory methods for creating and opening partition bundles.
     */
    public static final class Init {

        private Init() {} // static utility

        /**
         * Creates a new partition bundle file with 4 regions.
         *
         * <p>Computes the total file size from the region specs, creates the file,
         * maps it, writes the directory header, and returns an open bundle.</p>
         *
         * @param path               path to the new bundle file
         * @param semanticCapacity   max records for semantic region
         * @param episodicCapacity   max records for episodic region
         * @param proceduralCapacity max records for procedural region
         * @param textBytes          allocated bytes for the text append region
         * @param quantizedVecBytes  bytes per quantized vector (for stride calculation)
         * @param cognitiveLayoutId  the layoutId from CognitiveRecordLayout
         * @param cognitiveSchemaVer the schemaVersion from CognitiveRecordLayout
         * @param textLayoutId       the layoutId from TextBlobLayout
         * @param textSchemaVer      the schemaVersion from TextBlobLayout
         * @return an open PartitionBundle ready for use
         */
        public static PartitionBundle mmap(Path path,
                                            int semanticCapacity, int episodicCapacity,
                                            int proceduralCapacity, long textBytes,
                                            int quantizedVecBytes,
                                            int cognitiveLayoutId, int cognitiveSchemaVer,
                                            int textLayoutId, int textSchemaVer) {
            int cogStride = computeCognitiveStride(quantizedVecBytes);
            List<BundleLayoutCalculator.RegionSizeSpec> specs = List.of(
                    new BundleLayoutCalculator.RegionSizeSpec(
                            RegionId.SEMANTIC,
                            MemoryHeader.HEADER_BYTES + (long) semanticCapacity * cogStride,
                            semanticCapacity, cogStride, cognitiveLayoutId, cognitiveSchemaVer, false),
                    new BundleLayoutCalculator.RegionSizeSpec(
                            RegionId.EPISODIC,
                            MemoryHeader.HEADER_BYTES + (long) episodicCapacity * cogStride,
                            episodicCapacity, cogStride, cognitiveLayoutId, cognitiveSchemaVer, false),
                    new BundleLayoutCalculator.RegionSizeSpec(
                            RegionId.PROCEDURAL,
                            MemoryHeader.HEADER_BYTES + (long) proceduralCapacity * cogStride,
                            proceduralCapacity, cogStride, cognitiveLayoutId, cognitiveSchemaVer, false),
                    new BundleLayoutCalculator.RegionSizeSpec(
                            RegionId.TEXT,
                            MemoryHeader.HEADER_BYTES + textBytes,
                            0, 0, textLayoutId, textSchemaVer, false)
            );

            BundleLayoutCalculator.BundleComputedLayout computed =
                    BundleLayoutCalculator.compute(BundleSubHeader.MAGIC_PARTITION, specs);

            long totalFileSize = computed.totalFileSize();
            BundleDirectory dir = computed.directory();

            Arena arena = Arena.ofShared();
            try {
                // Create and size the file
                try (FileChannel fc = FileChannel.open(path,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                    // Pre-allocate via write at end
                    fc.write(java.nio.ByteBuffer.allocate(1), totalFileSize - 1);
                    MemorySegment mapped = fc.map(FileChannel.MapMode.READ_WRITE, 0, totalFileSize, arena);
                    // fc is closed after map per Option C (P0 hotfix)
                    dir.write(mapped);
                    log.info("Created partition bundle: {} ({} regions, {}KB)",
                            path, specs.size(), totalFileSize / 1024);
                    return new PartitionBundle(arena, mapped, dir, path, true);
                }
            } catch (IOException e) {
                arena.close();
                throw new UncheckedIOException("Failed to create partition bundle: " + path, e);
            }
        }

        /**
         * Opens an existing partition bundle file, validates headers.
         *
         * @param path path to the existing bundle file
         * @return an open PartitionBundle
         */
        public static PartitionBundle open(Path path) {
            Arena arena = Arena.ofShared();
            try {
                long fileSize = Files.size(path);
                try (FileChannel fc = FileChannel.open(path,
                        StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                    MemorySegment mapped = fc.map(FileChannel.MapMode.READ_WRITE, 0, fileSize, arena);
                    // fc closed after map per Option C

                    BundleDirectory dir = BundleDirectory.read(mapped);
                    log.info("Opened partition bundle: {} ({} live regions, {}KB)",
                            path, dir.liveRegionCount(), fileSize / 1024);
                    return new PartitionBundle(arena, mapped, dir, path, false);
                }
            } catch (IOException e) {
                arena.close();
                throw new UncheckedIOException("Failed to open partition bundle: " + path, e);
            }
        }

        /**
         * Creates an in-memory (heap) partition bundle for testing.
         *
         * @param semanticCapacity   max records for semantic region
         * @param episodicCapacity   max records for episodic region
         * @param proceduralCapacity max records for procedural region
         * @param textBytes          allocated bytes for the text append region
         * @param quantizedVecBytes  bytes per quantized vector
         * @param cognitiveLayoutId  the layoutId from CognitiveRecordLayout
         * @param cognitiveSchemaVer the schemaVersion from CognitiveRecordLayout
         * @param textLayoutId       the layoutId from TextBlobLayout
         * @param textSchemaVer      the schemaVersion from TextBlobLayout
         * @return an in-memory PartitionBundle
         */
        public static PartitionBundle heap(int semanticCapacity, int episodicCapacity,
                                            int proceduralCapacity, long textBytes,
                                            int quantizedVecBytes,
                                            int cognitiveLayoutId, int cognitiveSchemaVer,
                                            int textLayoutId, int textSchemaVer) {
            int cogStride = computeCognitiveStride(quantizedVecBytes);
            List<BundleLayoutCalculator.RegionSizeSpec> specs = List.of(
                    new BundleLayoutCalculator.RegionSizeSpec(
                            RegionId.SEMANTIC,
                            MemoryHeader.HEADER_BYTES + (long) semanticCapacity * cogStride,
                            semanticCapacity, cogStride, cognitiveLayoutId, cognitiveSchemaVer, false),
                    new BundleLayoutCalculator.RegionSizeSpec(
                            RegionId.EPISODIC,
                            MemoryHeader.HEADER_BYTES + (long) episodicCapacity * cogStride,
                            episodicCapacity, cogStride, cognitiveLayoutId, cognitiveSchemaVer, false),
                    new BundleLayoutCalculator.RegionSizeSpec(
                            RegionId.PROCEDURAL,
                            MemoryHeader.HEADER_BYTES + (long) proceduralCapacity * cogStride,
                            proceduralCapacity, cogStride, cognitiveLayoutId, cognitiveSchemaVer, false),
                    new BundleLayoutCalculator.RegionSizeSpec(
                            RegionId.TEXT,
                            MemoryHeader.HEADER_BYTES + textBytes,
                            0, 0, textLayoutId, textSchemaVer, false)
            );

            BundleLayoutCalculator.BundleComputedLayout computed =
                    BundleLayoutCalculator.compute(BundleSubHeader.MAGIC_PARTITION, specs);

            long totalSize = computed.totalFileSize();
            BundleDirectory dir = computed.directory();

            Arena arena = Arena.ofShared();
            MemorySegment segment = arena.allocate(totalSize, 4096);
            dir.write(segment);

            log.info("Created in-memory partition bundle ({} regions, {}KB)", specs.size(), totalSize / 1024);
            return new PartitionBundle(arena, segment, dir, null, true);
        }

        /**
         * Computes the cognitive record stride from the quantized vector bytes.
         * This mirrors CognitiveRecordLayout.stride() = SynapticHeaderConstants.HEADER_BYTES + quantizedVecBytes
         */
        private static int computeCognitiveStride(int quantizedVecBytes) {
            // SynapticHeaderConstants.HEADER_BYTES = 64
            return 64 + quantizedVecBytes;
        }
    }

    // ── Public API ──

    /**
     * Returns the region slice for the specified region.
     *
     * <p>The returned segment starts with a 64-byte SMKM {@link MemoryHeader}
     * followed by the region's data area. Stores should use this segment
     * in their {@code fromBundle()} factories.</p>
     *
     * @param id the region identifier (must be a partition region: SEMANTIC, EPISODIC, PROCEDURAL, TEXT)
     * @return a MemorySegment slice of the master segment for the region
     * @throws IllegalArgumentException if the region is not found or not live
     */
    public MemorySegment regionSegment(RegionId id) {
        RegionEntry entry = directory.findRegion(id);
        if (entry == null) {
            throw new IllegalArgumentException("Region not found in partition bundle: " + id);
        }
        if (!entry.isLive()) {
            throw new IllegalStateException("Region is not live: " + id);
        }
        return masterSegment.asSlice(entry.offset(), entry.allocatedSize());
    }

    /**
     * Returns the shared arena. Stores must <b>not</b> close this arena — the bundle owns it.
     */
    public Arena arena() {
        return arena;
    }

    /**
     * Returns the bundle directory with all region entries.
     */
    public BundleDirectory directory() {
        return directory;
    }

    /**
     * Returns the path to the bundle file (null for heap bundles).
     */
    public Path bundlePath() {
        return bundlePath;
    }

    /**
     * Whether this bundle was just created (regions need header initialization).
     */
    public boolean isNew() {
        return isNew;
    }

    /**
     * Flushes the directory and region data to disk, then closes the arena.
     *
     * <p>After close, all region segments become invalid. Stores backed by
     * this bundle must have already been closed (which flushes their slices).</p>
     */
    @Override
    public void close() {
        try {
            if (bundlePath != null) {
                directory.write(masterSegment);
                masterSegment.force();
            }
        } catch (Exception e) {
            log.debug("Error flushing partition bundle: {}", e.getMessage());
        }
        arena.close();
        log.info("Closed partition bundle: {}", bundlePath);
    }
}
