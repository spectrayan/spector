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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorStorageException;
import com.spectrayan.spector.memory.kernel.shape.AbstractRecordMemory;
import com.spectrayan.spector.memory.kernel.MemoryHeader;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.synapse.CognitiveRecordLayout;
import com.spectrayan.spector.memory.synapse.SynapticHeaderConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Base implementation for all cognitive record memory stores in Spector Memory,
 * extending {@link AbstractRecordMemory} directly and implementing {@link CognitiveRecordMemory}.
 *
 * <p>Standardizes on Kernel 64-byte {@link MemoryHeader} for header management and
 * implements full type-safe contracts for SWMR visibility and off-heap memory management.</p>
 *
 * @see CognitiveRecordMemory for the common interface
 */
public abstract class AbstractCognitiveRecordMemory 
        extends AbstractRecordMemory<CognitiveRecordLayout> 
        implements CognitiveRecordMemory {

    private static final Logger log = LoggerFactory.getLogger(AbstractCognitiveRecordMemory.class);

    private volatile MemoryId memoryId;

    /** Legacy metadata header magic: "TIER" in ASCII (0x54494552). */
    public static final int TIER_MAGIC = 0x54494552;

    /** Metadata header extra field for working memory circular index (offset 60 in MemoryHeader). */
    public static final int META_EXTRA1 = 60;

    /** Size of the metadata header in bytes. */
    public static final int METADATA_HEADER_BYTES = MemoryHeader.HEADER_BYTES;

    // ── SWMR Visibility Barrier ──
    private static final VarHandle VISIBLE_COUNT_HANDLE;
    static {
        try {
            VISIBLE_COUNT_HANDLE = MethodHandles.lookup()
                    .findVarHandle(AbstractCognitiveRecordMemory.class, "maxVisibleRecord", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    protected final CognitiveRecordLayout layout;
    protected int count = 0;

    @SuppressWarnings("unused") // accessed via VarHandle
    private volatile int maxVisibleRecord = 0;

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
            long totalBytes = METADATA_HEADER_BYTES + segmentBytes;
            boolean isNew = !Files.exists(filePath) || Files.size(filePath) < METADATA_HEADER_BYTES;
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
            return new MmapResult(arena, mapped, fc, isNew);
        } catch (IOException e) {
            throw new SpectorStorageException(ErrorCode.MMAP_FAILED, e, filePath);
        }
    }

    /**
     * Volatile constructor — allocates a single contiguous off-heap segment (no file).
     */
    protected AbstractCognitiveRecordMemory(int quantizedVecBytes, int capacity, long segmentBytes) {
        this(quantizedVecBytes, capacity, segmentBytes, Arena.ofShared());
    }

    private AbstractCognitiveRecordMemory(int quantizedVecBytes, int capacity, long segmentBytes, Arena sharedArena) {
        this(new CognitiveRecordLayout(quantizedVecBytes),
             capacity, sharedArena,
             sharedArena.allocate(segmentBytes, SynapticHeaderConstants.HEADER_BYTES),
             0, false, null, null);
    }

    /**
     * File-backed constructor — creates or opens a persistent mmap'd file.
     */
    protected AbstractCognitiveRecordMemory(int quantizedVecBytes, int capacity, long segmentBytes, Path filePath) {
        this(new CognitiveRecordLayout(quantizedVecBytes),
             capacity, segmentBytes, filePath, mmapFile(filePath, segmentBytes));
    }

    private AbstractCognitiveRecordMemory(CognitiveRecordLayout cogLayout,
                                          int capacity, long segmentBytes, Path filePath, MmapResult res) {
        super(MemoryId.of("tier", "pending"), cogLayout, capacity,
              res.arena, res.segment, 0, true, filePath, res.fileChannel);
        this.layout = cogLayout;
        if (res.isNew) {
            this.count = 0;
            writeMetadata();
            log.info("{} created new persistent file: {} ({}KB)",
                    getClass().getSimpleName(), filePath, (METADATA_HEADER_BYTES + segmentBytes) / 1024);
        } else {
            readMetadata();
            publishVisible();
            log.info("{} loaded from persistent file: {} ({} records)",
                    getClass().getSimpleName(), filePath, count);
        }
    }

    private AbstractCognitiveRecordMemory(CognitiveRecordLayout cogLayout,
                                          int capacity, Arena arena, MemorySegment segment, int count,
                                          boolean persistent, Path filePath, FileChannel fileChannel) {
        super(MemoryId.of("tier", "pending"), cogLayout, capacity,
              arena, segment, count, persistent, filePath, fileChannel);
        this.layout = cogLayout;
        this.count = count;
    }

    /**
     * Writes the metadata header to the mapped segment using standard Kernel MemoryHeader.
     */
    protected void writeMetadata() {
        if (!persistent) return;
        long now = System.currentTimeMillis();
        MemoryHeader.write(segment, 0, 1, MemoryShape.RECORD, 1, capacity, count,
                layout.stride(), layout.layoutId(), now, now);
    }

    /**
     * Reads the metadata header from the mapped segment.
     */
    protected void readMetadata() {
        if (MemoryHeader.isValid(segment, 0)) {
            this.count = (int) MemoryHeader.readCount(segment, 0);
            return;
        }
        // Fallback for legacy TIER header
        int magic = segment.get(ValueLayout.JAVA_INT, 0);
        if (magic == TIER_MAGIC) {
            this.count = segment.get(ValueLayout.JAVA_INT, 8);
        } else {
            log.warn("Invalid header magic in {}: 0x{}", filePath(), Integer.toHexString(magic));
            this.count = 0;
        }
    }

    /**
     * Persists the current count to the metadata header.
     */
    protected void persistCount() {
        if (persistent) {
            if (MemoryHeader.isValid(segment, 0)) {
                MemoryHeader.writeCount(segment, 0, count);
            } else {
                segment.set(ValueLayout.JAVA_INT, 8, count);
            }
        }
    }

    /**
     * Returns the byte offset where data records begin.
     */
    public long dataOffset() {
        return persistent ? METADATA_HEADER_BYTES : 0;
    }

    @Override
    public int size() {
        return count;
    }

    @Override
    public int visibleCount() {
        return (int) VISIBLE_COUNT_HANDLE.getAcquire(this);
    }

    protected void publishVisible() {
        VISIBLE_COUNT_HANDLE.setRelease(this, count);
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

    public MemoryId memoryId() {
        MemoryId id = this.memoryId;
        if (id == null) {
            synchronized (this) {
                id = this.memoryId;
                if (id == null) {
                    id = MemoryId.of("tier", type().name().toLowerCase());
                    this.memoryId = id;
                }
            }
        }
        return id;
    }

    @Override
    public int schemaVersion() {
        return 1;
    }

    @Override
    public MemoryShape shape() {
        return MemoryShape.RECORD;
    }

    @Override
    public void force() {
        super.flush();
    }
}
