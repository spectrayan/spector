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
package com.spectrayan.spector.kernel.sync;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorStorageException;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.engram.EncodingHeaderLayout;
import com.spectrayan.spector.kernel.engram.compat.LegacyEncodingHeaderReader;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.layout.StrengthLayout;
import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.shape.MemoryShape;
import com.spectrayan.spector.kernel.store.StrengthMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

/**
 * Kernel migration tool for converting store files between header layout versions (R9.1).
 */
public final class HeaderMigrator {

    private static final Logger log = LoggerFactory.getLogger(HeaderMigrator.class);

    private static final int METADATA_PREAMBLE_BYTES = 64;

    private static final int META_MAGIC    = 0;
    private static final int META_VERSION  = 4;
    private static final int META_COUNT    = 8;
    private static final int META_CAPACITY = 12;
    private static final int META_STRIDE   = 16;
    private static final int META_TIER_ORD = 20;

    private static final int TIER_MAGIC = 0x54494552;
    private static final int DEFAULT_HEADER_VERSION = 3;

    private HeaderMigrator() {}

    public static MigrationReport migrate(Path storePath, LegacyEncodingHeaderReader source,
                                          EncodingHeaderLayout target, int vectorBytes,
                                          boolean isHeaderOnly) {
        return migrate(storePath, source, target, vectorBytes, isHeaderOnly, null, null);
    }

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
            log.warn("LOSSY DOWNGRADE: V{} -> V{} - extended fields will be discarded",
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

            MemorySegment sourceSegment;
            try (FileChannel sourceCh = FileChannel.open(storePath, StandardOpenOption.READ)) {
                sourceSegment = sourceCh.map(FileChannel.MapMode.READ_ONLY, 0,
                        sourceCh.size(), sourceArena);
            }

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

            try (FileChannel targetCh = FileChannel.open(tempPath,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE)) {

                targetCh.position(targetTotalSize - 1);
                targetCh.write(ByteBuffer.wrap(new byte[]{0}));

                MemorySegment targetSegment = targetCh.map(FileChannel.MapMode.READ_WRITE,
                        0, targetTotalSize, targetArena);

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

                for (int i = 0; i < recordCount; i++) {
                    long sourceOff = METADATA_PREAMBLE_BYTES + (long) i * sourceRecordStride;
                    long targetOff = METADATA_PREAMBLE_BYTES + (long) i * targetRecordStride;

                    EncodingHeader header = source.readHeader(sourceSegment, sourceOff);
                    target.writeHeader(targetSegment, targetOff, header);

                    if (!isHeaderOnly && vectorBytes > 0) {
                        long sourceVecOff = sourceOff + source.headerBytes();
                        long targetVecOff = targetOff + target.headerBytes();
                        MemorySegment.copy(sourceSegment, sourceVecOff,
                                targetSegment, targetVecOff, vectorBytes);
                    }

                    if (strengthStore != null && tier != null) {
                        copyV1HeaderToStrength(sourceSegment, sourceOff, strengthStore, tier, i);
                    }
                }

                targetSegment.force();
                log.info("Migrated {} records from V{} to V{}", recordCount,
                        source.version(), target.version());
            }

            Files.move(storePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tempPath, storePath, StandardCopyOption.ATOMIC_MOVE);

            long bytesAfter;
            try {
                bytesAfter = Files.size(storePath);
            } catch (IOException e) {
                bytesAfter = targetTotalSize;
            }

            Duration duration = Duration.between(start, Instant.now());
            log.info("Migration complete: {} records, {}KB -> {}KB, took {}ms, backup at {}",
                    recordCount, bytesBefore / 1024, bytesAfter / 1024,
                    duration.toMillis(), backupPath);

            return new MigrationReport(recordCount, bytesBefore, bytesAfter,
                    duration, backupPath, isDowngrade);

        } catch (IOException e) {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException cleanupEx) {
                log.warn("Failed to clean up temp file: {}", tempPath, cleanupEx);
            }
            throw new SpectorStorageException(ErrorCode.STORAGE_MIGRATION_FAILED, e, storePath);
        }
    }

    public static long estimateTargetSize(long currentFileSize, int recordCount,
                                          LegacyEncodingHeaderReader source, EncodingHeaderLayout target,
                                          int vectorBytes, boolean isHeaderOnly) {
        int targetRecordStride = isHeaderOnly ? target.headerBytes()
                : target.headerBytes() + vectorBytes;
        int capacity = (int) ((currentFileSize - METADATA_PREAMBLE_BYTES)
                / (isHeaderOnly ? source.headerBytes() : source.headerBytes() + vectorBytes));
        return METADATA_PREAMBLE_BYTES + (long) targetRecordStride * capacity;
    }

    public static void copyV1HeaderToStrength(MemorySegment engramSegment, long engramRecordOffset,
                                              MemorySegment strengthSegment, long strengthRecordOffset,
                                              MemoryType tier) {
        float importance = engramSegment.get(ValueLayout.JAVA_FLOAT, engramRecordOffset + EncodingHeaderFields.OFFSET_IMPORTANCE);
        int agentRecallCount = engramSegment.get(ValueLayout.JAVA_INT, engramRecordOffset + EncodingHeaderFields.OFFSET_AGENT_RECALL_COUNT);
        float storageStrength = engramSegment.get(ValueLayout.JAVA_FLOAT, engramRecordOffset + EncodingHeaderFields.OFFSET_STORAGE_STRENGTH);
        int spectorRecallCount = engramSegment.get(ValueLayout.JAVA_INT, engramRecordOffset + EncodingHeaderFields.OFFSET_SPECTOR_RECALL_COUNT);
        long lastAutoLtp = engramSegment.get(ValueLayout.JAVA_LONG, engramRecordOffset + EncodingHeaderFields.OFFSET_LAST_AUTO_LTP);
        byte lastRecallProfile = engramSegment.get(ValueLayout.JAVA_BYTE, engramRecordOffset + EncodingHeaderFields.OFFSET_LAST_RECALL_PROFILE);

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

    public static void copyV1HeaderToStrength(MemorySegment engramSegment, long engramRecordOffset,
                                              StrengthMemory strengthStore, MemoryType tier,
                                              int slotIndex) {
        long strengthRecordOffset = strengthStore.strengthOffset(tier, slotIndex);
        copyV1HeaderToStrength(engramSegment, engramRecordOffset, strengthStore.segment(), strengthRecordOffset, tier);
    }

    public static int migrateRecordsToStrength(MemorySegment engramSegment, long engramDataOffset,
                                               int engramRecordStride, int recordCount,
                                               StrengthMemory strengthStore, MemoryType tier) {
        for (int i = 0; i < recordCount; i++) {
            long engramRecordOffset = engramDataOffset + (long) i * engramRecordStride;
            copyV1HeaderToStrength(engramSegment, engramRecordOffset, strengthStore, tier, i);
        }
        return recordCount;
    }

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

    public static int detectVersion(Path storePath, int vectorBytes,
                                    boolean isHeaderOnly) {
        try (FileChannel ch = FileChannel.open(storePath, StandardOpenOption.READ)) {
            if (ch.size() < METADATA_PREAMBLE_BYTES) {
                return DEFAULT_HEADER_VERSION;
            }

            ByteBuffer buf = ByteBuffer.allocate(METADATA_PREAMBLE_BYTES);
            ch.read(buf);
            buf.flip();

            int magic = buf.getInt(META_MAGIC);
            if (magic != TIER_MAGIC && magic != RegionPreamble.MAGIC) {
                log.warn("Invalid magic in {}, assuming current layout", storePath);
                return DEFAULT_HEADER_VERSION;
            }

            int stride;
            if (magic == RegionPreamble.MAGIC) {
                stride = buf.getInt(32);
            } else {
                stride = buf.getInt(META_STRIDE);
            }
            int headerBytes = isHeaderOnly ? stride : stride - vectorBytes;

            if (headerBytes != EncodingHeaderFields.HEADER_BYTES) {
                log.warn("Unexpected header size {} in {} (expected {}), assuming current layout",
                        headerBytes, storePath, EncodingHeaderFields.HEADER_BYTES);
            }

            return DEFAULT_HEADER_VERSION;
        } catch (IOException e) {
            log.warn("Cannot detect header version from {}: {}", storePath, e.getMessage());
            return DEFAULT_HEADER_VERSION;
        }
    }

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
}
