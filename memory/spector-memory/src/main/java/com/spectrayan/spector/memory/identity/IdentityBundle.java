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
package com.spectrayan.spector.memory.identity;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

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
import com.spectrayan.spector.memory.kernel.MemoryHeader;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.model.SoulContext;

import tools.jackson.databind.ObjectMapper;

/**
 * Manages an account or tenant identity bundle file (ADR-0029 §23).
 *
 * <p>An identity bundle is a lightweight mmap container (1 FD) that stores identity,
 * salience, continuity, and policy outside the data-plane rememberer. It does not contain
 * a full rememberer engine and does not count against {@code maxHotNamespaces}.</p>
 */
public final class IdentityBundle implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(IdentityBundle.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

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

            if (!exists || !MemoryHeader.isValid(segment, 0L)) {
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
    public Optional<SoulContext> readSoul(IdentityRegionId regionId) {
        return readRaw(regionId).flatMap(bytes -> {
            try {
                return Optional.of(MAPPER.readValue(bytes, SoulContext.class));
            } catch (Exception e) {
                try {
                    return Optional.of(MAPPER.readValue(bytes, com.spectrayan.spector.memory.model.UserSoul.class));
                } catch (Exception e1) {
                    try {
                        return Optional.of(MAPPER.readValue(bytes, com.spectrayan.spector.memory.model.AgentSoul.class));
                    } catch (Exception e2) {
                        try {
                            return Optional.of(MAPPER.readValue(bytes, com.spectrayan.spector.memory.model.TenantSoul.class));
                        } catch (Exception e3) {
                            try {
                                return Optional.of(MAPPER.readValue(bytes, com.spectrayan.spector.memory.model.OrgUnitSoul.class));
                            } catch (Exception e4) {
                                log.warn("Failed to deserialize SoulContext from region {}: {}", regionId, e.getMessage());
                                return Optional.empty();
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Reads the primary {@link SoulContext} from {@link IdentityRegionId#SOUL}.
     *
     * @return optional primary soul context DTO copy
     */
    public Optional<SoulContext> readSoul() {
        return readSoul(IdentityRegionId.SOUL);
    }

    /**
     * Writes the {@link SoulContext} to the specified region.
     *
     * @param regionId the target region
     * @param soul     the soul context to write
     */
    public void writeSoul(IdentityRegionId regionId, SoulContext soul) {
        if (soul == null) {
            clearRegion(regionId);
            return;
        }
        try {
            byte[] bytes = MAPPER.writeValueAsBytes(soul);
            writeRaw(regionId, bytes);
        } catch (Exception e) {
            throw new SpectorMemoryException(ErrorCode.GRAPH_PERSISTENCE_FAILED, "IdentityBundle",
                    "Failed to serialize SoulContext: " + e.getMessage());
        }
    }

    /**
     * Writes the primary {@link SoulContext} to {@link IdentityRegionId#SOUL}.
     *
     * @param soul the soul context to write
     */
    public void writeSoul(SoulContext soul) {
        writeSoul(IdentityRegionId.SOUL, soul);
    }

    /**
     * Reads the {@link SalienceProfile} from {@link IdentityRegionId#SALIENCE}.
     *
     * @return optional salience profile DTO copy
     */
    public Optional<SalienceProfile> readSalience() {
        return readRaw(IdentityRegionId.SALIENCE).flatMap(bytes -> {
            try {
                return Optional.of(MAPPER.readValue(bytes, SalienceProfile.class));
            } catch (Exception e) {
                log.warn("Failed to deserialize SalienceProfile: {}", e.getMessage());
                return Optional.empty();
            }
        });
    }

    /**
     * Writes the {@link SalienceProfile} to {@link IdentityRegionId#SALIENCE}.
     *
     * @param salience the salience profile to write
     */
    public void writeSalience(SalienceProfile salience) {
        if (salience == null) {
            clearRegion(IdentityRegionId.SALIENCE);
            return;
        }
        try {
            byte[] bytes = MAPPER.writeValueAsBytes(salience);
            writeRaw(IdentityRegionId.SALIENCE, bytes);
        } catch (Exception e) {
            throw new SpectorMemoryException(ErrorCode.GRAPH_PERSISTENCE_FAILED, "IdentityBundle",
                    "Failed to serialize SalienceProfile: " + e.getMessage());
        }
    }

    /**
     * Reads continuity trajectory from {@link IdentityRegionId#CONTINUITY}.
     */
    public Optional<String> readContinuity() {
        return readRaw(IdentityRegionId.CONTINUITY).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    /**
     * Writes continuity trajectory to {@link IdentityRegionId#CONTINUITY}.
     */
    public void writeContinuity(String continuity) {
        if (continuity == null) {
            clearRegion(IdentityRegionId.CONTINUITY);
        } else {
            writeRaw(IdentityRegionId.CONTINUITY, continuity.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Reads policy from {@link IdentityRegionId#POLICY}.
     */
    public Optional<String> readPolicy() {
        return readRaw(IdentityRegionId.POLICY).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    /**
     * Writes policy to {@link IdentityRegionId#POLICY}.
     */
    public void writePolicy(String policy) {
        if (policy == null) {
            clearRegion(IdentityRegionId.POLICY);
        } else {
            writeRaw(IdentityRegionId.POLICY, policy.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Reads an organizational unit soul by ID from {@link IdentityRegionId#ORG_DIR}.
     *
     * <p>The ORG_DIR region stores a JSON array of {@link com.spectrayan.spector.memory.model.OrgUnitSoul}
     * records. This method reads the array and filters by {@code orgUnitId}.</p>
     *
     * @param orgUnitId the organizational unit identifier
     * @return optional matching OrgUnitSoul as a SoulContext
     */
    public Optional<SoulContext> readOrgUnitSoul(String orgUnitId) {
        if (orgUnitId == null || orgUnitId.isBlank()) {
            return Optional.empty();
        }
        return readRaw(IdentityRegionId.ORG_DIR).flatMap(bytes -> {
            try {
                var type = MAPPER.getTypeFactory()
                        .constructCollectionType(java.util.List.class, com.spectrayan.spector.memory.model.OrgUnitSoul.class);
                java.util.List<com.spectrayan.spector.memory.model.OrgUnitSoul> orgSouls = MAPPER.readValue(bytes, type);
                return orgSouls.stream()
                        .filter(s -> orgUnitId.equals(s.id()))
                        .map(s -> (SoulContext) s)
                        .findFirst();
            } catch (Exception e) {
                log.warn("Failed to deserialize OrgUnitSoul list from ORG_DIR");
                return Optional.empty();
            }
        });
    }

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

            // Update MemoryHeader timestamp and CRC
            long createdAt = MemoryHeader.readCreatedAt(masterSegment, 0L);
            int flags = MemoryHeader.readFlags(masterSegment, 0L);
            MemoryHeader.write(
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
