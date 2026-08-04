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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.StampedLock;

/**
 * A V4 runtime bundle — packs all global runtime stores (Working, Co-Activation,
 * Index, Hebbian Graph, Temporal, Entity Directory, BM25, etc.) into a single
 * mmap'd file with one shared {@link Arena}.
 *
 * <h3>Key Differences from {@link PartitionBundle}</h3>
 * <ul>
 *   <li>Fields ({@code arena}, {@code masterSegment}, {@code directory}) are
 *       {@code volatile} — they change during region growth/remap</li>
 *   <li>{@link StampedLock} ({@code remapLock}) protects concurrent access
 *       during growth operations</li>
 *   <li>{@link #growRegion(RegionId)} — relocates a region to the tail of the
 *       file with doubled capacity, then remaps the entire bundle</li>
 *   <li>Cached region slices via {@code regionSlices} map are refreshed after
 *       each remap</li>
 * </ul>
 *
 * <h3>On-Disk Format</h3>
 * <pre>
 * ┌─────────────────────────────────────┐  offset 0
 * │ 64B MemoryHeader (SMKM)            │  shape=BUNDLE, layoutId=BUND
 * ├─────────────────────────────────────┤  offset 64
 * │ 64B BundleSubHeader (SRTB)         │  magic=SRTB, totalFileSize, etc.
 * ├─────────────────────────────────────┤  offset 128
 * │ RegionEntry[0..N] (64B × N)        │  runtime regions
 * ├─────────────────────────────────────┤  page-aligned (4096B)
 * │ Region data...                     │  variable per region
 * └─────────────────────────────────────┘
 * </pre>
 *
 * <h3>Growth Protocol</h3>
 * <ol>
 *   <li>Acquire {@code remapLock.writeLock()}</li>
 *   <li>{@code arena.close()} — unmap entire bundle (all slices invalidated)</li>
 *   <li>Open file, extend it, copy old region data to tail</li>
 *   <li>Mark old region entry DEAD, add new entry at tail</li>
 *   <li>Create new {@code Arena.ofShared()}, remap entire file</li>
 *   <li>Write updated directory, refresh all region slices</li>
 *   <li>Release write lock</li>
 * </ol>
 *
 * @since 1.2.0
 * @see PartitionBundle
 * @see BundleManager
 */
