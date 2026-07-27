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
package com.spectrayan.spector.memory.temporal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import com.spectrayan.spector.memory.error.SpectorGraphPersistenceException;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.MemoryHeader;
import com.spectrayan.spector.memory.kernel.AbstractMemory;
import com.spectrayan.spector.memory.kernel.shape.ChainMemory;
import com.spectrayan.spector.memory.kernel.layout.TemporalLayout;

/**
 * Off-heap temporal causal chain linking memories within a session.
 *
 * <h3>Biological Analog: Episodic Sequence Memory</h3>
 * <p>In the hippocampus, episodic memories are linked in temporal order.
 * When you recall one event from a day, you naturally remember what happened
 * next ("what happened after the meeting?"). This chain stores explicit
 * prev/next pointers between memories ingested within the same session.</p>
 *
 * <h3>Layout Per Node (16 bytes)</h3>
 * <pre>
 *   [prevIdx:4B] [nextIdx:4B] [sessionId:4B] [epochSec:4B]
 * </pre>
 *
 * <p>-1 is used as sentinel for "no link" (beginning or end of chain).</p>
 */
public final class TemporalChain implements ChainMemory<TemporalLayout>, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TemporalChain.class);

    /** Bytes per node: prevIdx(4) + nextIdx(4) + sessionId(4) + epochSec(4). */
    static final int NODE_BYTES = 16;

    /** Sentinel value for "no link". */
    private static final int NO_LINK = -1;

    // Offsets within each node
    private static final long OFF_PREV = 0;
    private static final long OFF_NEXT = 4;
    private static final long OFF_SESSION = 8;
    private static final long OFF_EPOCH_SEC = 12;

    private final TemporalChainBacking backing;

    /**
     * Creates a heap-allocated temporal chain (in-memory mode).
     *
     * @param capacity maximum number of nodes (memories)
     */
    public TemporalChain(int capacity) {
        TemporalLayout layout = new TemporalLayout();
        MemoryId id = MemoryId.of("temporal", "chain");
        long dataBytes = (long) NODE_BYTES * capacity;
        this.backing = new TemporalChainBacking(id, layout, capacity, dataBytes);

        MemorySegment seg = backing.segment();
        for (int i = 0; i < capacity; i++) {
            long offset = (long) i * NODE_BYTES;
            seg.set(ValueLayout.JAVA_INT, offset + OFF_PREV, NO_LINK);
            seg.set(ValueLayout.JAVA_INT, offset + OFF_NEXT, NO_LINK);
            seg.set(ValueLayout.JAVA_INT, offset + OFF_SESSION, 0);
            seg.set(ValueLayout.JAVA_INT, offset + OFF_EPOCH_SEC, 0);
        }

        log.info("TemporalChain initialized (heap): capacity={}, memory={}KB",
                capacity, dataBytes / 1024);
    }

    /**
     * Creates or opens a file-backed (mmap) temporal chain.
     *
     * @param filePath path to the chain file
     * @param capacity maximum number of nodes (used only for new files)
     */
    public TemporalChain(Path filePath, int capacity) {
        Path parent = filePath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new SpectorGraphPersistenceException("TemporalChain", parent, e);
            }
        }

        try {
            // 1. Perform in-place TPCH -> SMKM migration if needed
            checkAndMigrateHeader(filePath, capacity);

            // 2. Open using standard SMKM format
            TemporalLayout layout = new TemporalLayout();
            MemoryId id = MemoryId.of("temporal", "chain");
            long dataBytes = (long) NODE_BYTES * capacity;
            boolean isNew = !Files.exists(filePath) || Files.size(filePath) < MemoryHeader.HEADER_BYTES;

            this.backing = new TemporalChainBacking(id, layout, capacity, dataBytes, filePath);

            if (isNew) {
                MemorySegment seg = backing.segment();
                long base = backing.dataOffset();
                for (int i = 0; i < capacity; i++) {
                    long offset = base + (long) i * NODE_BYTES;
                    seg.set(ValueLayout.JAVA_INT, offset + OFF_PREV, NO_LINK);
                    seg.set(ValueLayout.JAVA_INT, offset + OFF_NEXT, NO_LINK);
                    seg.set(ValueLayout.JAVA_INT, offset + OFF_SESSION, 0);
                    seg.set(ValueLayout.JAVA_INT, offset + OFF_EPOCH_SEC, 0);
                }
                backing.flush();
            }

            log.info("TemporalChain initialized (mmap): capacity={}, file={}",
                    backing.capacity(), filePath.getFileName());

        } catch (IOException e) {
            throw new SpectorGraphPersistenceException("TemporalChain", filePath, e);
        }
    }

    private TemporalChain(TemporalChainBacking backing) {
        this.backing = backing;
    }

    private static void checkAndMigrateHeader(Path filePath, int capacity) throws IOException {
        if (filePath == null || !Files.exists(filePath) || Files.size(filePath) < 16) {
            return;
        }
        try (FileChannel ch = FileChannel.open(filePath, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            ByteBuffer header = ByteBuffer.allocate(16);
            ch.read(header);
            header.flip();
            int magic = header.getInt();
            if (magic == 0x54504348) { // legacy 'TPCH'
                log.info("Migrating legacy TPCH file to SMKM: {}", filePath);
                int version = header.getInt();
                int fileCapacity = header.getInt();
                int count = header.getInt();

                long dataSize = (long) fileCapacity * 16;
                ByteBuffer dataBuf = ByteBuffer.allocate((int) dataSize);
                ch.position(16);
                ch.read(dataBuf);
                dataBuf.flip();

                ch.truncate(0);
                ch.position(0);

                try (Arena tempArena = Arena.ofConfined()) {
                    long totalBytes = MemoryHeader.HEADER_BYTES + dataSize;
                    ch.position(totalBytes - 1);
                    ch.write(ByteBuffer.wrap(new byte[]{0}));
                    MemorySegment tempSegment = ch.map(FileChannel.MapMode.READ_WRITE, 0, totalBytes, tempArena);
                    
                    MemoryHeader.write(tempSegment, 0, 2, MemoryShape.CHAIN, 1, 
                            fileCapacity, count, 16, 0x54504348, 
                            System.currentTimeMillis(), System.currentTimeMillis());
                    
                    MemorySegment.copy(MemorySegment.ofBuffer(dataBuf), 0, tempSegment, MemoryHeader.HEADER_BYTES, dataSize);
                    tempSegment.force();
                }
                log.info("Migrated legacy TPCH file to SMKM successfully: {} (capacity={}, count={})", 
                        filePath, fileCapacity, count);
            }
        }
    }

    private long dataOffset() {
        return backing.dataOffset();
    }

    // ── ChainMemory implementation ──

    @Override
    public MemoryId id() {
        return backing.id();
    }

    @Override
    public TemporalLayout layout() {
        return backing.layout();
    }

    @Override
    public Arena arena() {
        return backing.arena();
    }

    @Override
    public MemorySegment segment() {
        return backing.segment();
    }

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public int capacity() {
        return backing.capacity();
    }

    @Override
    public int schemaVersion() {
        return backing.schemaVersion();
    }

    @Override
    public MemoryShape shape() {
        return backing.shape();
    }

    @Override
    public void flush() {
        backing.flush();
    }

    @Override
    public void link(int nodeId, int nextId) {
        link(nextId, nodeId, 0);
    }

    @Override
    public int next(int nodeId) {
        if (nodeId < 0 || nodeId >= capacity()) return NO_LINK;
        return segment().get(ValueLayout.JAVA_INT, dataOffset() + (long) nodeId * NODE_BYTES + OFF_NEXT);
    }

    @Override
    public int prev(int nodeId) {
        if (nodeId < 0 || nodeId >= capacity()) return NO_LINK;
        return segment().get(ValueLayout.JAVA_INT, dataOffset() + (long) nodeId * NODE_BYTES + OFF_PREV);
    }

    @Override
    public int head() {
        int cap = capacity();
        for (int i = 0; i < cap; i++) {
            if (isLinked(i) && prev(i) == NO_LINK) {
                return i;
            }
        }
        return NO_LINK;
    }

    @Override
    public int tail() {
        int cap = capacity();
        for (int i = 0; i < cap; i++) {
            if (isLinked(i) && next(i) == NO_LINK) {
                return i;
            }
        }
        return NO_LINK;
    }

    @Override
    public int chainLength() {
        int length = 0;
        int cap = capacity();
        for (int i = 0; i < cap; i++) {
            if (isLinked(i)) {
                length++;
            }
        }
        return length;
    }

    // ── Subsystem-specific Overloads ──

    /**
     * Links two memories in temporal order within the same session.
     *
     * @param currentIdx  index of the memory just ingested
     * @param previousIdx index of the memory ingested immediately before
     * @param sessionId   session identifier
     */
    public void link(int currentIdx, int previousIdx, int sessionId) {
        int cap = capacity();
        if (currentIdx < 0 || currentIdx >= cap) return;
        if (previousIdx < 0 || previousIdx >= cap) return;
        if (currentIdx == previousIdx) return;

        var wal = backing.getWal();
        if (wal != null && !backing.isBypassWal()) {
            wal.appendChainLink(backing.id().toString(), currentIdx, previousIdx, sessionId);
        }

        MemorySegment seg = segment();
        long currentOffset = dataOffset() + (long) currentIdx * NODE_BYTES;
        long previousOffset = dataOffset() + (long) previousIdx * NODE_BYTES;

        // currentIdx.prev = previousIdx
        seg.set(ValueLayout.JAVA_INT, currentOffset + OFF_PREV, previousIdx);
        seg.set(ValueLayout.JAVA_INT, currentOffset + OFF_SESSION, sessionId);
        seg.set(ValueLayout.JAVA_INT, currentOffset + OFF_EPOCH_SEC,
                (int) (System.currentTimeMillis() / 1000));

        // previousIdx.next = currentIdx
        seg.set(ValueLayout.JAVA_INT, previousOffset + OFF_NEXT, currentIdx);
        
        if (seg.get(ValueLayout.JAVA_INT, previousOffset + OFF_EPOCH_SEC) == 0) {
            seg.set(ValueLayout.JAVA_INT, previousOffset + OFF_EPOCH_SEC,
                    (int) (System.currentTimeMillis() / 1000));
        }
    }

    /**
     * Follows the chain forward from a starting memory.
     *
     * @param startIdx the starting memory index
     * @param maxHops  maximum number of hops to follow
     * @return array of memory indices in temporal order (excludes startIdx)
     */
    public int[] followForward(int startIdx, int maxHops) {
        int cap = capacity();
        if (startIdx < 0 || startIdx >= cap) return new int[0];
        int[] chain = new int[maxHops];
        int count = 0;
        int current = startIdx;
        MemorySegment seg = segment();
        for (int hop = 0; hop < maxHops; hop++) {
            long offset = dataOffset() + (long) current * NODE_BYTES;
            int next = seg.get(ValueLayout.JAVA_INT, offset + OFF_NEXT);
            if (next == NO_LINK || next < 0 || next >= cap) break;
            chain[count++] = next;
            current = next;
        }
        return count == maxHops ? chain : java.util.Arrays.copyOf(chain, count);
    }

    /**
     * Follows the chain backward from a starting memory.
     *
     * @param startIdx the starting memory index
     * @param maxHops  maximum number of hops to follow
     * @return array of memory indices in reverse temporal order (excludes startIdx)
     */
    public int[] followBackward(int startIdx, int maxHops) {
        int cap = capacity();
        if (startIdx < 0 || startIdx >= cap) return new int[0];
        int[] chain = new int[maxHops];
        int count = 0;
        int current = startIdx;
        MemorySegment seg = segment();
        for (int hop = 0; hop < maxHops; hop++) {
            long offset = dataOffset() + (long) current * NODE_BYTES;
            int prev = seg.get(ValueLayout.JAVA_INT, offset + OFF_PREV);
            if (prev == NO_LINK || prev < 0 || prev >= cap) break;
            chain[count++] = prev;
            current = prev;
        }
        return count == maxHops ? chain : java.util.Arrays.copyOf(chain, count);
    }

    /**
     * Returns the session ID for a memory.
     */
    public int sessionOf(int idx) {
        if (idx < 0 || idx >= capacity()) return 0;
        return segment().get(ValueLayout.JAVA_INT, dataOffset() + (long) idx * NODE_BYTES + OFF_SESSION);
    }

    /**
     * Returns whether a memory has any temporal links.
     */
    public boolean isLinked(int idx) {
        if (idx < 0 || idx >= capacity()) return false;
        long offset = dataOffset() + (long) idx * NODE_BYTES;
        MemorySegment seg = segment();
        int prev = seg.get(ValueLayout.JAVA_INT, offset + OFF_PREV);
        int next = seg.get(ValueLayout.JAVA_INT, offset + OFF_NEXT);
        return prev != NO_LINK || next != NO_LINK;
    }

    /**
     * Returns the epoch-second timestamp for a memory node.
     *
     * @param idx the memory index
     * @return epoch seconds (0 if unlinked)
     */
    public int epochSecOf(int idx) {
        if (idx < 0 || idx >= capacity()) return 0;
        return segment().get(ValueLayout.JAVA_INT, dataOffset() + (long) idx * NODE_BYTES + OFF_EPOCH_SEC);
    }

    /**
     * Prunes temporal chain nodes older than the given cutoff.
     *
     * @param cutoffEpochMs cutoff timestamp in milliseconds
     * @return number of nodes pruned
     */
    public int pruneOlderThan(long cutoffEpochMs) {
        int cutoffEpochSec = (int) (cutoffEpochMs / 1000);
        int pruned = 0;
        int cap = capacity();
        MemorySegment seg = segment();

        for (int i = 0; i < cap; i++) {
            long offset = dataOffset() + (long) i * NODE_BYTES;
            int prev = seg.get(ValueLayout.JAVA_INT, offset + OFF_PREV);
            int next = seg.get(ValueLayout.JAVA_INT, offset + OFF_NEXT);

            if (prev == NO_LINK && next == NO_LINK) continue;

            int epochSec = seg.get(ValueLayout.JAVA_INT, offset + OFF_EPOCH_SEC);
            if (epochSec == 0) continue;
            if (epochSec >= cutoffEpochSec) continue;

            if (prev >= 0 && prev < cap) {
                long prevOffset = dataOffset() + (long) prev * NODE_BYTES;
                seg.set(ValueLayout.JAVA_INT, prevOffset + OFF_NEXT, next);
            }
            if (next >= 0 && next < cap) {
                long nextOffset = dataOffset() + (long) next * NODE_BYTES;
                seg.set(ValueLayout.JAVA_INT, nextOffset + OFF_PREV, prev);
            }

            seg.set(ValueLayout.JAVA_INT, offset + OFF_PREV, NO_LINK);
            seg.set(ValueLayout.JAVA_INT, offset + OFF_NEXT, NO_LINK);
            seg.set(ValueLayout.JAVA_INT, offset + OFF_SESSION, 0);
            seg.set(ValueLayout.JAVA_INT, offset + OFF_EPOCH_SEC, 0);
            pruned++;
        }

        if (pruned > 0) {
            log.info("TemporalChain pruned {} nodes older than {}s", pruned, cutoffEpochSec);
        }
        return pruned;
    }

    @FunctionalInterface
    public interface ImportanceProvider {
        float importance(int memoryIndex);
    }

    /**
     * Prunes low-importance temporal chain entries older than the cutoff.
     *
     * @param cutoffEpochMs       cutoff timestamp in milliseconds
     * @param importanceThreshold importance below this value is prunable
     * @param provider            importance score provider for memory indices
     * @return number of nodes pruned
     */
    public int pruneByImportance(long cutoffEpochMs, float importanceThreshold,
                                  ImportanceProvider provider) {
        if (provider == null) return 0;
        int cutoffEpochSec = (int) (cutoffEpochMs / 1000);
        int pruned = 0;
        int cap = capacity();
        MemorySegment seg = segment();

        for (int i = 0; i < cap; i++) {
            long offset = dataOffset() + (long) i * NODE_BYTES;
            int prev = seg.get(ValueLayout.JAVA_INT, offset + OFF_PREV);
            int next = seg.get(ValueLayout.JAVA_INT, offset + OFF_NEXT);

            if (prev == NO_LINK && next == NO_LINK) continue;

            int epochSec = seg.get(ValueLayout.JAVA_INT, offset + OFF_EPOCH_SEC);
            if (epochSec == 0) continue;
            if (epochSec >= cutoffEpochSec) continue;

            float importance = provider.importance(i);
            if (importance >= importanceThreshold) continue;

            if (prev >= 0 && prev < cap) {
                long prevOffset = dataOffset() + (long) prev * NODE_BYTES;
                seg.set(ValueLayout.JAVA_INT, prevOffset + OFF_NEXT, next);
            }
            if (next >= 0 && next < cap) {
                long nextOffset = dataOffset() + (long) next * NODE_BYTES;
                seg.set(ValueLayout.JAVA_INT, nextOffset + OFF_PREV, prev);
            }

            seg.set(ValueLayout.JAVA_INT, offset + OFF_PREV, NO_LINK);
            seg.set(ValueLayout.JAVA_INT, offset + OFF_NEXT, NO_LINK);
            seg.set(ValueLayout.JAVA_INT, offset + OFF_SESSION, 0);
            seg.set(ValueLayout.JAVA_INT, offset + OFF_EPOCH_SEC, 0);
            pruned++;
        }

        if (pruned > 0) {
            log.info("TemporalChain importance-pruned {} low-importance nodes (threshold={})",
                    pruned, importanceThreshold);
        }
        return pruned;
    }

    /**
     * Saves the chain to a binary file.
     *
     * @param filePath path to write
     */
    public void save(Path filePath) {
        if (backing.isPersistent()) {
            backing.flush();
            return;
        }

        Path parent = filePath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new SpectorGraphPersistenceException("TemporalChain", parent, e);
            }
        }

        try (FileChannel ch = FileChannel.open(filePath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.READ, StandardOpenOption.TRUNCATE_EXISTING)) {

            try (Arena tempArena = Arena.ofConfined()) {
                long totalBytes = MemoryHeader.HEADER_BYTES + (long) capacity() * NODE_BYTES;
                ch.position(totalBytes - 1);
                ch.write(ByteBuffer.wrap(new byte[]{0}));
                MemorySegment tempSeg = ch.map(FileChannel.MapMode.READ_WRITE, 0, totalBytes, tempArena);

                MemoryHeader.write(tempSeg, 0, layout().schemaVersion(), shape(),
                        0x00, // volatile
                        capacity(), size(), layout().recordStride(), layout().layoutId(),
                        System.currentTimeMillis(), System.currentTimeMillis());

                MemorySegment.copy(backing.segment(), 0, tempSeg, MemoryHeader.HEADER_BYTES, (long) capacity() * NODE_BYTES);
                tempSeg.force();
            }
            log.info("TemporalChain saved (heap→file): capacity={} → {}", capacity(), filePath);

        } catch (IOException e) {
            throw new SpectorGraphPersistenceException("TemporalChain", filePath, e);
        }
    }

    public static TemporalChain load(Path filePath, int defaultCapacity) {
        if (filePath == null || !Files.exists(filePath)) {
            log.info("TemporalChain file not found, creating fresh: {}", filePath);
            return new TemporalChain(defaultCapacity);
        }

        try {
            return new TemporalChain(filePath, defaultCapacity);
        } catch (Exception e) {
            log.error("Failed to mmap TemporalChain from {}, creating fresh: {}",
                    filePath, e.getMessage());
            return new TemporalChain(defaultCapacity);
        }
    }

    /**
     * Resets all temporal links by re-initializing all nodes to NO_LINK.
     */
    public void reset() {
        int cap = capacity();
        MemorySegment seg = segment();
        for (int i = 0; i < cap; i++) {
            long offset = dataOffset() + (long) i * NODE_BYTES;
            seg.set(ValueLayout.JAVA_INT, offset + OFF_PREV, NO_LINK);
            seg.set(ValueLayout.JAVA_INT, offset + OFF_NEXT, NO_LINK);
            seg.set(ValueLayout.JAVA_INT, offset + OFF_SESSION, 0);
            seg.set(ValueLayout.JAVA_INT, offset + OFF_EPOCH_SEC, 0);
        }
        log.info("TemporalChain reset: capacity={}", capacity());
    }

    @Override
    public void close() {
        backing.close();
    }

    @Override
    public void bindWal(com.spectrayan.spector.memory.sync.MemoryWal wal) {
        backing.bindWal(wal);
    }

    @Override
    public void setBypassWal(boolean bypass) {
        backing.setBypassWal(bypass);
    }

    @Override
    public boolean isBypassWal() {
        return backing.isBypassWal();
    }

    @Override
    public com.spectrayan.spector.memory.sync.MemoryWal getWal() {
        return backing.getWal();
    }

    // ── Backing Kernel Memory class ──

    private static final class TemporalChainBacking extends AbstractMemory<TemporalLayout> {

        TemporalChainBacking(MemoryId id, TemporalLayout layout, int capacity, long segmentBytes) {
            super(id, layout, capacity, segmentBytes);
        }

        TemporalChainBacking(MemoryId id, TemporalLayout layout, int capacity, long segmentBytes, Path filePath) {
            super(id, layout, capacity, segmentBytes, filePath);
        }

        TemporalChainBacking(MemoryId id, TemporalLayout layout, int capacity,
                             Arena arena, MemorySegment segment, int count,
                             boolean persistent, Path filePath, FileChannel fileChannel) {
            super(id, layout, capacity, arena, segment, count, persistent, filePath, fileChannel);
        }

        @Override
        public MemoryShape shape() {
            return MemoryShape.CHAIN;
        }
    }
}
