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
import com.spectrayan.spector.memory.cortex.TextAppendMemory;
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

import com.spectrayan.spector.memory.kernel.shape.AbstractRecordMemory;
import com.spectrayan.spector.memory.kernel.layout.IndexEntryLayout;

/**
 * Centralized ID → metadata index for cognitive memories.
 * Implements SMK Phase 7 (Issue #386) extending AbstractRecordMemory.
 */
public class IndexRecordMemory extends AbstractRecordMemory<IndexEntryLayout> {

    private static final Logger log = LoggerFactory.getLogger(IndexRecordMemory.class);

    /** Legacy file magic: "MIDX" in ASCII. */
    private static final int LEGACY_INDEX_MAGIC = 0x4D494458;

    /** Standard SMKM schema versions. v6 adds the persisted colocatedPartition (#443). */
    private static final int INDEX_VERSION_V6 = 6;
    private static final int INDEX_VERSION_V5 = 5;

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

    // ── Off-heap text data store (active partition; back-compat / fallback) ──
    private volatile TextAppendMemory textDataStore;

    // ── Registry-backed per-partition text resolver (issue #443, D3b) ──
    // Resolves a colocated-partition seq → that partition's TextAppendMemory.
    private volatile java.util.function.IntFunction<TextAppendMemory> textResolver;

    // ── Active partition sequence (issue #443) — used by the legacy, partition-
    //    unaware findIdByOffset(type, offset) overload to resolve the reverse key. ──
    private volatile int activePartitionSeq = 0;

    // ── Inverted tag index ──
    private final ConcurrentHashMap<String, java.util.Set<String>> tagToIds = new ConcurrentHashMap<>();

    // ── Insertion-order tracking ──
    private final java.util.LinkedHashSet<String> orderedIds = new java.util.LinkedHashSet<>();

    /**
     * Tracks where a memory is physically stored.
     *
     * <p><b>issue #443 (Phase 2):</b> {@code colocatedPartition} identifies the DISK
     * partition the record's tier store + {@code text.dat} live in. As of Phase 2 it is
     * <b>persisted</b> to the {@code .midx} v6 slot at {@code [40:4]}, so recall fan-out
     * and direct-resolve work across partitions after restart.</p>
     *
     * <p>{@code graphSlot} is the semantic HNSW / Hebbian graph node slot (persisted at
     * {@code [24:4]}). It was misnamed {@code partitionIndex} through v5 — the rename in
     * Phase 2 ends the misnomer; its behaviour is unchanged (it never identified the
     * colocated partition).</p>
     */
    public record MemoryLocation(MemoryType type, long offset, int graphSlot,
                                  int colocatedPartition, long textOffset, int textLength) {

        /** Convenience ctor — colocatedPartition defaults to 0, no text position. */
        public MemoryLocation(MemoryType type, long offset, int graphSlot) {
            this(type, offset, graphSlot, 0, -1L, -1);
        }

        /** Convenience ctor — colocatedPartition defaults to 0. */
        public MemoryLocation(MemoryType type, long offset, int graphSlot,
                              long textOffset, int textLength) {
            this(type, offset, graphSlot, 0, textOffset, textLength);
        }

        public boolean hasTextPosition() {
            return textOffset >= 0 && textLength >= 0;
        }
    }

    public IndexRecordMemory() {
        super(MemoryId.of("kernel", "index"), new IndexEntryLayout(), 100_000,
              Arena.ofShared(), Arena.ofShared().allocate(100_000 * (long) INDEX_SLOT_STRIDE, 64),
              0, false, null, null);
    }

    /** Slot stride of the current on-disk index layout (v6 = 48 bytes). */
    private static final int INDEX_SLOT_STRIDE = new IndexEntryLayout().recordStride();

    /**
     * Whether the loaded {@code .midx} persisted a per-record {@code colocatedPartition}
     * (v6+). When {@code false} (v5/legacy load or a fresh index), the colocated partition
     * of every record defaults to 0 and callers must not assume a per-record partition map
     * survived the round-trip.
     */
    private volatile boolean colocatedPartitionPersisted = false;

    /** Returns {@code true} if load read a v6 {@code .midx} carrying colocatedPartition. */
    public boolean isColocatedPartitionPersisted() {
        return colocatedPartitionPersisted;
    }

