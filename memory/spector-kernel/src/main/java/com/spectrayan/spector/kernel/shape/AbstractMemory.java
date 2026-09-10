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
package com.spectrayan.spector.kernel.shape;

import com.spectrayan.spector.kernel.sync.MemoryWal;

import com.spectrayan.spector.kernel.id.MemoryId;

import com.spectrayan.spector.kernel.shape.MemoryShape;

import com.spectrayan.spector.kernel.layout.RegionLayout;

import com.spectrayan.spector.kernel.region.RegionPreamble;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorStorageException;
import com.spectrayan.spector.kernel.bundle.RegionRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Abstract base class for all {@link Memory} implementations.
 *
 * <p>Provides common infrastructure for both volatile (in-memory) and
 * persistent (file-backed) memory structures. Manages the lifecycle of
 * off-heap memory via {@link Arena} and ensures thread-safe SWMR
 * (Single Writer Multiple Reader) visibility using {@link VarHandle}.</p>
 *
 * @param <L> the type of memory layout used by this memory
 */
public abstract class AbstractMemory<L extends RegionLayout> implements Memory<L> {

    private static final Logger log = LoggerFactory.getLogger(AbstractMemory.class);

    // ── SWMR Visibility Barrier ──
    private static final VarHandle VISIBLE_COUNT_HANDLE;
    static {
        try {
            VISIBLE_COUNT_HANDLE = MethodHandles.lookup()
                    .findVarHandle(AbstractMemory.class, "visibleCount", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    protected final MemoryId id;
    protected final L layout;
    protected final Arena arena;
    protected final MemorySegment segment;
    protected final RegionRef regionRef;
    protected final int capacity;
    protected final boolean persistent;
    protected final boolean bundleManaged;
    protected int count = 0;
    protected FileChannel fileChannel;
    protected final Path filePath;

    @SuppressWarnings("unused") // accessed via VarHandle
    private volatile int visibleCount = 0;

    protected MemoryWal wal;
    protected boolean bypassWal = false;

    /**
     * Binds a Write-Ahead Log (WAL) to this memory.
     */
    @Override
    public void bindWal(MemoryWal wal) {
        this.wal = wal;
    }

    /**
     * Sets whether WAL writes should be bypassed (useful during recovery/replay).
     */
    @Override
    public void setBypassWal(boolean bypassWal) {
        this.bypassWal = bypassWal;
    }

    /**
     * Returns whether WAL writes are bypassed.
     */
    @Override
    public boolean isBypassWal() {
        return this.bypassWal;
    }

    @Override
    public boolean isBundleManaged() {
        return this.bundleManaged;
    }

    /**
     * Returns the bound Write-Ahead Log, if any.
     */
    @Override
    public MemoryWal getWal() {
        return this.wal;
    }

    /**
     * Volatile constructor — allocates memory off-heap without a backing file.
     *
     * @param id           the unique identifier for this memory
     * @param layout       the layout configuration
     * @param capacity     the maximum number of records
     * @param segmentBytes the total bytes to allocate
     */
    protected AbstractMemory(MemoryId id, L layout, int capacity, long segmentBytes) {
        this.id = id;
        this.layout = layout;
        this.capacity = capacity;
        this.persistent = false;
        this.bundleManaged = false;
        this.filePath = null;
        this.regionRef = null;
        this.arena = Arena.ofShared();
        this.segment = arena.allocate(segmentBytes, 64);
    }

    /**
     * Wrapping constructor — adopts a pre-made Arena and segment.
     *
     * <p>Used for deep composition: the caller (e.g., {@code AbstractEngramMemory})
     * manages mmap lifecycle and header format, then wraps the result in a
     * kernel {@code Memory} for standardized identity, shape, and accessor methods.</p>
     *
     * <p><b>Ownership:</b> The caller transfers ownership of the arena and segment
     * to this instance. {@link #close()} will close the arena and file channel.</p>
     *
     * @param id          the unique identifier for this memory
     * @param layout      the layout configuration
     * @param capacity    the maximum number of records
     * @param arena       the pre-made arena (caller transfers ownership)
     * @param segment     the pre-made segment (must belong to the arena)
     * @param count       the initial record count (restored from header)
     * @param persistent  whether this memory is file-backed
     * @param filePath    the file path (null for volatile)
     * @param fileChannel the file channel (null for volatile; caller transfers ownership)
     */
    protected AbstractMemory(MemoryId id, L layout, int capacity,
                             Arena arena, MemorySegment segment, int count,
                             boolean persistent, Path filePath, FileChannel fileChannel) {
        this(id, layout, capacity, arena, segment, count, persistent, filePath, fileChannel, false);
    }

    /**
     * Wrapping constructor with bundle-managed flag.
     *
     * <p>When {@code bundleManaged} is {@code true}, this store's {@link #close()} will
     * flush the segment but will <b>not</b> close the shared Arena — the owning bundle
     * manages arena lifecycle. This is used by {@code fromBundle()} factory methods.</p>
     *
     * @param bundleManaged {@code true} if this store is hosted inside a bundle and
     *                      does not own the Arena; {@code false} for standalone stores
     */
    protected AbstractMemory(MemoryId id, L layout, int capacity,
                             Arena arena, MemorySegment segment, int count,
                             boolean persistent, Path filePath, FileChannel fileChannel,
                             boolean bundleManaged) {
        this.id = id;
        this.layout = layout;
        this.capacity = capacity;
        this.arena = arena;
        this.segment = segment;
        this.regionRef = null;
        this.count = count;
        this.persistent = persistent;
        this.bundleManaged = bundleManaged;
        this.filePath = filePath;
        this.fileChannel = fileChannel;
        if (count > 0) {
            publishVisible();
        }
    }

    /**
     * Bundle-managed constructor with {@link RegionRef} dynamic rebinding.
     *
     * <p>When managed by a bundle, the segment is not captured permanently; instead,
     * calls to {@link #segment()} resolve dynamically through {@code regionRef.resolve()}
     * so that background growth and remapping are observed immediately without stale handle defects.</p>
     *
     * @param id         the unique identifier for this memory
     * @param layout     the layout configuration
     * @param capacity   the maximum number of records
     * @param regionRef  the region reference for dynamic resolution
     * @param count      the initial record count
     * @param persistent whether this memory is file-backed
     * @param filePath   the bundle file path
     */
    protected AbstractMemory(MemoryId id, L layout, int capacity,
                             RegionRef regionRef, int count,
                             boolean persistent, Path filePath) {
        this.id = id;
        this.layout = layout;
        this.capacity = capacity;
        this.regionRef = regionRef;
        this.arena = null;
        this.segment = regionRef != null ? regionRef.resolve() : null;
        this.count = count;
        this.persistent = persistent;
        this.bundleManaged = true;
        this.filePath = filePath;
        this.fileChannel = null;
        if (count > 0) {
            publishVisible();
        }
    }

    /**
     * File-backed constructor — creates or opens a persistent memory-mapped file.
     *
     * @param id           the unique identifier for this memory
     * @param layout       the layout configuration
     * @param capacity     the maximum number of records
     * @param segmentBytes the total data bytes (excluding header)
     * @param filePath     the path to the backing file
     */
    protected AbstractMemory(MemoryId id, L layout, int capacity, long segmentBytes, Path filePath) {
        this.bundleManaged = false;
        this.id = id;
        this.layout = layout;
        this.capacity = capacity;
        this.persistent = true;
        this.filePath = filePath;
        this.regionRef = null;
        this.arena = Arena.ofShared();

        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            long totalBytes = RegionPreamble.PREAMBLE_BYTES + segmentBytes;
            boolean isNew = !Files.exists(filePath) || Files.size(filePath) < RegionPreamble.PREAMBLE_BYTES;

            fileChannel = FileChannel.open(filePath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE);

            if (isNew) {
                fileChannel.position(totalBytes - 1);
                fileChannel.write(ByteBuffer.wrap(new byte[]{0}));
            }

            long mapSize = Math.max(totalBytes, fileChannel.size());
            this.segment = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, mapSize, arena);

            fileChannel.close();
            this.fileChannel = null;

            if (isNew) {
                this.count = 0;
                RegionPreamble.write(segment, 0, layout.schemaVersion(), shape(),
                        0x01, // flags: persistent
                        capacity, 0, layout.recordStride(), layout.layoutId(),
                        System.currentTimeMillis(), System.currentTimeMillis());
                log.info("Created new persistent memory: {} ({}KB)", filePath, totalBytes / 1024);
            } else {
                this.count = (int) RegionPreamble.readCount(segment, 0);
                publishVisible();
                log.info("Loaded persistent memory: {} ({} records)", filePath, count);
            }
        } catch (IOException e) {
            throw new SpectorStorageException(ErrorCode.MMAP_FAILED, e, filePath);
        }
    }

    /**
     * Publishes the current count as visible to concurrent readers.
     */
    protected void publishVisible() {
        VISIBLE_COUNT_HANDLE.setRelease(this, count);
    }

    /**
     * Returns the number of records visible to concurrent readers.
     * Uses acquire semantics for SWMR visibility.
     *
     * @return the visible record count
     */
    public int visibleCount() {
        return (int) VISIBLE_COUNT_HANDLE.getAcquire(this);
    }

    /**
     * Returns the byte offset where data records begin.
     *
     * @return the data offset
     */
    public long dataOffset() {
        return persistent ? RegionPreamble.PREAMBLE_BYTES : 0;
    }

    /**
     * Persists the current count to the metadata header.
     */
    protected void persistCount() {
        if (persistent || bundleManaged) {
            MemorySegment seg = segment();
            if (seg != null && RegionPreamble.isValid(seg, 0)) {
                RegionPreamble.writeCount(seg, 0, count);
            }
        }
    }

    @Override
    public MemoryId id() {
        return id;
    }


    @Override
    public L layout() {
        return layout;
    }

    @Override
    public Arena arena() {
        return arena;
    }

    @Override
    public MemorySegment segment() {
        if (regionRef != null) {
            return regionRef.resolve();
        }
        return segment;
    }

    /**
     * Returns the bound {@link RegionRef}, or {@code null} if this memory is not bundle-managed.
     */
    public RegionRef regionRef() {
        return regionRef;
    }

    @Override
    public MemorySegment headerSegment() {
        if (persistent) {
            MemorySegment seg = segment();
            if (seg != null) {
                return seg.asSlice(0, RegionPreamble.PREAMBLE_BYTES);
            }
        }
        return null;
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public int size() {
        MemorySegment seg = regionRef != null ? regionRef.resolve() : segment;
        if (seg != null && RegionPreamble.isValid(seg, 0)) {
            return (int) RegionPreamble.readCount(seg, 0L);
        }
        return count;
    }

    @Override
    public int schemaVersion() {
        return layout.schemaVersion();
    }

    /**
     * Returns the structural shape of this memory.
     * Must be implemented by each shape subclass.
     */
    @Override
    public abstract MemoryShape shape();

    public boolean isPersistent() {
        return persistent;
    }

    public Path filePath() {
        return filePath;
    }

    @Override
    public void flush() {
        if (persistent) {
            try {
                MemorySegment seg = segment();
                if (seg != null && seg.scope().isAlive()) {
                    seg.force();
                }
            } catch (Exception e) {
                log.debug("Error forcing segment during flush: {}", e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        log.info("Closing memory {} ({} records, persistent={}, bundleManaged={})", id, count, persistent, bundleManaged);
        if (persistent) {
            try {
                MemorySegment seg = segment();
                if (seg != null && seg.scope().isAlive()) {
                    seg.force();
                }
            } catch (Exception e) {
                log.debug("Error forcing segment: {}", e.getMessage());
            }
        }
        if (!bundleManaged && arena != null) {
            arena.close();
        }
        if (fileChannel != null) {
            try {
                fileChannel.close();
            } catch (IOException e) {
                log.debug("Error closing file channel: {}", e.getMessage());
            }
        }
    }
}