public final class RuntimeBundle implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RuntimeBundle.class);

    /** Minimum growth per region expansion (64KB). */
    private static final long MIN_GROWTH_BYTES = 64 * 1024L;

    private final Path bundlePath;
    private final StampedLock remapLock = new StampedLock();

    // Volatile fields — updated atomically under write lock during remap
    private volatile Arena arena;
    private volatile MemorySegment masterSegment;
    private volatile BundleDirectory directory;

    // Cached region slices — refreshed after each remap
    private volatile Map<RegionId, MemorySegment> regionSlices;

    private final boolean isNew;

    private RuntimeBundle(Arena arena, MemorySegment masterSegment,
                           BundleDirectory directory, Path bundlePath,
                           boolean isNew) {
        this.arena = arena;
        this.masterSegment = masterSegment;
        this.directory = directory;
        this.bundlePath = bundlePath;
        this.isNew = isNew;
        this.regionSlices = buildSliceMap(masterSegment, directory);
    }

    // ── Init factory (follows EntityDirectory.Init pattern) ──

    /**
     * Factory methods for creating and opening runtime bundles.
     */
    public static final class Init {

        private Init() {} // static utility

        /**
         * Creates a new runtime bundle file with the specified region specs.
         *
         * @param path  path to the new bundle file
         * @param specs region specifications (computed by the caller from config)
         * @return an open RuntimeBundle ready for use
         */
        public static RuntimeBundle mmap(Path path, List<BundleLayoutCalculator.RegionSizeSpec> specs) {
            BundleLayoutCalculator.BundleComputedLayout computed =
                    BundleLayoutCalculator.compute(BundleSubHeader.MAGIC_RUNTIME, specs);

            long totalFileSize = computed.totalFileSize();
            BundleDirectory dir = computed.directory();

            Arena arena = Arena.ofShared();
            try {
                try (FileChannel fc = FileChannel.open(path,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                    fc.write(ByteBuffer.allocate(1), totalFileSize - 1);
                    MemorySegment mapped = fc.map(FileChannel.MapMode.READ_WRITE, 0, totalFileSize, arena);
                    // fc closed after map per Option C (P0 hotfix)
                    dir.write(mapped);
                    log.info("Created runtime bundle: {} ({} regions, {}KB)",
                            path, specs.size(), totalFileSize / 1024);
                    return new RuntimeBundle(arena, mapped, dir, path, true);
                }
            } catch (IOException e) {
                arena.close();
                throw new UncheckedIOException("Failed to create runtime bundle: " + path, e);
            }
        }

        /**
         * Opens an existing runtime bundle file, validates headers.
         *
         * @param path path to the existing bundle file
         * @return an open RuntimeBundle
         */
        public static RuntimeBundle open(Path path) {
            Arena arena = Arena.ofShared();
            try {
                long fileSize = Files.size(path);
                try (FileChannel fc = FileChannel.open(path,
                        StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                    MemorySegment mapped = fc.map(FileChannel.MapMode.READ_WRITE, 0, fileSize, arena);
                    // fc closed after map per Option C

                    BundleDirectory dir = BundleDirectory.read(mapped);
                    if (dir.bundleMagic() != BundleSubHeader.MAGIC_RUNTIME) {
                        throw new IllegalStateException("Not a runtime bundle: magic=0x"
                                + Integer.toHexString(dir.bundleMagic()));
                    }
                    log.info("Opened runtime bundle: {} ({} live regions, {}KB)",
                            path, dir.liveRegionCount(), fileSize / 1024);
                    return new RuntimeBundle(arena, mapped, dir, path, false);
                }
            } catch (IOException e) {
                arena.close();
                throw new UncheckedIOException("Failed to open runtime bundle: " + path, e);
            }
        }

        /**
         * Creates an in-memory (heap) runtime bundle for testing.
         *
         * @param specs region specifications
         * @return an in-memory RuntimeBundle
         */
        public static RuntimeBundle heap(List<BundleLayoutCalculator.RegionSizeSpec> specs) {
            BundleLayoutCalculator.BundleComputedLayout computed =
                    BundleLayoutCalculator.compute(BundleSubHeader.MAGIC_RUNTIME, specs);

            long totalSize = computed.totalFileSize();
            BundleDirectory dir = computed.directory();

            Arena arena = Arena.ofShared();
            MemorySegment segment = arena.allocate(totalSize, 4096);
            dir.write(segment);

            log.info("Created in-memory runtime bundle ({} regions, {}KB)", specs.size(), totalSize / 1024);
            return new RuntimeBundle(arena, segment, dir, null, true);
        }
    }

    // ── Public API ──

    /**
     * Returns the region slice for the specified region.
     *
     * <p>Uses optimistic read from the {@code StampedLock} for lock-free access
     * in the common (non-growth) case. Falls back to a pessimistic read lock
     * only if a concurrent growth is detected.</p>
     *
     * @param id the region identifier
     * @return a MemorySegment slice of the master segment for the region
     * @throws IllegalArgumentException if the region is not found
     * @throws IllegalStateException if the region is not live
     */
    public MemorySegment regionSegment(RegionId id) {
        // Fast path: optimistic read
        long stamp = remapLock.tryOptimisticRead();
        Map<RegionId, MemorySegment> slices = this.regionSlices;
        MemorySegment slice = slices.get(id);
        if (remapLock.validate(stamp) && slice != null) {
            return slice;
        }

        // Slow path: pessimistic read lock
        stamp = remapLock.readLock();
        try {
            slice = this.regionSlices.get(id);
            if (slice == null) {
                RegionEntry entry = directory.findRegion(id);
                if (entry == null) {
                    throw new IllegalArgumentException("Region not found in runtime bundle: " + id);
                }
                throw new IllegalStateException("Region is not live: " + id);
            }
            return slice;
        } finally {
            remapLock.unlockRead(stamp);
        }
    }

    /**
     * Returns the shared arena. Stores must <b>not</b> close this arena.
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

    // ── Region Growth ──

    /**
     * Grows a region by relocating it to the tail of the file with doubled size.
     *
     * <p>This operation acquires the write lock, unmaps the entire bundle,
     * extends the file, copies old data to the tail, and remaps everything.
     * All cached region slices are refreshed.</p>
     *
     * <p>For heap bundles, this operation is not supported and throws
     * {@link UnsupportedOperationException}.</p>
     *
     * @param regionId the region to grow
     * @throws UnsupportedOperationException if this is a heap bundle
     * @throws UncheckedIOException if the file operations fail
     */
    public void growRegion(RegionId regionId) {
        if (bundlePath == null) {
            throw new UnsupportedOperationException("Cannot grow heap bundle regions");
        }

        long stamp = remapLock.writeLock();
        try {
            RegionEntry oldEntry = directory.findRegion(regionId);
            if (oldEntry == null) {
                throw new IllegalArgumentException("Region not found: " + regionId);
            }

            // Compute new size: 2x old or minimum growth, whichever is larger
            long newAllocatedSize = BundleLayoutCalculator.alignToPage(
                    Math.max(oldEntry.allocatedSize() * 2, oldEntry.allocatedSize() + MIN_GROWTH_BYTES));

            // Read old region data before unmapping
            byte[] oldData = new byte[(int) oldEntry.allocatedSize()];
            MemorySegment.copy(masterSegment, oldEntry.offset(),
                    MemorySegment.ofArray(oldData), 0, oldEntry.allocatedSize());

            // Close old arena — unmaps entire bundle
            arena.close();
            log.debug("Unmapped bundle for growth: {} (region {})", bundlePath, regionId);

            try (FileChannel fc = FileChannel.open(bundlePath,
                    StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                long oldFileSize = fc.size();
                long tailOffset = BundleLayoutCalculator.alignToPage(oldFileSize);
                long newFileSize = tailOffset + newAllocatedSize;

                // Extend file
                fc.write(ByteBuffer.allocate(1), newFileSize - 1);

                // Create new arena and remap entire file
                Arena newArena = Arena.ofShared();
                MemorySegment newMapped = fc.map(FileChannel.MapMode.READ_WRITE, 0, newFileSize, newArena);
                // fc closed after this block per Option C

                // Copy old region data to tail
                MemorySegment.copy(MemorySegment.ofArray(oldData), 0,
                        newMapped, tailOffset, oldData.length);

                // Update directory — create new entry at tail, mark old as dead
                RegionEntry newEntry = new RegionEntry(
                        regionId, oldEntry.flags(), tailOffset, newAllocatedSize,
                        oldEntry.usedSize(), oldEntry.capacity() * 2,
                        oldEntry.stride(), oldEntry.layoutId(), oldEntry.schemaVersion());

                // Mark old as dead (FLAG_LIVE removed) and add new live entry
                BundleDirectory newDir = directory.withUpdatedRegion(regionId, newEntry);

                // Write updated directory to the new mapping
                newDir.write(newMapped);

                // Update volatile references
                this.arena = newArena;
                this.masterSegment = newMapped;
                this.directory = newDir;
                this.regionSlices = buildSliceMap(newMapped, newDir);

                log.info("Grew region {} in runtime bundle: {} ({}KB → {}KB, tail@{})",
                        regionId, bundlePath,
                        oldEntry.allocatedSize() / 1024, newAllocatedSize / 1024,
                        tailOffset);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to grow region " + regionId + " in " + bundlePath, e);
        } finally {
            remapLock.unlockWrite(stamp);
        }
    }

    /**
     * Returns the usage ratio (usedSize / allocatedSize) for a region.
     *
     * @param regionId the region to check
     * @return a value between 0.0 and 1.0
     */
    public float regionUsage(RegionId regionId) {
        RegionEntry entry = directory.findRegion(regionId);
        if (entry == null || entry.allocatedSize() == 0) return 0f;
        return (float) entry.usedSize() / entry.allocatedSize();
    }

    /**
     * Flushes the directory to the master segment and forces the segment to disk.
     */
    public void flush() {
        if (bundlePath != null) {
            directory.write(masterSegment);
            masterSegment.force();
        }
    }

    /**
     * Flushes and closes the bundle. All region segments become invalid.
     */
    @Override
    public void close() {
        try {
            if (bundlePath != null) {
                directory.write(masterSegment);
                masterSegment.force();
            }
        } catch (Exception e) {
            log.debug("Error flushing runtime bundle: {}", e.getMessage());
        }
        arena.close();
        log.info("Closed runtime bundle: {}", bundlePath);
    }

    // ── Internal ──

    /**
     * Builds the region slice cache from the master segment and directory.
     * Only includes LIVE regions.
     */
    private static Map<RegionId, MemorySegment> buildSliceMap(MemorySegment masterSeg, BundleDirectory dir) {
        Map<RegionId, MemorySegment> slices = new HashMap<>();
        for (RegionEntry entry : dir.liveRegions()) {
            slices.put(entry.regionId(), masterSeg.asSlice(entry.offset(), entry.allocatedSize()));
        }
        return Map.copyOf(slices);
    }
}
