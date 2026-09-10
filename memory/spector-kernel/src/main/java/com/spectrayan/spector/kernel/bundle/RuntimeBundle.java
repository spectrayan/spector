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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorStorageException;


import com.spectrayan.spector.kernel.bundle.BundleFileLayoutCalculator;

import com.spectrayan.spector.kernel.bundle.BundleFileLayout;

import com.spectrayan.spector.kernel.id.SystemMemoryId;

import com.spectrayan.spector.kernel.id.MemoryId;

import com.spectrayan.spector.kernel.region.RegionId;
import com.spectrayan.spector.kernel.region.RegionEntry;
import com.spectrayan.spector.kernel.region.RegionSizeSpec;
import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.layout.RegionLayout;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.StampedLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * │ 64B RegionPreamble (SMKM)          │  shape=BUNDLE, layoutId=BUND
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
public final class RuntimeBundle implements AbstractBundle {

    private static final Logger log = LoggerFactory.getLogger(RuntimeBundle.class);

    /** Minimum growth per region expansion (64KB). */
    private static final long MIN_GROWTH_BYTES = 64 * 1024L;

    private final Path bundlePath;
    private final StampedLock remapLock = new StampedLock();
    private final java.util.concurrent.ConcurrentHashMap<RegionId, java.util.concurrent.atomic.AtomicInteger> generations = new java.util.concurrent.ConcurrentHashMap<>();

