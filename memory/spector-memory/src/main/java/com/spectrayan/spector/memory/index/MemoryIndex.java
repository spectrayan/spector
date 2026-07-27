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
package com.spectrayan.spector.memory.index;

import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.cortex.TextDataStore;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorStorageException;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.MemoryHeader;
import com.spectrayan.spector.memory.kernel.shape.DefaultRecordMemory;
import com.spectrayan.spector.memory.kernel.shape.DefaultAppendMemory;
import com.spectrayan.spector.memory.kernel.layout.IndexEntryLayout;
import com.spectrayan.spector.memory.kernel.layout.IdBlobLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized ID → metadata index for cognitive memories.
 *
 * <h3>Responsibility</h3>
 * <p>Owns the concurrent maps that track memory locations, raw text,
 * provenance sources, and synaptic tag strings. Provides O(1) lookup by ID
 * and O(1) reverse-lookup by offset (via dedicated reverse index).</p>
 *
 * <h3>Persistence</h3>
 * <p>Supports binary serialization via {@link #save(Path)} and {@link #load(Path)}.
 * Backed off-heap via standardized kernel shapes (RecordMemory for the slot table
 * and AppendMemory for the variable-length ID/metadata pool).</p>
 *
 * <h3>Performance: O(1) Reverse Index</h3>
 * <p>A dedicated {@code reverseIndex} maps {@code (type, offset) → id} for
 * constant-time reverse lookups during recall result assembly. The key is
 * computed as {@code (type.ordinal() << 48) | offset}, packing both into
 * a single {@code long} to avoid String concatenation.</p>
 */
public final class MemoryIndex {

    private static final Logger log = LoggerFactory.getLogger(MemoryIndex.class);

    /** Legacy file magic: "MIDX" in ASCII. */
    private static final int LEGACY_INDEX_MAGIC = 0x4D494458;

    /** Legacy V1-V4 formats. */
    private static final int INDEX_VERSION_V4 = 4;
    private static final int INDEX_VERSION_V3 = 3;
    private static final int INDEX_VERSION_V2 = 2;
    private static final int INDEX_VERSION_V1 = 1;

    /** File header for legacy files: 16 bytes. */
    private static final int LEGACY_FILE_HEADER_BYTES = 16;

    // ── Forward index: id → metadata ──
    private final ConcurrentHashMap<String, MemoryLocation> locations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> texts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MemorySource> sources = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String[]> tags = new ConcurrentHashMap<>();

    // ── Multimodal metadata: id → metadata map ──
    private final ConcurrentHashMap<String, Map<String, String>> metadataMap = new ConcurrentHashMap<>();

    // ── Reverse index: (type, offset) → id ──
    private final ConcurrentHashMap<Long, String> reverseIndex = new ConcurrentHashMap<>();

    // ── Off-heap text data store ──
    private volatile TextDataStore textDataStore;

    // ── Inverted tag index ──
    private final ConcurrentHashMap<String, java.util.Set<String>> tagToIds = new ConcurrentHashMap<>();

    // ── Insertion-order tracking ──
    private final java.util.LinkedHashSet<String> orderedIds = new java.util.LinkedHashSet<>();

    /**
     * Tracks where a memory is physically stored.
     */
    public record MemoryLocation(MemoryType type, long offset, int partitionIndex,
                                  long textOffset, int textLength) {

        public MemoryLocation(MemoryType type, long offset, int partitionIndex) {
            this(type, offset, partitionIndex, -1L, -1);
        }

        public boolean hasTextPosition() {
            return textOffset >= 0 && textLength >= 0;
        }
    }

    private static long reverseKey(MemoryType type, long offset) {
        return ((long) type.ordinal() << 48) | (offset & 0x0000_FFFF_FFFF_FFFFL);
    }

    public void register(String id, MemoryLocation location, String text,
                          MemorySource source, String[] tagArray) {
        register(id, location, text, source, tagArray, null);
    }

    public void register(String id, MemoryLocation location, String text,
                          MemorySource source, String[] tagArray,
                          Map<String, String> metadata) {
        locations.put(id, location);
        if (!location.hasTextPosition() && text != null) {
            texts.put(id, text);
        }
        sources.put(id, source);
        tags.put(id, tagArray != null ? tagArray : EMPTY_TAGS);

        if (metadata != null && !metadata.isEmpty()) {
            metadataMap.put(id, Map.copyOf(metadata));
        }

        reverseIndex.put(reverseKey(location.type(), location.offset()), id);

        synchronized (orderedIds) {
            orderedIds.add(id);
        }

        if (tagArray != null) {
            for (String tag : tagArray) {
                String normalizedTag = tag.toLowerCase();
                tagToIds.computeIfAbsent(normalizedTag, _ -> java.util.Collections.newSetFromMap(new ConcurrentHashMap<>()))
                        .add(id);
            }
        }
    }

    public void remove(String id) {
        MemoryLocation loc = locations.remove(id);
        texts.remove(id);
        synchronized (orderedIds) {
            orderedIds.remove(id);
        }
        sources.remove(id);
        String[] removedTags = tags.remove(id);
        metadataMap.remove(id);

        if (loc != null) {
            reverseIndex.remove(reverseKey(loc.type(), loc.offset()));
        }

        if (removedTags != null) {
            for (String tag : removedTags) {
                String normalizedTag = tag.toLowerCase();
                var idSet = tagToIds.get(normalizedTag);
                if (idSet != null) {
                    idSet.remove(id);
                    if (idSet.isEmpty()) {
                        tagToIds.remove(normalizedTag, idSet);
                    }
                }
            }
        }
    }

    public MemoryLocation locate(String id) {
        return locations.get(id);
    }

    public String text(String id) {
        MemoryLocation loc = locations.get(id);
        if (loc != null && loc.hasTextPosition() && textDataStore != null) {
            String offHeapText = textDataStore.readTextDirect(loc.textOffset(), loc.textLength());
            if (offHeapText != null) return offHeapText;
        }
        return texts.getOrDefault(id, "");
    }

    public MemorySource source(String id) {
        return sources.getOrDefault(id, MemorySource.OBSERVED);
    }

    private static final String[] EMPTY_TAGS = new String[0];

    public String[] tags(String id) {
        return tags.getOrDefault(id, EMPTY_TAGS);
    }

    public java.util.Set<String> idsByTag(String tag) {
        var ids = tagToIds.get(tag.toLowerCase());
        return ids != null ? java.util.Collections.unmodifiableSet(ids) : java.util.Set.of();
    }

    public java.util.Set<String> idsByAllTags(String... queryTags) {
        if (queryTags == null || queryTags.length == 0) return java.util.Set.of();

        java.util.Set<String> smallest = null;
        for (String tag : queryTags) {
            var ids = tagToIds.get(tag.toLowerCase());
            if (ids == null || ids.isEmpty()) return java.util.Set.of();
            if (smallest == null || ids.size() < smallest.size()) {
                smallest = ids;
            }
        }

        var result = new java.util.HashSet<>(smallest);
        for (String tag : queryTags) {
            var ids = tagToIds.get(tag.toLowerCase());
            if (ids != smallest) {
                result.retainAll(ids);
                if (result.isEmpty()) return java.util.Set.of();
            }
        }
        return java.util.Collections.unmodifiableSet(result);
    }

    private static final Map<String, String> EMPTY_METADATA = Map.of();

    public Map<String, String> metadata(String id) {
        return metadataMap.getOrDefault(id, EMPTY_METADATA);
    }

    public void putMetadata(String id, Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty() || !locations.containsKey(id)) return;
        metadataMap.merge(id, Map.copyOf(metadata), (existing, incoming) -> {
            var merged = new java.util.HashMap<>(existing);
            merged.putAll(incoming);
            return Map.copyOf(merged);
        });
    }

    public String findIdByOffset(MemoryType type, long offset) {
        return reverseIndex.get(reverseKey(type, offset));
    }

    public String findTextByOffset(MemoryType type, long offset) {
        String id = findIdByOffset(type, offset);
        return id != null ? text(id) : null;
    }

    public void setTextDataStore(TextDataStore store) {
        this.textDataStore = store;
    }

    public TextDataStore textDataStore() {
        return this.textDataStore;
    }

    public int size() {
        return locations.size();
    }

    public java.util.Set<String> allIds() {
        return java.util.Collections.unmodifiableSet(new java.util.HashSet<>(locations.keySet()));
    }

    public java.util.List<String> orderedIds() {
        synchronized (orderedIds) {
            return new java.util.ArrayList<>(orderedIds);
        }
    }

    public ConcurrentHashMap<String, MemoryLocation> locationMap() {
        return locations;
    }

    public void buildGraphSlotMappings(java.util.Map<Integer, String> slotToId,
                                        java.util.Map<String, Integer> idToSlot) {
        synchronized (orderedIds) {
            int i = 0;
            for (String id : orderedIds) {
                slotToId.put(i, id);
                idToSlot.put(id, i);
                i++;
            }
        }
    }

    public Map<String, String> textsByPartition(int partitionIndex) {
        Map<String, String> result = new java.util.HashMap<>();
        for (Map.Entry<String, MemoryLocation> entry : locations.entrySet()) {
            if (entry.getValue().partitionIndex() == partitionIndex) {
                String text = text(entry.getKey());
                if (text != null && !text.isEmpty()) {
                    result.put(entry.getKey(), text);
                }
            }
        }
        return result;
    }

    public int totalCount() {
        return locations.size();
    }

    public void relocate(String id, long newOffset) {
        MemoryLocation oldLoc = locations.get(id);
        if (oldLoc == null) return;

        reverseIndex.remove(reverseKey(oldLoc.type(), oldLoc.offset()));

        MemoryLocation newLoc = new MemoryLocation(oldLoc.type(), newOffset, oldLoc.partitionIndex(),
                oldLoc.textOffset(), oldLoc.textLength());
        locations.put(id, newLoc);

        reverseIndex.put(reverseKey(newLoc.type(), newOffset), id);
    }

    public void relocateBatch(Map<String, Long> relocations) {
        for (Map.Entry<String, Long> entry : relocations.entrySet()) {
            relocate(entry.getKey(), entry.getValue());
        }
    }

    // ── Persistence: save / load using DefaultRecordMemory & DefaultAppendMemory ──

    public void save(Path filePath) {
        Path parent = filePath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new SpectorStorageException(ErrorCode.PARTITION_DIR_FAILED, e, parent);
            }
        }

        String fileName = filePath.getFileName().toString();
        String poolName = fileName.endsWith(".midx") ? fileName.replace(".midx", ".idpl") : fileName + ".idpl";
        Path idPoolPath = filePath.resolveSibling(poolName);

        try {
            Files.deleteIfExists(filePath);
            Files.deleteIfExists(idPoolPath);

            int entryCount = locations.size();
            java.util.List<String> orderedKeys = orderedIds();
            java.util.List<byte[]> serializedBlobs = new java.util.ArrayList<>(entryCount);
            
            for (String id : orderedKeys) {
                MemoryLocation loc = locations.get(id);
                if (loc == null) continue;
                String textVal = text(id);
                MemorySource src = sources.getOrDefault(id, MemorySource.OBSERVED);
                String[] tagArray = tags.getOrDefault(id, EMPTY_TAGS);
                Map<String, String> meta = metadataMap.getOrDefault(id, Map.of());
                
                String textFallback = loc.hasTextPosition() ? null : textVal;
                byte[] blob = serializeIdBlob(id, src, tagArray, meta, textFallback);
                serializedBlobs.add(blob);
            }

            long totalPoolBytes = 0;
            for (byte[] b : serializedBlobs) {
                totalPoolBytes += 4 + b.length; // 4B length prefix + payload
            }

            MemoryId poolId = MemoryId.of("index", "idpool");
            IdBlobLayout poolLayout = new IdBlobLayout();
            try (DefaultAppendMemory<IdBlobLayout> poolMemory = new DefaultAppendMemory<>(
                    poolId, poolLayout, entryCount, MemoryHeader.HEADER_BYTES + totalPoolBytes, idPoolPath)) {
                
                MemoryId slotId = MemoryId.of("index", "slot");
                IndexEntryLayout slotLayout = new IndexEntryLayout();
                long totalSlotBytes = (long) entryCount * 40;
                try (DefaultRecordMemory<IndexEntryLayout> slotMemory = new DefaultRecordMemory<>(
                        slotId, slotLayout, entryCount, MemoryHeader.HEADER_BYTES + totalSlotBytes, filePath)) {
                    
                    int index = 0;
                    for (int i = 0; i < orderedKeys.size(); i++) {
                        String id = orderedKeys.get(i);
                        MemoryLocation loc = locations.get(id);
                        if (loc == null) continue;
                        byte[] blobBytes = serializedBlobs.get(index);
                        
                        long poolOffset = poolMemory.append(MemorySegment.ofArray(blobBytes));
                        int poolLen = blobBytes.length;
                        
                        // Create a temporary 40-byte segment for the slot entry
                        byte[] slotBytes = new byte[40];
                        ByteBuffer slotBuf = ByteBuffer.wrap(slotBytes);
                        slotBuf.order(java.nio.ByteOrder.nativeOrder());
                        
                        slotBuf.putLong(poolOffset);
                        slotBuf.putInt(poolLen);
                        slotBuf.putInt(loc.type().ordinal());
                        slotBuf.putLong(loc.offset());
                        slotBuf.putInt(loc.partitionIndex());
                        slotBuf.putLong(loc.textOffset());
                        slotBuf.putInt(loc.textLength());
                        
                        slotMemory.write(index, MemorySegment.ofArray(slotBytes));
                        
                        index++;
                    }
                    slotMemory.flush();
                }
                poolMemory.flush();
            }
            log.info("MemoryIndex saved: {} entries (slot table={}, id pool={})",
                    entryCount, filePath.getFileName(), idPoolPath.getFileName());
        } catch (Exception e) {
            throw new SpectorStorageException(ErrorCode.DISK_IO_FAILED, e, "save MemoryIndex: " + filePath);
        }
    }

    public static MemoryIndex load(Path filePath) {
        MemoryIndex index = new MemoryIndex();

        if (filePath == null || !Files.exists(filePath)) {
            log.info("MemoryIndex file not found, starting fresh: {}", filePath);
            return index;
        }

        try {
            long fileSize = Files.size(filePath);
            if (fileSize < 16) {
                log.warn("MemoryIndex file too small ({}B), starting fresh", fileSize);
                return index;
            }

            // Inspect magic number to distinguish between new standard SMKM and legacy MIDX formats
            int magic;
            try (FileChannel ch = FileChannel.open(filePath, StandardOpenOption.READ)) {
                ByteBuffer mb = ByteBuffer.allocate(4);
                ch.read(mb);
                mb.flip();
                magic = mb.getInt();
            }

            boolean isStandard = (magic == MemoryHeader.MAGIC || magic == 0x4D4B4D53);
            boolean isLegacy = (magic == LEGACY_INDEX_MAGIC || magic == 0x5844494D);

            if (isStandard) {
                // New standard format (V5+)
                String fileName = filePath.getFileName().toString();
                String poolName = fileName.endsWith(".midx") ? fileName.replace(".midx", ".idpl") : fileName + ".idpl";
                Path idPoolPath = filePath.resolveSibling(poolName);

                MemoryId slotId = MemoryId.of("index", "slot");
                IndexEntryLayout slotLayout = new IndexEntryLayout();
                try (DefaultRecordMemory<IndexEntryLayout> slotMemory = new DefaultRecordMemory<>(
                        slotId, slotLayout, 0, 0, filePath)) {
                    
                    int entryCount = slotMemory.size();
                    MemoryId poolId = MemoryId.of("index", "idpool");
                    IdBlobLayout poolLayout = new IdBlobLayout();
                    try (DefaultAppendMemory<IdBlobLayout> poolMemory = new DefaultAppendMemory<>(
                            poolId, poolLayout, 0, 0, idPoolPath)) {
                        
                        MemorySegment slotSeg = slotMemory.segment();
                        long slotBase = slotMemory.dataOffset();
                        
                        for (int i = 0; i < entryCount; i++) {
                            long slotOffset = slotBase + (long) i * 40;
                            long poolOffset = slotSeg.get(ValueLayout.JAVA_LONG_UNALIGNED, slotOffset);
                            int poolLen = slotSeg.get(ValueLayout.JAVA_INT_UNALIGNED, slotOffset + 8);
                            int typeOrd = slotSeg.get(ValueLayout.JAVA_INT_UNALIGNED, slotOffset + 12);
                            long offset = slotSeg.get(ValueLayout.JAVA_LONG_UNALIGNED, slotOffset + 16);
                            int partitionIndex = slotSeg.get(ValueLayout.JAVA_INT_UNALIGNED, slotOffset + 24);
                            long textOffset = slotSeg.get(ValueLayout.JAVA_LONG_UNALIGNED, slotOffset + 28);
                            int textLength = slotSeg.get(ValueLayout.JAVA_INT_UNALIGNED, slotOffset + 36);
                            
                            MemoryType type = MemoryType.values()[typeOrd];
                            MemoryLocation loc = new MemoryLocation(type, offset, partitionIndex, textOffset, textLength);
                            
                            MemorySegment blobSeg = poolMemory.read(poolOffset, poolLen);
                            DeserializedEntry target = new DeserializedEntry();
                            deserializeIdBlob(blobSeg, target);
                            
                            index.register(target.id, loc, target.textFallback, target.source, target.tags, target.metadata);
                        }
                    }
                }
                log.info("MemoryIndex loaded (SMKM V5): {} entries from {}", index.size(), filePath.getFileName());
            } else if (isLegacy) {
                // Legacy index loading (V1-V4)
                try (FileChannel ch = FileChannel.open(filePath, StandardOpenOption.READ)) {
                    ByteBuffer header = ByteBuffer.allocate(LEGACY_FILE_HEADER_BYTES);
                    ch.read(header);
                    header.flip();
                    header.getInt(); // Skip magic
                    int version = header.getInt();
                    int entryCount = header.getInt();
                    header.getInt(); // Skip reserved

                    boolean hasMetadata = (version >= INDEX_VERSION_V2);
                    boolean hasTextPosition = (version >= INDEX_VERSION_V3);
                    boolean hasInlineText = (version < INDEX_VERSION_V4);

                    for (int i = 0; i < entryCount; i++) {
                        readEntry(ch, index, hasMetadata, hasTextPosition, hasInlineText);
                    }
                }
                log.info("MemoryIndex loaded (legacy V{}): {} entries from {}", magic, index.size(), filePath.getFileName());
            } else {
                log.warn("Invalid MemoryIndex magic: 0x{}, starting fresh", Integer.toHexString(magic));
            }
        } catch (Exception e) {
            log.error("Failed to load MemoryIndex from {}, starting fresh: {}", filePath, e.getMessage());
        }
        return index;
    }

    // ── Internal Serialization/Deserialization Primitives ──

    private static byte[] serializeIdBlob(String id, MemorySource source, String[] tagArray,
                                           Map<String, String> metadata, String textFallback) {
        byte[] idBytes = id.getBytes(StandardCharsets.UTF_8);
        byte[] textBytes = textFallback != null ? textFallback.getBytes(StandardCharsets.UTF_8) : new byte[0];
        
        int size = 2 + idBytes.length
                + 1
                + 2;
        
        byte[][] tagBytesArray = new byte[tagArray.length][];
        for (int i = 0; i < tagArray.length; i++) {
            tagBytesArray[i] = tagArray[i].getBytes(StandardCharsets.UTF_8);
            size += 2 + tagBytesArray[i].length;
        }
        
        size += 2;
        byte[][] metaKeyBytes = new byte[metadata.size()][];
        byte[][] metaValBytes = new byte[metadata.size()][];
        int mi = 0;
        for (Map.Entry<String, String> me : metadata.entrySet()) {
            metaKeyBytes[mi] = me.getKey().getBytes(StandardCharsets.UTF_8);
            metaValBytes[mi] = me.getValue().getBytes(StandardCharsets.UTF_8);
            size += 2 + metaKeyBytes[mi].length + 2 + metaValBytes[mi].length;
            mi++;
        }
        
        size += 4 + textBytes.length;
        
        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.putShort((short) idBytes.length);
        buf.put(idBytes);
        buf.put((byte) source.ordinal());
        buf.putShort((short) tagArray.length);
        for (byte[] tb : tagBytesArray) {
            buf.putShort((short) tb.length);
            buf.put(tb);
        }
        buf.putShort((short) metadata.size());
        for (int j = 0; j < metaKeyBytes.length; j++) {
            buf.putShort((short) metaKeyBytes[j].length);
            buf.put(metaKeyBytes[j]);
            buf.putShort((short) metaValBytes[j].length);
            buf.put(metaValBytes[j]);
        }
        buf.putInt(textBytes.length);
        if (textBytes.length > 0) {
            buf.put(textBytes);
        }
        return buf.array();
    }

    private static void deserializeIdBlob(MemorySegment segment, DeserializedEntry target) {
        ByteBuffer buf = segment.asByteBuffer();
        buf.order(java.nio.ByteOrder.BIG_ENDIAN);
        
        int idLen = Short.toUnsignedInt(buf.getShort());
        byte[] idBytes = new byte[idLen];
        buf.get(idBytes);
        target.id = new String(idBytes, StandardCharsets.UTF_8);
        
        int sourceOrd = Byte.toUnsignedInt(buf.get());
        target.source = MemorySource.values()[sourceOrd];
        
        int tagCount = Short.toUnsignedInt(buf.getShort());
        target.tags = new String[tagCount];
        for (int i = 0; i < tagCount; i++) {
            int tagLen = Short.toUnsignedInt(buf.getShort());
            byte[] tagBytes = new byte[tagLen];
            buf.get(tagBytes);
            target.tags[i] = new String(tagBytes, StandardCharsets.UTF_8);
        }
        
        int metaCount = Short.toUnsignedInt(buf.getShort());
        if (metaCount > 0) {
            target.metadata = new java.util.HashMap<>(metaCount);
            for (int i = 0; i < metaCount; i++) {
                int keyLen = Short.toUnsignedInt(buf.getShort());
                byte[] keyBytes = new byte[keyLen];
                buf.get(keyBytes);
                String key = new String(keyBytes, StandardCharsets.UTF_8);
                
                int valLen = Short.toUnsignedInt(buf.getShort());
                byte[] valBytes = new byte[valLen];
                buf.get(valBytes);
                String val = new String(valBytes, StandardCharsets.UTF_8);
                target.metadata.put(key, val);
            }
        } else {
            target.metadata = Map.of();
        }
        
        int textLen = buf.getInt();
        if (textLen > 0) {
            byte[] textBytes = new byte[textLen];
            buf.get(textBytes);
            target.textFallback = new String(textBytes, StandardCharsets.UTF_8);
        } else {
            target.textFallback = "";
        }
    }

    private static class DeserializedEntry {
        String id;
        MemorySource source;
        String[] tags;
        Map<String, String> metadata;
        String textFallback;
    }

    // ── Legacy Format Read Helpers ──

    private static void readEntry(FileChannel ch, MemoryIndex index,
                                    boolean hasMetadata, boolean hasTextPosition,
                                    boolean hasInlineText) throws IOException {
        String id = readString(ch);

        ByteBuffer locBuf = ByteBuffer.allocate(4 + 8 + 4);
        ch.read(locBuf);
        locBuf.flip();
        int typeOrd = locBuf.getInt();
        long offset = locBuf.getLong();
        int partitionIndex = locBuf.getInt();
        MemoryType type = MemoryType.values()[typeOrd];

        String text = readString(ch);

        ByteBuffer srcBuf = ByteBuffer.allocate(4);
        ch.read(srcBuf);
        srcBuf.flip();
        int sourceOrd = srcBuf.getInt();
        MemorySource source = MemorySource.values()[sourceOrd];

        ByteBuffer tagCountBuf = ByteBuffer.allocate(4);
        ch.read(tagCountBuf);
        tagCountBuf.flip();
        int tagCount = tagCountBuf.getInt();
        String[] tagArray = new String[tagCount];
        for (int t = 0; t < tagCount; t++) {
            tagArray[t] = readString(ch);
        }

        Map<String, String> metadata = null;
        if (hasMetadata) {
            ByteBuffer metaCountBuf = ByteBuffer.allocate(4);
            ch.read(metaCountBuf);
            metaCountBuf.flip();
            int metaCount = metaCountBuf.getInt();
            if (metaCount > 0) {
                metadata = new java.util.HashMap<>(metaCount);
                for (int m = 0; m < metaCount; m++) {
                    String key = readString(ch);
                    String value = readString(ch);
                    metadata.put(key, value);
                }
            }
        }

        long textOffset = -1L;
        int textLength = -1;
        if (hasTextPosition) {
            ByteBuffer tpBuf = ByteBuffer.allocate(8 + 4);
            ch.read(tpBuf);
            tpBuf.flip();
            textOffset = tpBuf.getLong();
            textLength = tpBuf.getInt();
        }

        MemoryLocation loc = new MemoryLocation(type, offset, partitionIndex, textOffset, textLength);
        index.register(id, loc, text, source, tagArray, metadata);
    }

    private static String readString(FileChannel ch) throws IOException {
        ByteBuffer lenBuf = ByteBuffer.allocate(4);
        ch.read(lenBuf);
        lenBuf.flip();
        int len = lenBuf.getInt();

        if (len == 0) return "";

        ByteBuffer strBuf = ByteBuffer.allocate(len);
        ch.read(strBuf);
        strBuf.flip();
        return new String(strBuf.array(), 0, len, StandardCharsets.UTF_8);
    }
}
