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

import com.spectrayan.spector.memory.synapse.CognitiveRecordLayout;
import com.spectrayan.spector.memory.synapse.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.synapse.SynapticHeaderConstants;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryLayout;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayoutAdapter;
import com.spectrayan.spector.memory.kernel.shape.AbstractRecordMemory;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorStorageException;

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
 * Abstract base class for cognitive record memory stores (Prefrontal, Neocortex, Basal Ganglia).
 *
 * <p>Extends {@link AbstractRecordMemory} directly with {@link CognitiveRecordLayoutAdapter}
 * per ADR-013, completely replacing legacy {@code AbstractTierStore} nomenclature.</p>
 *
 * @see TierStore for the common interface
 */
public abstract class AbstractCognitiveRecordMemory 
        extends AbstractRecordMemory<CognitiveRecordLayoutAdapter> 
        implements TierStore {

    private static final Logger log = LoggerFactory.getLogger(AbstractCognitiveRecordMemory.class);

    private volatile MemoryId memoryId;
    private final CognitiveRecordLayoutAdapter layoutAdapter;

    /** Metadata header magic: "TIER" in ASCII. */
    static final int TIER_MAGIC = 0x54494552;

    /** Metadata header format version. */
    static final int TIER_VERSION = 1;

    /** Size of the metadata header in bytes. */
    public static final int METADATA_HEADER_BYTES = 64;

    // Metadata field offsets
    static final int META_MAGIC    = 0;
    static final int META_VERSION  = 4;
    static final int META_COUNT    = 8;
    static final int META_CAPACITY = 12;
    static final int META_STRIDE   = 16;
    static final int META_TIER_ORD = 20;
    static final int META_EXTRA1   = 24;
    static final int META_EXTRA2   = 28;

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
             new CognitiveRecordLayoutAdapter(new CognitiveRecordLayout(quantizedVecBytes)),
             capacity, sharedArena,
             sharedArena.allocate(segmentBytes, SynapticHeaderConstants.HEADER_BYTES),
             0, false, null, null);
    }

    /**
     * File-backed constructor — creates or opens a persistent mmap'd file.
     */
    protected AbstractCognitiveRecordMemory(int quantizedVecBytes, int capacity, long segmentBytes, Path filePath) {
        this(new CognitiveRecordLayout(quantizedVecBytes),
             new CognitiveRecordLayoutAdapter(new CognitiveRecordLayout(quantizedVecBytes)),
             capacity, segmentBytes, filePath, mmapFile(filePath, segmentBytes));
    }

    private AbstractCognitiveRecordMemory(CognitiveRecordLayout cogLayout,
                                         CognitiveRecordLayoutAdapter layoutAdapter,
                                         int capacity, long segmentBytes, Path filePath, MmapResult res) {
        super(MemoryId.of("tier", "pending"), layoutAdapter, capacity,
              res.arena, res.segment, 0, true, filePath, res.fileChannel);
        this.layout = cogLayout;
        this.layoutAdapter = layoutAdapter;
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
                                         CognitiveRecordLayoutAdapter layoutAdapter,
                                         int capacity, Arena arena, MemorySegment segment, int count,
                                         boolean persistent, Path filePath, FileChannel fileChannel) {
        super(MemoryId.of("tier", "pending"), layoutAdapter, capacity,
              arena, segment, count, persistent, filePath, fileChannel);
        this.layout = cogLayout;
        this.layoutAdapter = layoutAdapter;
        this.count = count;
    }

    /**
     * Writes the metadata header to the mapped segment.
     */
    protected void writeMetadata() {
        if (!persistent) return;
        segment.set(ValueLayout.JAVA_INT, META_MAGIC, TIER_MAGIC);
        segment.set(ValueLayout.JAVA_INT, META_VERSION, TIER_VERSION);
        segment.set(ValueLayout.JAVA_INT, META_COUNT, count);
        segment.set(ValueLayout.JAVA_INT, META_CAPACITY, capacity);
        segment.set(ValueLayout.JAVA_INT, META_STRIDE, layout.stride());
        segment.set(ValueLayout.JAVA_INT, META_TIER_ORD, type().ordinal());
    }

    /**
     * Reads the metadata header from the mapped segment.
     */
    protected void readMetadata() {
        int magic = segment.get(ValueLayout.JAVA_INT, META_MAGIC);
        if (magic != TIER_MAGIC) {
            log.warn("Invalid tier magic in {}: 0x{} (expected 0x{})",
                    filePath(), Integer.toHexString(magic), Integer.toHexString(TIER_MAGIC));
            this.count = 0;
            return;
        }
        this.count = segment.get(ValueLayout.JAVA_INT, META_COUNT);
    }

    /**
     * Persists the current count to the metadata header.
     */
    protected void persistCount() {
        if (persistent) {
            segment.set(ValueLayout.JAVA_INT, META_COUNT, count);
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
    public CognitiveRecordLayoutAdapter layout() {
        return layoutAdapter;
    }

    public CognitiveRecordLayout cognitiveLayout() {
        return layout;
    }

    @Override
    public MemorySegment primarySegment() {
        return segment;
    }

    public int capacity() {
        return capacity;
    }

    public MemorySegment segment() {
        return segment;
    }

    public boolean isPersistent() {
        return persistent;
    }

    public void force() {
        if (persistent && segment != null) {
            segment.force();
        }
    }

    public int tombstoneCount() {
        int tombstones = 0;
        long baseOffset = dataOffset();
        for (int i = 0; i < count; i++) {
            long offset = baseOffset + (long) i * layout.stride();
            CognitiveHeader header = layout.readHeader(segment, offset);
            if (SynapticHeaderConstants.isTombstoned(header.flags())) {
                tombstones++;
            }
        }
        return tombstones;
    }

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

    public CognitiveRecordLayoutAdapter kernelLayout() {
        return layoutAdapter;
    }

    public MemoryShape kernelShape() {
        return MemoryShape.RECORD;
    }

    @Override
    public void close() {
        log.info("{} closing ({} records, persistent={})", getClass().getSimpleName(), count, persistent);
        if (persistent) {
            try {
                if (segment != null) {
                    segment.force();
                }
            } catch (Exception e) {
                log.debug("Error forcing segment: {}", e.getMessage());
            }
        }
        super.close();
    }
}
