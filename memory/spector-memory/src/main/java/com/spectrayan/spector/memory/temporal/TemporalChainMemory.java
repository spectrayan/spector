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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.spectrayan.spector.memory.error.SpectorGraphPersistenceException;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.MemoryHeader;
import com.spectrayan.spector.memory.kernel.AbstractMemory;
import com.spectrayan.spector.memory.kernel.shape.ChainMemory;
import com.spectrayan.spector.memory.kernel.layout.TemporalLayout;
import com.spectrayan.spector.memory.sync.MemoryWal;

/**
 * Off-heap temporal causal chain linking memories within a session,
 * implementing {@link ChainMemory<TemporalLayout>} directly.
 */
public final class TemporalChainMemory implements ChainMemory<TemporalLayout>, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TemporalChainMemory.class);

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
    public TemporalChainMemory(int capacity) {
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

        log.info("TemporalChainMemory initialized (heap): capacity={}, memory={}KB",
                capacity, dataBytes / 1024);
    }

    /**
     * Creates or opens a file-backed (mmap) temporal chain.
     *
     * @param filePath path to the chain file
     * @param capacity maximum number of nodes (used only for new files)
     */
    public TemporalChainMemory(Path filePath, int capacity) {
        Path parent = filePath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new SpectorGraphPersistenceException("TemporalChainMemory", parent, e);
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

            log.info("TemporalChainMemory initialized (mmap): capacity={}, file={}",
                    backing.capacity(), filePath.getFileName());

        } catch (IOException e) {
            throw new SpectorGraphPersistenceException("TemporalChainMemory", filePath, e);
        }
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

                    MemorySegment dataSlice = tempSegment.asSlice(MemoryHeader.HEADER_BYTES, dataSize);
                    MemorySegment.copy(MemorySegment.ofBuffer(dataBuf), 0, dataSlice, 0, dataSize);
                    tempSegment.force();
                }
            }
        }
    }

    public void bindWal(MemoryWal wal) {
        backing.bindWal(wal);
    }

    public void setBypassWal(boolean bypass) {
        backing.setBypassWal(bypass);
    }

    @Override
    public MemoryId id() {
        return backing.id();
    }

    @Override
    public TemporalLayout layout() {
        return backing.layout();
    }

    @Override
    public MemoryShape shape() {
        return MemoryShape.CHAIN;
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
    public int capacity() {
        return backing.capacity();
    }

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public int schemaVersion() {
        return backing.schemaVersion();
    }

    @Override
    public void flush() {
        backing.flush();
    }

    @Override
    public void link(int nodeId, int nextId) {
        linkNodes(nodeId, nextId, 0, (int) (System.currentTimeMillis() / 1000));
        MemoryWal wal = backing.getWal();
        if (wal != null && !backing.isBypassWal()) {
            wal.appendChainLink(backing.id().toString(), nodeId, nextId, 0);
        }
    }

    public void link(int nodeIdx, int prevIdx, int sessionId) {
        linkNodes(prevIdx, nodeIdx, sessionId, (int) (System.currentTimeMillis() / 1000));
        MemoryWal wal = backing.getWal();
        if (wal != null && !backing.isBypassWal()) {
            wal.appendChainLink(backing.id().toString(), nodeIdx, prevIdx, sessionId);
        }
    }

    @Override
    public int next(int nodeId) {
        return getNextIndex(nodeId);
    }

    @Override
    public int prev(int nodeId) {
        return getPrevIndex(nodeId);
    }

    @Override
    public int head() {
        for (int i = 0; i < backing.capacity(); i++) {
            if (isLinked(i) && getPrevIndex(i) == NO_LINK) {
                return i;
            }
        }
        return NO_LINK;
    }

    @Override
    public int tail() {
        for (int i = 0; i < backing.capacity(); i++) {
            if (isLinked(i) && getNextIndex(i) == NO_LINK) {
                return i;
            }
        }
        return NO_LINK;
    }

    @Override
    public int chainLength() {
        int count = 0;
        for (int i = 0; i < backing.capacity(); i++) {
            if (isLinked(i)) count++;
        }
        return count;
    }

    public boolean isLinked(int nodeIdx) {
        boundsCheck(nodeIdx);
        return getPrevIndex(nodeIdx) != NO_LINK || getNextIndex(nodeIdx) != NO_LINK;
    }

    public synchronized void linkNodes(int prevIdx, int nextIdx, int sessionId, int epochSec) {
        boundsCheck(prevIdx);
        boundsCheck(nextIdx);

        long prevOff = backing.dataOffset() + (long) prevIdx * NODE_BYTES;
        long nextOff = backing.dataOffset() + (long) nextIdx * NODE_BYTES;

        MemorySegment seg = backing.segment();
        seg.set(ValueLayout.JAVA_INT, prevOff + OFF_NEXT, nextIdx);
        if (sessionId > 0) {
            seg.set(ValueLayout.JAVA_INT, prevOff + OFF_SESSION, sessionId);
        }
        if (epochSec > 0) {
            seg.set(ValueLayout.JAVA_INT, prevOff + OFF_EPOCH_SEC, epochSec);
        }

        seg.set(ValueLayout.JAVA_INT, nextOff + OFF_PREV, prevIdx);
        if (sessionId > 0) {
            seg.set(ValueLayout.JAVA_INT, nextOff + OFF_SESSION, sessionId);
        }
        if (epochSec > 0) {
            seg.set(ValueLayout.JAVA_INT, nextOff + OFF_EPOCH_SEC, epochSec);
        }
    }

    public int getPrevIndex(int nodeIdx) {
        boundsCheck(nodeIdx);
        long off = backing.dataOffset() + (long) nodeIdx * NODE_BYTES;
        return backing.segment().get(ValueLayout.JAVA_INT, off + OFF_PREV);
    }

    public int getNextIndex(int nodeIdx) {
        boundsCheck(nodeIdx);
        long off = backing.dataOffset() + (long) nodeIdx * NODE_BYTES;
        return backing.segment().get(ValueLayout.JAVA_INT, off + OFF_NEXT);
    }

    public int getSessionId(int nodeIdx) {
        boundsCheck(nodeIdx);
        long off = backing.dataOffset() + (long) nodeIdx * NODE_BYTES;
        return backing.segment().get(ValueLayout.JAVA_INT, off + OFF_SESSION);
    }

    public int getEpochSec(int nodeIdx) {
        boundsCheck(nodeIdx);
        long off = backing.dataOffset() + (long) nodeIdx * NODE_BYTES;
        return backing.segment().get(ValueLayout.JAVA_INT, off + OFF_EPOCH_SEC);
    }

    public int[] followForward(int startIdx, int maxSteps) {
        List<Integer> list = new ArrayList<>();
        int curr = startIdx;
        int steps = 0;
        while (curr != NO_LINK && steps < maxSteps) {
            curr = getNextIndex(curr);
            if (curr != NO_LINK) {
                list.add(curr);
                steps++;
            }
        }
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    public int[] followBackward(int startIdx, int maxSteps) {
        List<Integer> list = new ArrayList<>();
        int curr = startIdx;
        int steps = 0;
        while (curr != NO_LINK && steps < maxSteps) {
            curr = getPrevIndex(curr);
            if (curr != NO_LINK) {
                list.add(curr);
                steps++;
            }
        }
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    public synchronized int pruneOlderThan(long epochSecCutoff) {
        long cutoffSec = epochSecCutoff > 10_000_000_000L ? epochSecCutoff / 1000 : epochSecCutoff;
        int count = 0;
        for (int i = 0; i < backing.capacity(); i++) {
            if (isLinked(i)) {
                int epochSec = getEpochSec(i);
                if (epochSec > 0 && epochSec < cutoffSec) {
                    unlink(i);
                    count++;
                }
            }
        }
        return count;
    }

    public synchronized int pruneByImportance(long epochSecCutoff, float importanceThreshold, Function<Integer, Float> importanceLookup) {
        if (importanceLookup == null) {
            return 0;
        }
        long cutoffSec = epochSecCutoff > 10_000_000_000L ? epochSecCutoff / 1000 : epochSecCutoff;
        int count = 0;
        for (int i = 0; i < backing.capacity(); i++) {
            if (isLinked(i)) {
                int epochSec = getEpochSec(i);
                if (epochSec > 0 && epochSec < cutoffSec) {
                    float imp = importanceLookup.apply(i);
                    if (imp < importanceThreshold) {
                        unlink(i);
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public synchronized void unlink(int nodeIdx) {
        boundsCheck(nodeIdx);
        int p = getPrevIndex(nodeIdx);
        int n = getNextIndex(nodeIdx);

        if (p != NO_LINK) {
            long pOff = backing.dataOffset() + (long) p * NODE_BYTES;
            backing.segment().set(ValueLayout.JAVA_INT, pOff + OFF_NEXT, n);
        }
        if (n != NO_LINK) {
            long nOff = backing.dataOffset() + (long) n * NODE_BYTES;
            backing.segment().set(ValueLayout.JAVA_INT, nOff + OFF_PREV, p);
        }

        long selfOff = backing.dataOffset() + (long) nodeIdx * NODE_BYTES;
        backing.segment().set(ValueLayout.JAVA_INT, selfOff + OFF_PREV, NO_LINK);
        backing.segment().set(ValueLayout.JAVA_INT, selfOff + OFF_NEXT, NO_LINK);
        backing.segment().set(ValueLayout.JAVA_INT, selfOff + OFF_SESSION, 0);
        backing.segment().set(ValueLayout.JAVA_INT, selfOff + OFF_EPOCH_SEC, 0);
    }

    public synchronized void save(Path targetPath) {
        flush();
        if (backing.isPersistent() && backing.filePath() != null && !backing.filePath().equals(targetPath)) {
            try {
                Files.createDirectories(targetPath.getParent());
                Files.copy(backing.filePath(), targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new SpectorGraphPersistenceException("TemporalChainMemory", targetPath, e);
            }
        }
    }

    private void boundsCheck(int nodeIdx) {
        if (nodeIdx < 0 || nodeIdx >= backing.capacity()) {
            throw new IndexOutOfBoundsException(
                    "TemporalChain node index out of bounds: " + nodeIdx + " (capacity=" + backing.capacity() + ")");
        }
    }

    @Override
    public void close() {
        backing.close();
    }

    public TemporalChainBacking backing() {
        return backing;
    }

    public static final class TemporalChainBacking extends AbstractMemory<TemporalLayout> {
        TemporalChainBacking(MemoryId id, TemporalLayout layout, int capacity, long dataBytes) {
            super(id, layout, capacity, dataBytes);
        }

        TemporalChainBacking(MemoryId id, TemporalLayout layout, int capacity, long dataBytes, Path filePath) {
            super(id, layout, capacity, dataBytes, filePath);
        }

        @Override
        public MemoryShape shape() {
            return MemoryShape.CHAIN;
        }
    }
}
