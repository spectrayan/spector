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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorStorageException;
import com.spectrayan.spector.core.math.XxHash64;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.bundle.RegionRef;
import com.spectrayan.spector.kernel.id.SystemMemoryId;
import com.spectrayan.spector.kernel.layout.TextBlobLayout;
import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.shape.AbstractAppendMemory;
import com.spectrayan.spector.kernel.shape.AbstractRecordMemory;
import com.spectrayan.spector.kernel.shape.MemoryShape;
import com.spectrayan.spector.kernel.storage.StoragePaths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Binary reader/writer for {@code text.dat} files within partition directories,
 * extending {@link AbstractAppendMemory} directly with standard kernel layout.
 */
public final class TextBlobMemory extends AbstractAppendMemory<TextBlobLayout> {

    private static final Logger log = LoggerFactory.getLogger(TextBlobMemory.class);

    private static final int LEGACY_HEADER_BYTES = 16;

    /** Magic sentinel ('TCRC') marking a v2 CRC32C trailer after the text payload. */
    public static final int V2_CRC_TRAILER_MAGIC = 0x54435243;

    /** Size in bytes of the v2 CRC32C trailer: 4B magic + 4B text CRC32C + 4B entry CRC32C. */
    public static final int V2_CRC_TRAILER_BYTES = 12;

    /** Flag bit set on the tier ordinal byte of v2 CRC-protected entries. */
    private static final byte V2_ENTRY_FLAG = 0x40;

