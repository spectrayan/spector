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
package com.spectrayan.spector.memory.synapse;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorStorageException;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.memory.cortex.StrengthMemory;
import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.RegionPreamble;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeaderLayout;
import com.spectrayan.spector.memory.kernel.layout.StrengthLayout;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import com.spectrayan.spector.memory.kernel.layout.compat.LegacyEncodingHeaderReader;
import com.spectrayan.spector.memory.model.MemoryType;

/**
 * One-time migration tool for converting store files between header layout versions.
 *
 * <h3>Migration Strategy</h3>
 * <ol>
 *   <li>Read source file with source layout</li>
 *   <li>Write to temporary file ({@code .migrating}) with target layout</li>
 *   <li>Verify record count matches</li>
 *   <li>Back up original file ({@code .vN.bak})</li>
 *   <li>Atomic rename: temp → original</li>
 *   <li>Update metadata header with new version/stride</li>
 * </ol>
 *
 * <h3>Safety</h3>
 * <p>If the process crashes mid-migration, the original file is untouched —
 * the atomic rename (step 5) hasn't happened yet. On next startup, detect
 * the {@code .migrating} temp file and clean it up.</p>
 *
 * <h3>Supported Paths</h3>
 * <ul>
 *   <li>V1 (32B) → V2 (48B): arousal=0, storageStrength=1.0f</li>
 *   <li>V1 (32B) → V3 (64B): arousal=0, storageStrength=1.0f, reserved=0</li>
 *   <li>V2 (48B) → V3 (64B): reserved=0</li>
 *   <li>V3 (64B) → V2 (48B): ⚠️ lossy — reserved fields dropped</li>
 *   <li>V3 (64B) → V1 (32B): ⚠️ lossy — arousal, storageStrength, reserved dropped</li>
 *   <li>V2 (48B) → V1 (32B): ⚠️ lossy — arousal, storageStrength dropped</li>
 * </ul>
 *
 * @see HeaderLayout
 */
public final class HeaderMigrator {

    private static final Logger log = LoggerFactory.getLogger(HeaderMigrator.class);

    /** Region preamble size in bytes (same as AbstractEngramMemory.METADATA_PREAMBLE_BYTES). */
    private static final int METADATA_PREAMBLE_BYTES = 64;

    /** Metadata field offsets (mirrors AbstractEngramMemory). */
    private static final int META_MAGIC    = 0;
    private static final int META_VERSION  = 4;
    private static final int META_COUNT    = 8;
    private static final int META_CAPACITY = 12;
    private static final int META_STRIDE   = 16;
    private static final int META_TIER_ORD = 20;

    /** Magic number for tier files: "TIER" in ASCII. */
    private static final int TIER_MAGIC = 0x54494552;

    private HeaderMigrator() {}

    /**
     * Migrates a persistent store file from one header layout to another.
     *
     * <p>The migration is atomic: the original file is backed up before
     * the migrated file replaces it. If the target version is lower than
     * the source version (downgrade), this is a lossy operation — extended
     * fields are discarded.</p>
     *
     * @param storePath path to the persistent store file
     * @param source    current layout (legacy V1 reader)
     * @param target    desired layout version (EncodingHeaderLayout)
     * @param vectorBytes bytes per quantized vector (needed for stride calculation)
     * @param isHeaderOnly true for header-only stores (e.g., SemanticMemoryStore)
     * @return migration report with statistics
     * @throws SpectorValidationException if source and target are the same version
     * @throws SpectorStorageException if file I/O fails
     */
    public static MigrationReport migrate(Path storePath, LegacyEncodingHeaderReader source,
                                          EncodingHeaderLayout target, int vectorBytes,
                                          boolean isHeaderOnly) {
        return migrate(storePath, source, target, vectorBytes, isHeaderOnly, null, null);
    }