    /** Marks that a per-record colocatedPartition was loaded from a v6 {@code .midx}. */
    void markColocatedPartitionPersisted() {
        this.colocatedPartitionPersisted = true;
    }

    @Override
    public long recordOffset(long index) {
        return MemoryHeader.HEADER_BYTES + index * layout.recordStride();
    }

    // Reverse-index key (issue #443): partition-aware IN MEMORY only.
    //   bits [52:64) partition (up to 4096), [48:52) type ordinal, [0:48) offset.
    // WORKING memory is a single GLOBAL store that never rolls, so its (type, offset)
    // pairs are globally unique — its key is partition-independent (fixed 0). This keeps
    // working-record reverse lookups consistent regardless of which partition seq was
    // active at ingest time.
    private static long reverseKey(int partition, MemoryType type, long offset) {
        int p = (type == MemoryType.WORKING) ? 0 : partition;
        return (((long) p & 0xFFFL) << 52)
                | (((long) type.ordinal() & 0xFL) << 48)
                | (offset & 0x0000_FFFF_FFFF_FFFFL);
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

        reverseIndex.put(reverseKey(location.colocatedPartition(), location.type(), location.offset()), id);

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
            reverseIndex.remove(reverseKey(loc.colocatedPartition(), loc.type(), loc.offset()));
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
        if (loc != null && loc.hasTextPosition()) {
            // issue #443 (D3b): resolve the text store by the memory's colocated
            // partition; fall back to the active store, then the on-heap map.
            TextAppendMemory store = resolveTextStore(loc.colocatedPartition());
            if (store != null) {
                String offHeapText = store.readTextDirect(loc.textOffset(), loc.textLength());
                if (offHeapText != null) return offHeapText;
            }
            if (textDataStore != null && store != textDataStore) {
                String offHeapText = textDataStore.readTextDirect(loc.textOffset(), loc.textLength());
                if (offHeapText != null) return offHeapText;
            }
        }
        return texts.getOrDefault(id, "");
    }

