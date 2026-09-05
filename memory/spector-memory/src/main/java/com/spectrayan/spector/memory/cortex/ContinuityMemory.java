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
import com.spectrayan.spector.commons.error.SpectorMemoryException;
import com.spectrayan.spector.memory.aisme.continuity.IdentityTrajectorySnapshot;
import com.spectrayan.spector.memory.kernel.Memory;
import com.spectrayan.spector.memory.kernel.RegionPreamble;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.SystemMemoryId;
import com.spectrayan.spector.memory.kernel.layout.ContinuityLayout;
import com.spectrayan.spector.memory.sync.MemoryWal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
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
public final class ContinuityMemory implements Memory<ContinuityLayout>, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ContinuityMemory.class);

    private final MemoryId id;
    private final ContinuityLayout layout = ContinuityLayout.SINGLETON;
    private final Arena arena;
    private final MemorySegment segment;
    private final int capacity;
    private final boolean persistent;
    private final boolean bundleManaged;
    private final FileChannel fileChannel;
    private final Path filePath;
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
        this.id = id;
        this.arena = arena;
        this.segment = segment;
        this.capacity = capacity;
        this.persistent = persistent;
        this.bundleManaged = bundleManaged;
        this.fileChannel = fileChannel;
        this.filePath = filePath;
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

    // ── Core Operations ──

    /**
     * Appends a new longitudinal identity trajectory snapshot into the circular ring buffer.
     *
     * @param snapshot the snapshot to record
     */
    public void appendSnapshot(IdentityTrajectorySnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        writeLock.lock();
        try {
            int head = ContinuityLayout.readHeadIndex(segment);
            int total = ContinuityLayout.readTotalSnapshots(segment);

            long recordOff = ContinuityLayout.recordOffset(head);
            ContinuityLayout.writeRecord(
                    segment,
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
            ContinuityLayout.writeHeadIndex(segment, nextHead);
            ContinuityLayout.writeTotalSnapshots(segment, total + 1);
            ContinuityLayout.writeLastSnapshotTimestamp(segment, snapshot.timestamp());

            if (persistent && !bundleManaged) {
                segment.asSlice(recordOff, ContinuityLayout.RECORD_STRIDE).force();
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
    public Optional<IdentityTrajectorySnapshot> latestSnapshot() {
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
    public List<IdentityTrajectorySnapshot> readHistory(int limit) {
        int total = ContinuityLayout.readTotalSnapshots(segment);
        int count = Math.min(Math.min(total, capacity), Math.max(1, limit));
        List<IdentityTrajectorySnapshot> history = new ArrayList<>(count);

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
        List<IdentityTrajectorySnapshot> history = readHistory(100);
        if (history.isEmpty()) {
            return 0.0f;
        }
        float maxDrift = 0.0f;
        for (IdentityTrajectorySnapshot s : history) {
            if (s.priorDrift() > maxDrift) {
                maxDrift = s.priorDrift();
            }
        }
        return maxDrift;
    }

    private IdentityTrajectorySnapshot readSnapshotAt(long off) {
        return new IdentityTrajectorySnapshot(
                ContinuityLayout.readTimestamp(segment, off),
                ContinuityLayout.readPhiCc(segment, off),
                ContinuityLayout.readTraceG(segment, off),
                ContinuityLayout.readPriorDrift(segment, off),
                ContinuityLayout.readValence(segment, off),
                ContinuityLayout.readArousal(segment, off),
                ContinuityLayout.readEnergy(segment, off),
                ContinuityLayout.readSoulVersion(segment, off)
        );
    }

    // ── Memory Interface Implementation ──

    @Override
    public MemoryId id() {
        return id;
    }

    @Override
    public ContinuityLayout layout() {
        return layout;
    }

    @Override
    public MemorySegment segment() {
        return segment;
    }

    @Override
    public Arena arena() {
        return arena;
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public int size() {
        int total = ContinuityLayout.readTotalSnapshots(segment);
        return Math.min(total, capacity);
    }

    @Override
    public int schemaVersion() {
        return ContinuityLayout.SCHEMA_VERSION;
    }

    public int totalSnapshots() {
        return ContinuityLayout.readTotalSnapshots(segment);
    }

    public long lastSnapshotTimestamp() {
        return ContinuityLayout.readLastSnapshotTimestamp(segment);
    }

    public boolean isPersistent() {
        return persistent;
    }

    public boolean isBundleManaged() {
        return bundleManaged;
    }

    @Override
    public MemoryShape shape() {
        return MemoryShape.RECORD;
    }

    @Override
    public void bindWal(MemoryWal wal) {
        // WAL binding for continuity records
    }

    @Override
    public void flush() {
        if (persistent && segment != null) {
            segment.force();
        }
    }

    public void sync() {
        flush();
    }

    @Override
    public void close() {
        if (persistent && segment != null) {
            segment.force();
        }
        if (!bundleManaged && arena != null && arena.scope().isAlive()) {
            arena.close();
        }
        if (fileChannel != null && fileChannel.isOpen()) {
            try {
                fileChannel.close();
            } catch (IOException e) {
                log.warn("Failed to close fileChannel for ContinuityMemory: {}", e.getMessage());
            }
        }
    }
}