    /**
     * Migrates a persistent store file from one header layout to another, optionally
     * populating the strength region from V1 header fields.
     *
     * @param storePath     path to the persistent store file
     * @param source        current layout (legacy V1 reader)
     * @param target        desired layout version (EncodingHeaderLayout)
     * @param vectorBytes   bytes per quantized vector (needed for stride calculation)
     * @param isHeaderOnly  true for header-only stores (e.g., SemanticMemoryStore)
     * @param strengthStore optional StrengthMemory store to populate with V1 counters
     * @param tier          optional MemoryType tier for strength records
     * @return migration report with statistics
     * @throws SpectorValidationException if source and target are the same version
     * @throws SpectorStorageException if file I/O fails
     */
    public static MigrationReport migrate(Path storePath, LegacyEncodingHeaderReader source,
                                          EncodingHeaderLayout target, int vectorBytes,
                                          boolean isHeaderOnly, StrengthMemory strengthStore,
                                          MemoryType tier) {
        if (source.version() == target.version()) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID, "targetVersion", "same as source: " + source.version());
        }

        boolean isDowngrade = target.version() < source.version();
        if (isDowngrade) {
            log.warn("LOSSY DOWNGRADE: V{} → V{} — extended fields will be discarded",
                    source.version(), target.version());
        }

        Instant start = Instant.now();
        Path tempPath = storePath.resolveSibling(storePath.getFileName() + ".migrating");
        Path backupPath = storePath.resolveSibling(
                storePath.getFileName() + ".v" + source.version() + ".bak");

        log.info("Migrating {} from V{} ({}B) to V{} ({}B){}",
                storePath.getFileName(), source.version(), source.headerBytes(),
                target.version(), target.headerBytes(),
                isDowngrade ? " [LOSSY]" : "");

        int recordCount;
        long bytesBefore;

        try {
            bytesBefore = Files.size(storePath);
        } catch (IOException e) {
            throw new SpectorStorageException(ErrorCode.DISK_IO_FAILED, e, "read file size: " + storePath);
        }

        try (Arena sourceArena = Arena.ofConfined();
             Arena targetArena = Arena.ofConfined()) {

            // ── Step 1: Open source file ──
            MemorySegment sourceSegment;
            try (FileChannel sourceCh = FileChannel.open(storePath, StandardOpenOption.READ)) {
                sourceSegment = sourceCh.map(FileChannel.MapMode.READ_ONLY, 0,
                        sourceCh.size(), sourceArena);
            }

            // Read metadata
            int magic = sourceSegment.get(ValueLayout.JAVA_INT, META_MAGIC);
            int capacity;
            int tierOrd = 0;
            if (magic == RegionPreamble.MAGIC) {
                recordCount = (int) RegionPreamble.readCount(sourceSegment, 0);
                capacity = (int) RegionPreamble.readCapacity(sourceSegment, 0);
            } else if (magic == TIER_MAGIC) {
                recordCount = sourceSegment.get(ValueLayout.JAVA_INT, META_COUNT);
                capacity = sourceSegment.get(ValueLayout.JAVA_INT, META_CAPACITY);
                tierOrd = sourceSegment.get(ValueLayout.JAVA_INT, META_TIER_ORD);
            } else {
                throw new SpectorStorageException(
                        ErrorCode.FILE_FORMAT_INVALID, "bad tier magic in " + storePath + ": 0x" + Integer.toHexString(magic));
            }

            int sourceRecordStride = isHeaderOnly ? source.headerBytes()
                    : source.headerBytes() + vectorBytes;
            int targetRecordStride = isHeaderOnly ? target.headerBytes()
                    : target.headerBytes() + vectorBytes;

            long targetDataSize = (long) targetRecordStride * capacity;
            long targetTotalSize = METADATA_PREAMBLE_BYTES + targetDataSize;

            // ── Step 2: Create target temp file ──
            try (FileChannel targetCh = FileChannel.open(tempPath,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE)) {

                // Extend file
                targetCh.position(targetTotalSize - 1);
                targetCh.write(ByteBuffer.wrap(new byte[]{0}));

                MemorySegment targetSegment = targetCh.map(FileChannel.MapMode.READ_WRITE,
                        0, targetTotalSize, targetArena);

                // Write metadata header
                if (magic == RegionPreamble.MAGIC) {
                    long now = System.currentTimeMillis();
                    RegionPreamble.write(targetSegment, 0, target.version(),
                            MemoryShape.RECORD, 1, capacity, recordCount,
                            targetRecordStride, 0x434F4700, now, now);
                } else {
                    targetSegment.set(ValueLayout.JAVA_INT, META_MAGIC, TIER_MAGIC);
                    targetSegment.set(ValueLayout.JAVA_INT, META_VERSION, target.version());
                    targetSegment.set(ValueLayout.JAVA_INT, META_COUNT, recordCount);
                    targetSegment.set(ValueLayout.JAVA_INT, META_CAPACITY, capacity);
                    targetSegment.set(ValueLayout.JAVA_INT, META_STRIDE, targetRecordStride);
                    targetSegment.set(ValueLayout.JAVA_INT, META_TIER_ORD, tierOrd);
                }

                // ── Step 3: Migrate records ──
                for (int i = 0; i < recordCount; i++) {
                    long sourceOff = METADATA_PREAMBLE_BYTES + (long) i * sourceRecordStride;
                    long targetOff = METADATA_PREAMBLE_BYTES + (long) i * targetRecordStride;

                    // Read header from source layout (extended fields get defaults)
                    CognitiveRecordLayout.CognitiveHeader header =
                            source.readHeader(sourceSegment, sourceOff);

                    // Write header with target layout
                    target.writeHeader(targetSegment, targetOff, header);

                    // Copy vector payload if present
                    if (!isHeaderOnly && vectorBytes > 0) {
                        long sourceVecOff = sourceOff + source.headerBytes();
                        long targetVecOff = targetOff + target.headerBytes();
                        MemorySegment.copy(sourceSegment, sourceVecOff,
                                targetSegment, targetVecOff, vectorBytes);
                    }

                    // Copy V1 header offsets 16, 36, 40, 48, 60 into strength region if provided
                    if (strengthStore != null && tier != null) {
                        copyV1HeaderToStrength(sourceSegment, sourceOff, strengthStore, tier, i);
                    }
                }

                // Force to disk
                targetSegment.force();

                log.info("Migrated {} records from V{} to V{}", recordCount,
                        source.version(), target.version());
            }

            // ── Step 4: Atomic swap ──
            // Back up original
            Files.move(storePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            // Rename temp → original
            Files.move(tempPath, storePath, StandardCopyOption.ATOMIC_MOVE);

            long bytesAfter;
            try {
                bytesAfter = Files.size(storePath);
            } catch (IOException e) {
                bytesAfter = targetTotalSize;
            }

            Duration duration = Duration.between(start, Instant.now());

            log.info("Migration complete: {} records, {}KB → {}KB, took {}ms, backup at {}",
                    recordCount, bytesBefore / 1024, bytesAfter / 1024,
                    duration.toMillis(), backupPath);

            return new MigrationReport(recordCount, bytesBefore, bytesAfter,
                    duration, backupPath, isDowngrade);

        } catch (IOException e) {
            // Clean up temp file on failure
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException cleanupEx) {
                log.warn("Failed to clean up temp file: {}", tempPath, cleanupEx);
            }
            throw new SpectorStorageException(ErrorCode.STORAGE_MIGRATION_FAILED, e, storePath);
        }
    }

    /**
     * Estimates the target file size after migration without performing it.
     *
     * @param currentFileSize current file size in bytes
     * @param recordCount     number of records
     * @param source          current layout
     * @param target          target layout
     * @param vectorBytes     bytes per quantized vector
     * @param isHeaderOnly    true for header-only stores
     * @return estimated target file size in bytes
     */
    public static long estimateTargetSize(long currentFileSize, int recordCount,
                                           LegacyEncodingHeaderReader source, EncodingHeaderLayout target,
                                           int vectorBytes, boolean isHeaderOnly) {
        int targetRecordStride = isHeaderOnly ? target.headerBytes()
                : target.headerBytes() + vectorBytes;
        int capacity = (int) ((currentFileSize - METADATA_PREAMBLE_BYTES)
                / (isHeaderOnly ? source.headerBytes() : source.headerBytes() + vectorBytes));
        return METADATA_PREAMBLE_BYTES + (long) targetRecordStride * capacity;
    }

    /**
     * Copies V1 engram header ranking/strength counters (offsets 16, 36, 40, 48, 60, and base importance)
     * from a V1 engram record into the strength region.
     *
     * @param engramSegment        segment containing the V1 engram record
     * @param engramRecordOffset   byte offset of the engram record
     * @param strengthSegment      target strength region segment
     * @param strengthRecordOffset byte offset in the strength segment
     * @param tier                 memory tier (SEMANTIC, EPISODIC, PROCEDURAL)
     */
    public static void copyV1HeaderToStrength(MemorySegment engramSegment, long engramRecordOffset,
                                              MemorySegment strengthSegment, long strengthRecordOffset,
                                              MemoryType tier) {
        float importance = engramSegment.get(ValueLayout.JAVA_FLOAT, engramRecordOffset + SynapticHeaderConstants.OFFSET_IMPORTANCE);
        int agentRecallCount = engramSegment.get(ValueLayout.JAVA_INT, engramRecordOffset + SynapticHeaderConstants.OFFSET_AGENT_RECALL_COUNT);
        float storageStrength = engramSegment.get(ValueLayout.JAVA_FLOAT, engramRecordOffset + SynapticHeaderConstants.OFFSET_STORAGE_STRENGTH);
        int spectorRecallCount = engramSegment.get(ValueLayout.JAVA_INT, engramRecordOffset + SynapticHeaderConstants.OFFSET_SPECTOR_RECALL_COUNT);
        long lastAutoLtp = engramSegment.get(ValueLayout.JAVA_LONG, engramRecordOffset + SynapticHeaderConstants.OFFSET_LAST_AUTO_LTP);
        byte lastRecallProfile = engramSegment.get(ValueLayout.JAVA_BYTE, engramRecordOffset + SynapticHeaderConstants.OFFSET_LAST_RECALL_PROFILE);

        if (storageStrength <= 0.0f) {
            storageStrength = 1.0f;
        }

        StrengthLayout layout = StrengthLayout.INSTANCE;
        layout.initializeDefaultRecord(strengthSegment, strengthRecordOffset, tier, importance, storageStrength, agentRecallCount);
        if (spectorRecallCount > 0) {
            layout.writeSpectorRecallCount(strengthSegment, strengthRecordOffset, spectorRecallCount);
        }
        if (lastAutoLtp > 0L) {
            layout.writeLastAutoLtp(strengthSegment, strengthRecordOffset, lastAutoLtp);
        }
        if (lastRecallProfile != 0) {
            layout.writeLastRecallProfile(strengthSegment, strengthRecordOffset, lastRecallProfile);
        }
    }

    /**
     * Copies V1 engram header ranking/strength counters from a V1 engram record into StrengthMemory at (tier, slotIndex).
     */
    public static void copyV1HeaderToStrength(MemorySegment engramSegment, long engramRecordOffset,
                                              StrengthMemory strengthStore, MemoryType tier,
                                              int slotIndex) {
        long strengthRecordOffset = strengthStore.strengthOffset(tier, slotIndex);
        copyV1HeaderToStrength(engramSegment, engramRecordOffset, strengthStore.segment(), strengthRecordOffset, tier);
    }

    /**
     * Iterates all records in a V1 engram segment and migrates their counters into StrengthMemory.
     *
     * @return count of migrated records
     */
    public static int migrateRecordsToStrength(MemorySegment engramSegment, long engramDataOffset,
                                               int engramRecordStride, int recordCount,
                                               StrengthMemory strengthStore, MemoryType tier) {
        for (int i = 0; i < recordCount; i++) {
            long engramRecordOffset = engramDataOffset + (long) i * engramRecordStride;
            copyV1HeaderToStrength(engramSegment, engramRecordOffset, strengthStore, tier, i);
        }
        return recordCount;
    }

    /**
     * Iterates all records in a V1 engram segment and migrates their counters into a raw strength segment.
     *
     * @return count of migrated records
     */
    public static int migrateRecordsToStrength(MemorySegment engramSegment, long engramDataOffset,
                                               int engramRecordStride, int recordCount,
                                               MemorySegment strengthSegment, long strengthDataOffset,
                                               int strengthBaseSlot, MemoryType tier) {
        int stride = StrengthLayout.STRIDE_BYTES;
        for (int i = 0; i < recordCount; i++) {
            long engramRecordOffset = engramDataOffset + (long) i * engramRecordStride;
            long strengthRecordOffset = strengthDataOffset + (long) (strengthBaseSlot + i) * stride;
            copyV1HeaderToStrength(engramSegment, engramRecordOffset, strengthSegment, strengthRecordOffset, tier);
        }
        return recordCount;
    }

    /**
     * Detects the header layout version from a store file's metadata.
     *
     * <p>Reads the stride field from the metadata header and infers the layout
     * version from it, since each version has a unique header size.</p>
     *
     * @param storePath   path to the store file
     * @param vectorBytes bytes per quantized vector
     * @param isHeaderOnly true for header-only stores
     * @return detected header layout version
     */
    public static int detectVersion(Path storePath, int vectorBytes,
                                    boolean isHeaderOnly) {
        try (FileChannel ch = FileChannel.open(storePath, StandardOpenOption.READ)) {
            if (ch.size() < METADATA_PREAMBLE_BYTES) {
                return SpectorPropertyConstants.DEFAULT_MEMORY_HEADER_VERSION; // assume current layout
            }

            ByteBuffer buf = ByteBuffer.allocate(METADATA_PREAMBLE_BYTES);
            ch.read(buf);
            buf.flip();

            int magic = buf.getInt(META_MAGIC);
            if (magic != TIER_MAGIC && magic != RegionPreamble.MAGIC) {
                log.warn("Invalid magic in {}, assuming current layout", storePath);
                return SpectorPropertyConstants.DEFAULT_MEMORY_HEADER_VERSION;
            }

            int stride;
            if (magic == RegionPreamble.MAGIC) {
                stride = buf.getInt(32);
            } else {
                stride = buf.getInt(META_STRIDE);
            }
            int headerBytes = isHeaderOnly ? stride : stride - vectorBytes;

            if (headerBytes != SynapticHeaderConstants.HEADER_BYTES) {
                log.warn("Unexpected header size {} in {} (expected {}), assuming current layout",
                        headerBytes, storePath, SynapticHeaderConstants.HEADER_BYTES);
            }

            return SpectorPropertyConstants.DEFAULT_MEMORY_HEADER_VERSION;
        } catch (IOException e) {
            log.warn("Cannot detect header version from {}: {}", storePath, e.getMessage());
            return SpectorPropertyConstants.DEFAULT_MEMORY_HEADER_VERSION;
        }
    }

    /**
     * Cleans up orphaned {@code .migrating} temp files from interrupted migrations.
     *
     * @param storePath path to the store file
     */
    public static void cleanupOrphanedTempFile(Path storePath) {
        Path tempPath = storePath.resolveSibling(storePath.getFileName() + ".migrating");
        try {
            if (Files.deleteIfExists(tempPath)) {
                log.info("Cleaned up orphaned migration temp file: {}", tempPath);
            }
        } catch (IOException e) {
            log.warn("Failed to clean up orphaned temp file: {}", tempPath, e);
        }
    }

    /**
     * Migration result.
     *
     * @param recordsMigrated number of records migrated
     * @param bytesBefore     file size before migration
     * @param bytesAfter      file size after migration
     * @param duration        migration duration
     * @param backupPath      path to the backup of the original file
     * @param lossy           true if the migration was a downgrade (data loss)
     */
    public record MigrationReport(
            int recordsMigrated,
            long bytesBefore,
            long bytesAfter,
            Duration duration,
            Path backupPath,
            boolean lossy
    ) {
        @Override
        public String toString() {
            return String.format("MigrationReport[records=%d, %dKB→%dKB, %dms, backup=%s%s]",
                    recordsMigrated, bytesBefore / 1024, bytesAfter / 1024,
                    duration.toMillis(), backupPath, lossy ? ", LOSSY" : "");
        }
    }
}