    private TextAppendMemory resolveTextStore(int partition) {
        var resolver = this.textResolver;
        if (resolver != null) {
            TextAppendMemory store = resolver.apply(partition);
            if (store != null) return store;
        }
        return textDataStore;
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

    /** Partition-aware reverse lookup (issue #443). */
    public String findIdByOffset(int partition, MemoryType type, long offset) {
        return reverseIndex.get(reverseKey(partition, type, offset));
    }

    /**
     * Legacy, partition-unaware reverse lookup. Resolves against the active partition
     * seq — correct for active-partition operations (graph expansion, HNSW rebuild).
     */
    public String findIdByOffset(MemoryType type, long offset) {
        return reverseIndex.get(reverseKey(activePartitionSeq, type, offset));
    }

    public String findTextByOffset(MemoryType type, long offset) {
        String id = findIdByOffset(type, offset);
        return id != null ? text(id) : null;
    }

    public void setTextDataStore(TextAppendMemory store) {
        this.textDataStore = store;
    }

    public TextAppendMemory textDataStore() {
        return this.textDataStore;
    }

    /** Injects the registry-backed per-partition text resolver (issue #443, D3b). */
    public void setTextResolver(java.util.function.IntFunction<TextAppendMemory> resolver) {
        this.textResolver = resolver;
    }

    /** Sets the active partition sequence used by the legacy reverse-lookup overload. */
    public void setActivePartitionSeq(int seq) {
        this.activePartitionSeq = seq;
    }

    /** Returns the active partition sequence. */
    public int activePartitionSeq() {
        return this.activePartitionSeq;
    }

    /**
     * Re-stamps every location's colocated partition to {@code seq} and rebuilds the reverse
     * index (issue #443). In-memory only. As of Phase 2 the {@code .midx} v6 format persists
     * {@code colocatedPartition} per record, so the factory no longer force-stamps loaded
     * records — this helper is retained for callers that deliberately collapse a store into a
     * single partition (e.g. compaction) and is a no-op for records already at {@code seq}.
     */
    public void stampColocatedPartition(int seq) {
        for (Map.Entry<String, MemoryLocation> e : locations.entrySet()) {
            MemoryLocation old = e.getValue();
            if (old.colocatedPartition() == seq) continue;
            MemoryLocation updated = new MemoryLocation(old.type(), old.offset(),
                    old.graphSlot(), seq, old.textOffset(), old.textLength());
            e.setValue(updated);
        }
        reverseIndex.clear();
        for (Map.Entry<String, MemoryLocation> e : locations.entrySet()) {
            MemoryLocation loc = e.getValue();
            reverseIndex.put(reverseKey(loc.colocatedPartition(), loc.type(), loc.offset()), e.getKey());
        }
        this.activePartitionSeq = seq;
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

    // NOTE (issue #443): this remains keyed on graphSlot (the former misnamed field), not
    // the colocated partition. Re-pointing it at colocatedPartition is a consolidation
    // follow-up (see ADR-0002 D5) and is intentionally out of scope for #443.
    public Map<String, String> textsByPartition(int graphSlot) {
        Map<String, String> result = new java.util.HashMap<>();
        for (Map.Entry<String, MemoryLocation> entry : locations.entrySet()) {
            if (entry.getValue().graphSlot() == graphSlot) {
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

        reverseIndex.remove(reverseKey(oldLoc.colocatedPartition(), oldLoc.type(), oldLoc.offset()));

        MemoryLocation newLoc = new MemoryLocation(oldLoc.type(), newOffset, oldLoc.graphSlot(),
                oldLoc.colocatedPartition(), oldLoc.textOffset(), oldLoc.textLength());
        locations.put(id, newLoc);

        reverseIndex.put(reverseKey(newLoc.colocatedPartition(), newLoc.type(), newOffset), id);
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
                // #443 Phase 2: stride flows from the layout (v6 = 48). The 64-byte header
                // records recordStride + schemaVersion, so the loader can version-gate.
                final int stride = slotLayout.recordStride();
                long totalSlotBytes = (long) entryCount * stride;
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
                        
                        // Temporary slot segment sized to the current layout stride (v6 = 48B).
                        byte[] slotBytes = new byte[stride];
                        ByteBuffer slotBuf = ByteBuffer.wrap(slotBytes);
                        slotBuf.order(java.nio.ByteOrder.nativeOrder());
                        
                        slotBuf.putLong(poolOffset);            // [0:8]  idPoolOffset
                        slotBuf.putInt(poolLen);                // [8:4]  idPoolLength
                        slotBuf.putInt(loc.type().ordinal());   // [12:4] typeOrdinal
                        slotBuf.putLong(loc.offset());          // [16:8] offset
                        slotBuf.putInt(loc.graphSlot());        // [24:4] graphSlot
                        slotBuf.putLong(loc.textOffset());      // [28:8] textOffset
                        slotBuf.putInt(loc.textLength());       // [36:4] textLength
                        slotBuf.putInt(loc.colocatedPartition());// [40:4] colocatedPartition (v6)
                        slotBuf.putInt(0);                      // [44:4] reserved (v6)
                        
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
            if (fileSize < 4) {
                // Too small to even classify (no magic). Treat an empty placeholder as fresh.
                log.warn("MemoryIndex file too small to classify ({}B), starting fresh", fileSize);
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
                // Standard SMKM format (v5+) carries the full 64-byte MemoryHeader. A file
                // that claims the SMKM magic but is shorter than the header is corrupt/truncated
                // — throw rather than silently returning an empty index (#443 Phase 2 discipline).
                if (fileSize < MemoryHeader.HEADER_BYTES) {
                    throw new SpectorStorageException(ErrorCode.FILE_FORMAT_INVALID,
                            "MemoryIndex truncated: " + filePath + " (" + fileSize + "B < "
                                    + MemoryHeader.HEADER_BYTES + "B header)");
                }

                String fileName = filePath.getFileName().toString();
                String poolName = fileName.endsWith(".midx") ? fileName.replace(".midx", ".idpl") : fileName + ".idpl";
                Path idPoolPath = filePath.resolveSibling(poolName);

                MemoryId slotId = MemoryId.of("index", "slot");
                IndexEntryLayout slotLayout = new IndexEntryLayout();
                try (DefaultRecordMemory<IndexEntryLayout> slotMemory = new DefaultRecordMemory<>(
                        slotId, slotLayout, 0, 0, filePath)) {

                    // #443 Phase 2: version-gate on the persisted schemaVersion (throw-on-unreadable).
                    int schemaVersion = MemoryHeader.readSchemaVersion(slotMemory.segment(), 0);
                    final int slotStride;
                    final boolean readColocated;
                    switch (schemaVersion) {
                        case INDEX_VERSION_V6 -> {
                            slotStride = 48;      // v6 slot: 48 bytes incl. colocatedPartition + reserved
                            readColocated = true;
                        }
                        case INDEX_VERSION_V5 -> {
                            slotStride = 40;      // v5 slot: 40 bytes, no colocatedPartition
                            readColocated = false;
                            log.warn("v5 .midx: multi-partition data not recoverable, treating as partition 0 ({})",
                                    filePath.getFileName());
                        }
                        default -> throw new SpectorStorageException(ErrorCode.FILE_FORMAT_INVALID,
                                "MemoryIndex unsupported schema version v" + schemaVersion
                                        + (schemaVersion > INDEX_VERSION_V6 ? " (newer than supported v6)" : "")
                                        + ": " + filePath);
                    }

                    int entryCount = slotMemory.size();
                    MemoryId poolId = MemoryId.of("index", "idpool");
                    IdBlobLayout poolLayout = new IdBlobLayout();
                    try (DefaultAppendMemory<IdBlobLayout> poolMemory = new DefaultAppendMemory<>(
                            poolId, poolLayout, 0, 0, idPoolPath)) {
                        
                        MemorySegment slotSeg = slotMemory.segment();
                        long slotBase = slotMemory.dataOffset();
                        
                        for (int i = 0; i < entryCount; i++) {
                            long slotOffset = slotBase + (long) i * slotStride;
                            long poolOffset = slotSeg.get(ValueLayout.JAVA_LONG_UNALIGNED, slotOffset);
                            int poolLen = slotSeg.get(ValueLayout.JAVA_INT_UNALIGNED, slotOffset + 8);
                            int typeOrd = slotSeg.get(ValueLayout.JAVA_INT_UNALIGNED, slotOffset + 12);
                            long offset = slotSeg.get(ValueLayout.JAVA_LONG_UNALIGNED, slotOffset + 16);
                            int graphSlot = slotSeg.get(ValueLayout.JAVA_INT_UNALIGNED, slotOffset + 24);
                            long textOffset = slotSeg.get(ValueLayout.JAVA_LONG_UNALIGNED, slotOffset + 28);
                            int textLength = slotSeg.get(ValueLayout.JAVA_INT_UNALIGNED, slotOffset + 36);
                            // v6: colocatedPartition at [40:4]; v5: absent → default 0.
                            int colocatedPartition = readColocated
                                    ? slotSeg.get(ValueLayout.JAVA_INT_UNALIGNED, slotOffset + 40) : 0;
                            
                            MemoryType type = MemoryType.values()[typeOrd];
                            MemoryLocation loc = new MemoryLocation(type, offset, graphSlot,
                                    colocatedPartition, textOffset, textLength);
                            
                            MemorySegment blobSeg = poolMemory.read(poolOffset, poolLen);
                            DeserializedEntry target = new DeserializedEntry();
                            deserializeIdBlob(blobSeg, target);
                            
                            index.register(target.id, loc, target.textFallback, target.source, target.tags, target.metadata);
                        }
                    }

                    // Only a v6 file carries a persisted per-record colocated partition.
                    if (readColocated) {
                        index.markColocatedPartitionPersisted();
                    }
                    log.info("MemoryIndex loaded (SMKM v{}): {} entries from {}",
                            schemaVersion, index.size(), filePath.getFileName());
                }
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
        } catch (SpectorStorageException sse) {
            // Format errors (unknown/newer schema, truncation) must NOT be silently swallowed
            // into an empty index — that would erase durable data (#443 Phase 2; #432/#433).
            throw sse;
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
        int graphSlot = locBuf.getInt();
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

        // Legacy (v1–v4) has no colocated partition → default 0 (partition 0).
        MemoryLocation loc = new MemoryLocation(type, offset, graphSlot, textOffset, textLength);
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