    private final java.util.concurrent.atomic.AtomicInteger activeLeases = new java.util.concurrent.atomic.AtomicInteger(0);
    private final ThreadLocal<Integer> threadLeaseCount = ThreadLocal.withInitial(() -> 0);
    private final Object leaseDrainLock = new Object();

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
        public static RuntimeBundle mmap(Path path, List<RegionSizeSpec> specs) {
            BundleFileLayoutCalculator.BundleComputedLayout computed =
                    BundleFileLayoutCalculator.compute(BundleSubHeader.MAGIC_RUNTIME, specs);

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
                        throw new SpectorStorageException(ErrorCode.RECORD_CRC_CORRUPTED, "Not a runtime bundle: magic=0x"
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
        public static RuntimeBundle heap(List<RegionSizeSpec> specs) {
            BundleFileLayoutCalculator.BundleComputedLayout computed =
                    BundleFileLayoutCalculator.compute(BundleSubHeader.MAGIC_RUNTIME, specs);

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
    @Override
    public MemorySegment currentSlice(RegionId id) {
        return regionSegment(id);
    }

    @Override
    public int generation(RegionId id) {
        return generations.computeIfAbsent(id, _ -> new java.util.concurrent.atomic.AtomicInteger(0)).get();
    }

    @Override
    public RegionLease lease(RegionId id) {
        int currentThreadLeases = threadLeaseCount.get();
        MemorySegment slice;
        if (currentThreadLeases > 0) {
            slice = this.regionSlices.get(id);
            if (slice == null) {
                throw new IllegalArgumentException("Region not found in runtime bundle: " + id);
            }
            threadLeaseCount.set(currentThreadLeases + 1);
            activeLeases.incrementAndGet();
        } else {
            long stamp = remapLock.readLock();
            try {
                slice = this.regionSlices.get(id);
                if (slice == null) {
                    throw new IllegalArgumentException("Region not found in runtime bundle: " + id);
                }
                threadLeaseCount.set(1);
                activeLeases.incrementAndGet();
            } finally {
                remapLock.unlockRead(stamp);
            }
        }
        return new RegionLease(slice, this::releaseLease);
    }

    private void releaseLease() {
        int remaining = threadLeaseCount.get() - 1;
        if (remaining <= 0) {
            threadLeaseCount.remove();
        } else {
            threadLeaseCount.set(remaining);
        }
        if (activeLeases.decrementAndGet() == 0) {
            synchronized (leaseDrainLock) {
                leaseDrainLock.notifyAll();
            }
        }
    }

    /**
     * Returns the count of currently outstanding active leases on this bundle.
     */
    public int activeLeases() {
        return activeLeases.get();
    }

    private void awaitActiveLeasesDrained() {
        synchronized (leaseDrainLock) {
            while (activeLeases.get() > 0) {
                try {
                    leaseDrainLock.wait(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while awaiting active leases drain", e);
                }
            }
        }
    }

    // ── Specialized Typed Region Openers ──

    public RegionRef checkpointRef() {
        return hasRegion(RegionId.CHECKPOINT) ? regionRef(RegionId.CHECKPOINT) : null;
    }

    public com.spectrayan.spector.kernel.store.WorkingMemory openWorking(int quantizedVecBytes, int capacity) {
        return com.spectrayan.spector.kernel.store.WorkingMemory.fromRegionRef(
                regionRef(RegionId.WORKING), capacity, quantizedVecBytes, bundlePath, isNew);
    }

    public com.spectrayan.spector.kernel.store.HebbianGraphMemory openHebbian(
            int capacity, int edgeCapacity, int maxDegree, com.spectrayan.spector.kernel.score.EdgeImportance edgeImportance) {
        return com.spectrayan.spector.kernel.store.HebbianGraphMemory.fromRegionRef(
                regionRef(RegionId.HEBBIAN), capacity, edgeCapacity, maxDegree, edgeImportance, bundlePath, isNew);
    }

    public com.spectrayan.spector.kernel.store.TypeRegistryMemory openRegistry(
            RegionId id, com.spectrayan.spector.kernel.id.SystemMemoryId sysId, String[] seedTypes) {
        return com.spectrayan.spector.kernel.store.TypeRegistryMemory.fromRegionRef(
                sysId, regionRef(id), bundlePath, isNew, seedTypes);
    }

    public com.spectrayan.spector.kernel.store.EntityDirectoryMemory openEntityDirectory() {
        return new com.spectrayan.spector.kernel.store.EntityDirectoryMemory(
                regionRef(RegionId.ENTITY_DIRECTORY), regionRef(RegionId.ENTITY_NAMES));
    }

    public com.spectrayan.spector.kernel.store.InsulaMemory openInsula() {
        return com.spectrayan.spector.kernel.store.InsulaMemory.fromRegionRef(
                regionRef(RegionId.INSULA), isNew);
    }

    // ── Generic RegionOpener Implementation ──

    @Override
    public <L extends com.spectrayan.spector.kernel.layout.RegionLayout> com.spectrayan.spector.kernel.shape.RecordMemory<L> openRecord(RegionId id, L layout) {
        RegionRef ref = regionRef(id);
        int count = hasRegion(id) ? (int) com.spectrayan.spector.kernel.region.RegionPreamble.readCount(currentSlice(id), 0L) : 0;
        return new com.spectrayan.spector.kernel.shape.DefaultRecordMemory<>(com.spectrayan.spector.kernel.id.MemoryId.of("bundle", id.name()), layout, 0, ref, count, bundlePath != null, bundlePath);
    }

    @Override
    public <L extends com.spectrayan.spector.kernel.layout.RegionLayout> com.spectrayan.spector.kernel.shape.AppendMemory<L> openAppend(RegionId id, L layout) {
        RegionRef ref = regionRef(id);
        int count = hasRegion(id) ? (int) com.spectrayan.spector.kernel.region.RegionPreamble.readCount(currentSlice(id), 0L) : 0;
        return new com.spectrayan.spector.kernel.shape.DefaultAppendMemory<>(com.spectrayan.spector.kernel.id.MemoryId.of("bundle", id.name()), layout, 0, ref, count, bundlePath != null, bundlePath);
    }

    @Override
    public <L extends com.spectrayan.spector.kernel.layout.RegionLayout> com.spectrayan.spector.kernel.shape.GraphMemory<L> openGraph(RegionId id, L layout) {
        RegionRef ref = regionRef(id);
        int count = hasRegion(id) ? (int) com.spectrayan.spector.kernel.region.RegionPreamble.readCount(currentSlice(id), 0L) : 0;
        return new com.spectrayan.spector.kernel.shape.DefaultGraphMemory<>(com.spectrayan.spector.kernel.id.MemoryId.of("bundle", id.name()), layout, 1000, 2000, ref, count, bundlePath != null, bundlePath);
    }

    @Override
    public <L extends com.spectrayan.spector.kernel.layout.RegionLayout> com.spectrayan.spector.kernel.shape.ChainMemory<L> openChain(RegionId id, L layout) {
        RegionRef ref = regionRef(id);
        int count = hasRegion(id) ? (int) com.spectrayan.spector.kernel.region.RegionPreamble.readCount(currentSlice(id), 0L) : 0;
        return new com.spectrayan.spector.kernel.shape.DefaultChainMemory<>(com.spectrayan.spector.kernel.id.MemoryId.of("bundle", id.name()), layout, 0, ref, count, bundlePath != null, bundlePath);
    }

    @Override
    public <L extends com.spectrayan.spector.kernel.layout.RegionLayout> com.spectrayan.spector.kernel.shape.HashTableMemory<L> openHashTable(RegionId id, L layout) {
        RegionRef ref = regionRef(id);
        int count = hasRegion(id) ? (int) com.spectrayan.spector.kernel.region.RegionPreamble.readCount(currentSlice(id), 0L) : 0;
        return new com.spectrayan.spector.kernel.shape.DefaultHashTableMemory<>(com.spectrayan.spector.kernel.id.MemoryId.of("bundle", id.name()), layout, 0, ref, count, bundlePath != null, bundlePath);
    }

    @Override
    public com.spectrayan.spector.kernel.shape.RegistryMemory openRegistry(RegionId id) {
        RegionRef ref = regionRef(id);
        int count = hasRegion(id) ? (int) com.spectrayan.spector.kernel.region.RegionPreamble.readCount(currentSlice(id), 0L) : 0;
        return new com.spectrayan.spector.kernel.shape.DefaultRegistryMemory(com.spectrayan.spector.kernel.id.MemoryId.of("bundle", id.name()), new com.spectrayan.spector.kernel.layout.RegistryLayout(), 0, ref, count, bundlePath != null, bundlePath);
    }

    @Override
    public <L extends com.spectrayan.spector.kernel.layout.RegionLayout> java.util.Optional<com.spectrayan.spector.kernel.shape.RecordMemory<L>> tryOpenRecord(RegionId id, L layout) {
        if (!hasRegion(id)) return java.util.Optional.empty();
        return java.util.Optional.of(openRecord(id, layout));
    }

    @Override
    public <L extends com.spectrayan.spector.kernel.layout.RegionLayout> java.util.Optional<com.spectrayan.spector.kernel.shape.AppendMemory<L>> tryOpenAppend(RegionId id, L layout) {
        if (!hasRegion(id)) return java.util.Optional.empty();
        return java.util.Optional.of(openAppend(id, layout));
    }

    @Override
    public <L extends com.spectrayan.spector.kernel.layout.RegionLayout> java.util.Optional<com.spectrayan.spector.kernel.shape.GraphMemory<L>> tryOpenGraph(RegionId id, L layout) {
        if (!hasRegion(id)) return java.util.Optional.empty();
        return java.util.Optional.of(openGraph(id, layout));
    }

    @Override
    public <L extends com.spectrayan.spector.kernel.layout.RegionLayout> java.util.Optional<com.spectrayan.spector.kernel.shape.ChainMemory<L>> tryOpenChain(RegionId id, L layout) {
        if (!hasRegion(id)) return java.util.Optional.empty();
        return java.util.Optional.of(openChain(id, layout));
    }

    @Override
    public <L extends com.spectrayan.spector.kernel.layout.RegionLayout> java.util.Optional<com.spectrayan.spector.kernel.shape.HashTableMemory<L>> tryOpenHashTable(RegionId id, L layout) {
        if (!hasRegion(id)) return java.util.Optional.empty();
        return java.util.Optional.of(openHashTable(id, layout));
    }

    @Override
    public java.util.Optional<com.spectrayan.spector.kernel.shape.RegistryMemory> tryOpenRegistry(RegionId id) {
        if (!hasRegion(id)) return java.util.Optional.empty();
        return java.util.Optional.of(openRegistry(id));
    }

    MemorySegment regionSegment(RegionId id) {
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
                throw new com.spectrayan.spector.commons.error.SpectorMemoryException(
                        com.spectrayan.spector.commons.error.ErrorCode.MEMORY_ID_NOT_FOUND, "Region is not live: " + id);
            }
            return slice;
        } finally {
            remapLock.unlockRead(stamp);
        }
    }

    @Override
    public boolean hasRegion(RegionId id) {
        long stamp = remapLock.tryOptimisticRead();
        Map<RegionId, MemorySegment> slices = this.regionSlices;
        if (remapLock.validate(stamp) && slices.containsKey(id)) {
            return true;
        }
        stamp = remapLock.readLock();
        try {
            return this.regionSlices.containsKey(id) || directory.findRegion(id) != null;
        } finally {
            remapLock.unlockRead(stamp);
        }
    }

    MemorySegment optionalRegionSegment(RegionId id) {
        if (!hasRegion(id)) {
            return null;
        }
        try {
            return regionSegment(id);
        } catch (Exception e) {
            return null;
        }
    }

    Arena arena() {
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
     * Ensures that the specified region has at least the required capacity in bytes.
     * If the current allocated capacity is less than requiredBytes, grows the region.
     *
     * @param regionId the region identifier
     * @param requiredBytes the minimum required capacity in bytes
     */
    @Override
    public void ensureCapacity(RegionId regionId, long requiredBytes) {
        RegionEntry entry = directory.findRegion(regionId);
        if (entry != null && entry.allocatedSize() >= requiredBytes) {
            return;
        }
        growRegion(regionId, requiredBytes);
    }

    /**
     * Grows a region by relocating it to the tail of the file with doubled size.
     *
     * <p>This operation acquires the write lock, awaits draining of all active leases,
     * unmaps the entire bundle, extends the file, copies old data to the tail, and
     * remaps everything. All cached region slices are refreshed.</p>
     *
     * <p>For heap bundles, this operation is not supported and throws
     * {@link UnsupportedOperationException}.</p>
     *
     * @param regionId the region to grow
     * @throws UnsupportedOperationException if this is a heap bundle
     * @throws UncheckedIOException if the file operations fail
     */
    public void growRegion(RegionId regionId) {
        growRegion(regionId, 0);
    }

    /**
     * Grows a region by relocating it to the tail of the file with at least
     * the specified minimum capacity.
     *
     * @param regionId the region to grow
     * @param minRequiredBytes the minimum required capacity in bytes (or 0 for default 2x growth)
     * @throws UnsupportedOperationException if this is a heap bundle
     * @throws UncheckedIOException if the file operations fail
     */
    public void growRegion(RegionId regionId, long minRequiredBytes) {
        if (bundlePath == null) {
            throw new UnsupportedOperationException("Cannot grow heap bundle regions");
        }

        long stamp = remapLock.writeLock();
        try {
            awaitActiveLeasesDrained();

            RegionEntry oldEntry = directory.findRegion(regionId);
            if (oldEntry == null) {
                throw new IllegalArgumentException("Region not found: " + regionId);
            }

            // Compute new size: 2x old or minimum growth, whichever is larger, or at least minRequiredBytes
            long target = Math.max(oldEntry.allocatedSize() * 2, oldEntry.allocatedSize() + MIN_GROWTH_BYTES);
            if (minRequiredBytes > target) {
                target = minRequiredBytes;
            }
            long newAllocatedSize = BundleFileLayoutCalculator.alignToPage(target);

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
                long tailOffset = BundleFileLayoutCalculator.alignToPage(oldFileSize);
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
                generations.computeIfAbsent(regionId, _ -> new java.util.concurrent.atomic.AtomicInteger(0)).incrementAndGet();

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
     * Updates the recorded used size for a specific region.
     *
     * @param regionId the region identifier
     * @param usedSize the new used byte size
     */
    public void updateRegionUsedSize(RegionId regionId, long usedSize) {
        long stamp = remapLock.writeLock();
        try {
            RegionEntry old = directory.findRegion(regionId);
            if (old != null && old.usedSize() != usedSize) {
                RegionEntry updated = old.withUsedSize(usedSize);
                BundleDirectory newDir = directory.withUpdatedRegion(regionId, updated);
                newDir.write(masterSegment);
                this.directory = newDir;
            }
        } finally {
            remapLock.unlockWrite(stamp);
        }
    }

    /**
     * Calculates the estimated dead space in bytes caused by region relocations and fragmentation.
     *
     * @return number of dead or uncompacted bytes in the bundle file
     */
    public long deadSpaceBytes() {
        if (bundlePath == null) return 0L;
        try {
            long fileSize = Files.size(bundlePath);
            long dataStart = BundleDirectory.dataStartOffset(directory.maxRegions());
            long liveAllocated = 0;
            for (RegionEntry entry : directory.liveRegions()) {
                liveAllocated += entry.allocatedSize();
            }
            long liveRequired = BundleFileLayoutCalculator.alignToPage(dataStart + liveAllocated);
            return Math.max(0L, fileSize - liveRequired);
        } catch (IOException e) {
            return 0L;
        }
    }

    /**
     * Returns the fragmentation ratio (dead space / total file size).
     *
     * @return a value between 0.0 and 1.0
     */
    public float fragmentationRatio() {
        if (bundlePath == null) return 0f;
        try {
            long fileSize = Files.size(bundlePath);
            if (fileSize == 0) return 0f;
            long dead = deadSpaceBytes();
            return (float) dead / fileSize;
        } catch (IOException e) {
            return 0f;
        }
    }

    /**
     * Checks if the bundle has dead regions or uncompacted space.
     *
     * @return true if dead space exists
     */
    public boolean hasDeadRegions() {
        return deadSpaceBytes() > 0;
    }

    /**
     * Compacts the bundle file by defragmenting all live regions into contiguous layout
     * starting at {@code dataStartOffset}, eliminating dead space left by region relocations,
     * and truncating the underlying file on disk.
     *
     * <p>Acquires an exclusive write lock, extracts live region payloads, unmaps the segment,
     * rewrites and truncates the file, remaps with a fresh shared arena, and refreshes
     * all cached region slices.</p>
     *
     * @return the number of bytes reclaimed, or 0 if no compaction was needed
     * @throws UncheckedIOException if file I/O operations fail
     */
    public long compact() {
        if (bundlePath == null) {
            return 0L;
        }

        long stamp = remapLock.writeLock();
        try {
            long initialFileSize = Files.size(bundlePath);
            List<RegionEntry> live = directory.liveRegions();
            long dataStart = BundleDirectory.dataStartOffset(directory.maxRegions());

            // Check if compaction is needed
            long expectedOffset = dataStart;
            boolean needsCompaction = false;
            for (RegionEntry entry : live) {
                long aligned = BundleFileLayoutCalculator.alignToPage(expectedOffset);
                if (entry.offset() != aligned) {
                    needsCompaction = true;
                    break;
                }
                expectedOffset = aligned + entry.allocatedSize();
            }
            long expectedFileSize = BundleFileLayoutCalculator.alignToPage(expectedOffset);
            if (!needsCompaction && expectedFileSize >= initialFileSize) {
                log.debug("Runtime bundle already compact: {}", bundlePath);
                return 0L;
            }

            // 1. Copy data of all live regions into memory buffers
            List<byte[]> regionPayloads = new java.util.ArrayList<>(live.size());
            List<RegionEntry> compactedEntries = new java.util.ArrayList<>(live.size());
            long currentOffset = dataStart;

            for (RegionEntry oldEntry : live) {
                byte[] data = new byte[(int) oldEntry.allocatedSize()];
                MemorySegment.copy(masterSegment, oldEntry.offset(),
                        MemorySegment.ofArray(data), 0, oldEntry.allocatedSize());
                regionPayloads.add(data);

                long newOffset = BundleFileLayoutCalculator.alignToPage(currentOffset);
                RegionEntry newEntry = new RegionEntry(
                        oldEntry.regionId(),
                        oldEntry.flags(),
                        newOffset,
                        oldEntry.allocatedSize(),
                        oldEntry.usedSize(),
                        oldEntry.capacity(),
                        oldEntry.stride(),
                        oldEntry.layoutId(),
                        oldEntry.schemaVersion()
                );
                compactedEntries.add(newEntry);
                currentOffset = newOffset + oldEntry.allocatedSize();
            }

            long newTotalFileSize = BundleFileLayoutCalculator.alignToPage(currentOffset);

            // 2. Unmap old master segment
            arena.close();
            log.debug("Unmapped bundle for compaction: {}", bundlePath);

            // 3. Rebuild bundle file contiguously
            try (FileChannel fc = FileChannel.open(bundlePath,
                    StandardOpenOption.READ, StandardOpenOption.WRITE)) {

                // Truncate to new compacted size
                fc.truncate(newTotalFileSize);
                fc.write(ByteBuffer.allocate(1), newTotalFileSize - 1);

                // Create new shared arena and remap
                Arena newArena = Arena.ofShared();
                MemorySegment newMapped = fc.map(FileChannel.MapMode.READ_WRITE, 0, newTotalFileSize, newArena);

                // Copy all region payloads to their new contiguous positions
                for (int i = 0; i < compactedEntries.size(); i++) {
                    RegionEntry entry = compactedEntries.get(i);
                    byte[] payload = regionPayloads.get(i);
                    MemorySegment.copy(MemorySegment.ofArray(payload), 0,
                            newMapped, entry.offset(), payload.length);
                }

                // Create and write updated BundleDirectory
                BundleDirectory newDir = new BundleDirectory(
                        directory.bundleMagic(), directory.maxRegions(), compactedEntries);
                newDir.write(newMapped);

                // Update volatile references
                this.arena = newArena;
                this.masterSegment = newMapped;
                this.directory = newDir;
                this.regionSlices = buildSliceMap(newMapped, newDir);

                long reclaimed = Math.max(0L, initialFileSize - newTotalFileSize);
                log.info("Compacted runtime bundle: {} ({}KB → {}KB, reclaimed {}KB)",
                        bundlePath, initialFileSize / 1024, newTotalFileSize / 1024, reclaimed / 1024);
                return reclaimed;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to compact runtime bundle " + bundlePath, e);
        } finally {
            remapLock.unlockWrite(stamp);
        }
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

    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    /**
     * Returns true if there are any active region leases.
     */
    public boolean hasActiveLeases() {
        return activeLeases.get() > 0;
    }

    /**
     * Returns true if this runtime bundle is closed or its arena has been closed.
     */
    public boolean isClosed() {
        return isClosed.get() || (arena != null && !arena.scope().isAlive());
    }

    /**
     * Flushes and closes the bundle. All region segments become invalid.
     */
    @Override
    public void close() {
        if (!isClosed.compareAndSet(false, true)) {
            return;
        }
        long stamp = remapLock.writeLock();
        try {
            awaitActiveLeasesDrained();
            if (bundlePath != null && arena != null && arena.scope().isAlive()) {
                directory.write(masterSegment);
                masterSegment.force();
            }
            if (arena != null && arena.scope().isAlive()) {
                arena.close();
            }
        } catch (Exception e) {
            log.debug("Error flushing/closing runtime bundle: {}", e.getMessage());
        } finally {
            remapLock.unlockWrite(stamp);
        }
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
