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
package com.spectrayan.spector.memory.cortex;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorStorageException;
import com.spectrayan.spector.memory.error.SpectorPartitionFrozenException;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.RegionPreamble;
import com.spectrayan.spector.memory.kernel.SystemMemoryId;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import com.spectrayan.spector.memory.kernel.shape.AbstractRecordMemory;
import com.spectrayan.spector.memory.model.MemoryType;

/**
 * Base implementation for all engram memory stores in Spector Memory,
 * extending {@link AbstractRecordMemory} directly and implementing {@link EngramMemory}.
 *
 * <p>Standardizes on Kernel 64-byte {@link RegionPreamble} for header management and
 * implements full type-safe contracts for SWMR visibility and off-heap memory management.</p>
 *
 * @see EngramMemory for the common interface
 */
public abstract class AbstractEngramMemory 
        extends AbstractRecordMemory<CognitiveRecordLayout> 
        implements EngramMemory {

    private static final Logger log = LoggerFactory.getLogger(AbstractEngramMemory.class);

    /** Legacy metadata header magic: "TIER" in ASCII (0x54494552). */
    public static final int TIER_MAGIC = 0x54494552;

    /** Metadata header extra field for working memory circular index (offset 60 in RegionPreamble). */
    public static final int META_EXTRA1 = 60;

    /**
     * Size of the {@link RegionPreamble} that prefixes a store file, in bytes.
     *
     * <p>This is the region prologue, not a per-engram encoding header — see
     * {@link RegionPreamble} for why the two are named differently.</p>
     */
    public static final int METADATA_PREAMBLE_BYTES = RegionPreamble.PREAMBLE_BYTES;

    private static final class MmapResult {
        final Arena arena;
        final MemorySegment segment;
        final FileChannel fileChannel;
        final boolean isNew;
        MmapResult(Arena arena, MemorySegment segment, FileChannel fileChannel, boolean isNew) {
            this.arena = arena;
            this.segment = segment;
            this.fileChannel = fileChannel;
            this.isNew = isNew;
        }
    }

    private static MmapResult mmapFile(Path filePath, long segmentBytes) {
        Arena arena = Arena.ofShared();
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            long totalBytes = METADATA_PREAMBLE_BYTES + segmentBytes;
            boolean isNew = !Files.exists(filePath) || Files.size(filePath) < METADATA_PREAMBLE_BYTES;
            FileChannel fc = FileChannel.open(filePath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE);
            if (isNew) {
                fc.position(totalBytes - 1);
                fc.write(ByteBuffer.wrap(new byte[]{0}));
            }
            long mapSize = Math.max(totalBytes, fc.size());
            MemorySegment mapped = fc.map(FileChannel.MapMode.READ_WRITE, 0, mapSize, arena);
            fc.close();
            return new MmapResult(arena, mapped, null, isNew);
        } catch (IOException e) {
            throw new SpectorStorageException(ErrorCode.MMAP_FAILED, e, filePath);
        }
    }

    /**
     * Volatile constructor — allocates a single contiguous off-heap segment (no file).
     *
     * @param type the cognitive tier this store represents; used to derive the stable
     *             {@link MemoryId} up-front so identity is final and lock-free.
     */
    protected AbstractEngramMemory(MemoryType type, int quantizedVecBytes, int capacity, long segmentBytes) {
        this(type, quantizedVecBytes, capacity, segmentBytes, Arena.ofShared());
    }

    private AbstractEngramMemory(MemoryType type, int quantizedVecBytes, int capacity, long segmentBytes, Arena sharedArena) {
        this(type, new CognitiveRecordLayout(quantizedVecBytes),
             capacity, sharedArena,
             sharedArena.allocate(segmentBytes, SynapticHeaderConstants.HEADER_BYTES),
             0, false, null, null);
    }

    /**
     * File-backed constructor — creates or opens a persistent mmap'd file.
     *
     * @param type the cognitive tier this store represents; used to derive the stable
     *             {@link MemoryId} up-front so identity is final and lock-free.
     */
    protected AbstractEngramMemory(MemoryType type, int quantizedVecBytes, int capacity, long segmentBytes, Path filePath) {
        this(type, new CognitiveRecordLayout(quantizedVecBytes),
             capacity, segmentBytes, filePath, mmapFile(filePath, segmentBytes));
    }

    private AbstractEngramMemory(MemoryType type, CognitiveRecordLayout cogLayout,
                                  int capacity, long segmentBytes, Path filePath, MmapResult res) {
        super(tierId(type), cogLayout, capacity,
              res.arena, res.segment, 0, true, filePath, res.fileChannel);
        if (res.isNew) {
            setCount(0);
            writeMetadata();
            log.info("{} created new persistent file: {} ({}KB)",
                    getClass().getSimpleName(), filePath, (METADATA_PREAMBLE_BYTES + segmentBytes) / 1024);
        } else {
            readMetadata();
            publishVisible();
            log.info("{} loaded from persistent file: {} ({} records)",
                    getClass().getSimpleName(), filePath, count);
        }
    }

    private AbstractEngramMemory(MemoryType type, CognitiveRecordLayout cogLayout,
                                  int capacity, Arena arena, MemorySegment segment, int count,
                                  boolean persistent, Path filePath, FileChannel fileChannel) {
        super(tierId(type), cogLayout, capacity,
              arena, segment, count, persistent, filePath, fileChannel);
        setCount(count);
    }

    /**
     * Bundle-backed constructor — adopts a pre-sliced region segment from a bundle.
     *
     * <p>The region slice already contains a 64-byte {@link RegionPreamble} at offset 0
     * followed by record data. The count is read from the region's own SMKM header.
     * The arena is shared across all bundle regions and is <b>not</b> owned by this store.</p>
     *
     * @param type         the cognitive tier (SEMANTIC, EPISODIC, PROCEDURAL)
     * @param cogLayout    the cognitive record layout (determines stride, vector dims)
     * @param capacity     the maximum number of records in this region
     * @param arena        the shared arena from the owning bundle (NOT owned by this store)
     * @param regionSlice  the memory segment sliced from the bundle's master segment
     * @param bundlePath   the path to the bundle file (for diagnostics)
     * @param isNew        true if the region was just created and needs header initialization
     */
    protected AbstractEngramMemory(MemoryType type, CognitiveRecordLayout cogLayout,
                                    int capacity, Arena arena, MemorySegment regionSlice,
                                    Path bundlePath, boolean isNew) {
        super(tierId(type), cogLayout, capacity,
              arena, regionSlice,
              isNew ? 0 : (int) RegionPreamble.readCount(regionSlice, 0),
              true, bundlePath, null, true);  // bundleManaged=true
        if (isNew) {
            setCount(0);
            writeMetadata();
            log.info("{} initialized new bundle region in: {} ({}KB)",
                    getClass().getSimpleName(), bundlePath, regionSlice.byteSize() / 1024);
        } else {
            readMetadata();
            publishVisible();
            log.info("{} loaded from bundle region in: {} ({} records)",
                    getClass().getSimpleName(), bundlePath, count);
        }
    }

    /** Derives the stable, tier-scoped identity for this store (e.g. {@code tier/semantic}). */
    private static MemoryId tierId(MemoryType type) {
        return switch (type) {
            case WORKING -> SystemMemoryId.WORKING.id();
            case SEMANTIC -> SystemMemoryId.SEMANTIC.id();
            case PROCEDURAL -> SystemMemoryId.PROCEDURAL.id();
            case EPISODIC -> SystemMemoryId.EPISODIC.id();
        };
    }

    /**
     * Writes the metadata header to the mapped segment using standard Kernel RegionPreamble.
     */
    protected void writeMetadata() {
        if (!persistent) return;
        long now = System.currentTimeMillis();
        RegionPreamble.write(segment, 0, 1, MemoryShape.RECORD, 1, capacity, count,
                layout.stride(), layout.layoutId(), now, now);
    }

    /**
     * Reads the metadata header from the mapped segment.
     */
    protected void readMetadata() {
        if (RegionPreamble.isValid(segment, 0)) {
            setCount((int) RegionPreamble.readCount(segment, 0));
            return;
        }
        // Fallback for legacy TIER header
        int magic = segment.get(ValueLayout.JAVA_INT, 0);
        if (magic == TIER_MAGIC) {
            setCount(segment.get(ValueLayout.JAVA_INT, 8));
        } else {
            log.warn("Invalid header magic in {}: 0x{}", filePath(), Integer.toHexString(magic));
            setCount(0);
        }
    }

    /**
     * Persists the current count to the metadata header.
     */
    protected void persistCount() {
        if (persistent) {
            if (RegionPreamble.isValid(segment, 0)) {
                RegionPreamble.writeCount(segment, 0, count);
            } else {
                segment.set(ValueLayout.JAVA_INT, 8, count);
            }
        }
    }

    /**
     * Returns the byte offset where data records begin.
     */
    public long dataOffset() {
        return persistent ? METADATA_PREAMBLE_BYTES : 0;
    }

    @Override
    public int size() {
        return count;
    }

    @Override
    public CognitiveRecordLayout layout() {
        return layout;
    }

    @Override
    public CognitiveRecordLayout cognitiveLayout() {
        return layout;
    }

    @Override
    public long recordOffset(long index) {
        return dataOffset() + index * layout.stride();
    }

    @Override
    public long write(long recordId, MemorySegment recordBytes) {
        long offset = recordOffset(recordId);
        MemorySegment.copy(recordBytes, 0, segment, offset, Math.min(recordBytes.byteSize(), layout.stride()));
        return offset;
    }

    @Override
    public void read(long recordId, MemorySegment dest) {
        long offset = recordOffset(recordId);
        MemorySegment.copy(segment, offset, dest, 0, Math.min(dest.byteSize(), layout.stride()));
    }

    @Override
    public MemorySegment primarySegment() {
        return segment;
    }

    @Override
    public MemorySegment segment() {
        return segment;
    }

    @Override
    public MemorySegment headerSlab() {
        return segment;
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public boolean isPersistent() {
        return persistent;
    }

    @Override
    public Path filePath() {
        return filePath;
    }

    public int tombstoneCount() {
        if (count == 0) return 0;
        int tombstones = 0;
        long base = dataOffset();
        int stride = layout.stride();
        for (int i = 0; i < count; i++) {
            byte flags = layout.readFlags(segment, base + (long) i * stride);
            if (SynapticHeaderConstants.isTombstoned(flags)) {
                tombstones++;
            }
        }
        return tombstones;
    }

    @Override
    public float tombstoneRatio() {
        if (count == 0) return 0.0f;
        return (float) tombstoneCount() / count;
    }

    /**
     * Returns the stable, tier-scoped identity of this store (e.g. {@code tier/semantic}).
     *
     * <p>The identity is initialized up-front from the tier {@link MemoryType} passed to
     * the constructor and held by the kernel base ({@link #id()}), so this accessor is
     * lock-free and allocation-free.</p>
     */
    public MemoryId memoryId() {
        return id();
    }

    @Override
    public int schemaVersion() {
        // The layout is the single source of truth for the record schema version (#434 TD-06).
        return layout().schemaVersion();
    }

    @Override
    public MemoryShape shape() {
        return MemoryShape.RECORD;
    }

    @Override
    public boolean isFrozen() {
        return frozen;
    }

    @Override
    public void force() {
        if (frozen) return;
        super.flush();
    }

    protected final java.util.concurrent.locks.ReentrantLock writeLock = new java.util.concurrent.locks.ReentrantLock();
    private volatile boolean frozen = false;

    public void markFrozen() {
        this.frozen = true;
    }

    public void append(CognitiveRecordLayout.CognitiveHeader header, byte[] quantizedVec) {
        if (frozen) throw new SpectorPartitionFrozenException(type().name());
        writeLock.lock();
        try {
            long maxCapacity = Math.min(capacity(), (segment().byteSize() - dataOffset()) / layout.stride());
            if (count >= maxCapacity) {
                throw new com.spectrayan.spector.memory.error.SpectorMemoryTierFullException(type().name(), (int) maxCapacity);
            }
            long offset = dataOffset() + (long) count * layout.stride();
            layout.writeHeader(segment(), offset, header);
            if (quantizedVec != null) {
                int copyLen = Math.min(quantizedVec.length, layout.quantizedVecBytes());
                MemorySegment.copy(MemorySegment.ofArray(quantizedVec), 0,
                        segment(), layout.vectorOffset(offset), copyLen);
            }
            count++;
            persistCount();
            publishVisible();
        } finally { writeLock.unlock(); }
    }

    protected int getCount() {
        return count;
    }

    protected void setCount(int c) {
        this.count = c;
    }
}
