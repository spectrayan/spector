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
import com.spectrayan.spector.memory.kernel.StorageLayout;
import com.spectrayan.spector.memory.kernel.MemoryHeader;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.codec.XxHash64;
import com.spectrayan.spector.memory.kernel.layout.TextBlobLayout;
import com.spectrayan.spector.memory.kernel.shape.AbstractAppendMemory;

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

/**
 * Binary reader/writer for {@code text.dat} files within partition directories,
 * extending {@link AbstractAppendMemory} directly with standard kernel layout.
 */
public final class TextAppendMemory extends AbstractAppendMemory<TextBlobLayout> {

    private static final Logger log = LoggerFactory.getLogger(TextAppendMemory.class);

    private static final int LEGACY_HEADER_BYTES = 16;

    private static final ValueLayout.OfInt BE_INT =
            ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);

    private final Path file;
    private final DataEncryptor encryptor;
    private int entryCount;

    private final Map<String, TextPosition> textPositionMap = new LinkedHashMap<>();
    private final Map<Long, TextPosition> hashToPosition = new java.util.HashMap<>();
    private final AtomicInteger decryptFailCount = new AtomicInteger();

    /**
     * Creates a TextAppendMemory for the given file path (no encryption).
     *
     * @param file path to the text.dat file (may or may not exist yet)
     */
    public TextAppendMemory(Path file) {
        this(file, DataEncryptor.NOOP);
    }

    /**
     * Creates a TextAppendMemory with encryption support.
     *
     * @param file      path to the text.dat file
     * @param encryptor data encryptor for text-at-rest (null → NOOP)
     */
    public TextAppendMemory(Path file, DataEncryptor encryptor) {
        this(file, encryptor, migrateLegacyIfNeeded(file, encryptor != null ? encryptor : DataEncryptor.NOOP));
    }

    private TextAppendMemory(Path file, DataEncryptor encryptor, Map<String, TextEntry> legacyEntries) {
        super(MemoryId.of("cortex", "text"), new TextBlobLayout(), 0, calculateInitialSize(file, legacyEntries), file);
        this.file = file;
        this.encryptor = encryptor != null ? encryptor : DataEncryptor.NOOP;
        this.entryCount = 0;

        if (legacyEntries != null && !legacyEntries.isEmpty()) {
            for (TextEntry entry : legacyEntries.values()) {
                write(entry.id(), entry.tier(), entry.text());
            }
            flush();
        }
    }

    /**
     * Creates a bundle-backed TextAppendMemory from a pre-sliced region segment.
     *
     * <p>The region slice contains a 64-byte SMKM header followed by append-log data.
     * The arena is shared across all bundle regions and is <b>not</b> owned by this store.</p>
     *
     * @param arena        the shared arena from the owning bundle
     * @param regionSlice  the memory segment sliced from the bundle's master segment
     * @param bundlePath   the path to the bundle file (for diagnostics)
     * @param isNew        true if the region was just created
     * @param encryptor    the data encryptor (null treated as NOOP)
     * @return a new bundle-backed TextAppendMemory
     */
    public static TextAppendMemory fromBundle(Arena arena, MemorySegment regionSlice,
                                               Path bundlePath, boolean isNew,
                                               DataEncryptor encryptor) {
        return new TextAppendMemory(arena, regionSlice, bundlePath, isNew, encryptor);
    }

    private TextAppendMemory(Arena arena, MemorySegment regionSlice, Path bundlePath,
                              boolean isNew, DataEncryptor encryptor) {
        super(MemoryId.of("cortex", "text"), new TextBlobLayout(), 0,
              arena, regionSlice,
              isNew ? 0 : (int) MemoryHeader.readCount(regionSlice, 0),
              true, bundlePath, null, true);  // bundleManaged=true
        this.file = bundlePath;
        this.encryptor = encryptor != null ? encryptor : DataEncryptor.NOOP;
        this.entryCount = 0;
        if (isNew) {
            long now = System.currentTimeMillis();
            MemoryHeader.write(segment(), 0, 1, MemoryShape.APPEND, 1, 0, 0,
                    layout.recordStride(), layout.layoutId(), now, now);
            log.info("TextAppendMemory initialized new bundle region in: {} ({}KB)",
                    bundlePath, regionSlice.byteSize() / 1024);
        } else {
            log.info("TextAppendMemory loaded from bundle region in: {} (cursor={}B)",
                    bundlePath, count);
        }
    }

    private static long calculateInitialSize(Path file, Map<String, TextEntry> legacyEntries) {
        long size = Long.getLong("spector.memory.text-segment-size", 32 * 1024 * 1024L); // 32MB default
        if (legacyEntries != null && !legacyEntries.isEmpty()) {
            long totalBytes = 0;
            for (TextEntry entry : legacyEntries.values()) {
                byte[] idBytes = entry.id().getBytes(StandardCharsets.UTF_8);
                byte[] textBytes = entry.text().getBytes(StandardCharsets.UTF_8);
                totalBytes += 4 + (1 + 4 + idBytes.length + 4 + textBytes.length);
            }
            return Math.max(size, totalBytes);
        }
        if (Files.exists(file)) {
            try {
                size = Math.max(size, Files.size(file) - MemoryHeader.HEADER_BYTES);
            } catch (IOException e) {
                // ignore
            }
        }
        return size;
    }

    private static Map<String, TextEntry> migrateLegacyIfNeeded(Path file, DataEncryptor encryptor) {
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

        if (magic == StorageLayout.TEXT_DAT_MAGIC) {
            log.info("Migrating legacy text.dat format to standard Memory Kernel format: {}", file);
            Map<String, TextEntry> entries = readLegacyEntries(file, encryptor);
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to delete legacy file for migration: " + file, e);
            }
            return entries;
        }
        return null;
    }

    private static Map<String, TextEntry> readLegacyEntries(Path file, DataEncryptor encryptor) {
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

                    String text = decryptIfNeeded(rawText, encryptor);
                    MemoryType tier = MemoryType.values()[tierOrd];
                    entries.put(id, new TextEntry(id, tier, text));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read legacy text.dat: " + file, e);
        }
        return entries;
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
        if (existing != null && existing.textLength() == textBytes.length) {
            textPositionMap.put(id, existing);
            entryCount++;
            return existing;
        }

        int entrySize = 1 + 4 + idBytes.length + 4 + textBytes.length;

        MemorySegment entrySeg = Arena.ofAuto().allocate(entrySize);
        entrySeg.set(ValueLayout.JAVA_BYTE, 0, (byte) tier.ordinal());
        entrySeg.set(ValueLayout.JAVA_INT_UNALIGNED, 1, idBytes.length);
        MemorySegment.copy(MemorySegment.ofArray(idBytes), 0, entrySeg, 5, idBytes.length);
        entrySeg.set(ValueLayout.JAVA_INT_UNALIGNED, 5 + idBytes.length, textBytes.length);
        MemorySegment.copy(MemorySegment.ofArray(textBytes), 0, entrySeg, 5 + idBytes.length + 4, textBytes.length);

        long payloadOffset = append(entrySeg);
        long textOffset = dataOffset() + payloadOffset + 9 + idBytes.length;

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
        if (textOffset < 0 || textLength < 0) return null;
        MemorySegment seg = segment();
        if (seg == null || textOffset + textLength > seg.byteSize()) return null;
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

            long textOffset = dataOffset() + currentOffset + 4 + 9 + idLen;
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
    public Map<String, TextPosition> textPositions() {
        return java.util.Collections.unmodifiableMap(textPositionMap);
    }

    /**
     * Rebuilds the file from the given entries (compaction in-place).
     *
     * @param entries the surviving entries to write
     */
    public synchronized void rebuild(Map<String, TextEntry> entries) {
        textPositionMap.clear();
        hashToPosition.clear();

        // Reset append cursor and count in-place
        this.count = 0;
        persistCount();

        for (TextEntry entry : entries.values()) {
            write(entry.id(), entry.tier(), entry.text());
        }
        flush();
        this.entryCount = entries.size();
        log.debug("Rebuilt text.dat with {} entries (deduplicated): {}", entries.size(), file);
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
        return decryptIfNeeded(raw, encryptor, decryptFailCount);
    }

    private static String decryptIfNeeded(String raw, DataEncryptor encryptor) {
        return decryptIfNeeded(raw, encryptor, null);
    }

    private static String decryptIfNeeded(String raw, DataEncryptor encryptor, AtomicInteger failCounter) {
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
            if (failCounter != null) {
                failCounter.incrementAndGet();
            }
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

        TextPosition pos = textPositionMap.get(targetId);
        if (pos == null) return false;

        MemorySegment seg = segment();
        if (seg != null) {
            seg.asSlice(pos.textOffset(), pos.textLength()).fill((byte) 0);
            flush();
        }

        hashToPosition.entrySet().removeIf(entry -> entry.getValue().equals(pos));
        textPositionMap.remove(targetId);

        log.debug("Securely erased {} bytes of text for memory '{}'", pos.textLength(), targetId);
        return true;
    }
}