    private static final ValueLayout.OfInt BE_INT =
            ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);

    private final Path file;
    private int entryCount;

    private final Map<String, TextPosition> textPositionMap = new LinkedHashMap<>();
    private final Map<Long, TextPosition> hashToPosition = new java.util.HashMap<>();
    private final Map<Long, Long> textOffsetToEntryStart = new java.util.HashMap<>();
    private final AtomicInteger decryptFailCount = new AtomicInteger();
    private final ReentrantLock writeLock = new ReentrantLock();
    private volatile com.spectrayan.spector.kernel.storage.PayloadEncryptor encryptor;

    /**
     * Creates a TextBlobMemory for the given file path (no encryption).
     *
     * @param file path to the text.dat file (may or may not exist yet)
     */
    public TextBlobMemory(Path file) {
        this(file, null, 0);
    }

    /**
     * Creates a TextBlobMemory with encryption support.
     *
     * @param file      path to the text.dat file
     * @param encryptor data encryptor for text-at-rest (null → NOOP)
     */
    public TextBlobMemory(Path file, Object encryptor) {
        this(file, encryptor, 0L);
    }

    /**
     * Creates a TextBlobMemory with encryption support and an explicit segment size.
     *
     * @param file                   path to the text.dat file
     * @param encryptor              data encryptor for text-at-rest (null → NOOP)
     * @param configuredSegmentSize  configured segment size in bytes, or {@code 0} for default (32 MB)
     */
    public TextBlobMemory(Path file, Object encryptor, long configuredSegmentSize) {
        this(file, encryptor, migrateLegacyIfNeeded(file), configuredSegmentSize);
    }

    private TextBlobMemory(Path file, Object encryptor, Map<String, TextEntry> legacyEntries, long configuredSegmentSize) {
        super(SystemMemoryId.CORTEX_TEXT.id(), new TextBlobLayout(), 0, calculateInitialSize(file, legacyEntries, configuredSegmentSize), file);
        this.file = file;
        this.encryptor = encryptor instanceof com.spectrayan.spector.kernel.storage.PayloadEncryptor pe ? pe : null;
        this.entryCount = 0;

        if (legacyEntries != null && !legacyEntries.isEmpty()) {
            for (TextEntry entry : legacyEntries.values()) {
                write(entry.id(), entry.tier(), entry.text());
            }
            flush();
        }
    }

    /**
     * Creates a bundle-backed TextBlobMemory from a pre-sliced region segment.
     */
    public static TextBlobMemory fromBundle(Arena arena, MemorySegment regionSlice,
                                               Path bundlePath, boolean isNew,
                                               Object encryptor) {
        return new TextBlobMemory(arena, regionSlice, bundlePath, isNew, encryptor);
    }

    public static TextBlobMemory fromRegionRef(RegionRef regionRef, Path bundlePath,
                                               boolean isNew, Object encryptor) {
        return new TextBlobMemory(regionRef, bundlePath, isNew, encryptor);
    }

    private TextBlobMemory(RegionRef regionRef, Path bundlePath,
                           boolean isNew, Object encryptor) {
        super(SystemMemoryId.CORTEX_TEXT.id(), new TextBlobLayout(), 0,
              regionRef,
              isNew ? 0 : (int) RegionPreamble.readCount(regionRef.resolve(), 0),
              true, bundlePath);
        this.file = bundlePath;
        this.encryptor = encryptor instanceof com.spectrayan.spector.kernel.storage.PayloadEncryptor pe ? pe : null;
        this.entryCount = 0;
        if (isNew) {
            long now = System.currentTimeMillis();
            RegionPreamble.write(segment(), 0, 1, MemoryShape.APPEND, 1, 0, 0,
                    layout.recordStride(), layout.layoutId(), now, now);
            log.info("TextBlobMemory initialized new bundle region in: {} ({}KB)",
                    bundlePath, regionRef.resolve().byteSize() / 1024);
        } else {
            log.info("TextBlobMemory loaded from bundle region in: {} (cursor={}B)",
                    bundlePath, count);
        }
    }

    private TextBlobMemory(Arena arena, MemorySegment regionSlice, Path bundlePath,
                              boolean isNew, Object encryptor) {
        super(SystemMemoryId.CORTEX_TEXT.id(), new TextBlobLayout(), 0,
              arena, regionSlice,
              isNew ? 0 : (int) RegionPreamble.readCount(regionSlice, 0),
              true, bundlePath, null, true);  // bundleManaged=true
        this.file = bundlePath;
        this.encryptor = encryptor instanceof com.spectrayan.spector.kernel.storage.PayloadEncryptor pe ? pe : null;
        this.entryCount = 0;
        if (isNew) {
            long now = System.currentTimeMillis();
            RegionPreamble.write(segment(), 0, 1, MemoryShape.APPEND, 1, 0, 0,
                    layout.recordStride(), layout.layoutId(), now, now);
            log.info("TextBlobMemory initialized new bundle region in: {} ({}KB)",
                    bundlePath, regionSlice.byteSize() / 1024);
        } else {
            log.info("TextBlobMemory loaded from bundle region in: {} (cursor={}B)",
                    bundlePath, count);
        }
    }

    private static long calculateInitialSize(Path file, Map<String, TextEntry> legacyEntries, long configuredSegmentSize) {
        long size = configuredSegmentSize > 0 ? configuredSegmentSize : 32L * 1024 * 1024;
        if (legacyEntries != null && !legacyEntries.isEmpty()) {
            long totalBytes = 0;
            for (TextEntry entry : legacyEntries.values()) {
                byte[] idBytes = entry.id().getBytes(StandardCharsets.UTF_8);
                byte[] textBytes = entry.text().getBytes(StandardCharsets.UTF_8);
                totalBytes += 4 + (1 + 4 + idBytes.length + 4 + textBytes.length + V2_CRC_TRAILER_BYTES);
            }
            return Math.max(size, totalBytes);
        }
        if (Files.exists(file)) {
            try {
                size = Math.max(size, Files.size(file) - RegionPreamble.PREAMBLE_BYTES);
            } catch (IOException e) {
                // ignore
            }
        }
        return size;
    }

    private static Map<String, TextEntry> migrateLegacyIfNeeded(Path file) {
        if (!Files.exists(file)) {
            return null;
        }
        int magic = 0;
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            if (ch.size() >= 4) {
                ByteBuffer mb = ByteBuffer.allocate(4);
                ch.read(mb);
                mb.flip();
                magic = mb.getInt();
            }
        } catch (IOException e) {
            return null;
        }

        if (magic == StoragePaths.TEXT_DAT_MAGIC) {
            log.info("Migrating legacy text.dat format to standard Memory Kernel format: {}", file);
            Map<String, TextEntry> entries = readLegacyEntries(file);
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to delete legacy file for migration: " + file, e);
            }
            return entries;
        }
        return null;
    }

    private static Map<String, TextEntry> readLegacyEntries(Path file) {
        Map<String, TextEntry> entries = new LinkedHashMap<>();
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            long fileSize = ch.size();
            if (fileSize < LEGACY_HEADER_BYTES) return entries;

            try (Arena tempArena = Arena.ofShared()) {
                MemorySegment mapped = ch.map(FileChannel.MapMode.READ_ONLY, 0, fileSize, tempArena);
                int magic = mapped.get(BE_INT, 0);
                if (magic != StoragePaths.TEXT_DAT_MAGIC) {
                    return entries;
                }
                long pos = LEGACY_HEADER_BYTES;
                while (pos < fileSize) {
                    if (fileSize - pos < 9) break;
                    byte tierOrd = mapped.get(ValueLayout.JAVA_BYTE, pos);
                    pos += 1;
                    if (tierOrd < 0 || tierOrd >= MemoryType.values().length) break;

                    int idLen = mapped.get(BE_INT, pos);
                    pos += 4;
                    if (idLen < 0 || idLen > 10_000 || pos + idLen + 4 > fileSize) break;

                    String id = decodeUtf8FromSegment(mapped, pos, idLen);
                    pos += idLen;

                    int textLen = mapped.get(BE_INT, pos);
                    pos += 4;
                    if (textLen < 0 || textLen > 10_000_000 || pos + textLen > fileSize) break;

                    String rawText = decodeUtf8FromSegment(mapped, pos, textLen);
                    pos += textLen;

                    MemoryType tier = MemoryType.values()[tierOrd];
                    entries.put(id, new TextEntry(id, tier, rawText));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read legacy text.dat: " + file, e);
        }
        return entries;
    }

    /**
     * A single entry in the text.dat file.
     */
    public record TextEntry(String id, MemoryType tier, String text) {}

    /**
     * Position of a text entry within text.dat for off-heap random-access reads.
     */
    public record TextPosition(long textOffset, int textLength) {}

    private static final String ENCRYPTED_TEXT_PREFIX = "\u0000ENC1:";

    private static void writeEntryCrcTrailer(MemorySegment entrySeg, long textStartOffset, int trailerOffset) {
        int textSpan = (int) (trailerOffset - textStartOffset);
        int textCrc = AbstractRecordMemory.nonZeroCrc32c(entrySeg, textStartOffset, textSpan);
        int entryCrc = AbstractRecordMemory.nonZeroCrc32c(entrySeg, 0, trailerOffset);
        entrySeg.set(ValueLayout.JAVA_INT_UNALIGNED, trailerOffset, V2_CRC_TRAILER_MAGIC);
        entrySeg.set(ValueLayout.JAVA_INT_UNALIGNED, trailerOffset + 4, textCrc);
        entrySeg.set(ValueLayout.JAVA_INT_UNALIGNED, trailerOffset + 8, entryCrc);
    }

    /**
     * Appends a single text entry to the file and returns its byte position.
     *
     * @param id   memory identifier
     * @param tier the cognitive tier
     * @param text the raw text content
     * @return the byte position of the text within text.dat for direct off-heap reads
     */
    public TextPosition write(String id, MemoryType tier, String text) {
        writeLock.lock();
        try {
            byte[] idBytes = id.getBytes(StandardCharsets.UTF_8);
            byte[] plainBytes = text.getBytes(StandardCharsets.UTF_8);

            long hash = XxHash64.hash(plainBytes);
            TextPosition existing = hashToPosition.get(hash);
            if (existing != null && (encryptor == null || !encryptor.isEnabled()) && existing.textLength() == plainBytes.length) {
                textPositionMap.put(id, existing);
                entryCount++;
                return existing;
            }

            String storedText = text;
            if (encryptor != null && encryptor.isEnabled() && !text.isEmpty() && !text.startsWith(ENCRYPTED_TEXT_PREFIX)) {
                byte[] encBytes = encryptor.encryptText(plainBytes);
                storedText = ENCRYPTED_TEXT_PREFIX + java.util.Base64.getEncoder().encodeToString(encBytes);
            }
            byte[] textBytes = storedText.getBytes(StandardCharsets.UTF_8);

            int bodySize = 1 + 4 + idBytes.length + 4 + textBytes.length;
            int entrySize = bodySize + V2_CRC_TRAILER_BYTES;

            MemorySegment entrySeg = MemorySegment.ofArray(new byte[entrySize]);
            entrySeg.set(ValueLayout.JAVA_BYTE, 0, (byte) (tier.ordinal() | V2_ENTRY_FLAG));
            entrySeg.set(ValueLayout.JAVA_INT_UNALIGNED, 1, idBytes.length);
            MemorySegment.copy(MemorySegment.ofArray(idBytes), 0, entrySeg, 5, idBytes.length);
            entrySeg.set(ValueLayout.JAVA_INT_UNALIGNED, 5 + idBytes.length, textBytes.length);
            MemorySegment.copy(MemorySegment.ofArray(textBytes), 0, entrySeg, 5 + idBytes.length + 4, textBytes.length);

            long payloadOffset = append(entrySeg);
            long entryStart = dataOffset() + payloadOffset;
            long textOffset = entryStart + 9 + idBytes.length;

            // Compute CRC32C directly over the off-heap segment slice and write into the trailer
            MemorySegment seg = segment();
            long trailerOffset = entryStart + bodySize;
            int textCrc = AbstractRecordMemory.nonZeroCrc32c(seg, textOffset, textBytes.length);
            int entryCrc = AbstractRecordMemory.nonZeroCrc32c(seg, entryStart, bodySize);
            seg.set(ValueLayout.JAVA_INT_UNALIGNED, trailerOffset, V2_CRC_TRAILER_MAGIC);
            seg.set(ValueLayout.JAVA_INT_UNALIGNED, trailerOffset + 4, textCrc);
            seg.set(ValueLayout.JAVA_INT_UNALIGNED, trailerOffset + 8, entryCrc);

            TextPosition pos = new TextPosition(textOffset, textBytes.length);
            textPositionMap.put(id, pos);
            hashToPosition.put(hash, pos);
            textOffsetToEntryStart.put(textOffset, entryStart);
            entryCount++;

            return pos;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Reads text directly from the mmap'd segment at the given offset — zero-copy, off-heap.
     *
     * @param textOffset byte offset of the text in text.dat
     * @param textLength byte length of the UTF-8 text
     * @return the text string, or null if the mmap'd segment is unavailable
     */
    public String readTextDirect(long textOffset, int textLength) {
        if (textOffset < 0 || textLength < 0) return null;
        MemorySegment seg = segment();
        if (seg == null || textOffset + textLength > seg.byteSize()) return null;
        if (layout.crcEnabled()) {
            long trailerOffset = textOffset + textLength;
            if (trailerOffset + 8 <= seg.byteSize()
                    && seg.get(ValueLayout.JAVA_INT_UNALIGNED, trailerOffset) == V2_CRC_TRAILER_MAGIC) {
                int expectedTextCrc = seg.get(ValueLayout.JAVA_INT_UNALIGNED, trailerOffset + 4);
                int actualTextCrc = AbstractRecordMemory.nonZeroCrc32c(seg, textOffset, textLength);
                if (expectedTextCrc != actualTextCrc) {
                    throw new SpectorStorageException(ErrorCode.RECORD_CRC_CORRUPTED, textOffset);
                }
            }
        }
        String raw = decodeUtf8FromSegment(seg, textOffset, textLength);
        return decryptIfNeeded(raw);
    }

    /**
     * Reads all entries from the file using memory-mapped I/O (zero-copy).
     *
     * @return map of memory ID → TextEntry, empty map if file doesn't exist
     */
    public Map<String, TextEntry> readAll() {
        writeLock.lock();
        try {
            Map<String, TextEntry> entries = new LinkedHashMap<>();
            textPositionMap.clear();
            hashToPosition.clear();
            textOffsetToEntryStart.clear();

            if (file != null && !Files.exists(file)) {
                return entries;
            }

            java.util.Iterator<MemorySegment> it = replay(0);
            long currentOffset = 0;
            while (it.hasNext()) {
                MemorySegment entrySeg = it.next();
                int entryLen = (int) entrySeg.byteSize();
                if (entryLen < 9) {
                    currentOffset += 4 + entryLen;
                    continue;
                }

                byte rawTier = entrySeg.get(ValueLayout.JAVA_BYTE, 0);
                boolean isV2Entry = (rawTier & V2_ENTRY_FLAG) != 0;
                byte tierOrd = (byte) (rawTier & ~V2_ENTRY_FLAG);
                if (tierOrd < 0 || tierOrd >= MemoryType.values().length) {
                    if (isV2Entry) {
                        throw new SpectorStorageException(ErrorCode.RECORD_CRC_CORRUPTED, currentOffset);
                    }
                    currentOffset += 4 + entryLen;
                    continue;
                }

                if (isV2Entry && layout.crcEnabled()) {
                    if (entryLen < 9 + V2_CRC_TRAILER_BYTES) {
                        throw new SpectorStorageException(ErrorCode.RECORD_CRC_CORRUPTED, currentOffset);
                    }
                    int trailerOffset = entryLen - V2_CRC_TRAILER_BYTES;
                    int magic = entrySeg.get(ValueLayout.JAVA_INT_UNALIGNED, trailerOffset);
                    if (magic != V2_CRC_TRAILER_MAGIC) {
                        throw new SpectorStorageException(ErrorCode.RECORD_CRC_CORRUPTED, currentOffset);
                    }
                    int expectedEntryCrc = entrySeg.get(ValueLayout.JAVA_INT_UNALIGNED, trailerOffset + 8);
                    int actualEntryCrc = AbstractRecordMemory.nonZeroCrc32c(entrySeg, 0, trailerOffset);
                    if (expectedEntryCrc != actualEntryCrc) {
                        throw new SpectorStorageException(ErrorCode.RECORD_CRC_CORRUPTED, currentOffset);
                    }
                }

                int idLen = entrySeg.get(ValueLayout.JAVA_INT_UNALIGNED, 1);
                int maxBodyLen = isV2Entry ? (entryLen - V2_CRC_TRAILER_BYTES) : entryLen;
                if (idLen < 0 || idLen > 10_000 || 5 + idLen + 4 > maxBodyLen) {
                    if (isV2Entry) {
                        throw new SpectorStorageException(ErrorCode.RECORD_CRC_CORRUPTED, currentOffset);
                    }
                    currentOffset += 4 + entryLen;
                    continue;
                }

                String id = decodeUtf8FromSegment(entrySeg, 5, idLen);
                int textLen = entrySeg.get(ValueLayout.JAVA_INT_UNALIGNED, 5 + idLen);
                if (textLen < 0 || textLen > 10_000_000 || 5 + idLen + 4 + textLen > maxBodyLen) {
                    if (isV2Entry) {
                        throw new SpectorStorageException(ErrorCode.RECORD_CRC_CORRUPTED, currentOffset);
                    }
                    currentOffset += 4 + entryLen;
                    continue;
                }

                if (isV2Entry && layout.crcEnabled()) {
                    int trailerOffset = entryLen - V2_CRC_TRAILER_BYTES;
                    int expectedTextCrc = entrySeg.get(ValueLayout.JAVA_INT_UNALIGNED, trailerOffset + 4);
                    int actualTextCrc = AbstractRecordMemory.nonZeroCrc32c(
                            entrySeg, 9 + idLen, trailerOffset - (9 + idLen));
                    if (expectedTextCrc != actualTextCrc) {
                        throw new SpectorStorageException(ErrorCode.RECORD_CRC_CORRUPTED, currentOffset);
                    }
                }

                String rawText = decodeUtf8FromSegment(entrySeg, 5 + idLen + 4, textLen);
                String text = decryptIfNeeded(rawText);

                MemoryType tier = MemoryType.values()[tierOrd];
                entries.put(id, new TextEntry(id, tier, text));

                long entryStart = dataOffset() + currentOffset + 4;
                long textOffset = entryStart + 9 + idLen;
                TextPosition pos = new TextPosition(textOffset, textLen);
                textPositionMap.put(id, pos);
                textOffsetToEntryStart.put(textOffset, entryStart);

                byte[] textBytes = rawText.getBytes(StandardCharsets.UTF_8);
                long hash = XxHash64.hash(textBytes);
                hashToPosition.put(hash, pos);

                currentOffset += 4 + entryLen;
            }

            this.entryCount = entries.size();
            log.debug("Loaded {} text entries from {} (mmap'd off-heap)", entries.size(), file);

            int failures = decryptFailCount.getAndSet(0);
            if (failures > 0) {
                log.warn("text.dat: {} of {} entries failed decryption (wrong key or legacy data): {}",
                        failures, entries.size(), file);
            }

            return entries;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Returns text positions collected during {@link #readAll()}.
     *
     * @return unmodifiable map of text positions, empty if readAll() not called
     */
    public Map<String, TextPosition> textPositions() {
        return java.util.Collections.unmodifiableMap(textPositionMap);
    }

    /**
     * Rebuilds the file from the given entries (compaction in-place).
     *
     * @param entries the surviving entries to write
     */
    public void rebuild(Map<String, TextEntry> entries) {
        writeLock.lock();
        try {
            textPositionMap.clear();
            hashToPosition.clear();
            textOffsetToEntryStart.clear();

            // Reset append cursor and count in-place
            this.count = 0;
            persistCount();

            for (TextEntry entry : entries.values()) {
                write(entry.id(), entry.tier(), entry.text() != null ? entry.text() : "");
            }
            flush();
            this.entryCount = entries.size();
            log.debug("Rebuilt text.dat with {} entries (deduplicated): {}", entries.size(), file);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Re-encrypts all {@code ENC1:} text entries in-place from {@code oldEncryptor} to {@code newEncryptor}
     * without changing byte offsets or lengths in {@code INDEX_MIDX}.
     */
    public int rekeyEncryptedEntries(com.spectrayan.spector.kernel.storage.PayloadEncryptor oldEncryptor,
                                     com.spectrayan.spector.kernel.storage.PayloadEncryptor newEncryptor) {
        writeLock.lock();
        try {
            int rekeyed = 0;
            java.util.Iterator<MemorySegment> it = replay(0);
            while (it.hasNext()) {
                MemorySegment entrySeg = it.next();
                int entryLen = (int) entrySeg.byteSize();
                if (entryLen < 9) {
                    continue;
                }
                byte rawTier = entrySeg.get(ValueLayout.JAVA_BYTE, 0);
                boolean isV2Entry = (rawTier & V2_ENTRY_FLAG) != 0;
                int maxBodyLen = isV2Entry ? (entryLen - V2_CRC_TRAILER_BYTES) : entryLen;
                int idLen = entrySeg.get(ValueLayout.JAVA_INT_UNALIGNED, 1);
                if (idLen < 0 || idLen > 10_000 || 5 + idLen + 4 > maxBodyLen) {
                    continue;
                }
                int textLen = entrySeg.get(ValueLayout.JAVA_INT_UNALIGNED, 5 + idLen);
                if (textLen <= 0 || textLen > 10_000_000 || 5 + idLen + 4 + textLen > maxBodyLen) {
                    continue;
                }
                String rawText = decodeUtf8FromSegment(entrySeg, 5 + idLen + 4, textLen);
                if (rawText.startsWith(ENCRYPTED_TEXT_PREFIX)
                        && oldEncryptor != null && oldEncryptor.isEnabled()
                        && newEncryptor != null && newEncryptor.isEnabled()) {
                    byte[] cipherBytes = java.util.Base64.getDecoder().decode(
                            rawText.substring(ENCRYPTED_TEXT_PREFIX.length()));
                    byte[] plainBytes = oldEncryptor.decryptText(cipherBytes);
                    byte[] newCipherBytes = newEncryptor.encryptText(plainBytes);
                    java.util.Arrays.fill(plainBytes, (byte) 0);
                    String newStoredText = ENCRYPTED_TEXT_PREFIX
                            + java.util.Base64.getEncoder().encodeToString(newCipherBytes);
                    byte[] newTextBytes = newStoredText.getBytes(StandardCharsets.UTF_8);
                    if (newTextBytes.length == textLen) {
                        MemorySegment.copy(MemorySegment.ofArray(newTextBytes), 0,
                                entrySeg, 5 + idLen + 4, textLen);
                        if (isV2Entry) {
                            writeEntryCrcTrailer(entrySeg, 9 + idLen, entryLen - V2_CRC_TRAILER_BYTES);
                        }
                        rekeyed++;
                    }
                }
            }
            this.encryptor = newEncryptor;
            flush();
            return rekeyed;
        } finally {
            writeLock.unlock();
        }
    }

    /** Returns the number of entries in this store. */
    @Override
    public int size() {
        return entryCount;
    }

    /** Returns the file path. */
    public Path path() {
        return file;
    }

    private String decryptIfNeeded(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        if (raw.startsWith(ENCRYPTED_TEXT_PREFIX)) {
            if (encryptor == null || !encryptor.isEnabled()) {
                decryptFailCount.incrementAndGet();
                throw new com.spectrayan.spector.commons.error.SpectorMemoryException(
                        com.spectrayan.spector.commons.error.ErrorCode.MEMORY_ENCRYPTION_KEY_MISSING,
                        file != null ? file.toString() : "text.dat");
            }
            try {
                byte[] cipherBytes = java.util.Base64.getDecoder().decode(raw.substring(ENCRYPTED_TEXT_PREFIX.length()));
                byte[] plainBytes = encryptor.decryptText(cipherBytes);
                return new String(plainBytes, StandardCharsets.UTF_8);
            } catch (com.spectrayan.spector.commons.error.SpectorMemoryException e) {
                decryptFailCount.incrementAndGet();
                throw e;
            } catch (RuntimeException e) {
                decryptFailCount.incrementAndGet();
                throw new com.spectrayan.spector.commons.error.SpectorMemoryException(
                        com.spectrayan.spector.commons.error.ErrorCode.MEMORY_AUTH_TAG_MISMATCH,
                        e,
                        "Failed to decrypt text entry in " + file);
            }
        }
        return raw;
    }

    private static String decodeUtf8FromSegment(MemorySegment segment, long offset, int length) {
        if (length == 0) return "";
        byte[] bytes = new byte[length];
        MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, offset, bytes, 0, length);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Outcome of an {@link #eraseEntry(String)} attempt.
     */
    public record EraseOutcome(Status status, int bytesZeroed, int sharedWith) {

        /** Erase outcome classification. */
        public enum Status {
            /** The text bytes were overwritten with zeros. */
            ERASED,
            /** No text was recorded for this id; nothing to erase. */
            NOT_FOUND,
            /**
             * The id's mapping was removed, but the bytes were <b>kept</b> because one or more other live
             * ids share them via content-hash deduplication. Zeroing would have destroyed their text too.
             * Must be disclosed, not reported as an erasure.
             */
            SHARED
        }

        public static EraseOutcome erased(int bytes) { return new EraseOutcome(Status.ERASED, bytes, 0); }

        public static EraseOutcome notFound() { return new EraseOutcome(Status.NOT_FOUND, 0, 0); }

        public static EraseOutcome shared(int n) { return new EraseOutcome(Status.SHARED, 0, n); }

        /** Returns {@code true} only if bytes were actually overwritten. */
        public boolean erased() { return status == Status.ERASED; }
    }

    /**
     * Overwrites the stored text for {@code targetId} with zeros, in place.
     *
     * @param targetId the memory id whose text should be destroyed
     * @return what actually happened
     */
    public EraseOutcome eraseEntry(String targetId) {
        writeLock.lock();
        try {
            if (!Files.exists(file)) {
                return EraseOutcome.notFound();
            }

            TextPosition pos = textPositionMap.get(targetId);
            if (pos == null) return EraseOutcome.notFound();

            int coTenants = 0;
            for (var e : textPositionMap.entrySet()) {
                if (!e.getKey().equals(targetId) && pos.equals(e.getValue())) {
                    coTenants++;
                }
            }
            if (coTenants > 0) {
                textPositionMap.remove(targetId);
                entryCount--;
                log.warn("Text for memory '{}' NOT erased: its {} bytes are shared with {} other live "
                                + "record(s) by content-hash deduplication. Mapping removed only.",
                        targetId, pos.textLength(), coTenants);
                return EraseOutcome.shared(coTenants);
            }

            MemorySegment seg = segment();
            if (seg != null) {
                seg.asSlice(pos.textOffset(), pos.textLength()).fill((byte) 0);
                // Collapse the frame's declared text length so a reload yields empty text rather than a
                // NUL-filled string of the original length.
                long textLenFieldOffset = pos.textOffset() - 4L;
                if (textLenFieldOffset >= 0) {
                    seg.set(ValueLayout.JAVA_INT_UNALIGNED, textLenFieldOffset, 0);
                }
                long trailerOffset = pos.textOffset() + pos.textLength();
                if (trailerOffset + V2_CRC_TRAILER_BYTES <= seg.byteSize()
                        && seg.get(ValueLayout.JAVA_INT_UNALIGNED, trailerOffset) == V2_CRC_TRAILER_MAGIC) {
                    int textCrc = AbstractRecordMemory.nonZeroCrc32c(seg, pos.textOffset(), pos.textLength());
                    seg.set(ValueLayout.JAVA_INT_UNALIGNED, trailerOffset + 4, textCrc);
                    Long entryStart = textOffsetToEntryStart.remove(pos.textOffset());
                    if (entryStart != null && entryStart >= dataOffset()) {
                        int entryCrc = AbstractRecordMemory.nonZeroCrc32c(
                                seg, entryStart, (int) (trailerOffset - entryStart));
                        seg.set(ValueLayout.JAVA_INT_UNALIGNED, trailerOffset + 8, entryCrc);
                    }
                }
                flush();
            }

            hashToPosition.entrySet().removeIf(entry -> entry.getValue().equals(pos));
            textPositionMap.remove(targetId);
            entryCount--;

            log.debug("Erased {} bytes of text for memory '{}'", pos.textLength(), targetId);
            return EraseOutcome.erased(pos.textLength());
        } finally {
            writeLock.unlock();
        }
    }
}
