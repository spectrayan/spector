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
package com.spectrayan.spector.kernel.bundle.identity;

import com.spectrayan.spector.kernel.region.RegionEntry;

import com.spectrayan.spector.kernel.region.RegionId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.CRC32C;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorMemoryException;
import com.spectrayan.spector.commons.error.SpectorServerException;
import com.spectrayan.spector.commons.error.SpectorStorageException;
import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.shape.MemoryShape;


/**
 * Manages an account or tenant identity bundle file (ADR-0029 §23).
 *
 * <p>An identity bundle is a lightweight mmap container (1 FD) that stores identity,
 * salience, continuity, and policy outside the data-plane rememberer. It does not contain
 * a full rememberer engine and does not count against {@code maxHotNamespaces}.</p>
 */
public final class IdentityBundle implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(IdentityBundle.class);
    private final Path bundlePath;
    private final Arena arena;
    private final MemorySegment masterSegment;
    private final boolean isHeap;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private IdentityBundle(Path bundlePath, Arena arena, MemorySegment masterSegment, boolean isHeap) {
        this.bundlePath = bundlePath;
        this.arena = arena;
        this.masterSegment = masterSegment;
        this.isHeap = isHeap;
    }

    /**
     * Opens or creates an {@link IdentityBundle} at the specified path.
     *
     * @param bundlePath      the file path
     * @param createIfMissing whether to create the bundle file if missing
     * @return open IdentityBundle
     */
    public static IdentityBundle open(Path bundlePath, boolean createIfMissing) {
        try {
            boolean exists = Files.exists(bundlePath);
            if (!exists && !createIfMissing) {
                throw new SpectorStorageException(ErrorCode.DISK_IO_FAILED, "IdentityBundle file not found: " + bundlePath);
            }

            if (!exists) {
                if (bundlePath.getParent() != null) {
                    Files.createDirectories(bundlePath.getParent());
                }
            }

            FileChannel channel = FileChannel.open(
                    bundlePath,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE
            );

            if (channel.size() < IdentityBundleHeader.TOTAL_INITIAL_SIZE) {
                channel.truncate(IdentityBundleHeader.TOTAL_INITIAL_SIZE);
            }

            Arena arena = Arena.ofShared();
            MemorySegment segment = channel.map(
                    FileChannel.MapMode.READ_WRITE,
                    0L,
                    IdentityBundleHeader.TOTAL_INITIAL_SIZE,
                    arena
            );
            channel.close(); // MemorySegment retains the mapping

            if (!exists || !RegionPreamble.isValid(segment, 0L)) {
                IdentityBundleHeader.initialize(segment);
                segment.force();
            } else {
                IdentityBundleHeader.validate(segment);
            }

            return new IdentityBundle(bundlePath, arena, segment, false);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to open IdentityBundle at " + bundlePath, e);
        }
    }

    /**
     * Opens an existing {@link IdentityBundle}, creating it if it does not yet exist.
     *
     * @param bundlePath the file path
     * @return open IdentityBundle
     */
    public static IdentityBundle open(Path bundlePath) {
        return open(bundlePath, true);
    }

    /**
     * Creates an in-memory, heap-backed {@link IdentityBundle} for unit tests.
     *
     * @return in-memory IdentityBundle
     */
    public static IdentityBundle heap() {
        Arena arena = Arena.ofShared();
        MemorySegment segment = arena.allocate(IdentityBundleHeader.TOTAL_INITIAL_SIZE, 4096);
        IdentityBundleHeader.initialize(segment);
        return new IdentityBundle(null, arena, segment, true);
    }

    // ── Public Accessors & Mutators ──

    /**
     * Reads the {@link SoulContext} from the given region (typically {@link IdentityRegionId#SOUL}).
     *
     * @param regionId the target region
     * @return optional soul context DTO copy
     */
    

    

    /**
     * Checks if the given region has no payload.
     *
     * @param regionId the target region
     * @return true if empty or not present
     */
    public boolean isEmpty(IdentityRegionId regionId) {
        ensureOpen();
        IdentityRegionEntry entry = getEntry(regionId);
        return !entry.isPresent() || entry.usedSize() == 0;
    }

    /**
     * Returns the version counter for the specified region.
     *
     * @param regionId the target region
     * @return version counter
     */
    public int getVersion(IdentityRegionId regionId) {
        ensureOpen();
        return getEntry(regionId).version();
    }

    /**
     * Reads raw bytes from the specified region.
     *
     * @param regionId the target region
     * @return raw payload bytes, or empty if region is empty
     */
    public Optional<byte[]> readRaw(IdentityRegionId regionId) {
        ensureOpen();
        lock.lock();
        try {
            IdentityRegionEntry entry = getEntry(regionId);
            if (!entry.isPresent() || entry.usedSize() == 0) {
                return Optional.empty();
            }

            int used = (int) entry.usedSize();
            if (used < 0 || used > entry.allocatedSize()) {
                throw new SpectorMemoryException(ErrorCode.RECORD_CRC_CORRUPTED, "IdentityBundle",
                        "Corrupted usedSize " + used + " for region " + regionId);
            }

            byte[] data = new byte[used];
            MemorySegment.copy(masterSegment, entry.offset(), MemorySegment.ofArray(data), 0L, used);

            // Check CRC
            CRC32C crc = new CRC32C();
            crc.update(data, 0, used);
            if ((int) crc.getValue() != entry.checksum()) {
                throw new SpectorMemoryException(ErrorCode.RECORD_CRC_CORRUPTED, "IdentityBundle",
                        "Checksum mismatch for region " + regionId);
            }

            return Optional.of(data);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Writes raw bytes to the specified region.
     *
     * @param regionId the target region
     * @param payload  the byte array to write
     */
    public void writeRaw(IdentityRegionId regionId, byte[] payload) {
        ensureOpen();
        if (payload == null) {
            clearRegion(regionId);
            return;
        }

        lock.lock();
        try {
            IdentityRegionEntry entry = getEntry(regionId);
            if (payload.length > entry.allocatedSize()) {
                throw new SpectorMemoryException(ErrorCode.CAPACITY_EXCEEDED, "IdentityBundle",
                        "Payload size " + payload.length + " exceeds region allocation " + entry.allocatedSize());
            }

            CRC32C crc = new CRC32C();
            crc.update(payload, 0, payload.length);
            int checksum = (int) crc.getValue();

            long now = System.currentTimeMillis();
            int nextVersion = entry.version() + 1;

            // Copy payload into mmap region
            MemorySegment.copy(MemorySegment.ofArray(payload), 0L, masterSegment, entry.offset(), payload.length);

            // Update entry
            IdentityRegionEntry updated = new IdentityRegionEntry(
                    regionId,
                    IdentityRegionEntry.FLAG_PRESENT,
                    entry.offset(),
                    entry.allocatedSize(),
                    payload.length,
                    nextVersion,
                    checksum,
                    now
            );
            putEntry(updated);

            // Update RegionPreamble timestamp and CRC
            long createdAt = RegionPreamble.readCreatedAt(masterSegment, 0L);
            int flags = RegionPreamble.readFlags(masterSegment, 0L);
            RegionPreamble.write(
                    masterSegment,
                    0L,
                    IdentityBundleHeader.SCHEMA_VERSION,
                    MemoryShape.BUNDLE,
                    flags,
                    IdentityBundleHeader.MAX_REGIONS,
                    0,
                    IdentityRegionEntry.ENTRY_BYTES,
                    IdentityBundleHeader.LAYOUT_ID,
                    createdAt,
                    now
            );

            if (!isHeap) {
                masterSegment.force();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Clears a region, resetting it to empty state.
     */
    public void clearRegion(IdentityRegionId regionId) {
        ensureOpen();
        lock.lock();
        try {
            IdentityRegionEntry entry = getEntry(regionId);
            long now = System.currentTimeMillis();
            IdentityRegionEntry updated = new IdentityRegionEntry(
                    regionId,
                    IdentityRegionEntry.FLAG_EMPTY,
                    entry.offset(),
                    entry.allocatedSize(),
                    0L,
                    entry.version() + 1,
                    0,
                    now
            );
            putEntry(updated);
            if (!isHeap) {
                masterSegment.force();
            }
        } finally {
            lock.unlock();
        }
    }

    private IdentityRegionEntry getEntry(IdentityRegionId regionId) {
        long offset = IdentityBundleHeader.OFF_ENTRIES + (long) regionId.id() * IdentityRegionEntry.ENTRY_BYTES;
        return IdentityRegionEntry.read(masterSegment, offset);
    }

    private void putEntry(IdentityRegionEntry entry) {
        long offset = IdentityBundleHeader.OFF_ENTRIES + (long) entry.regionId().id() * IdentityRegionEntry.ENTRY_BYTES;
        IdentityRegionEntry.write(masterSegment, offset, entry);
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new SpectorMemoryException(ErrorCode.ENGINE_CLOSED, "IdentityBundle", "IdentityBundle is closed");
        }
    }

    public Path bundlePath() {
        return bundlePath;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            lock.lock();
            try {
                if (!isHeap) {
                    masterSegment.force();
                }
                arena.close();
            } finally {
                lock.unlock();
            }
        }
    }
}
