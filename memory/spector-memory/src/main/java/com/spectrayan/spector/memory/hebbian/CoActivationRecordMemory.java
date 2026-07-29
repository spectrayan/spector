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
package com.spectrayan.spector.memory.hebbian;

import com.spectrayan.spector.memory.adaptor.RunningStats;
import com.spectrayan.spector.memory.error.SpectorGraphPersistenceException;
import com.spectrayan.spector.memory.model.CognitiveProfile;
import com.spectrayan.spector.memory.kernel.MemoryHeader;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.layout.CoActivationLayout;
import com.spectrayan.spector.memory.kernel.shape.AbstractRecordMemory;

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
 * Off-heap synaptic tag co-occurrence and STDP tracking for Hebbian learning, extending
 * the Spector Memory Kernel {@link AbstractRecordMemory}.
 *
 * @see OffHeapPairTable
 * @see OffHeapEdgeTable
 */
public final class CoActivationRecordMemory extends AbstractRecordMemory<CoActivationLayout> {

    private static final Logger log = LoggerFactory.getLogger(CoActivationRecordMemory.class);

    // ── STDP Constants ──
    private static final float A_PLUS = 0.1f;
    private static final float A_MINUS = 0.05f;
    private static final float TAU_PLUS = 30_000f;
    private static final float TAU_MINUS = 30_000f;
    static final float MIN_WEIGHT = 0.0f;
    static final float MAX_WEIGHT = 1.0f;

    // ── Persistence ──
    private static final int FILE_MAGIC = 0x434F4158;
    private static final int FILE_VERSION = 2;
    private static final int FILE_HEADER_V1_BYTES = 24;
    private static final int FILE_HEADER_BYTES = 32;

    // ── Tables ──
    private OffHeapPairTable pairTable;
    private OffHeapEdgeTable edgeTable;

    private final ConcurrentHashMap<Long, String> hashToTag = new ConcurrentHashMap<>();
    private volatile Map<Long, EnumMap<CognitiveProfile, RunningStats>> banditStats =
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

    // ══════════════════════════════════════════════════════════════
    // Constructors
    // ══════════════════════════════════════════════════════════════

    public CoActivationRecordMemory() {
        this(10_000);
    }

    public CoActivationRecordMemory(int maxPairs) {
        this(maxPairs, maxPairs * 2);
    }

    public CoActivationRecordMemory(int maxPairs, int maxEdges) {
        this(MemoryId.of("hebbian", "coactivation"), calculateTotalBytes(maxPairs, maxEdges), maxPairs, maxEdges);
    }

    private CoActivationRecordMemory(MemoryId id, long totalBytes, int maxPairs, int maxEdges) {
        super(id, new CoActivationLayout(), (int) totalBytes, totalBytes);

        int pairCap = nextPowerOf2(Math.max(64, maxPairs * 2));
        int edgeCap = nextPowerOf2(Math.max(64, maxEdges * 2));

        MemorySegment segment = segment();
        long dataOffset = dataOffset();

        segment.set(ValueLayout.JAVA_INT, dataOffset, pairCap);
        segment.set(ValueLayout.JAVA_INT, dataOffset + 4, edgeCap);

        this.pairTable = new OffHeapPairTable(pairCap, segment.asSlice(dataOffset + 8, 32L * pairCap), 0);
        this.edgeTable = new OffHeapEdgeTable(edgeCap, segment.asSlice(dataOffset + 8 + 32L * pairCap, 40L * edgeCap), 0);

        log.info("CoActivationRecordMemory initialized (volatile): pairCap={}, edgeCap={}, memory={}KB",
                pairCap, edgeCap, totalBytes / 1024);
    }

    private CoActivationRecordMemory(Path filePath, int pairCap, int edgeCap) {
        super(MemoryId.of("hebbian", "coactivation"), new CoActivationLayout(),
                (int) (8 + 32L * pairCap + 40L * edgeCap), 8 + 32L * pairCap + 40L * edgeCap, filePath);

        long totalBytes = 8 + 32L * pairCap + 40L * edgeCap;
        MemorySegment segment = segment();
        long dataOffset = dataOffset();

        if (size() < totalBytes) {
            segment.set(ValueLayout.JAVA_INT, dataOffset, pairCap);
            segment.set(ValueLayout.JAVA_INT, dataOffset + 4, edgeCap);

            MemorySegment singleByte = Arena.ofAuto().allocate(1);
            singleByte.set(ValueLayout.JAVA_BYTE, 0, (byte) 0);
            write(totalBytes - 1, singleByte);
        }

        this.pairTable = new OffHeapPairTable(pairCap, segment.asSlice(dataOffset + 8, 32L * pairCap), 0);
        this.edgeTable = new OffHeapEdgeTable(edgeCap, segment.asSlice(dataOffset + 8 + 32L * pairCap, 40L * edgeCap), 0);

        log.info("CoActivationRecordMemory initialized (persistent): pairCap={}, edgeCap={}, file={}",
                pairCap, edgeCap, filePath);
    }

    private static long calculateTotalBytes(int maxPairs, int maxEdges) {
        int pairCap = nextPowerOf2(Math.max(64, maxPairs * 2));
        int edgeCap = nextPowerOf2(Math.max(64, maxEdges * 2));
        return 8 + 32L * pairCap + 40L * edgeCap;
    }

    // ══════════════════════════════════════════════════════════════
    // Undirected Co-Activation
    // ══════════════════════════════════════════════════════════════

    public void recordCoActivation(String... tags) {
        if (tags == null || tags.length < 2) return;

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
        if (tagBefore.equals(tagAfter)) return;
        if (timeAfter < timeBefore) return;

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
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < tag.length(); i++) {
            hash ^= tag.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash == 0 ? 1 : hash;
    }

    private void registerTag(String tag, long hash) {
        hashToTag.putIfAbsent(hash, tag);
    }

    // ══════════════════════════════════════════════════════════════
    // PERSISTENCE: save / load
    // ══════════════════════════════════════════════════════════════

    public synchronized void save(Path filePath) {
        if (!isPersistent()) {
            try {
                Path parent = filePath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try (FileChannel ch = FileChannel.open(filePath,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

                    long totalBytes = 8 + 32L * pairTable.capacity() + 40L * edgeTable.capacity();
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
                try (FileChannel ch = FileChannel.open(filePath, StandardOpenOption.WRITE)) {
                    ch.position(MemoryHeader.HEADER_BYTES + 8 + 32L * pairTable.capacity() + 40L * edgeTable.capacity());

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
    }

    public static CoActivationRecordMemory load(Path filePath, int defaultPairs, int defaultEdges) {
        if (filePath == null || !Files.exists(filePath)) {
            log.info("CoActivationRecordMemory file not found, creating fresh: {}", filePath);
            int pairCap = nextPowerOf2(Math.max(64, defaultPairs * 2));
            int edgeCap = nextPowerOf2(Math.max(64, defaultEdges * 2));
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
                ch.position(MemoryHeader.HEADER_BYTES + 8 + 32L * pairCap + 40L * edgeCap);

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
