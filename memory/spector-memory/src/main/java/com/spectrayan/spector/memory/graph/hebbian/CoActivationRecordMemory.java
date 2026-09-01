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
package com.spectrayan.spector.memory.graph.hebbian;

import com.spectrayan.spector.memory.cortex.adaptor.RunningStats;
import com.spectrayan.spector.memory.error.SpectorGraphPersistenceException;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.kernel.MemoryHeader;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.SystemMemoryId;
import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.memory.kernel.layout.CoActivationLayout;
import com.spectrayan.spector.memory.kernel.layout.CoActivationMetadataLayout;
import com.spectrayan.spector.memory.kernel.shape.AbstractHashTableMemory;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Off-heap synaptic tag co-occurrence, STDP tracking, and Cross-Capture Graph
 * traversal for Hebbian learning.
 *
 * <p>Extends {@link AbstractHashTableMemory} with
 * {@link com.spectrayan.spector.memory.kernel.MemoryShape#HASHTABLE} shape,
 * hosting two compound open-addressing hash tables ({@link OffHeapPairTable}
 * for undirected co-occurrence and {@link OffHeapEdgeTable} for directed STDP)
 * plus a tag → memory inverted index for Cross-Capture Graph traversal.</p>
 *
 * <h3>Cross-Capture Graph (ADR-0009)</h3>
 * <p>Enables tag co-occurrence traversal during recall: given query tags,
 * finds strongly co-occurring tags via the pair table, then discovers
 * memories carrying those related tags via the inverted index.
 * Biologically models STC cross-tagging (Sajikumar &amp; Frey, 2004).</p>
 *
 * @see OffHeapPairTable
 * @see OffHeapEdgeTable
 */
public final class CoActivationRecordMemory extends AbstractHashTableMemory<CoActivationLayout> {

    private static final Logger log = LoggerFactory.getLogger(CoActivationRecordMemory.class);

    // ── STDP Constants ──
    private static final float A_PLUS = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_STDP_A_PLUS;
    private static final float A_MINUS = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_STDP_A_MINUS;
    private static final float TAU_PLUS = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_STDP_TAU_PLUS;
    private static final float TAU_MINUS = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_STDP_TAU_MINUS;
    static final float MIN_WEIGHT = 0.0f;
    static final float MAX_WEIGHT = 1.0f;

    // ── Persistence ──
    private static final int FILE_MAGIC = CoActivationMetadataLayout.FILE_MAGIC;
    private static final int FILE_VERSION = CoActivationMetadataLayout.FILE_VERSION;
    private static final int FILE_HEADER_V1_BYTES = CoActivationMetadataLayout.FILE_HEADER_V1_BYTES;
    private static final int FILE_HEADER_BYTES = CoActivationMetadataLayout.FILE_HEADER_BYTES;

    private static final long FNV1A_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV1A_PRIME = 0x100000001b3L;

    // ── Tables ──
    private OffHeapPairTable pairTable;
    private OffHeapEdgeTable edgeTable;

    private final ConcurrentHashMap<Long, String> hashToTag = new ConcurrentHashMap<>();
    private volatile Map<Long, EnumMap<CognitiveProfile, RunningStats>> banditStats =
            new ConcurrentHashMap<>();
    private final ReentrantLock saveLock = new ReentrantLock();
    private volatile MemorySegment checkpointRegion;   // nullable — V4 bundle CHECKPOINT region for metadata

    // ── Cross-Capture Graph: Tag → Memory Inverted Index (ADR-0009) ──
    private final ConcurrentHashMap<Long, java.util.concurrent.CopyOnWriteArrayList<Integer>> tagToMemoryIndex =
            new ConcurrentHashMap<>();

    public record DirectedEdge(String sourceTag, String targetTag) {
        @Override
        public String toString() {
            return sourceTag + "→" + targetTag;
        }
    }

    public record EdgeWeight(float weight, long lastActivatedMs, int activationCount) {
        public EdgeWeight withUpdate(float deltaWeight, long nowMs) {
            float newWeight = Math.clamp(weight + deltaWeight, MIN_WEIGHT, MAX_WEIGHT);
            return new EdgeWeight(newWeight, nowMs, activationCount + 1);
        }
    }

    /**
     * Sets the V4 bundle CHECKPOINT region for persisting CoActivation metadata.
     *
     * <p>When set, {@link #save(Path)} writes tag index and bandit stats directly
     * to this region slice (at offset 16, after the 16-byte checkpoint header)
     * instead of a standalone {@code .meta} sidecar file.</p>
     *
     * @param checkpointRegion the CHECKPOINT region MemorySegment, or null for V3 fallback
     */
    public void setCheckpointRegion(MemorySegment checkpointRegion) {
        this.checkpointRegion = checkpointRegion;
    }

    // ══════════════════════════════════════════════════════════════
    // Constructors
    // ══════════════════════════════════════════════════════════════

    public CoActivationRecordMemory() {
        this(SpectorPropertyConstants.DEFAULT_MEMORY_COACTIVATION_CAPACITY);
    }

    public CoActivationRecordMemory(int maxPairs) {
        this(maxPairs, maxPairs * 2);
    }

    public CoActivationRecordMemory(int maxPairs, int maxEdges) {
        this(SystemMemoryId.COACTIVATION.id(), calculateTotalBytes(maxPairs, maxEdges), maxPairs, maxEdges);
    }

    private CoActivationRecordMemory(MemoryId id, long totalBytes, int maxPairs, int maxEdges) {
        super(id, new CoActivationLayout(), (int) totalBytes, totalBytes);

        int pairCap = nextPowerOf2(Math.max(SpectorPropertyConstants.DEFAULT_MEMORY_COACTIVATION_MIN_TABLE_CAPACITY, maxPairs * 2));
        int edgeCap = nextPowerOf2(Math.max(SpectorPropertyConstants.DEFAULT_MEMORY_COACTIVATION_MIN_TABLE_CAPACITY, maxEdges * 2));

        MemorySegment segment = segment();
        long dataOffset = dataOffset();

        segment.set(ValueLayout.JAVA_INT, dataOffset, pairCap);
        segment.set(ValueLayout.JAVA_INT, dataOffset + 4, edgeCap);

        this.pairTable = new OffHeapPairTable(pairCap, segment.asSlice(dataOffset + layout.pairTableOffset(), (long) CoActivationLayout.PAIR_SLOT_BYTES * pairCap), 0);
        this.edgeTable = new OffHeapEdgeTable(edgeCap, segment.asSlice(dataOffset + layout.edgeTableOffset(pairCap), (long) CoActivationLayout.EDGE_SLOT_BYTES * edgeCap), 0);

        log.info("CoActivationRecordMemory initialized (volatile): pairCap={}, edgeCap={}, memory={}KB",
                pairCap, edgeCap, totalBytes / 1024);
    }

    private CoActivationRecordMemory(Path filePath, int pairCap, int edgeCap) {
        super(SystemMemoryId.COACTIVATION.id(), new CoActivationLayout(),
                new CoActivationLayout().totalDataBytes(pairCap, edgeCap), new CoActivationLayout().totalDataBytes(pairCap, edgeCap), filePath);

        long totalBytes = layout.totalDataBytes(pairCap, edgeCap);
        MemorySegment segment = segment();
        long dataOffset = dataOffset();

        if (size() < totalBytes) {
            segment.set(ValueLayout.JAVA_INT, dataOffset, pairCap);
            segment.set(ValueLayout.JAVA_INT, dataOffset + 4, edgeCap);

            // Zero-fill the data region (replaces the old write(totalBytes-1) hack
            // that abused AbstractRecordMemory.write() for file-sizing)
            segment.asSlice(dataOffset + layout.pairTableOffset(), totalBytes - CoActivationLayout.SUB_HEADER_BYTES).fill((byte) 0);
        }

        this.pairTable = new OffHeapPairTable(pairCap, segment.asSlice(dataOffset + layout.pairTableOffset(), (long) CoActivationLayout.PAIR_SLOT_BYTES * pairCap), 0);
        this.edgeTable = new OffHeapEdgeTable(edgeCap, segment.asSlice(dataOffset + layout.edgeTableOffset(pairCap), (long) CoActivationLayout.EDGE_SLOT_BYTES * edgeCap), 0);

        log.info("CoActivationRecordMemory initialized (persistent): pairCap={}, edgeCap={}, file={}",
                pairCap, edgeCap, filePath);
    }

    /**
     * Creates a bundle-backed CoActivationRecordMemory from a pre-sliced region segment.
     */
    public static CoActivationRecordMemory fromBundle(Arena arena, MemorySegment regionSlice,
                                                       int pairCap, int edgeCap,
                                                       Path bundlePath, boolean isNew) {
        return new CoActivationRecordMemory(arena, regionSlice, pairCap, edgeCap, bundlePath, isNew, null);
    }

    public static CoActivationRecordMemory fromBundle(Arena arena, MemorySegment regionSlice,
                                                       int pairCap, int edgeCap,
                                                       Path bundlePath, boolean isNew,
                                                       MemorySegment checkpointRegion) {
        return new CoActivationRecordMemory(arena, regionSlice, pairCap, edgeCap, bundlePath, isNew, checkpointRegion);
    }

    private CoActivationRecordMemory(Arena arena, MemorySegment regionSlice,
                                     int pairCap, int edgeCap,
                                     Path bundlePath, boolean isNew,
                                     MemorySegment checkpointRegion) {
        super(SystemMemoryId.COACTIVATION.id(), new CoActivationLayout(),
              pairCap, arena, regionSlice,
              isNew ? 0 : (int) MemoryHeader.readCount(regionSlice, 0),
              true, bundlePath, null, true); // bundleManaged=true
        this.checkpointRegion = checkpointRegion;

        long totalBytes = layout.totalDataBytes(pairCap, edgeCap);
        MemorySegment segment = segment();
        long dataOffset = dataOffset();

        if (isNew) {
            segment.set(ValueLayout.JAVA_INT, dataOffset, pairCap);
            segment.set(ValueLayout.JAVA_INT, dataOffset + 4, edgeCap);
            segment.asSlice(dataOffset + layout.pairTableOffset(), totalBytes - CoActivationLayout.SUB_HEADER_BYTES).fill((byte) 0);

            long now = System.currentTimeMillis();
            MemoryHeader.write(segment, 0L, layout().schemaVersion(), MemoryShape.HASHTABLE, 1,
                    (int) totalBytes, 0, 0, layout().layoutId(), now, now);
        } else {
            if (!MemoryHeader.isValid(segment, 0L)) {
                throw new com.spectrayan.spector.commons.error.SpectorMemoryException(
                        com.spectrayan.spector.commons.error.ErrorCode.MEMORY_RECALL_FAILED,
                        "Invalid SMKM header for CoActivationRecordMemory in bundle");
            }
        }

        this.pairTable = new OffHeapPairTable(pairCap, segment.asSlice(dataOffset + layout.pairTableOffset(), (long) CoActivationLayout.PAIR_SLOT_BYTES * pairCap), 0);
        this.edgeTable = new OffHeapEdgeTable(edgeCap, segment.asSlice(dataOffset + layout.edgeTableOffset(pairCap), (long) CoActivationLayout.EDGE_SLOT_BYTES * edgeCap), 0);

        // One-time migration of standalone legacy file into bundle region
        if (isNew && bundlePath != null) {
            Path legacyPath = bundlePath.resolveSibling("coactivation.dat");
            if (Files.exists(legacyPath)) {
                log.info("Migrating legacy standalone coactivation.dat to bundle region...");
                CoActivationRecordMemory legacy = CoActivationRecordMemory.load(legacyPath, pairCap, edgeCap);
                MemorySegment.copy(legacy.pairTable.segment(), 0, pairTable.segment(), 0, legacy.pairTable.segment().byteSize());
                pairTable.setCount(legacy.pairTable.count());
                MemorySegment.copy(legacy.edgeTable.segment(), 0, edgeTable.segment(), 0, legacy.edgeTable.segment().byteSize());
                edgeTable.setCount(legacy.edgeTable.count());
                this.hashToTag.putAll(legacy.hashToTag);
                this.banditStats = new ConcurrentHashMap<>(legacy.banditStats);

                save(legacyPath); // writes coactivation.dat.meta
                try {
                    Files.deleteIfExists(legacyPath);
                } catch (IOException e) {
                    log.warn("Failed to delete legacy coactivation.dat after migration: {}", e.getMessage());
                }
            }
        } else if (!isNew && checkpointRegion != null) {
            try {
                int pairs = checkpointRegion.get(ValueLayout.JAVA_INT, CoActivationMetadataLayout.OFF_CHK_PAIR_COUNT);
                int edges = checkpointRegion.get(ValueLayout.JAVA_INT, CoActivationMetadataLayout.OFF_CHK_EDGE_COUNT);
                this.pairTable.setCount(pairs);
                this.edgeTable.setCount(edges);

                int nameCount = checkpointRegion.get(ValueLayout.JAVA_INT, CoActivationMetadataLayout.OFF_CHK_NAME_COUNT);
                long offset = CoActivationMetadataLayout.OFF_CHK_TAG_DATA;
                for (int i = 0; i < nameCount; i++) {
                    long hash = checkpointRegion.get(ValueLayout.JAVA_LONG, offset);
                    offset += 8;
                    int len = checkpointRegion.get(ValueLayout.JAVA_INT, offset);
                    offset += 4;
                    byte[] nameBytes = new byte[len];
                    MemorySegment.copy(checkpointRegion, offset, MemorySegment.ofArray(nameBytes), 0, len);
                    offset += len;
                    String name = new String(nameBytes, StandardCharsets.UTF_8);
                    this.hashToTag.put(hash, name);
                }

                int entryCount = checkpointRegion.get(ValueLayout.JAVA_INT, offset);
                offset += 4;
                CognitiveProfile[] profiles = CognitiveProfile.values();
                for (int i = 0; i < entryCount; i++) {
                    long ctxHash = checkpointRegion.get(ValueLayout.JAVA_LONG, offset);
                    int ordinal = checkpointRegion.get(ValueLayout.JAVA_BYTE, offset + CoActivationMetadataLayout.OFF_BANDIT_ORDINAL) & 0xFF;
                    float ema = checkpointRegion.get(ValueLayout.JAVA_FLOAT, offset + CoActivationMetadataLayout.OFF_BANDIT_EMA);
                    int totalSignals = checkpointRegion.get(ValueLayout.JAVA_INT, offset + CoActivationMetadataLayout.OFF_BANDIT_TOTAL_SIGNALS);
                    int positiveSignals = checkpointRegion.get(ValueLayout.JAVA_INT, offset + CoActivationMetadataLayout.OFF_BANDIT_POS_SIGNALS);
                    long lastUpdatedMs = checkpointRegion.get(ValueLayout.JAVA_LONG, offset + CoActivationMetadataLayout.OFF_BANDIT_LAST_UPDATED_MS);
                    offset += CoActivationMetadataLayout.BANDIT_RECORD_BYTES;

                    if (ordinal < profiles.length) {
                        CognitiveProfile profile = profiles[ordinal];
                        RunningStats rs = new RunningStats(ema, totalSignals, positiveSignals, lastUpdatedMs);
                        this.banditStats.computeIfAbsent(ctxHash, _ -> new EnumMap<>(CognitiveProfile.class))
                                .put(profile, rs);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to load CoActivationRecordMemory metadata from CHECKPOINT region, starting empty", e);
            }
        } else if (!isNew && bundlePath != null) {
            Path metaPath = bundlePath.resolveSibling(bundlePath.getFileName().toString() + ".meta");
            if (Files.exists(metaPath)) {
                try (FileChannel ch = FileChannel.open(metaPath, StandardOpenOption.READ)) {
                    ByteBuffer countsBuf = ByteBuffer.allocate(8);
                    ch.read(countsBuf);
                    countsBuf.flip();
                    int pairs = countsBuf.getInt();
                    int edges = countsBuf.getInt();
                    this.pairTable.setCount(pairs);
                    this.edgeTable.setCount(edges);

                    ConcurrentHashMap<Long, String> names = readTagIndex(ch);
                    this.hashToTag.putAll(names);

                    if (ch.position() < ch.size()) {
                        this.banditStats = new ConcurrentHashMap<>(readBanditStats(ch));
                    }
                } catch (IOException e) {
                    log.warn("Failed to load CoActivationRecordMemory metadata from {}, starting empty", metaPath, e);
                }
            }
        }

        this.checkpointRegion = checkpointRegion;

        log.info("CoActivationRecordMemory initialized (bundle): pairCap={}, edgeCap={}, count={}",
                pairCap, edgeCap, size());
    }

    private static long calculateTotalBytes(int maxPairs, int maxEdges) {
        int pairCap = nextPowerOf2(Math.max(SpectorPropertyConstants.DEFAULT_MEMORY_COACTIVATION_MIN_TABLE_CAPACITY, maxPairs * 2));
        int edgeCap = nextPowerOf2(Math.max(SpectorPropertyConstants.DEFAULT_MEMORY_COACTIVATION_MIN_TABLE_CAPACITY, maxEdges * 2));
        return new CoActivationLayout().totalDataBytes(pairCap, edgeCap);
    }

    // ══════════════════════════════════════════════════════════════
    // Undirected Co-Activation
    // ══════════════════════════════════════════════════════════════

    public void recordCoActivation(String... tags) {
        if (tags == null || tags.length < 2 || pairTable == null) return;

        try {
            for (int i = 0; i < tags.length; i++) {
                for (int j = i + 1; j < tags.length; j++) {
                    long hashA = hashTag(tags[i]);
                    long hashB = hashTag(tags[j]);
                    registerTag(tags[i], hashA);
                    registerTag(tags[j], hashB);

                    long keyA = Math.min(hashA, hashB);
                    long keyB = Math.max(hashA, hashB);

                    pairTable.increment(keyA, keyB);
                }
            }
        } catch (IllegalStateException ignored) {
            // Memory closed concurrently during background async recording
        }
    }

    public int getCoActivation(String tagA, String tagB) {
        long hashA = hashTag(tagA);
        long hashB = hashTag(tagB);
        long keyA = Math.min(hashA, hashB);
        long keyB = Math.max(hashA, hashB);
        return pairTable.get(keyA, keyB);
    }

    public List<String> getAssociatedTags(String tag, int topN) {
        long tagHash = hashTag(tag);

        record TagCount(String name, int count) {}

        return pairTable.findAssociations(tagHash).stream()
                .map(arr -> {
                    String name = hashToTag.get(arr[0]);
                    return name != null ? new TagCount(name, (int) arr[1]) : null;
                })
                .filter(tc -> tc != null)
                .sorted((a, b) -> Integer.compare(b.count(), a.count()))
                .limit(topN)
                .map(TagCount::name)
                .toList();
    }

    // ══════════════════════════════════════════════════════════════
    // STDP — Spike-Timing-Dependent Plasticity
    // ══════════════════════════════════════════════════════════════

    public void recordSequentialActivation(String tagBefore, String tagAfter,
                                            long timeBefore, long timeAfter) {
        if (tagBefore == null || tagAfter == null || tagBefore.equals(tagAfter) || edgeTable == null) return;
        if (timeAfter < timeBefore) return;

        try {
            long dt = timeAfter - timeBefore;
            long hashBefore = hashTag(tagBefore);
            long hashAfter = hashTag(tagAfter);
            registerTag(tagBefore, hashBefore);
            registerTag(tagAfter, hashAfter);

            float dW_causal = A_PLUS * (float) Math.exp(-dt / TAU_PLUS);
            edgeTable.update(hashBefore, hashAfter, dW_causal, timeAfter);

            float dW_anti = -A_MINUS * (float) Math.exp(-dt / TAU_MINUS);
            edgeTable.update(hashAfter, hashBefore, dW_anti, timeAfter);

            log.trace("STDP: {}→{} Δt={}ms, causal ΔW={}, anti-causal ΔW={}",
                    tagBefore, tagAfter, dt,
                    String.format("%.4f", dW_causal), String.format("%.4f", dW_anti));
        } catch (IllegalStateException ignored) {
            // Memory closed concurrently during background async recording
        }
    }

    public void recordSequentialActivations(List<String> orderedTags, List<Long> timestamps) {
        if (orderedTags.size() < 2) return;
        if (orderedTags.size() != timestamps.size()) return;

        for (int i = 0; i < orderedTags.size() - 1; i++) {
            recordSequentialActivation(
                    orderedTags.get(i), orderedTags.get(i + 1),
                    timestamps.get(i), timestamps.get(i + 1));
        }
    }

    public float getPredictiveStrength(List<String> queryTags, String[] resultTags) {
        if (queryTags == null || queryTags.isEmpty() || resultTags == null || resultTags.length == 0) {
            return 0.0f;
        }

        float maxStrength = 0.0f;
        for (String qTag : queryTags) {
            long srcHash = hashTag(qTag);
            for (String rTag : resultTags) {
                long tgtHash = hashTag(rTag);
                float weight = edgeTable.getWeight(srcHash, tgtHash);
                if (weight > maxStrength) maxStrength = weight;
            }
        }
        return maxStrength;
    }

    public float getAveragePredictiveStrength(List<String> queryTags, String[] resultTags) {
        if (queryTags == null || queryTags.isEmpty() || resultTags == null || resultTags.length == 0) {
            return 0.0f;
        }

        float sum = 0.0f;
        int matchCount = 0;
        for (String qTag : queryTags) {
            long srcHash = hashTag(qTag);
            for (String rTag : resultTags) {
                long tgtHash = hashTag(rTag);
                float weight = edgeTable.getWeight(srcHash, tgtHash);
                if (weight > 0) {
                    sum += weight;
                    matchCount++;
                }
            }
        }
        return matchCount > 0 ? sum / matchCount : 0.0f;
    }

    public EdgeWeight getEdge(String sourceTag, String targetTag) {
        long srcHash = hashTag(sourceTag);
        long tgtHash = hashTag(targetTag);
        return edgeTable.getEdge(srcHash, tgtHash);
    }

    // ══════════════════════════════════════════════════════════════
    // Counts / Reset / Close
    // ══════════════════════════════════════════════════════════════

    public int edgeCount() { return edgeTable.count(); }
    public int pairCount() { return pairTable.count(); }

    public void reset() {
        pairTable.reset();
        edgeTable.reset();
        hashToTag.clear();
        banditStats = new ConcurrentHashMap<>();
    }

    // ══════════════════════════════════════════════════════════════
    // Bandit Stats (ProfileAdaptor persistence)
    // ══════════════════════════════════════════════════════════════

    public Map<Long, EnumMap<CognitiveProfile, RunningStats>> banditStats() {
        return banditStats;
    }

    public void updateBanditStats(Map<Long, EnumMap<CognitiveProfile, RunningStats>> stats) {
        this.banditStats = stats != null ? stats : new ConcurrentHashMap<>();
    }

    private int banditStatsCount() {
        int count = 0;
        for (EnumMap<CognitiveProfile, RunningStats> map : banditStats.values()) {
            count += map.size();
        }
        return count;
    }

    // ══════════════════════════════════════════════════════════════
    // Tag Hashing
    // ══════════════════════════════════════════════════════════════

    static long hashTag(String tag) {
        long hash = FNV1A_OFFSET_BASIS;
        for (int i = 0; i < tag.length(); i++) {
            hash ^= tag.charAt(i);
            hash *= FNV1A_PRIME;
        }
        return hash == 0 ? 1 : hash;
    }

    private void registerTag(String tag, long hash) {
        hashToTag.putIfAbsent(hash, tag);
    }

    // ══════════════════════════════════════════════════════════════
    // CROSS-CAPTURE GRAPH: Tag → Memory Inverted Index (ADR-0009)
    // ══════════════════════════════════════════════════════════════

    /**
     * A tag neighbor discovered via co-occurrence traversal.
     *
     * @param tagHash          hash of the neighbor tag
     * @param tagName          human-readable tag name (from hashToTag dictionary)
     * @param coOccurrenceCount number of times this tag co-occurred with the query tag
     */
    public record TagNeighbor(long tagHash, String tagName, int coOccurrenceCount) {}

    /**
     * A memory candidate discovered via Cross-Capture Graph traversal.
     *
     * @param memorySlotIndex the slot index of the discovered memory
     * @param viaTag          the related tag through which this memory was found
     * @param score           composite score incorporating co-occurrence and fan-factor
     */
    public record CrossCaptureCandidate(int memorySlotIndex, String viaTag, float score) {}

    /**
     * Records that the memory at {@code slotIndex} carries the given tag.
     * Called during ingestion (RememberPathway) after synaptic tag extraction.
     *
     * @param tagHash   FNV-1a hash of the tag string
     * @param slotIndex memory slot index
     */
    public void indexMemoryTag(long tagHash, int slotIndex) {
        tagToMemoryIndex
                .computeIfAbsent(tagHash, _ -> new java.util.concurrent.CopyOnWriteArrayList<>())
                .addIfAbsent(slotIndex);
    }

    /**
     * Records that the memory at {@code slotIndex} carries the given tag.
     * Convenience overload accepting the tag string directly.
     *
     * @param tag       tag string
     * @param slotIndex memory slot index
     */
    public void indexMemoryTag(String tag, int slotIndex) {
        indexMemoryTag(hashTag(tag), slotIndex);
    }

    /**
     * Removes a memory from the inverted index (e.g., when tombstoned/pruned).
     *
     * @param slotIndex memory slot index to deindex
     */
    public void deindexMemory(int slotIndex) {
        Integer boxed = slotIndex;
        tagToMemoryIndex.values().forEach(list -> list.remove(boxed));
    }

    /**
     * Finds the top-N tags co-occurring with the given tag, ranked by co-occurrence count.
     *
     * @param tagHash       FNV-1a hash of the query tag
     * @param maxNeighbors  maximum number of neighbor tags to return
     * @return ordered list of tag neighbors, highest co-occurrence first
     */
    public List<TagNeighbor> traverseRelatedTags(long tagHash, int maxNeighbors) {
        return pairTable.findAssociations(tagHash).stream()
                .map(arr -> {
                    String name = hashToTag.get(arr[0]);
                    return name != null ? new TagNeighbor(arr[0], name, (int) arr[1]) : null;
                })
                .filter(tn -> tn != null)
                .sorted((a, b) -> Integer.compare(b.coOccurrenceCount(), a.coOccurrenceCount()))
                .limit(maxNeighbors)
                .toList();
    }

    /**
     * Finds memory slot indices carrying the given tag.
     *
     * @param tagHash FNV-1a hash of the tag
     * @param limit   maximum number of memory indices to return
     * @return array of memory slot indices
     */
    public int[] findMemoriesByTag(long tagHash, int limit) {
        var list = tagToMemoryIndex.get(tagHash);
        if (list == null || list.isEmpty()) return new int[0];
        return list.stream().limit(limit).mapToInt(Integer::intValue).toArray();
    }

    /**
     * Cross-Capture Graph traversal: from query tags, find related tags via
     * co-occurrence, then discover memories carrying those related tags.
     *
     * <p>This implements the biological STC cross-capture mechanism where
     * tagged synapses that co-occur share Plasticity-Related Proteins,
     * creating associative links between conceptually related memory traces.</p>
     *
     * <p>Uses ACT-R spreading activation dilution: each tag's contribution
     * is attenuated by {@code 1/√(degree)} to prevent high-degree supernodes
     * from dominating the result set.</p>
     *
     * @param queryTags          tags extracted from the recall query
     * @param maxTagNeighbors    max co-occurring tags to explore per query tag
     * @param maxMemoriesPerTag  max memories to retrieve per related tag
     * @return list of candidate memories with scores
     */
    public List<CrossCaptureCandidate> crossCaptureTraversal(
            java.util.Collection<String> queryTags, int maxTagNeighbors, int maxMemoriesPerTag) {

        if (queryTags == null || queryTags.isEmpty()) return List.of();

        List<CrossCaptureCandidate> candidates = new java.util.ArrayList<>();
        java.util.Set<Integer> seen = new java.util.HashSet<>();

        int uniqueSlots = (int) tagToMemoryIndex.values().stream().flatMap(java.util.List::stream).distinct().count();
        int corpusN = Math.max(1, uniqueSlots);

        for (String queryTag : queryTags) {
            long qHash = hashTag(queryTag);

            // Find top co-occurring tags
            List<TagNeighbor> neighbors = traverseRelatedTags(qHash, maxTagNeighbors);

            for (TagNeighbor neighbor : neighbors) {
                // ACT-R fan-factor attenuation: 1/√(degree) + Information Theory IDF attenuation
                var neighborList = tagToMemoryIndex.get(neighbor.tagHash());
                int degree = neighborList != null ? neighborList.size() : 0;
                if (degree == 0) continue;

                float idf = (float) Math.log(1.0 + (double) corpusN / (double) (degree + 1));
                float fanFactor = 1.0f / (float) Math.pow(degree, SpectorPropertyConstants.DEFAULT_MEMORY_CROSS_CAPTURE_FAN_EXPONENT);
                float saturatedCoOccurrence = Math.min((float) neighbor.coOccurrenceCount(), 20.0f);
                float baseScore = saturatedCoOccurrence * fanFactor * idf;

                int[] memorySlots = findMemoriesByTag(neighbor.tagHash(), maxMemoriesPerTag);
                for (int slot : memorySlots) {
                    if (seen.add(slot)) {
                        candidates.add(new CrossCaptureCandidate(slot, neighbor.tagName(), baseScore));
                    }
                }
            }
        }

        // Sort by score descending
        candidates.sort((a, b) -> Float.compare(b.score(), a.score()));
        return candidates;
    }

    /**
     * Rebuilds the tag → memory inverted index from scratch.
     * Called on startup or during ReflectPathway consolidation cycles.
     *
     * @param tagSets mapping from memory slot index to the set of tag strings for that memory
     */
    public void rebuildInvertedIndex(Map<Integer, java.util.Collection<String>> tagSets) {
        tagToMemoryIndex.clear();
        for (Map.Entry<Integer, java.util.Collection<String>> entry : tagSets.entrySet()) {
            int slotIndex = entry.getKey();
            for (String tag : entry.getValue()) {
                indexMemoryTag(tag, slotIndex);
            }
        }
        log.info("Cross-Capture inverted index rebuilt: {} tags → {} total entries",
                tagToMemoryIndex.size(),
                tagToMemoryIndex.values().stream().mapToInt(java.util.List::size).sum());
    }

    /**
     * Returns the number of tags in the inverted index.
     */
    public int invertedIndexTagCount() {
        return tagToMemoryIndex.size();
    }

    /**
     * Returns the total number of tag→memory entries in the inverted index.
     */
    public int invertedIndexEntryCount() {
        return tagToMemoryIndex.values().stream().mapToInt(java.util.List::size).sum();
    }

    // ══════════════════════════════════════════════════════════════
    // PERSISTENCE: save / load
    // ══════════════════════════════════════════════════════════════

    public void save(Path filePath) {
        saveLock.lock();
        try {
            if (isBundleManaged()) {
                if (checkpointRegion != null) {
                    flush();
                    int tagsSize = 4;
                    for (String tag : hashToTag.values()) {
                        tagsSize += 12 + tag.getBytes(StandardCharsets.UTF_8).length;
                    }
                    int banditSize = 4 + banditStatsCount() * CoActivationMetadataLayout.BANDIT_RECORD_BYTES;
                    int totalSize = 8 + tagsSize + banditSize;
                    ByteBuffer buf = ByteBuffer.allocate(totalSize);

                    buf.putInt(pairTable.count());
                    buf.putInt(edgeTable.count());

                    buf.putInt(hashToTag.size());
                    for (Map.Entry<Long, String> entry : hashToTag.entrySet()) {
                        byte[] nameBytes = entry.getValue().getBytes(StandardCharsets.UTF_8);
                        buf.putLong(entry.getKey());
                        buf.putInt(nameBytes.length);
                        buf.put(nameBytes);
                    }

                    buf.putInt(banditStatsCount());
                    for (Map.Entry<Long, EnumMap<CognitiveProfile, RunningStats>> ctxEntry : banditStats.entrySet()) {
                        long ctxHash = ctxEntry.getKey();
                        for (Map.Entry<CognitiveProfile, RunningStats> profEntry : ctxEntry.getValue().entrySet()) {
                            RunningStats rs = profEntry.getValue();
                            buf.putLong(ctxHash);
                            buf.put((byte) profEntry.getKey().ordinal());
                            buf.put((byte) 0);
                            buf.put((byte) 0);
                            buf.put((byte) 0);
                            buf.putFloat(rs.ema());
                            buf.putInt(rs.totalSignals());
                            buf.putInt(rs.positiveSignals());
                            buf.putLong(rs.lastUpdatedMs());
                        }
                    }
                    buf.flip();
                    MemorySegment.copy(MemorySegment.ofBuffer(buf), 0, checkpointRegion, CoActivationMetadataLayout.OFF_CHK_PAIR_COUNT, totalSize);
                } else {
                    Path path = filePath != null ? filePath : filePath();
                    if (path == null) return;
                    Path metaPath = path.resolveSibling(path.getFileName().toString() + ".meta");
                    try {
                        Path parent = metaPath.getParent();
                        if (parent != null) {
                            Files.createDirectories(parent);
                        }
                        flush();
                        try (FileChannel ch = FileChannel.open(metaPath,
                                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                            ByteBuffer countsBuf = ByteBuffer.allocate(8);
                            countsBuf.putInt(pairTable.count());
                            countsBuf.putInt(edgeTable.count());
                            countsBuf.flip();
                            ch.write(countsBuf);

                            writeTagIndex(ch);
                            writeBanditStats(ch);
                        }
                    } catch (IOException e) {
                        throw new SpectorGraphPersistenceException("CoActivationRecordMemory", metaPath, e);
                    }
                }
            } else if (!isPersistent()) {
                try {
                    Path parent = filePath.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    try (FileChannel ch = FileChannel.open(filePath,
                            StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

                        long totalBytes = layout.totalDataBytes(pairTable.capacity(), edgeTable.capacity());
                        ByteBuffer header = ByteBuffer.allocate(64);
                        MemorySegment headerSeg = MemorySegment.ofBuffer(header);
                        MemoryHeader.write(headerSeg, 0, layout().schemaVersion(), shape(),
                                0x01, totalBytes, totalBytes, layout().recordStride(), layout().layoutId(),
                                System.currentTimeMillis(), System.currentTimeMillis());
                        header.limit(64).position(0);
                        ch.write(header);

                        ByteBuffer dataBuf = segment().asSlice(0, totalBytes).asByteBuffer().asReadOnlyBuffer();
                        ch.write(dataBuf);

                        ByteBuffer countsBuf = ByteBuffer.allocate(8);
                        countsBuf.putInt(pairTable.count());
                        countsBuf.putInt(edgeTable.count());
                        countsBuf.flip();
                        ch.write(countsBuf);

                        writeTagIndex(ch);
                        writeBanditStats(ch);
                    }

                } catch (IOException e) {
                    throw new SpectorGraphPersistenceException("CoActivationRecordMemory", filePath, e);
                }
            } else {
                try {
                    flush();
                    Path path = filePath != null ? filePath : filePath();
                    try (FileChannel ch = FileChannel.open(path, StandardOpenOption.WRITE)) {
                        ch.position(MemoryHeader.HEADER_BYTES + layout.totalDataBytes(pairTable.capacity(), edgeTable.capacity()));

                        ByteBuffer countsBuf = ByteBuffer.allocate(8);
                        countsBuf.putInt(pairTable.count());
                        countsBuf.putInt(edgeTable.count());
                        countsBuf.flip();
                        ch.write(countsBuf);

                        writeTagIndex(ch);
                        writeBanditStats(ch);
                    }
                } catch (IOException e) {
                    throw new SpectorGraphPersistenceException("CoActivationRecordMemory", filePath, e);
                }
            }
        } finally {
            saveLock.unlock();
        }
    }

    public static CoActivationRecordMemory load(Path filePath, int defaultPairs, int defaultEdges) {
        if (filePath == null || !Files.exists(filePath)) {
            log.info("CoActivationRecordMemory file not found, creating fresh: {}", filePath);
            int pairCap = nextPowerOf2(Math.max(SpectorPropertyConstants.DEFAULT_MEMORY_COACTIVATION_MIN_TABLE_CAPACITY, defaultPairs * 2));
            int edgeCap = nextPowerOf2(Math.max(SpectorPropertyConstants.DEFAULT_MEMORY_COACTIVATION_MIN_TABLE_CAPACITY, defaultEdges * 2));
            return new CoActivationRecordMemory(filePath, pairCap, edgeCap);
        }

        try {
            int magic = 0;
            int version = 0;
            try (FileChannel ch = FileChannel.open(filePath, StandardOpenOption.READ)) {
                if (ch.size() >= 8) {
                    ByteBuffer mb = ByteBuffer.allocate(8);
                    ch.read(mb);
                    mb.flip();
                    magic = mb.getInt();
                    version = mb.getInt();
                }
            } catch (IOException e) {
                // ignore
            }

            if (magic == FILE_MAGIC && (version == 1 || version == 2)) {
                return migrateLegacy(filePath, magic, version);
            }

            int pairCap;
            int edgeCap;
            try (FileChannel ch = FileChannel.open(filePath, StandardOpenOption.READ)) {
                ByteBuffer capBuf = ByteBuffer.allocate(8);
                ch.position(MemoryHeader.HEADER_BYTES);
                ch.read(capBuf);
                capBuf.flip();
                capBuf.order(ByteOrder.nativeOrder());
                pairCap = capBuf.getInt();
                edgeCap = capBuf.getInt();
            }

            CoActivationRecordMemory tracker = new CoActivationRecordMemory(filePath, pairCap, edgeCap);

            try (FileChannel ch = FileChannel.open(filePath, StandardOpenOption.READ)) {
                ch.position(MemoryHeader.HEADER_BYTES + new CoActivationLayout().totalDataBytes(pairCap, edgeCap));

                ByteBuffer countsBuf = ByteBuffer.allocate(8);
                ch.read(countsBuf);
                countsBuf.flip();
                int pairs = countsBuf.getInt();
                int edges = countsBuf.getInt();

                tracker.pairTable.setCount(pairs);
                tracker.edgeTable.setCount(edges);

                ConcurrentHashMap<Long, String> names = readTagIndex(ch);
                tracker.hashToTag.putAll(names);

                if (ch.position() < ch.size()) {
                    tracker.banditStats = new ConcurrentHashMap<>(readBanditStats(ch));
                } else {
                    tracker.banditStats = new ConcurrentHashMap<>();
                }
            }

            return tracker;

        } catch (IOException e) {
            log.error("Failed to load CoActivationRecordMemory, creating fresh: {}", e.getMessage());
            return new CoActivationRecordMemory(filePath, defaultPairs, defaultEdges);
        }
    }

    private static CoActivationRecordMemory migrateLegacy(Path filePath, int magic, int version) {
        log.info("Migrating legacy CoActivationTracker format (v{}) to standard Memory Kernel format: {}", version, filePath);
        try (FileChannel ch = FileChannel.open(filePath, StandardOpenOption.READ)) {
            ch.position(8);
            ByteBuffer headerV1 = ByteBuffer.allocate(FILE_HEADER_V1_BYTES - 8);
            ch.read(headerV1);
            headerV1.flip();
            int pairCap = headerV1.getInt();
            int edgeCap = headerV1.getInt();
            int pairs = headerV1.getInt();
            int edges = headerV1.getInt();

            if (version >= 2) {
                ByteBuffer headerV2 = ByteBuffer.allocate(FILE_HEADER_BYTES - FILE_HEADER_V1_BYTES);
                ch.read(headerV2);
                headerV2.flip();
                headerV2.getInt();
                headerV2.getInt();
            }

            try (Arena tempArena = Arena.ofShared()) {
                OffHeapPairTable legacyPairTable = OffHeapPairTable.readFrom(ch, pairCap, pairs, tempArena);
                OffHeapEdgeTable legacyEdgeTable = OffHeapEdgeTable.readFrom(ch, edgeCap, edges, tempArena);
                ConcurrentHashMap<Long, String> names = readTagIndex(ch);
                Map<Long, EnumMap<CognitiveProfile, RunningStats>> bandit;
                if (version >= 2 && ch.position() < ch.size()) {
                    bandit = readBanditStats(ch);
                } else {
                    bandit = new ConcurrentHashMap<>();
                }

                ch.close();
                Files.deleteIfExists(filePath);

                CoActivationRecordMemory tracker = new CoActivationRecordMemory(filePath, pairCap, edgeCap);

                MemorySegment.copy(legacyPairTable.segment(), 0, tracker.pairTable.segment(), 0, (long) OffHeapPairTable.SLOT_BYTES * pairCap);
                MemorySegment.copy(legacyEdgeTable.segment(), 0, tracker.edgeTable.segment(), 0, (long) OffHeapEdgeTable.SLOT_BYTES * edgeCap);
                tracker.pairTable.setCount(pairs);
                tracker.edgeTable.setCount(edges);

                tracker.hashToTag.putAll(names);
                tracker.banditStats = new ConcurrentHashMap<>(bandit);

                tracker.save(filePath);
                return tracker;
            }
        } catch (IOException e) {
            throw new SpectorGraphPersistenceException("CoActivationRecordMemory migration failed", filePath, e);
        }
    }

    private void writeTagIndex(FileChannel ch) throws IOException {
        ByteBuffer countBuf = ByteBuffer.allocate(4);
        countBuf.putInt(hashToTag.size());
        countBuf.flip();
        ch.write(countBuf);

        for (Map.Entry<Long, String> entry : hashToTag.entrySet()) {
            byte[] nameBytes = entry.getValue().getBytes(StandardCharsets.UTF_8);
            ByteBuffer entryBuf = ByteBuffer.allocate(8 + 4 + nameBytes.length);
            entryBuf.putLong(entry.getKey());
            entryBuf.putInt(nameBytes.length);
            entryBuf.put(nameBytes);
            entryBuf.flip();
            ch.write(entryBuf);
        }
    }

    private static ConcurrentHashMap<Long, String> readTagIndex(FileChannel ch) throws IOException {
        ConcurrentHashMap<Long, String> names = new ConcurrentHashMap<>();

        ByteBuffer countBuf = ByteBuffer.allocate(4);
        ch.read(countBuf);
        countBuf.flip();
        int nameCount = countBuf.getInt();

        for (int i = 0; i < nameCount; i++) {
            ByteBuffer hashBuf = ByteBuffer.allocate(8);
            ch.read(hashBuf);
            hashBuf.flip();
            long hash = hashBuf.getLong();

            ByteBuffer lenBuf = ByteBuffer.allocate(4);
            ch.read(lenBuf);
            lenBuf.flip();
            int len = lenBuf.getInt();

            ByteBuffer nameBuf = ByteBuffer.allocate(len);
            ch.read(nameBuf);
            nameBuf.flip();
            String name = new String(nameBuf.array(), 0, len, StandardCharsets.UTF_8);

            names.put(hash, name);
        }

        return names;
    }

    private void writeBanditStats(FileChannel ch) throws IOException {
        int entryCount = banditStatsCount();
        ByteBuffer countBuf = ByteBuffer.allocate(4);
        countBuf.putInt(entryCount);
        countBuf.flip();
        ch.write(countBuf);

        if (entryCount == 0) return;

        ByteBuffer entryBuf = ByteBuffer.allocate(32);
        for (Map.Entry<Long, EnumMap<CognitiveProfile, RunningStats>> ctxEntry : banditStats.entrySet()) {
            long ctxHash = ctxEntry.getKey();
            for (Map.Entry<CognitiveProfile, RunningStats> profEntry : ctxEntry.getValue().entrySet()) {
                RunningStats rs = profEntry.getValue();
                entryBuf.clear();
                entryBuf.putLong(ctxHash);
                entryBuf.put((byte) profEntry.getKey().ordinal());
                entryBuf.put((byte) 0);
                entryBuf.put((byte) 0);
                entryBuf.put((byte) 0);
                entryBuf.putFloat(rs.ema());
                entryBuf.putInt(rs.totalSignals());
                entryBuf.putInt(rs.positiveSignals());
                entryBuf.putLong(rs.lastUpdatedMs());
                entryBuf.flip();
                ch.write(entryBuf);
            }
        }
    }

    private static Map<Long, EnumMap<CognitiveProfile, RunningStats>> readBanditStats(
            FileChannel ch) throws IOException {
        ByteBuffer countBuf = ByteBuffer.allocate(4);
        ch.read(countBuf);
        countBuf.flip();
        int entryCount = countBuf.getInt();

        CognitiveProfile[] profiles = CognitiveProfile.values();
        ConcurrentHashMap<Long, EnumMap<CognitiveProfile, RunningStats>> result =
                new ConcurrentHashMap<>();

        ByteBuffer entryBuf = ByteBuffer.allocate(32);
        for (int i = 0; i < entryCount; i++) {
            entryBuf.clear();
            ch.read(entryBuf);
            entryBuf.flip();

            long ctxHash = entryBuf.getLong();
            int ordinal = entryBuf.get() & 0xFF;
            entryBuf.get(); entryBuf.get(); entryBuf.get();
            float ema = entryBuf.getFloat();
            int totalSignals = entryBuf.getInt();
            int positiveSignals = entryBuf.getInt();
            long lastUpdatedMs = entryBuf.getLong();

            if (ordinal >= profiles.length) {
                log.warn("Skipping bandit entry with unknown profile ordinal: {}", ordinal);
                continue;
            }

            CognitiveProfile profile = profiles[ordinal];
            RunningStats rs = new RunningStats(ema, totalSignals, positiveSignals, lastUpdatedMs);
            result.computeIfAbsent(ctxHash, _ -> new EnumMap<>(CognitiveProfile.class))
                    .put(profile, rs);
        }

        return result;
    }

    private static int nextPowerOf2(int n) {
        int p = 1;
        while (p < n) p <<= 1;
        return p;
    }
}
