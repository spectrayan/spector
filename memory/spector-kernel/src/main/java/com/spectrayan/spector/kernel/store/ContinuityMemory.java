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

import com.spectrayan.spector.kernel.region.RegionId;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorMemoryException;

import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.id.MemoryId;
import com.spectrayan.spector.kernel.shape.MemoryShape;
import com.spectrayan.spector.kernel.id.SystemMemoryId;
import com.spectrayan.spector.kernel.layout.ContinuityLayout;
import com.spectrayan.spector.kernel.shape.AbstractRecordMemory;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * High-performance, zero-copy off-heap memory store for longitudinal identity and consciousness continuity (\(\Phi_{CC}\)) trajectories.
 *
 * <h3>Biological Analog: Hippocampal-Cortical Longitudinal Cohesion Ledger</h3>
 * <p>Tracks self-model continuity across operational epochs, recording changes in Integrated Information
 * Theory cohesion (\(\Phi_{CC}\)), personal Riemannian manifold curvature (\(\text{Trace}(G)\)),
 * generative prior drift (\(\|\boldsymbol{\mu}_t - \boldsymbol{\mu}_0\|\)), and homeostatic states.</p>
 *
 * <h3>Kernel Standards & Layout</h3>
 * <ul>
 *   <li>Conforms to {@link MemoryShape#RECORD} and {@link ContinuityLayout}</li>
 *   <li>64B standard {@link RegionPreamble} + 32B {@link ContinuityLayout} sub-header + 32B fixed-stride records</li>
 *   <li>Operates as a circular ring-buffer over a fixed capacity without dynamic heap allocation</li>
 * </ul>
 *
 * @since 1.2.0
 */
public final class ContinuityMemory extends AbstractRecordMemory<ContinuityLayout> implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ContinuityMemory.class);

    private final ReentrantLock writeLock = new ReentrantLock();

    private ContinuityMemory(
            MemoryId id,
            Arena arena,
            MemorySegment segment,
            int capacity,
            boolean persistent,
            boolean bundleManaged,
            FileChannel fileChannel,
            Path filePath) {
        super(id, ContinuityLayout.SINGLETON, capacity, arena, segment,
                (persistent || segment != null) && RegionPreamble.isValid(segment, 0L)
                        ? (int) Math.min(ContinuityLayout.readTotalSnapshots(segment), capacity)
                        : 0,
                persistent, filePath, fileChannel, bundleManaged);
    }

    // ── Factory Methods ──

    /**
     * Creates a {@link ContinuityMemory} from an existing runtime bundle region slice.
     *
     * @param arena shared bundle arena
     * @param regionSlice off-heap memory slice allocated for RegionId.CONTINUITY
     * @param isNew whether this region was newly initialized
     * @return initialized ContinuityMemory
     */
    public static ContinuityMemory fromBundle(Arena arena, MemorySegment regionSlice, boolean isNew) {
        MemoryId memoryId = SystemMemoryId.CONTINUITY.id();
        int recordCapacity = (int) ((regionSlice.byteSize() - ContinuityLayout.DATA_START) / ContinuityLayout.RECORD_STRIDE);
        if (recordCapacity <= 0) {
            throw new SpectorMemoryException(ErrorCode.RECORD_CRC_CORRUPTED, "Region slice too small for ContinuityMemory");
        }

        boolean effectivelyNew = isNew || !RegionPreamble.isValid(regionSlice, 0L);
        if (effectivelyNew) {
            long now = System.currentTimeMillis();
            RegionPreamble.write(regionSlice, 0L, ContinuityLayout.SCHEMA_VERSION, MemoryShape.RECORD, 1,
                    ContinuityLayout.RECORD_STRIDE, recordCapacity, 0, ContinuityLayout.LAYOUT_ID, now, now);

            ContinuityLayout.writeHeadIndex(regionSlice, 0);
            ContinuityLayout.writeTotalSnapshots(regionSlice, 0);
            ContinuityLayout.writeLastSnapshotTimestamp(regionSlice, 0L);
            ContinuityLayout.writeCapacity(regionSlice, recordCapacity);
            regionSlice.force();
        } else {
            validateHeader(regionSlice);
        }

        return new ContinuityMemory(memoryId, arena, regionSlice, recordCapacity, true, true, null, null);
    }

    public static ContinuityMemory fromRegionRef(com.spectrayan.spector.kernel.bundle.RegionRef regionRef, boolean isNew) {
        MemoryId memoryId = SystemMemoryId.CONTINUITY.id();
        MemorySegment regionSlice = regionRef.resolve();
        int recordCapacity = (int) ((regionSlice.byteSize() - ContinuityLayout.DATA_START) / ContinuityLayout.RECORD_STRIDE);
        if (recordCapacity <= 0) {
            throw new SpectorMemoryException(ErrorCode.RECORD_CRC_CORRUPTED, "Region slice too small for ContinuityMemory");
        }

        boolean effectivelyNew = isNew || !RegionPreamble.isValid(regionSlice, 0L);
        if (effectivelyNew) {
            long now = System.currentTimeMillis();
            RegionPreamble.write(regionSlice, 0L, ContinuityLayout.SCHEMA_VERSION, MemoryShape.RECORD, 1,
                    ContinuityLayout.RECORD_STRIDE, recordCapacity, 0, ContinuityLayout.LAYOUT_ID, now, now);

            ContinuityLayout.writeHeadIndex(regionSlice, 0);
            ContinuityLayout.writeTotalSnapshots(regionSlice, 0);
            ContinuityLayout.writeLastSnapshotTimestamp(regionSlice, 0L);
            ContinuityLayout.writeCapacity(regionSlice, recordCapacity);
            regionSlice.force();
        } else {
            validateHeader(regionSlice);
        }

        int count = effectivelyNew ? 0 : (int) Math.min(ContinuityLayout.readTotalSnapshots(regionSlice), recordCapacity);
        return new ContinuityMemory(memoryId, regionRef, recordCapacity, count, regionRef.bundlePath() != null, regionRef.bundlePath());
    }

    private ContinuityMemory(
            MemoryId id,
            com.spectrayan.spector.kernel.bundle.RegionRef regionRef,
            int capacity,
            int count,
            boolean persistent,
            Path filePath) {
        super(id, ContinuityLayout.SINGLETON, capacity, regionRef, count, persistent, filePath);
    }

    /**
     * Creates an in-memory heap-backed {@link ContinuityMemory} for testing or ephemeral sessions.
     *
     * @param capacity maximum number of circular history records (e.g. 1,000)
     * @return ephemeral ContinuityMemory
     */
    public static ContinuityMemory heap(int capacity) {
        if (capacity <= 0) {
            capacity = 1000;
        }
        Arena arena = Arena.ofShared();
        long totalBytes = ContinuityLayout.DATA_START + (long) capacity * ContinuityLayout.RECORD_STRIDE;
        MemorySegment seg = arena.allocate(totalBytes, 4096);

        long now = System.currentTimeMillis();
        RegionPreamble.write(seg, 0L, ContinuityLayout.SCHEMA_VERSION, MemoryShape.RECORD, 0,
                ContinuityLayout.RECORD_STRIDE, capacity, 0, ContinuityLayout.LAYOUT_ID, now, now);

        ContinuityLayout.writeHeadIndex(seg, 0);
        ContinuityLayout.writeTotalSnapshots(seg, 0);
        ContinuityLayout.writeLastSnapshotTimestamp(seg, 0L);
        ContinuityLayout.writeCapacity(seg, capacity);

        return new ContinuityMemory(SystemMemoryId.CONTINUITY.id(), arena, seg, capacity, false, false, null, null);
    }

    /**
     * Opens or creates a standalone file-backed {@link ContinuityMemory}.
     *
     * @param filePath path to the memory file
     * @param capacity record capacity
     * @return file-backed ContinuityMemory
     */
    public static ContinuityMemory open(Path filePath, int capacity) {
        if (capacity <= 0) {
            capacity = 10_000;
        }
        try {
            boolean exists = Files.exists(filePath) && Files.size(filePath) > 0;
            long totalBytes = ContinuityLayout.DATA_START + (long) capacity * ContinuityLayout.RECORD_STRIDE;
            FileChannel fc = FileChannel.open(filePath,
                    StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);

            if (!exists || fc.size() < totalBytes) {
                fc.truncate(totalBytes);
            }

            Arena arena = Arena.ofShared();
            MemorySegment seg = fc.map(FileChannel.MapMode.READ_WRITE, 0, totalBytes, arena);

            if (!exists) {
                long now = System.currentTimeMillis();
                RegionPreamble.write(seg, 0L, ContinuityLayout.SCHEMA_VERSION, MemoryShape.RECORD, 1,
                        ContinuityLayout.RECORD_STRIDE, capacity, 0, ContinuityLayout.LAYOUT_ID, now, now);
                ContinuityLayout.writeHeadIndex(seg, 0);
                ContinuityLayout.writeTotalSnapshots(seg, 0);
                ContinuityLayout.writeLastSnapshotTimestamp(seg, 0L);
                ContinuityLayout.writeCapacity(seg, capacity);
                seg.force();
            } else {
                validateHeader(seg);
            }

            return new ContinuityMemory(SystemMemoryId.CONTINUITY.id(), arena, seg, capacity, true, false, fc, filePath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to open ContinuityMemory at " + filePath, e);
        }
    }

    private static void validateHeader(MemorySegment slice) {
        if (!RegionPreamble.isValid(slice, 0L)) {
            throw new SpectorMemoryException(ErrorCode.RECORD_CRC_CORRUPTED, "Invalid RegionPreamble in ContinuityMemory");
        }
        int layoutId = RegionPreamble.readLayoutId(slice, 0L);
        if (layoutId != ContinuityLayout.LAYOUT_ID) {
            throw new SpectorMemoryException(ErrorCode.RECORD_CRC_CORRUPTED,
                    String.format("Invalid Continuity layout ID: expected 0x%08X but got 0x%08X", ContinuityLayout.LAYOUT_ID, layoutId));
        }
    }

    @Override
    public long dataOffset() {
        return ContinuityLayout.DATA_START;
    }

    // ── Core Operations ──

    /**
     * Appends a new longitudinal identity trajectory snapshot into the circular ring buffer.
     *
     * @param snapshot the snapshot to record
     */
    public void appendSnapshot(ContinuityRecord snapshot) {
        if (snapshot == null) {
            return;
        }
        writeLock.lock();
        try {
            MemorySegment seg = segment();
            int head = ContinuityLayout.readHeadIndex(seg);
            int total = ContinuityLayout.readTotalSnapshots(seg);

            long recordOff = ContinuityLayout.recordOffset(head);
            ContinuityLayout.writeRecord(
                    seg,
                    recordOff,
                    snapshot.timestamp(),
                    snapshot.phiCc(),
                    snapshot.traceG(),
                    snapshot.priorDrift(),
                    snapshot.valence(),
                    snapshot.arousal(),
                    snapshot.energy(),
                    snapshot.soulVersion()
            );

            int nextHead = (head + 1) % capacity;
            ContinuityLayout.writeHeadIndex(seg, nextHead);
            ContinuityLayout.writeTotalSnapshots(seg, total + 1);
            ContinuityLayout.writeLastSnapshotTimestamp(seg, snapshot.timestamp());
            this.count = Math.min(total + 1, capacity);

            if (persistent && !bundleManaged) {
                seg.asSlice(recordOff, ContinuityLayout.RECORD_STRIDE).force();
            }

            if (log.isDebugEnabled()) {
                log.debug("Appended identity trajectory snapshot: phiCc={}, traceG={}, priorDrift={}, total={}",
                        snapshot.phiCc(), snapshot.traceG(), snapshot.priorDrift(), total + 1);
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Retrieves the most recent trajectory snapshot, if one exists.
     *
     * @return optional latest snapshot
     */
    public Optional<ContinuityRecord> latestSnapshot() {
        int total = ContinuityLayout.readTotalSnapshots(segment);
        if (total == 0) {
            return Optional.empty();
        }
        int head = ContinuityLayout.readHeadIndex(segment);
        int slot = (head - 1 + capacity) % capacity;
        long off = ContinuityLayout.recordOffset(slot);

        return Optional.of(readSnapshotAt(off));
    }

    /**
     * Reads the trajectory history in reverse chronological order (newest first).
     *
     * @param limit maximum number of snapshots to return
     * @return list of snapshots ordered newest to oldest
     */
    public List<ContinuityRecord> readHistory(int limit) {
        int total = ContinuityLayout.readTotalSnapshots(segment);
        int count = Math.min(Math.min(total, capacity), Math.max(1, limit));
        List<ContinuityRecord> history = new ArrayList<>(count);

        int head = ContinuityLayout.readHeadIndex(segment);
        for (int i = 0; i < count; i++) {
            int slot = (head - 1 - i + capacity * 2) % capacity;
            long off = ContinuityLayout.recordOffset(slot);
            history.add(readSnapshotAt(off));
        }
        return history;
    }

    /**
     * Calculates the cumulative prior mean drift across recorded history.
     *
     * @return maximum or cumulative drift from generative baseline
     */
    public float calculateLongitudinalDrift() {
        List<ContinuityRecord> history = readHistory(100);
        if (history.isEmpty()) {
            return 0.0f;
        }
        float maxDrift = 0.0f;
        for (ContinuityRecord s : history) {
            if (s.priorDrift() > maxDrift) {
                maxDrift = s.priorDrift();
            }
        }
        return maxDrift;
    }

    private ContinuityRecord readSnapshotAt(long off) {
        MemorySegment seg = segment();
        return new ContinuityRecord(
                ContinuityLayout.readTimestamp(seg, off),
                ContinuityLayout.readPhiCc(seg, off),
                ContinuityLayout.readTraceG(seg, off),
                ContinuityLayout.readPriorDrift(seg, off),
                ContinuityLayout.readValence(seg, off),
                ContinuityLayout.readArousal(seg, off),
                ContinuityLayout.readEnergy(seg, off),
                ContinuityLayout.readSoulVersion(seg, off)
        );
    }

    @Override
    public int size() {
        int total = ContinuityLayout.readTotalSnapshots(segment());
        return Math.min(total, capacity);
    }

    public int totalSnapshots() {
        return ContinuityLayout.readTotalSnapshots(segment());
    }

    public long lastSnapshotTimestamp() {
        return ContinuityLayout.readLastSnapshotTimestamp(segment());
    }

    public void sync() {
        flush();
    }
}
