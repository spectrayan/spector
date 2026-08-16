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
package com.spectrayan.spector.memory.insula;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorMemoryException;
import com.spectrayan.spector.memory.kernel.Memory;
import com.spectrayan.spector.memory.kernel.MemoryHeader;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.SystemMemoryId;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.CRC32C;

/**
 * InsularCortex implements the self-model region (Anterior Insular Cortex)
 * storing a single, persistent JSON self-model.
 *
 * @since 1.2.0
 */
public final class InsularCortex implements Memory<InsularLayout>, AutoCloseable {

    private final MemoryId id;
    private final Arena arena;
    private final MemorySegment segment;
    private final boolean bundleManaged;
    private final ReentrantLock writeLock = new ReentrantLock();

    private static final long HEADER_START = MemoryHeader.HEADER_BYTES; // 64
    private static final long DATA_START = HEADER_START + InsularLayout.INSULAR_HEADER_BYTES; // 96

    private InsularCortex(MemoryId id, Arena arena, MemorySegment segment, boolean bundleManaged) {
        this.id = id;
        this.arena = arena;
        this.segment = segment;
        this.bundleManaged = bundleManaged;
    }

    // ── Factory Methods ──

    /**
     * Creates an InsularCortex region from an existing bundle slice.
     */
    public static InsularCortex fromBundle(Arena arena, MemorySegment regionSlice, boolean isNew) {
        MemoryId memoryId = SystemMemoryId.INSULA.id();
        if (isNew) {
            long now = System.currentTimeMillis();
            MemoryHeader.write(regionSlice, 0L, InsularLayout.SCHEMA_VERSION, MemoryShape.INSULAR, 1,
                    1, 0, 0, InsularLayout.LAYOUT_ID, now, now);
            
            regionSlice.set(ValueLayout.JAVA_INT_UNALIGNED, HEADER_START + InsularLayout.OFF_VERSION, 0);
            regionSlice.set(ValueLayout.JAVA_INT_UNALIGNED, HEADER_START + InsularLayout.OFF_DATA_LENGTH, 0);
            regionSlice.set(ValueLayout.JAVA_LONG_UNALIGNED, HEADER_START + InsularLayout.OFF_UPDATED_AT, 0L);
            regionSlice.set(ValueLayout.JAVA_INT_UNALIGNED, HEADER_START + InsularLayout.OFF_CHECKSUM, 0);
            regionSlice.set(ValueLayout.JAVA_INT_UNALIGNED, HEADER_START + InsularLayout.OFF_FLAGS, InsularLayout.FLAG_EMPTY);
            regionSlice.set(ValueLayout.JAVA_LONG_UNALIGNED, HEADER_START + 24, 0L); // reserved
            regionSlice.force();
        } else {
            validateHeader(regionSlice);
        }
        return new InsularCortex(memoryId, arena, regionSlice, true);
    }

    /**
     * Creates an in-memory heap-backed InsularCortex for testing.
     */
    public static InsularCortex heap() {
        Arena arena = Arena.ofShared();
        MemorySegment heapSeg = arena.allocate(1024 * 1024, 4096);
        long now = System.currentTimeMillis();
        MemoryHeader.write(heapSeg, 0L, InsularLayout.SCHEMA_VERSION, MemoryShape.INSULAR, 0,
                1, 0, 0, InsularLayout.LAYOUT_ID, now, now);
        
        heapSeg.set(ValueLayout.JAVA_INT_UNALIGNED, HEADER_START + InsularLayout.OFF_VERSION, 0);
        heapSeg.set(ValueLayout.JAVA_INT_UNALIGNED, HEADER_START + InsularLayout.OFF_DATA_LENGTH, 0);
        heapSeg.set(ValueLayout.JAVA_LONG_UNALIGNED, HEADER_START + InsularLayout.OFF_UPDATED_AT, 0L);
        heapSeg.set(ValueLayout.JAVA_INT_UNALIGNED, HEADER_START + InsularLayout.OFF_CHECKSUM, 0);
        heapSeg.set(ValueLayout.JAVA_INT_UNALIGNED, HEADER_START + InsularLayout.OFF_FLAGS, InsularLayout.FLAG_EMPTY);
        heapSeg.set(ValueLayout.JAVA_LONG_UNALIGNED, HEADER_START + 24, 0L); // reserved
        
        return new InsularCortex(SystemMemoryId.INSULA.id(), arena, heapSeg, false);
    }

