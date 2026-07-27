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

import com.spectrayan.spector.memory.DataEncryptor;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.StorageLayout;
import com.spectrayan.spector.memory.kernel.MemoryHeader;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.layout.TextBlobLayout;
import com.spectrayan.spector.memory.kernel.shape.DefaultAppendMemory;

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
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Binary reader/writer for {@code text.dat} files within partition directories,
 * delegating storage to {@link DefaultAppendMemory} with standard kernel layout.
 */
public final class TextDataStore implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TextDataStore.class);

    private static final int LEGACY_HEADER_BYTES = 16;

    private static final ValueLayout.OfInt BE_INT =
            ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);

    private final Path file;
    private final DataEncryptor encryptor;
    private int entryCount;
    private DefaultAppendMemory<TextBlobLayout> appendMemory;

    private final Map<String, TextPosition> textPositionMap = new LinkedHashMap<>();
    private final Map<Long, TextPosition> hashToPosition = new java.util.HashMap<>();
    private final AtomicInteger decryptFailCount = new AtomicInteger();

    /**
     * Creates a TextDataStore for the given file path (no encryption).
     *
     * @param file path to the text.dat file (may or may not exist yet)
     */
    public TextDataStore(Path file) {
        this(file, DataEncryptor.NOOP);
    }

    /**
     * Creates a TextDataStore with encryption support.
     *
     * @param file      path to the text.dat file
     * @param encryptor data encryptor for text-at-rest (null → NOOP)
     */
    public TextDataStore(Path file, DataEncryptor encryptor) {
        this.file = file;
        this.encryptor = encryptor != null ? encryptor : DataEncryptor.NOOP;
        this.entryCount = 0;
    }

    /**
     * Creates a TextDataStore for a partition directory, using the standard file name.
     *
     * @param partitionDir the partition directory
     * @return a new TextDataStore instance
     */
    public static TextDataStore forPartition(Path partitionDir) {
        return new TextDataStore(StorageLayout.textDat(partitionDir));
    }

    /**
     * Factory for a partition directory with encryption support.
     *
     * @param partitionDir the partition directory
     * @param encryptor    data encryptor (null → NOOP)
     * @return a new TextDataStore instance
     */
    public static TextDataStore forPartition(Path partitionDir, DataEncryptor encryptor) {
        return new TextDataStore(StorageLayout.textDat(partitionDir), encryptor);
    }

    /**
     * A single entry in the text.dat file.
     *
     * @param id   memory identifier
     * @param tier the cognitive tier this memory belongs to
     * @param text the raw text content
     */
    public record TextEntry(String id, MemoryType tier, String text) {}

    /**
     * Position of a text entry within text.dat for off-heap random-access reads.
     *
     * @param textOffset byte offset of the text content in text.dat (after entry header)
     * @param textLength byte length of the UTF-8 encoded text
     */
    public record TextPosition(long textOffset, int textLength) {}

    private synchronized void initAppendMemory(long requiredSize) {
        if (appendMemory != null) {
            appendMemory.close();
            appendMemory = null;
        }

        // Check for legacy migration first
        if (Files.exists(file)) {
            int magic = 0;
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
                if (ch.size() >= 4) {
                    ByteBuffer mb = ByteBuffer.allocate(4);
                    ch.read(mb);
                    mb.flip();
                    magic = mb.getInt();
                }
            } catch (IOException e) {
                // ignore
            }
            if (magic == StorageLayout.TEXT_DAT_MAGIC) {
                Map<String, TextEntry> legacyEntries = readLegacyEntries();
                migrateLegacyFile(legacyEntries);
                return;
            }
        }

        long size = 1024 * 1024; // 1MB default
        if (Files.exists(file)) {
            try {
                size = Math.max(size, Files.size(file) - MemoryHeader.HEADER_BYTES);
            } catch (IOException e) {
                // ignore
            }
        }
        if (requiredSize > 0) {
            size = Math.max(size, requiredSize);
        }

        MemoryId memoryId = MemoryId.of("cortex", "text");
        TextBlobLayout layout = new TextBlobLayout();
        appendMemory = new DefaultAppendMemory<>(memoryId, layout, 0, size, file);
    }

    private Map<String, TextEntry> readLegacyEntries() {
        Map<String, TextEntry> entries = new LinkedHashMap<>();
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            long fileSize = ch.size();
            if (fileSize < LEGACY_HEADER_BYTES) return entries;

            try (Arena tempArena = Arena.ofShared()) {
                MemorySegment mapped = ch.map(FileChannel.MapMode.READ_ONLY, 0, fileSize, tempArena);
                int magic = mapped.get(BE_INT, 0);
                if (magic != StorageLayout.TEXT_DAT_MAGIC) {
                    return entries;
                }
                int count = mapped.get(BE_INT, 8);
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

                    String text = decryptIfNeeded(rawText);
                    MemoryType tier = MemoryType.values()[tierOrd];
                    entries.put(id, new TextEntry(id, tier, text));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read legacy text.dat: " + file, e);
        }
        return entries;
    }

    private void migrateLegacyFile(Map<String, TextEntry> legacyEntries) {
        log.info("Migrating legacy text.dat format to standard Memory Kernel format: {}", file);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete legacy file for migration: " + file, e);
        }

        long totalBytes = 0;
        for (TextEntry entry : legacyEntries.values()) {
            byte[] idBytes = entry.id().getBytes(StandardCharsets.UTF_8);
            byte[] textBytes = entry.text().getBytes(StandardCharsets.UTF_8);
            totalBytes += 4 + (1 + 4 + idBytes.length + 4 + textBytes.length);
        }

        initAppendMemory(totalBytes);

        for (TextEntry entry : legacyEntries.values()) {
            byte[] idBytes = entry.id().getBytes(StandardCharsets.UTF_8);
            byte[] textBytes = entry.text().getBytes(StandardCharsets.UTF_8);

            int entrySize = 1 + 4 + idBytes.length + 4 + textBytes.length;
            MemorySegment entrySeg = Arena.ofAuto().allocate(entrySize);
            entrySeg.set(ValueLayout.JAVA_BYTE, 0, (byte) entry.tier().ordinal());
            entrySeg.set(ValueLayout.JAVA_INT_UNALIGNED, 1, idBytes.length);
            MemorySegment.copy(MemorySegment.ofArray(idBytes), 0, entrySeg, 5, idBytes.length);
            entrySeg.set(ValueLayout.JAVA_INT_UNALIGNED, 5 + idBytes.length, textBytes.length);
            MemorySegment.copy(MemorySegment.ofArray(textBytes), 0, entrySeg, 5 + idBytes.length + 4, textBytes.length);

            long payloadOffset = appendMemory.append(entrySeg);
            long textOffset = appendMemory.dataOffset() + payloadOffset + 9 + idBytes.length;
            textPositionMap.put(entry.id(), new TextPosition(textOffset, textBytes.length));

            long hash = XxHash64.hash(textBytes);
            hashToPosition.put(hash, new TextPosition(textOffset, textBytes.length));
        }
        appendMemory.flush();
        this.entryCount = legacyEntries.size();
    }

    /**
     * Appends a single text entry to the file and returns its byte position.
     *
     * @param id   memory identifier
     * @param tier the cognitive tier
     * @param text the raw text content
     * @return the byte position of the text within text.dat for direct off-heap reads
     */
    public synchronized TextPosition write(String id, MemoryType tier, String text) {
        byte[] idBytes = id.getBytes(StandardCharsets.UTF_8);
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);

        long hash = XxHash64.hash(textBytes);
        TextPosition existing = hashToPosition.get(hash);
        if (existing != null) {
            textPositionMap.put(id, existing);
            entryCount++;
            return existing;
        }

        int entrySize = 1 + 4 + idBytes.length + 4 + textBytes.length;

        if (appendMemory == null) {
            initAppendMemory(0);
        }

        long availableSpace = appendMemory.segment().byteSize() - appendMemory.dataOffset() - appendMemory.appendCursor();
        if (availableSpace < 4 + entrySize) {
            long newSize = Math.max(appendMemory.segment().byteSize() - appendMemory.dataOffset() * 2,
                    appendMemory.appendCursor() + 4 + entrySize + 1024 * 1024);
            initAppendMemory(newSize);
        }

        MemorySegment entrySeg = Arena.ofAuto().allocate(entrySize);
        entrySeg.set(ValueLayout.JAVA_BYTE, 0, (byte) tier.ordinal());
        entrySeg.set(ValueLayout.JAVA_INT_UNALIGNED, 1, idBytes.length);
        MemorySegment.copy(MemorySegment.ofArray(idBytes), 0, entrySeg, 5, idBytes.length);
        entrySeg.set(ValueLayout.JAVA_INT_UNALIGNED, 5 + idBytes.length, textBytes.length);
        MemorySegment.copy(MemorySegment.ofArray(textBytes), 0, entrySeg, 5 + idBytes.length + 4, textBytes.length);

        long payloadOffset = appendMemory.append(entrySeg);
        long textOffset = appendMemory.dataOffset() + payloadOffset + 9 + idBytes.length;

        TextPosition pos = new TextPosition(textOffset, textBytes.length);
        textPositionMap.put(id, pos);
        hashToPosition.put(hash, pos);
        entryCount++;

        return pos;
    }

    /**
     * Reads text directly from the mmap'd segment at the given offset — zero-copy, off-heap.
     *
     * @param textOffset byte offset of the text in text.dat
     * @param textLength byte length of the UTF-8 text
     * @return the text string, or null if the mmap'd segment is unavailable
     */
    public String readTextDirect(long textOffset, int textLength) {
        DefaultAppendMemory<TextBlobLayout> mem = this.appendMemory;
        if (mem == null || textOffset < 0 || textLength < 0) return null;
        MemorySegment seg = mem.segment();
        if (textOffset + textLength > seg.byteSize()) return null;
        String raw = decodeUtf8FromSegment(seg, textOffset, textLength);
        return decryptIfNeeded(raw);
    }

    /**
     * Reads all entries from the file using memory-mapped I/O (zero-copy).
     *
     * @return map of memory ID → TextEntry, empty map if file doesn't exist
     */
    public synchronized Map<String, TextEntry> readAll() {
        Map<String, TextEntry> entries = new LinkedHashMap<>();
        textPositionMap.clear();
        hashToPosition.clear();

        if (!Files.exists(file)) {
            initAppendMemory(0);
            return entries;
        }

        initAppendMemory(0);

        if (appendMemory == null) {
            return entries;
        }

        java.util.Iterator<MemorySegment> it = appendMemory.replay(0);
        long currentOffset = 0;
        while (it.hasNext()) {
            MemorySegment entrySeg = it.next();
            int entryLen = (int) entrySeg.byteSize();
            if (entryLen < 9) {
                currentOffset += 4 + entryLen;
                continue;
            }

            byte tierOrd = entrySeg.get(ValueLayout.JAVA_BYTE, 0);
            if (tierOrd < 0 || tierOrd >= MemoryType.values().length) {
                currentOffset += 4 + entryLen;
                continue;
            }

            int idLen = entrySeg.get(ValueLayout.JAVA_INT_UNALIGNED, 1);
            if (idLen < 0 || idLen > 10_000 || 5 + idLen + 4 > entryLen) {
                currentOffset += 4 + entryLen;
                continue;
            }

            String id = decodeUtf8FromSegment(entrySeg, 5, idLen);
            int textLen = entrySeg.get(ValueLayout.JAVA_INT_UNALIGNED, 5 + idLen);
            if (textLen < 0 || textLen > 10_000_000 || 5 + idLen + 4 + textLen > entryLen) {
                currentOffset += 4 + entryLen;
                continue;
            }

            String rawText = decodeUtf8FromSegment(entrySeg, 5 + idLen + 4, textLen);
            String text = decryptIfNeeded(rawText);

            MemoryType tier = MemoryType.values()[tierOrd];
            entries.put(id, new TextEntry(id, tier, text));

            long textOffset = appendMemory.dataOffset() + currentOffset + 4 + 9 + idLen;
            TextPosition pos = new TextPosition(textOffset, textLen);
            textPositionMap.put(id, pos);

            byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
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
    }

    /**
     * Returns text positions collected during {@link #readAll()}.
     *
     * @return unmodifiable map of text positions, empty if readAll() not called
     */
    public java.util.Map<String, TextPosition> textPositions() {
        return java.util.Collections.unmodifiableMap(textPositionMap);
    }

    /**
     * Rebuilds the file from the given entries (compaction).
     *
     * @param entries the surviving entries to write
     */
    public synchronized void rebuild(Map<String, TextEntry> entries) {
        try {
            if (appendMemory != null) {
                appendMemory.close();
                appendMemory = null;
            }

            Files.deleteIfExists(file);

            textPositionMap.clear();
            hashToPosition.clear();

            Path tempFile = file.resolveSibling(file.getFileName() + ".tmp");
            Files.deleteIfExists(tempFile);

            MemoryId tempId = MemoryId.of("cortex", "text_temp");
            TextBlobLayout layout = new TextBlobLayout();

            long estimatedSize = 1024 * 1024;
            for (TextEntry entry : entries.values()) {
                estimatedSize += 4 + 1 + 4 + entry.id().getBytes(StandardCharsets.UTF_8).length + 4 + entry.text().getBytes(StandardCharsets.UTF_8).length;
            }

            try (DefaultAppendMemory<TextBlobLayout> tempMemory = new DefaultAppendMemory<>(tempId, layout, 0, estimatedSize, tempFile)) {
                for (TextEntry entry : entries.values()) {
                    byte[] idBytes = entry.id().getBytes(StandardCharsets.UTF_8);
                    byte[] textBytes = entry.text().getBytes(StandardCharsets.UTF_8);
                    long hash = XxHash64.hash(textBytes);

                    TextPosition pos = hashToPosition.get(hash);
                    if (pos != null) {
                        textPositionMap.put(entry.id(), pos);
                        continue;
                    }

                    int entrySize = 1 + 4 + idBytes.length + 4 + textBytes.length;
                    MemorySegment entrySeg = Arena.ofAuto().allocate(entrySize);
                    entrySeg.set(ValueLayout.JAVA_BYTE, 0, (byte) entry.tier().ordinal());
                    entrySeg.set(ValueLayout.JAVA_INT_UNALIGNED, 1, idBytes.length);
                    MemorySegment.copy(MemorySegment.ofArray(idBytes), 0, entrySeg, 5, idBytes.length);
                    entrySeg.set(ValueLayout.JAVA_INT_UNALIGNED, 5 + idBytes.length, textBytes.length);
                    MemorySegment.copy(MemorySegment.ofArray(textBytes), 0, entrySeg, 5 + idBytes.length + 4, textBytes.length);

                    long payloadOffset = tempMemory.append(entrySeg);
                    long textOffset = tempMemory.dataOffset() + payloadOffset + 9 + idBytes.length;

                    TextPosition newPos = new TextPosition(textOffset, textBytes.length);
                    textPositionMap.put(entry.id(), newPos);
                    hashToPosition.put(hash, newPos);
                }
                tempMemory.flush();
            }

            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            initAppendMemory(0);
            this.entryCount = entries.size();
            log.debug("Rebuilt text.dat with {} entries (deduplicated): {}", entries.size(), file);

        } catch (IOException e) {
            throw new UncheckedIOException("Failed to rebuild text.dat: " + file, e);
        }
    }

    /**
     * Returns the off-heap mapped segment for zero-copy text access.
     *
     * @return the mapped MemorySegment, or null
     */
    public MemorySegment segment() {
        return appendMemory != null ? appendMemory.segment() : null;
    }

    /**
     * Returns the underlying kernel DefaultAppendMemory backing this text store.
     */
    public DefaultAppendMemory<TextBlobLayout> backing() {
        return this.appendMemory;
    }

    /** Returns the number of entries in this store. */
    public int size() {
        return entryCount;
    }

    /** Returns the file path. */
    public Path path() {
        return file;
    }

    @Override
    public synchronized void close() {
        if (appendMemory != null) {
            appendMemory.close();
            appendMemory = null;
        }
    }

    private final String decryptIfNeeded(String raw) {
        if (!encryptor.isEnabled() || raw == null || raw.isEmpty()) {
            return raw;
        }
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(raw);
            byte[] decrypted = encryptor.decryptText(decoded);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return raw;
        } catch (RuntimeException e) {
            decryptFailCount.incrementAndGet();
            log.debug("Failed to decrypt text entry (wrong key?): {}", e.getMessage());
            return raw;
        }
    }

    private static String decodeUtf8FromSegment(MemorySegment segment, long offset, int length) {
        if (length == 0) return "";
        byte[] bytes = new byte[length];
        MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, offset, bytes, 0, length);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public synchronized boolean eraseEntry(String targetId) {
        if (!Files.exists(file)) {
            return false;
        }

        if (appendMemory == null) {
            initAppendMemory(0);
        }

        TextPosition pos = textPositionMap.get(targetId);
        if (pos == null) return false;

        MemorySegment seg = appendMemory.segment();
        seg.asSlice(pos.textOffset(), pos.textLength()).fill((byte) 0);
        appendMemory.flush();

        log.debug("Securely erased {} bytes of text for memory '{}'", pos.textLength(), targetId);
        return true;
    }

    /**
     * Pure-Java xxHash64 implementation.
     */
    public static final class XxHash64 {
        private static final long PRIME1 = 0x9E3779B185EBCA87L;
        private static final long PRIME2 = 0xC2B2AE3D27D4EB4FL;
        private static final long PRIME3 = 0x165667B19E3779F9L;
        private static final long PRIME4 = 0x85EBCA77C2B2AE63L;
        private static final long PRIME5 = 0x27D4EB2F165667C5L;

        public static long hash(byte[] input) {
            int len = input.length;
            long h64;
            int index = 0;

            if (len >= 32) {
                long v1 = PRIME1 + PRIME2;
                long v2 = PRIME2;
                long v3 = 0;
                long v4 = -PRIME1;

                int limit = len - 32;
                while (index <= limit) {
                    v1 = round(v1, readLongLE(input, index));
                    index += 8;
                    v2 = round(v2, readLongLE(input, index));
                    index += 8;
                    v3 = round(v3, readLongLE(input, index));
                    index += 8;
                    v4 = round(v4, readLongLE(input, index));
                    index += 8;
                }

                h64 = Long.rotateLeft(v1, 1) + Long.rotateLeft(v2, 7) + Long.rotateLeft(v3, 12) + Long.rotateLeft(v4, 18);
                h64 = mergeRound(h64, v1);
                h64 = mergeRound(h64, v2);
                h64 = mergeRound(h64, v3);
                h64 = mergeRound(h64, v4);
            } else {
                h64 = PRIME5;
            }

            h64 += len;

            int limit = len - 8;
            while (index <= limit) {
                long k1 = round(0, readLongLE(input, index));
                h64 ^= k1;
                h64 = Long.rotateLeft(h64, 27) * PRIME1 + PRIME4;
                index += 8;
            }

            limit = len - 4;
            if (index <= limit) {
                h64 ^= (readIntLE(input, index) & 0xFFFFFFFFL) * PRIME1;
                h64 = Long.rotateLeft(h64, 23) * PRIME2 + PRIME3;
                index += 4;
            }

            while (index < len) {
                h64 ^= (input[index] & 0xFFL) * PRIME5;
                h64 = Long.rotateLeft(h64, 11) * PRIME1;
                index++;
            }

            h64 ^= h64 >>> 33;
            h64 *= PRIME2;
            h64 ^= h64 >>> 29;
            h64 *= PRIME3;
            h64 ^= h64 >>> 32;

            return h64;
        }

        private static long round(long acc, long val) {
            acc += val * PRIME2;
            acc = Long.rotateLeft(acc, 31);
            acc *= PRIME1;
            return acc;
        }

        private static long mergeRound(long acc, long val) {
            val = round(0, val);
            acc ^= val;
            acc = acc * PRIME1 + PRIME4;
            return acc;
        }

        private static long readLongLE(byte[] bytes, int index) {
            return ((bytes[index] & 0xFFL)) |
                   ((bytes[index + 1] & 0xFFL) << 8) |
                   ((bytes[index + 2] & 0xFFL) << 16) |
                   ((bytes[index + 3] & 0xFFL) << 24) |
                   ((bytes[index + 4] & 0xFFL) << 32) |
                   ((bytes[index + 5] & 0xFFL) << 40) |
                   ((bytes[index + 6] & 0xFFL) << 48) |
                   ((bytes[index + 7] & 0xFFL) << 56);
        }

        private static int readIntLE(byte[] bytes, int index) {
            return ((bytes[index] & 0xFF)) |
                   ((bytes[index + 1] & 0xFF) << 8) |
                   ((bytes[index + 2] & 0xFF) << 16) |
                   ((bytes[index + 3] & 0xFF) << 24);
        }
    }
}
