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
package com.spectrayan.spector.kernel.store;

import com.spectrayan.spector.kernel.store.EngramRegion;

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
import com.spectrayan.spector.kernel.error.SpectorPartitionFrozenException;
import com.spectrayan.spector.kernel.engram.FloatUnaryOperator;
import com.spectrayan.spector.kernel.id.MemoryId;
import com.spectrayan.spector.kernel.shape.MemoryShape;
import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.id.SystemMemoryId;
import com.spectrayan.spector.kernel.bundle.RegionRef;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.layout.EngramLayout;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.layout.FixedEngramLayout;
import com.spectrayan.spector.kernel.shape.AbstractRecordMemory;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.score.Valence;

import com.spectrayan.spector.kernel.score.DecayStrategy;

/**
 * Base implementation for all engram memory stores in Spector Memory,
 * extending {@link AbstractRecordMemory} directly and implementing {@link EngramRegion}.
 *
 * <p>Standardizes on Kernel 64-byte {@link RegionPreamble} for header management and
 * implements full type-safe contracts for SWMR visibility and off-heap memory management.</p>
 *
 * @param <L> the fixed-stride engram layout type
 * @see EngramRegion for the common interface
 */
public abstract class AbstractEngramMemory<L extends FixedEngramLayout> 
        extends AbstractRecordMemory<L> 
        implements EngramRegion {

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
    protected AbstractEngramMemory(MemoryType type, L cogLayout, int capacity, long segmentBytes) {
        this(type, cogLayout, capacity, segmentBytes, Arena.ofShared());
    }

    private AbstractEngramMemory(MemoryType type, L cogLayout, int capacity, long segmentBytes, Arena sharedArena) {
        this(type, cogLayout,
             capacity, sharedArena,
             sharedArena.allocate(segmentBytes, EncodingHeaderFields.HEADER_BYTES),
             0, false, null, null);
    }

    /**
     * File-backed constructor — creates or opens a persistent mmap'd file.
     *
     * @param type the cognitive tier this store represents; used to derive the stable
     *             {@link MemoryId} up-front so identity is final and lock-free.
     */
    protected AbstractEngramMemory(MemoryType type, L cogLayout, int capacity, long segmentBytes, Path filePath) {
        this(type, cogLayout,
             capacity, segmentBytes, filePath, mmapFile(filePath, segmentBytes));
    }

    private AbstractEngramMemory(MemoryType type, L cogLayout,
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

    private AbstractEngramMemory(MemoryType type, L cogLayout,
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
    protected AbstractEngramMemory(MemoryType type, L cogLayout,
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

    protected AbstractEngramMemory(MemoryType type, L cogLayout,
                                    int capacity, RegionRef regionRef,
                                    Path bundlePath, boolean isNew) {
        super(tierId(type), cogLayout, capacity,
              regionRef,
              isNew ? 0 : (int) RegionPreamble.readCount(regionRef.resolve(), 0),
              true, bundlePath);
        if (isNew) {
            setCount(0);
            writeMetadata();
            log.info("{} initialized new bundle region in: {} ({}KB)",
                    getClass().getSimpleName(), bundlePath, regionRef.resolve().byteSize() / 1024);
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
        RegionPreamble.write(segment(), 0, 1, MemoryShape.RECORD, 1, capacity, count,
                layout.stride(), layout.layoutId(), now, now);
    }

    /**
     * Reads the metadata header from the mapped segment.
     */
    protected void readMetadata() {
        MemorySegment seg = segment();
        if (RegionPreamble.isValid(seg, 0)) {
            setCount((int) RegionPreamble.readCount(seg, 0));
            return;
        }
        // Fallback for legacy TIER header
        int magic = seg.get(ValueLayout.JAVA_INT, 0);
        if (magic == TIER_MAGIC) {
            setCount(seg.get(ValueLayout.JAVA_INT, 8));
        } else {
            log.warn("Invalid header magic in {}: 0x{}", filePath(), Integer.toHexString(magic));
            setCount(0);
        }
    }

    /**
     * Persists the current count to the metadata header.
     */
    protected void persistCount() {
        if (persistent || isBundleManaged()) {
            MemorySegment seg = segment();
            if (RegionPreamble.isValid(seg, 0)) {
                RegionPreamble.writeCount(seg, 0, count);
            } else {
                seg.set(ValueLayout.JAVA_INT, 8, count);
            }
        }
    }

    /**
     * Returns the byte offset where data records begin.
     */
    public long dataOffset() {
        return (persistent || isBundleManaged()) ? METADATA_PREAMBLE_BYTES : 0;
    }

    @Override
    public int size() {
        return super.size();
    }

    @Override
    public L layout() {
        return layout;
    }



    @Override
    public long recordOffset(long index) {
        return dataOffset() + index * layout.stride();
    }

    @Override
    public long write(long recordId, MemorySegment recordBytes) {
        long offset = recordOffset(recordId);
        MemorySegment.copy(recordBytes, 0, segment(), offset, Math.min(recordBytes.byteSize(), layout.stride()));
        return offset;
    }

    @Override
    public void read(long recordId, MemorySegment dest) {
        long offset = recordOffset(recordId);
        MemorySegment.copy(segment(), offset, dest, 0, Math.min(dest.byteSize(), layout.stride()));
    }

    public MemorySegment primarySegment() {
        return segment();
    }

    @Override
    public MemorySegment segment() {
        return super.segment();
    }

    /**
     * Reads the quantized vector for a record slot into a heap byte array, or returns null if tombstoned.
     */

    public com.spectrayan.spector.kernel.api.HeaderCursor cursor() {
        return cursor(null);
    }

    public com.spectrayan.spector.kernel.api.HeaderCursor cursor(StrengthMemory strengthMemory) {
        if (regionRef != null) {
            return new DefaultHeaderCursor(regionRef, layout, type(), capacity, dataOffset(), strengthMemory);
        } else {
            return new DefaultHeaderCursor(segment(), layout, type(), capacity, dataOffset(), strengthMemory);
        }
    }

    public byte[] readQuantizedVector(int slot) {
        if (slot < 0 || slot >= visibleCount()) {
            return null;
        }
        long recordOff = recordOffset(slot);
        byte flags = layout.readFlags(segment(), recordOff);
        if (com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields.isTombstoned(flags)) {
            return null;
        }
        int vecBytes = layout.quantizedVecBytes();
        byte[] dest = new byte[vecBytes];
        MemorySegment.copy(segment(), ValueLayout.JAVA_BYTE, layout.vectorOffset(recordOff), dest, 0, vecBytes);
        return dest;
    }

    /**
     * Summary statistics collected from scanning engram record headers.
     *
     * @param liveCount number of non-tombstoned records
     * @param minTimestampMs minimum record timestamp in epoch milliseconds (0 if none)
     * @param maxTimestampMs maximum record timestamp in epoch milliseconds (0 if none)
     * @param synapticTagMask cumulative bitwise-OR of synaptic tag masks
     */
    public record SummaryStats(int liveCount, long minTimestampMs, long maxTimestampMs, long synapticTagMask) {}

    /**
     * Scans record headers and returns summary statistics without leaking raw segments.
     */
    public SummaryStats scanSummary() {
        int vCount = visibleCount();
        if (vCount <= 0) {
            return new SummaryStats(0, 0L, 0L, 0L);
        }
        MemorySegment seg = segment();
        int stride = layout.stride();
        long base = dataOffset();
        int live = 0;
        long minTs = Long.MAX_VALUE;
        long maxTs = Long.MIN_VALUE;
        long tagMask = 0L;
        var headerLayout = layout.headerLayout();
        for (int i = 0; i < vCount; i++) {
            long offset = base + (long) i * stride;
            byte flags = headerLayout.readFlags(seg, offset);
            if (EncodingHeaderFields.isTombstoned(flags)) continue;
            live++;
            long ts = headerLayout.readTimestamp(seg, offset);
            long tags = headerLayout.readSynapticTags(seg, offset);
            if (ts > 0) {
                minTs = Math.min(minTs, ts);
                maxTs = Math.max(maxTs, ts);
            }
            tagMask |= tags;
        }
        return new SummaryStats(
                live,
                minTs == Long.MAX_VALUE ? 0L : minTs,
                maxTs == Long.MIN_VALUE ? 0L : maxTs,
                tagMask
        );
    }

    public EncodingHeader readHeader(long offset) {
        return layout.readHeader(segment(), offset);
    }

    public byte[] readVector(long offset) {
        int vecBytes = layout.quantizedVecBytes();
        byte[] quantizedVec = new byte[vecBytes];
        long vecOffset = layout.vectorOffset(offset);
        MemorySegment.copy(
                segment(), ValueLayout.JAVA_BYTE, vecOffset,
                MemorySegment.ofArray(quantizedVec),
                ValueLayout.JAVA_BYTE, 0, vecBytes);
        return quantizedVec;
    }

    public byte readFlags(long offset) {
        return layout.headerLayout().readFlags(segment(), offset);
    }

    public boolean isTombstoned(long offset) {
        return EncodingHeaderFields.isTombstoned(readFlags(offset));
    }

    public boolean isContradicted(long offset) {
        return EncodingHeaderFields.isContradicted(layout.readConsolidationFlags(segment(), offset));
    }

    public void tombstone(long offset) {
        layout.tombstone(segment(), offset);
    }

    public void markContradicted(long offset) {
        layout.markContradicted(segment(), offset);
    }

    public void markResolved(long offset) {
        layout.markResolved(segment(), offset);
    }

    public void markUnresolved(long offset) {
        layout.markUnresolved(segment(), offset);
    }

    public void writeLastRecallProfile(long offset, byte profileOrdinal) {
        segment().set(ValueLayout.JAVA_BYTE, offset + EncodingHeaderFields.OFFSET_LAST_RECALL_PROFILE, profileOrdinal);
    }

    public byte readLastRecallProfile(long offset) {
        return segment().get(ValueLayout.JAVA_BYTE, offset + EncodingHeaderFields.OFFSET_LAST_RECALL_PROFILE);
    }

    public float readImportance(long offset) {
        if (layout instanceof FixedEngramLayout fixedLayout) {
            return fixedLayout.readImportance(segment(), offset);
        }
        return 0f;
    }

    public float casImportance(long offset, FloatUnaryOperator updateOp) {
        if (layout instanceof FixedEngramLayout fixedLayout) {
            return fixedLayout.headerLayout().casImportance(segment(), offset, updateOp);
        }
        return 0f;
    }

    public void writeImportance(long offset, float importance) {
        if (layout instanceof FixedEngramLayout fixedLayout) {
            fixedLayout.writeImportance(segment(), offset, importance);
        }
    }

    public void writeSoulVersion(long offset, short soulVersion) {
        if (layout instanceof FixedEngramLayout fixedLayout) {
            fixedLayout.writeSoulVersion(segment(), offset, soulVersion);
        }
    }

    public void reinforceValence(long offset, byte outcome, float learningRate) {
        if (layout instanceof FixedEngramLayout fixedLayout) {
            byte currentValence = fixedLayout.readValence(segment(), offset);
            byte blended = Valence.blend(currentValence, outcome, learningRate);
            segment().set(EncodingHeaderFields.LAYOUT_VALENCE, offset + EncodingHeaderFields.OFFSET_VALENCE, blended);
        }
    }

    public void reinforceInSitu(long offset, long creationTs, long nowMs, float sGain, float sMax) {
        if (layout instanceof FixedEngramLayout fixedLayout) {
            fixedLayout.incrementAgentRecallCount(segment(), offset);
            if (fixedLayout.headerLayout().version() >= 3) {
                com.spectrayan.spector.kernel.layout.StrengthLayout.INSTANCE.recordActRRecall(segment(), offset, creationTs, nowMs);
            }
            var headerLayout = fixedLayout.headerLayout();
            if (headerLayout.headerBytes() > 32) {
                int rawBucket = DecayStrategy.ageToBucket(creationTs, nowMs);
                float currentR = DecayStrategy.decay(rawBucket);
                float deltaS = sGain * (1.0f - currentR);
                headerLayout.casStorageStrength(segment(), offset,
                        currentS -> Math.min(sMax, Math.max(0.01f, currentS + deltaS)));
            }
        }
    }

    public long readTimestamp(long offset) {
        if (layout instanceof FixedEngramLayout fixedLayout) {
            return fixedLayout.readTimestamp(segment(), offset);
        }
        return 0L;
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
            if (EncodingHeaderFields.isTombstoned(flags)) {
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

    public void append(EncodingHeader header, byte[] quantizedVec) {
        if (frozen) throw new SpectorPartitionFrozenException(type().name());
        writeLock.lock();
        try {
            long maxCapacity = Math.min(capacity(), (segment().byteSize() - dataOffset()) / layout.stride());
            if (count >= maxCapacity) {
                throw new com.spectrayan.spector.kernel.error.SpectorMemoryTierFullException(type().name(), (int) maxCapacity);
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