    private static void validateHeader(MemorySegment slice) {
        if (!MemoryHeader.isValid(slice, 0L)) {
            throw new SpectorMemoryException(ErrorCode.GRAPH_PERSISTENCE_FAILED, "InsularCortex", "Invalid SMKM MemoryHeader");
        }
        if (MemoryHeader.readLayoutId(slice, 0L) != InsularLayout.LAYOUT_ID) {
            throw new SpectorMemoryException(ErrorCode.GRAPH_PERSISTENCE_FAILED, "InsularCortex",
                    "layout ID mismatch: expected 0x" + Integer.toHexString(InsularLayout.LAYOUT_ID)
                    + " but found 0x" + Integer.toHexString(MemoryHeader.readLayoutId(slice, 0L)));
        }
    }

    // ── Insular API ──

    /**
     * Writes or updates the self-model JSON. Returns the new version.
     */
    public int put(byte[] selfModelJson) {
        if (selfModelJson == null) {
            throw new IllegalArgumentException("selfModelJson cannot be null");
        }
        
        writeLock.lock();
        try {
            long maxPayloadSize = segment.byteSize() - DATA_START;
            if (selfModelJson.length > maxPayloadSize) {
                throw new SpectorMemoryException(ErrorCode.MEMORY_TIER_FULL,
                        "Self-model size of " + selfModelJson.length + " bytes exceeds allocated capacity of " + maxPayloadSize + " bytes");
            }

            // Compute checksum
            CRC32C crc32c = new CRC32C();
            crc32c.update(selfModelJson, 0, selfModelJson.length);
            int checksum = (int) crc32c.getValue();

            // Read version and increment
            int currentVersion = segment.get(ValueLayout.JAVA_INT_UNALIGNED, HEADER_START + InsularLayout.OFF_VERSION);
            int nextVersion = currentVersion + 1;
            long now = System.currentTimeMillis();

            // Write JSON payload
            MemorySegment.copy(MemorySegment.ofArray(selfModelJson), 0L, segment, DATA_START, selfModelJson.length);

            // Write insular sub-header
            segment.set(ValueLayout.JAVA_INT_UNALIGNED, HEADER_START + InsularLayout.OFF_VERSION, nextVersion);
            segment.set(ValueLayout.JAVA_INT_UNALIGNED, HEADER_START + InsularLayout.OFF_DATA_LENGTH, selfModelJson.length);
            segment.set(ValueLayout.JAVA_LONG_UNALIGNED, HEADER_START + InsularLayout.OFF_UPDATED_AT, now);
            segment.set(ValueLayout.JAVA_INT_UNALIGNED, HEADER_START + InsularLayout.OFF_CHECKSUM, checksum);
            segment.set(ValueLayout.JAVA_INT_UNALIGNED, HEADER_START + InsularLayout.OFF_FLAGS, InsularLayout.FLAG_PRESENT);

            // Recompute master MemoryHeader CRC by rewriting it
            long createdAt = MemoryHeader.readCreatedAt(segment, 0L);
            int flags = MemoryHeader.readFlags(segment, 0L);
            MemoryHeader.write(segment, 0L, InsularLayout.SCHEMA_VERSION, MemoryShape.INSULAR, flags,
                    1L, 1L, 0, InsularLayout.LAYOUT_ID, createdAt, now);

            flush();
            return nextVersion;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Reads the current self-model JSON, if present.
     */
    public Optional<byte[]> get() {
        int flags = segment.get(ValueLayout.JAVA_INT_UNALIGNED, HEADER_START + InsularLayout.OFF_FLAGS);
        if (flags == InsularLayout.FLAG_EMPTY) {
            return Optional.empty();
        }

        int length = segment.get(ValueLayout.JAVA_INT_UNALIGNED, HEADER_START + InsularLayout.OFF_DATA_LENGTH);
        if (length < 0 || length > segment.byteSize() - DATA_START) {
            throw new SpectorMemoryException(ErrorCode.GRAPH_PERSISTENCE_FAILED, "InsularCortex", "Corrupted self-model payload length: " + length);
        }

        byte[] payload = new byte[length];
        MemorySegment.copy(segment, DATA_START, MemorySegment.ofArray(payload), 0L, length);

        // Check checksum
        CRC32C crc32c = new CRC32C();
        crc32c.update(payload, 0, length);
        int expectedChecksum = segment.get(ValueLayout.JAVA_INT_UNALIGNED, HEADER_START + InsularLayout.OFF_CHECKSUM);
        if ((int) crc32c.getValue() != expectedChecksum) {
            throw new SpectorMemoryException(ErrorCode.GRAPH_PERSISTENCE_FAILED, "InsularCortex", "CRC32C checksum mismatch");
        }

        return Optional.of(payload);
    }

    /**
     * Removes the self-model (resets to empty). Returns true if a model was present.
     */
    public boolean clear() {
        writeLock.lock();
        try {
            int flags = segment.get(ValueLayout.JAVA_INT_UNALIGNED, HEADER_START + InsularLayout.OFF_FLAGS);
            if (flags == InsularLayout.FLAG_EMPTY) {
                return false;
            }

            int currentVersion = segment.get(ValueLayout.JAVA_INT_UNALIGNED, HEADER_START + InsularLayout.OFF_VERSION);
            int nextVersion = currentVersion + 1;
            long now = System.currentTimeMillis();

            // Set flags to empty and reset version
            segment.set(ValueLayout.JAVA_INT_UNALIGNED, HEADER_START + InsularLayout.OFF_VERSION, nextVersion);
            segment.set(ValueLayout.JAVA_INT_UNALIGNED, HEADER_START + InsularLayout.OFF_DATA_LENGTH, 0);
            segment.set(ValueLayout.JAVA_LONG_UNALIGNED, HEADER_START + InsularLayout.OFF_UPDATED_AT, now);
            segment.set(ValueLayout.JAVA_INT_UNALIGNED, HEADER_START + InsularLayout.OFF_CHECKSUM, 0);
            segment.set(ValueLayout.JAVA_INT_UNALIGNED, HEADER_START + InsularLayout.OFF_FLAGS, InsularLayout.FLAG_EMPTY);

            // Re-write MemoryHeader with count 0L
            long createdAt = MemoryHeader.readCreatedAt(segment, 0L);
            int headerFlags = MemoryHeader.readFlags(segment, 0L);
            MemoryHeader.write(segment, 0L, InsularLayout.SCHEMA_VERSION, MemoryShape.INSULAR, headerFlags,
                    1L, 0L, 0, InsularLayout.LAYOUT_ID, createdAt, now);

            flush();
            return true;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Gets the current version of the self-model payload.
     *
     * @return the version integer
     */
    public int version() {
        return segment.get(ValueLayout.JAVA_INT_UNALIGNED, HEADER_START + InsularLayout.OFF_VERSION);
    }

    /**
     * Gets the epoch millisecond timestamp of the last self-model update.
     *
     * @return the update timestamp in milliseconds
     */
    public long updatedAt() {
        return segment.get(ValueLayout.JAVA_LONG_UNALIGNED, HEADER_START + InsularLayout.OFF_UPDATED_AT);
    }

    /**
     * Checks if a valid self-model payload is currently present in the region.
     *
     * @return true if a self-model exists, false otherwise
     */
    public boolean isPresent() {
        return segment.get(ValueLayout.JAVA_INT_UNALIGNED, HEADER_START + InsularLayout.OFF_FLAGS) == InsularLayout.FLAG_PRESENT;
    }

    // ── Memory Interface Contract ──

    @Override
    public MemoryId id() {
        return id;
    }

    @Override
    public InsularLayout layout() {
        return InsularLayout.SINGLETON;
    }

    @Override
    public Arena arena() {
        return arena;
    }

    @Override
    public MemorySegment segment() {
        return segment;
    }

    @Override
    public int size() {
        return isPresent() ? 1 : 0;
    }

    @Override
    public int capacity() {
        return 1;
    }

    @Override
    public int schemaVersion() {
        return InsularLayout.SCHEMA_VERSION;
    }

    @Override
    public MemoryShape shape() {
        return MemoryShape.INSULAR;
    }

    @Override
    public void flush() {
        if (segment.isMapped()) {
            segment.force();
        }
    }

    @Override
    public void close() {
        if (!bundleManaged) {
            arena.close();
        }
    }
}
