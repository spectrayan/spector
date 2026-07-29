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

import com.spectrayan.spector.memory.error.SpectorGraphPersistenceException;
import com.spectrayan.spector.memory.graph.BridgeDetector;
import com.spectrayan.spector.memory.graph.EdgeImportance;
import com.spectrayan.spector.memory.graph.GraphHealthMetrics;
import com.spectrayan.spector.memory.hebbian.HebbianGraph.HebbianEdge;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.layout.HebbianLayout;
import com.spectrayan.spector.memory.kernel.shape.GraphMemory;
import com.spectrayan.spector.memory.sync.MemoryWal;

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
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Compressed Sparse Row (CSR) layout for the Hebbian association graph, implementing
 * the Spector Memory Kernel {@link GraphMemory} specification.
 *
 * @see HebbianGraph
 */
public final class HebbianGraphMemory implements HebbianGraphBase, GraphMemory<HebbianLayout> {

    private static final Logger log = LoggerFactory.getLogger(HebbianGraphMemory.class);

    /** File magic: "HCSR" in ASCII. */
    private static final int FILE_MAGIC = 0x48435352;

    /** File format version (v3: CSR layout). */
    private static final int FILE_VERSION = 3;

    /** Legacy file magic for migration detection. */
    private static final int LEGACY_MAGIC = 0x48475048; // "HGPH"

    /** File header: 6 × 4B = 24 bytes. */
    private static final int FILE_HEADER_BYTES = 24;

    /** Bytes per edge (same as V2 for compatibility). */
    static final int EDGE_BYTES = 12;
    private static final int EDGE_OFF_NEIGHBOR = 0;
    private static final int EDGE_OFF_WEIGHT = 4;
    private static final int EDGE_OFF_LAST_CYCLE = 8;
    private static final int EDGE_OFF_BRIDGE_SCORE = 10;
    private static final int EDGE_OFF_EDGE_FLAGS = 11;

    /**
     * Minimum bridge score to protect an edge from eviction during decay.
     */
    static final int BRIDGE_PROTECTION_THRESHOLD = 224;

    /** Maximum degree per node (prevents graph explosion). */
    private final int maxDegree;

    /** Edge importance scorer. */
    private final EdgeImportance edgeImportance;

    /** Current reflection cycle. */
    private int currentCycle;

    /** Maximum number of nodes. */
    private final int capacity;

    /** Total edges stored in CSR. */
    private int totalEdgeCount;

    /** Maximum edge slots allocated. */
    private final int edgeCapacity;

    // ── Off-heap segments ──

    private final Arena arena;

    /**
     * Offset segment: 4B × (capacity + 1).
     */
    private final MemorySegment offsets;

    /**
     * Edge segment: 12B × edgeCapacity.
     */
    private final MemorySegment edges;

    // ── Overflow for edge insertion between compaction cycles ──

    @SuppressWarnings("unchecked")
    private List<int[]>[] overflow; // int[] = {neighbor, Float.floatToRawIntBits(weight)}

    private int overflowEdgeCount;

    // ── Thread safety & session ──

    private final ReentrantLock graphLock = new ReentrantLock();
    private volatile long lastActivityMs = System.currentTimeMillis();
    private volatile long sessionBoundaryMs = 30 * 60 * 1000L;
    private volatile HebbianGraph.DecayModulator decayModulator;
    private final MemoryId memoryId;
    private MemoryWal wal;
    private boolean bypassWal = false;

    // ══════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ══════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    public HebbianGraphMemory(int capacity, int edgeCapacity, int maxDegree,
                               EdgeImportance edgeImportance) {
        this.capacity = capacity;
        this.edgeCapacity = edgeCapacity;
        this.maxDegree = maxDegree;
        this.edgeImportance = edgeImportance;
        this.currentCycle = 0;
        this.totalEdgeCount = 0;
        this.overflowEdgeCount = 0;
        this.arena = Arena.ofShared();

        long offsetBytes = (long) (capacity + 1) * Integer.BYTES;
        this.offsets = arena.allocate(offsetBytes);
        offsets.fill((byte) 0);

        long edgeBytes = (long) edgeCapacity * EDGE_BYTES;
        this.edges = arena.allocate(edgeBytes);
        edges.fill((byte) 0);

        this.overflow = new List[capacity];
        this.memoryId = MemoryId.of("graph", "hebbian-csr");

        long totalKB = (offsetBytes + edgeBytes) / 1024;
        log.info("HebbianGraphMemory initialized (heap): capacity={}, edgeCap={}, maxDegree={}, memory={}KB",
                capacity, edgeCapacity, maxDegree, totalKB);
    }

    public HebbianGraphMemory(int capacity) {
        this(capacity, capacity * 2, HebbianGraph.DEFAULT_MAX_DEGREE, EdgeImportance.DEFAULT);
    }

    // ══════════════════════════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════════════════════════

    @Override
    public int capacity() { return capacity; }

    public int currentCycle() { return currentCycle; }

    @Override
    public void strengthen(int nodeA, int nodeB, float weightDelta) {
        graphLock.lock();
        try {
            if (nodeA < 0 || nodeA >= capacity || nodeB < 0 || nodeB >= capacity) return;
            if (nodeA == nodeB) return;

            if (wal != null && !bypassWal) {
                ByteBuffer buf = ByteBuffer.allocate(4);
                buf.putFloat(weightDelta);
                wal.appendAdjAddEdge(memoryId.toString(), nodeA, nodeB, buf.array());
            }

            addOrUpdateEdge(nodeA, nodeB, weightDelta);
            addOrUpdateEdge(nodeB, nodeA, weightDelta);
            lastActivityMs = System.currentTimeMillis();
        } finally {
            graphLock.unlock();
        }
    }

    @Override
    public List<HebbianEdge> neighbors(int node) {
        if (node < 0 || node >= capacity) return List.of();

        List<HebbianEdge> result = new ArrayList<>();

        int start = getOffset(node);
        int end = getOffset(node + 1);
        for (int i = start; i < end; i++) {
            long edgeOff = (long) i * EDGE_BYTES;
            int neighbor = edges.get(ValueLayout.JAVA_INT, edgeOff + EDGE_OFF_NEIGHBOR);
            float weight = edges.get(ValueLayout.JAVA_FLOAT, edgeOff + EDGE_OFF_WEIGHT);
            int bridge = Byte.toUnsignedInt(edges.get(ValueLayout.JAVA_BYTE, edgeOff + EDGE_OFF_BRIDGE_SCORE));
            if (weight > 0) {
                result.add(new HebbianEdge(neighbor, weight, bridge));
            }
        }

        List<int[]> ov = overflow[node];
        if (ov != null) {
            for (int[] entry : ov) {
                float weight = Float.intBitsToFloat(entry[1]);
                if (weight > 0) {
                    result.add(new HebbianEdge(entry[0], weight, 0));
                }
            }
        }

        result.sort((a, b) -> Float.compare(b.weight(), a.weight()));
        return result;
    }

    @Override
    public int degree(int node) {
        if (node < 0 || node >= capacity) return 0;
        int csrDegree = getOffset(node + 1) - getOffset(node);
        List<int[]> ov = overflow[node];
        return csrDegree + (ov != null ? ov.size() : 0);
    }

    @Override
    public int totalEdges() {
        return totalEdgeCount + overflowEdgeCount;
    }

    @Override
    public void setDecayModulator(HebbianGraph.DecayModulator modulator) {
        this.decayModulator = modulator;
    }

    @Override
    public void setSessionBoundary(long durationMs) {
        this.sessionBoundaryMs = durationMs;
    }

    @Override
    public boolean isNewSession() {
        return (System.currentTimeMillis() - lastActivityMs) > sessionBoundaryMs;
    }

    @Override
    public int decayEdges(float decayFactor) {
        return decayEdges(decayFactor, null);
    }

    @Override
    public int decayEdges(float decayFactor, GraphHealthMetrics metrics) {
        graphLock.lock();
        try {
            currentCycle++;
            HebbianGraph.DecayModulator mod = this.decayModulator;
            int removed = 0;
            int activeNodes = 0;

            int writePos = 0;
            int[] newOffsets = new int[capacity + 1];

            for (int node = 0; node < capacity; node++) {
                newOffsets[node] = writePos;

                List<EdgeData> allEdges = collectAllEdges(node);
                if (allEdges.isEmpty()) continue;

                float nodeDecay = decayFactor;
                boolean arousalModulated = false;
                if (mod != null) {
                    float modulation = mod.modulateDecay(node);
                    nodeDecay = decayFactor * modulation;
                    arousalModulated = modulation != 1.0f;
                }

                for (EdgeData e : allEdges) {
                    float newWeight = e.weight * nodeDecay;
                    int bridge = e.bridgeScore;

                    boolean bridgeProtected = false;
                    if (newWeight < 0.1f && bridge >= BRIDGE_PROTECTION_THRESHOLD) {
                        newWeight = 0.1f;
                        bridgeProtected = true;
                        if (metrics != null) metrics.recordHebbianBridgeProtection();
                    }

                    if (newWeight >= 0.1f) {
                        if (writePos < edgeCapacity) {
                            long edgeOff = (long) writePos * EDGE_BYTES;
                            edges.set(ValueLayout.JAVA_INT, edgeOff + EDGE_OFF_NEIGHBOR, e.neighbor);
                            edges.set(ValueLayout.JAVA_FLOAT, edgeOff + EDGE_OFF_WEIGHT, newWeight);
                            edges.set(ValueLayout.JAVA_SHORT, edgeOff + EDGE_OFF_LAST_CYCLE, (short) e.lastCycle);
                            edges.set(ValueLayout.JAVA_BYTE, edgeOff + EDGE_OFF_BRIDGE_SCORE, (byte) bridge);
                            edges.set(ValueLayout.JAVA_BYTE, edgeOff + EDGE_OFF_EDGE_FLAGS, (byte) e.flags);
                            writePos++;

                            if (metrics != null) {
                                int edgeAge = (currentCycle - e.lastCycle) & 0xFFFF;
                                metrics.recordHebbianSurvivor(bridge, edgeAge);
                                if (arousalModulated) metrics.recordHebbianArousalModulation();
                            }
                        }
                    } else {
                        removed++;
                        if (metrics != null) metrics.recordHebbianDecay();
                    }
                }

                if (writePos > newOffsets[node]) activeNodes++;
            }

            newOffsets[capacity] = writePos;
            totalEdgeCount = writePos;

            for (int i = 0; i <= capacity; i++) {
                offsets.set(ValueLayout.JAVA_INT, (long) i * Integer.BYTES, newOffsets[i]);
            }

            clearOverflow();

            updateBridgeScores();

            if (metrics != null) {
                int components = countConnectedComponents();
                metrics.setHebbianFragmentation(components, activeNodes);
            }

            if (removed > 0) {
                log.debug("HebbianGraphMemory decay: {} edges removed (factor={:.3f}), {} surviving, cycle={}",
                        removed, decayFactor, totalEdgeCount, currentCycle);
            }
            return removed;
        } finally {
            graphLock.unlock();
        }
    }

    @Override
    public List<HebbianEdge> activateNeighbors(int node, int depth) {
        if (node < 0 || node >= capacity) return List.of();
        List<HebbianEdge> activated = new ArrayList<>();
        boolean[] visited = new boolean[capacity];
        activateRecursive(node, depth, 1.0f, activated, visited);
        activated.sort((a, b) -> Float.compare(b.weight(), a.weight()));
        return activated;
    }

    @Override
    public int reset() {
        graphLock.lock();
        try {
            int edgesBefore = totalEdges();
            offsets.fill((byte) 0);
            edges.fill((byte) 0);
            clearOverflow();
            totalEdgeCount = 0;
            lastActivityMs = System.currentTimeMillis();
            log.info("HebbianGraphMemory reset: {} edges cleared, capacity={}", edgesBefore, capacity);
            return edgesBefore;
        } finally {
            graphLock.unlock();
        }
    }

    @Override
    public long memoryUsageBytes() {
        return offsets.byteSize() + edges.byteSize();
    }

    // ══════════════════════════════════════════════════════════════
    // KERNEL INTEGRATION
    // ══════════════════════════════════════════════════════════════

    @Override
    public MemoryId id() {
        return memoryId;
    }

    @Override
    public HebbianLayout layout() {
        return new HebbianLayout();
    }

    @Override
    public Arena arena() {
        return arena;
    }

    @Override
    public MemorySegment segment() {
        return edges;
    }

    @Override
    public int size() {
        return totalEdges();
    }

    @Override
    public int schemaVersion() {
        return 1;
    }

    @Override
    public MemoryShape shape() {
        return MemoryShape.GRAPH;
    }

    @Override
    public void flush() {
        try {
            if (edges != null) edges.force();
        } catch (UnsupportedOperationException ignored) {}
        try {
            if (offsets != null) offsets.force();
        } catch (UnsupportedOperationException ignored) {}
    }

    @Override
    public int addEdge(int fromNode, int toNode, MemorySegment edgeBytes) {
        strengthen(fromNode, toNode, 1.0f);
        return totalEdges();
    }

    @Override
    public void removeEdge(int edgeId) {
        // CSR decays and compacts edges
    }

    @Override
    public java.util.PrimitiveIterator.OfInt neighbours(int nodeId) {
        return neighbors(nodeId).stream().mapToInt(HebbianEdge::neighborIndex).iterator();
    }

    @Override
    public int edgeCount() {
        return totalEdges();
    }

    @Override
    public int nodeCount() {
        int activeNodes = 0;
        for (int i = 0; i < capacity; i++) {
            if (degree(i) > 0) {
                activeNodes++;
            }
        }
        return activeNodes;
    }

    public MemoryId memoryId() {
        return memoryId;
    }

    public MemoryShape kernelShape() {
        return MemoryShape.GRAPH;
    }

    @Override
    public void bindWal(MemoryWal wal) {
        this.wal = wal;
    }

    @Override
    public void setBypassWal(boolean bypass) {
        this.bypassWal = bypass;
    }

    @Override
    public MemoryWal getWal() {
        return this.wal;
    }

    @Override
    public void close() {
        log.info("HebbianGraphMemory closing (capacity={}, edges={})", capacity, totalEdgeCount);
        arena.close();
    }

    // ══════════════════════════════════════════════════════════════
    // PERSISTENCE
    // ══════════════════════════════════════════════════════════════

    @Override
    public void save(Path filePath) {
        Path parent = filePath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new SpectorGraphPersistenceException("HebbianGraphMemory", parent, e);
            }
        }

        compactIfNeeded();

        try (FileChannel ch = FileChannel.open(filePath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            ByteBuffer header = ByteBuffer.allocate(FILE_HEADER_BYTES);
            header.putInt(FILE_MAGIC);
            header.putInt(FILE_VERSION);
            header.putInt(capacity);
            header.putInt(edgeCapacity);
            header.putInt(totalEdgeCount);
            header.putInt(currentCycle);
            header.flip();
            ch.write(header);

            writeSegmentToChannel(offsets, (long) (capacity + 1) * Integer.BYTES, ch);

            writeSegmentToChannel(edges, (long) totalEdgeCount * EDGE_BYTES, ch);

            ch.force(true);
            log.info("HebbianGraphMemory saved: capacity={}, edges={}, file={}",
                    capacity, totalEdgeCount, filePath);

        } catch (IOException e) {
            throw new SpectorGraphPersistenceException("HebbianGraphMemory", filePath, e);
        }
    }

    public static HebbianGraphMemory load(Path filePath, int defaultCapacity) {
        return load(filePath, defaultCapacity, HebbianGraph.DEFAULT_MAX_DEGREE, EdgeImportance.DEFAULT);
    }

    public static HebbianGraphMemory load(Path filePath, int defaultCapacity,
                                          int maxDegree, EdgeImportance edgeImportance) {
        if (filePath == null || !Files.exists(filePath)) {
            log.info("HebbianGraphMemory file not found, creating fresh: {}", filePath);
            return new HebbianGraphMemory(defaultCapacity, defaultCapacity * 2, maxDegree, edgeImportance);
        }

        try {
            int magic;
            try (FileChannel ch = FileChannel.open(filePath, StandardOpenOption.READ)) {
                ByteBuffer buf = ByteBuffer.allocate(4);
                ch.read(buf);
                buf.flip();
                magic = buf.getInt();
            }

            if (magic == FILE_MAGIC) {
                return loadV3(filePath, maxDegree, edgeImportance);
            } else if (magic == LEGACY_MAGIC) {
                log.info("Detected legacy V2 HebbianGraph file, migrating to CSR: {}", filePath);
                return migrateFromV2(filePath, maxDegree, edgeImportance);
            } else {
                log.warn("Unknown HebbianGraph file magic: 0x{}, creating fresh", Integer.toHexString(magic));
                return new HebbianGraphMemory(defaultCapacity, defaultCapacity * 2, maxDegree, edgeImportance);
            }
        } catch (Exception e) {
            log.error("Failed to load HebbianGraphMemory from {}, creating fresh: {}",
                    filePath, e.getMessage());
            return new HebbianGraphMemory(defaultCapacity, defaultCapacity * 2, maxDegree, edgeImportance);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // INTERNAL: Edge Mutation
    // ══════════════════════════════════════════════════════════════

    private void addOrUpdateEdge(int from, int to, float weightDelta) {
        int start = getOffset(from);
        int end = getOffset(from + 1);
        for (int i = start; i < end; i++) {
            long edgeOff = (long) i * EDGE_BYTES;
            int neighbor = edges.get(ValueLayout.JAVA_INT, edgeOff + EDGE_OFF_NEIGHBOR);
            if (neighbor == to) {
                float weight = edges.get(ValueLayout.JAVA_FLOAT, edgeOff + EDGE_OFF_WEIGHT);
                edges.set(ValueLayout.JAVA_FLOAT, edgeOff + EDGE_OFF_WEIGHT, weight + weightDelta);
                edges.set(ValueLayout.JAVA_SHORT, edgeOff + EDGE_OFF_LAST_CYCLE, (short) currentCycle);
                return;
            }
        }

        List<int[]> ov = overflow[from];
        if (ov != null) {
            for (int[] entry : ov) {
                if (entry[0] == to) {
                    float weight = Float.intBitsToFloat(entry[1]);
                    entry[1] = Float.floatToRawIntBits(weight + weightDelta);
                    return;
                }
            }
        }

        int currentDegree = degree(from);
        if (currentDegree >= maxDegree) {
            replaceLowestImportance(from, to, weightDelta);
            return;
        }

        if (ov == null) {
            ov = new ArrayList<>(4);
            overflow[from] = ov;
        }
        ov.add(new int[]{to, Float.floatToRawIntBits(weightDelta)});
        overflowEdgeCount++;
    }

    private void replaceLowestImportance(int node, int newNeighbor, float newWeight) {
        float minScore = Float.MAX_VALUE;
        int minCsrIdx = -1;
        int minOvIdx = -1;

        int start = getOffset(node);
        int end = getOffset(node + 1);
        for (int i = start; i < end; i++) {
            long edgeOff = (long) i * EDGE_BYTES;
            float weight = edges.get(ValueLayout.JAVA_FLOAT, edgeOff + EDGE_OFF_WEIGHT);
            short lastCycle = edges.get(ValueLayout.JAVA_SHORT, edgeOff + EDGE_OFF_LAST_CYCLE);
            byte bridge = edges.get(ValueLayout.JAVA_BYTE, edgeOff + EDGE_OFF_BRIDGE_SCORE);
            float score = edgeImportance.scoreStructural(
                    weight, currentCycle, Short.toUnsignedInt(lastCycle),
                    Byte.toUnsignedInt(bridge), 0);
            if (score < minScore) {
                minScore = score;
                minCsrIdx = i;
                minOvIdx = -1;
            }
        }

        List<int[]> ov = overflow[node];
        if (ov != null) {
            for (int i = 0; i < ov.size(); i++) {
                float weight = Float.intBitsToFloat(ov.get(i)[1]);
                float score = edgeImportance.scoreStructural(weight, currentCycle, currentCycle, 0, 0);
                if (score < minScore) {
                    minScore = score;
                    minCsrIdx = -1;
                    minOvIdx = i;
                }
            }
        }

        float newScore = edgeImportance.scoreStructural(newWeight, currentCycle, currentCycle, 0, 0);
        if (newScore <= minScore) return;

        if (minCsrIdx >= 0) {
            long edgeOff = (long) minCsrIdx * EDGE_BYTES;
            edges.set(ValueLayout.JAVA_INT, edgeOff + EDGE_OFF_NEIGHBOR, newNeighbor);
            edges.set(ValueLayout.JAVA_FLOAT, edgeOff + EDGE_OFF_WEIGHT, newWeight);
            edges.set(ValueLayout.JAVA_SHORT, edgeOff + EDGE_OFF_LAST_CYCLE, (short) currentCycle);
            edges.set(ValueLayout.JAVA_BYTE, edgeOff + EDGE_OFF_BRIDGE_SCORE, (byte) 0);
            edges.set(ValueLayout.JAVA_BYTE, edgeOff + EDGE_OFF_EDGE_FLAGS, (byte) 0);
        } else if (minOvIdx >= 0) {
            ov.set(minOvIdx, new int[]{newNeighbor, Float.floatToRawIntBits(newWeight)});
        }
    }

    private int getOffset(int node) {
        return offsets.get(ValueLayout.JAVA_INT, (long) node * Integer.BYTES);
    }

    private record EdgeData(int neighbor, float weight, int lastCycle, int bridgeScore, int flags) {}

    private List<EdgeData> collectAllEdges(int node) {
        List<EdgeData> all = new ArrayList<>();

        int start = getOffset(node);
        int end = getOffset(node + 1);
        for (int i = start; i < end; i++) {
            long edgeOff = (long) i * EDGE_BYTES;
            int neighbor = edges.get(ValueLayout.JAVA_INT, edgeOff + EDGE_OFF_NEIGHBOR);
            float weight = edges.get(ValueLayout.JAVA_FLOAT, edgeOff + EDGE_OFF_WEIGHT);
            int lastCycle = Short.toUnsignedInt(edges.get(ValueLayout.JAVA_SHORT, edgeOff + EDGE_OFF_LAST_CYCLE));
            int bridge = Byte.toUnsignedInt(edges.get(ValueLayout.JAVA_BYTE, edgeOff + EDGE_OFF_BRIDGE_SCORE));
            int flags = Byte.toUnsignedInt(edges.get(ValueLayout.JAVA_BYTE, edgeOff + EDGE_OFF_EDGE_FLAGS));
            if (weight > 0) {
                all.add(new EdgeData(neighbor, weight, lastCycle, bridge, flags));
            }
        }

        List<int[]> ov = overflow[node];
        if (ov != null) {
            for (int[] entry : ov) {
                float weight = Float.intBitsToFloat(entry[1]);
                if (weight > 0) {
                    all.add(new EdgeData(entry[0], weight, currentCycle, 0, 0));
                }
            }
        }

        return all;
    }

    @SuppressWarnings("unchecked")
    private void clearOverflow() {
        overflow = new List[capacity];
        overflowEdgeCount = 0;
    }

    private void compactIfNeeded() {
        if (overflowEdgeCount == 0) return;
        graphLock.lock();
        try {
            int writePos = 0;
            int[] newOffsets = new int[capacity + 1];

            for (int node = 0; node < capacity; node++) {
                newOffsets[node] = writePos;
                List<EdgeData> all = collectAllEdges(node);
                for (EdgeData e : all) {
                    if (writePos < edgeCapacity) {
                        long edgeOff = (long) writePos * EDGE_BYTES;
                        edges.set(ValueLayout.JAVA_INT, edgeOff + EDGE_OFF_NEIGHBOR, e.neighbor);
                        edges.set(ValueLayout.JAVA_FLOAT, edgeOff + EDGE_OFF_WEIGHT, e.weight);
                        edges.set(ValueLayout.JAVA_SHORT, edgeOff + EDGE_OFF_LAST_CYCLE, (short) e.lastCycle);
                        edges.set(ValueLayout.JAVA_BYTE, edgeOff + EDGE_OFF_BRIDGE_SCORE, (byte) e.bridgeScore);
                        edges.set(ValueLayout.JAVA_BYTE, edgeOff + EDGE_OFF_EDGE_FLAGS, (byte) e.flags);
                        writePos++;
                    }
                }
            }

            newOffsets[capacity] = writePos;
            totalEdgeCount = writePos;

            for (int i = 0; i <= capacity; i++) {
                offsets.set(ValueLayout.JAVA_INT, (long) i * Integer.BYTES, newOffsets[i]);
            }

            clearOverflow();
        } finally {
            graphLock.unlock();
        }
    }

    private void updateBridgeScores() {
        int[][] adjacency = new int[capacity][];
        for (int node = 0; node < capacity; node++) {
            int start = getOffset(node);
            int end = getOffset(node + 1);
            int deg = end - start;
            if (deg > 0) {
                int[] neighbors = new int[deg];
                for (int i = 0; i < deg; i++) {
                    neighbors[i] = edges.get(ValueLayout.JAVA_INT,
                            (long) (start + i) * EDGE_BYTES + EDGE_OFF_NEIGHBOR);
                }
                adjacency[node] = neighbors;
            }
        }

        int[][] scores = BridgeDetector.computeBridgeScoresSpanningTree(
                adjacency, capacity,
                BridgeDetector.DEFAULT_SAMPLE_COUNT,
                BridgeDetector.DEFAULT_BUDGET_MS);

        if (scores != null) {
            for (int node = 0; node < capacity; node++) {
                int start = getOffset(node);
                int end = getOffset(node + 1);
                for (int i = 0; i < end - start; i++) {
                    long edgeOff = (long) (start + i) * EDGE_BYTES;
                    int score = scores[node] != null && i < scores[node].length
                            ? scores[node][i] : 128;
                    edges.set(ValueLayout.JAVA_BYTE, edgeOff + EDGE_OFF_BRIDGE_SCORE, (byte) score);
                }
            }
        } else {
            updateBridgeScoresHeuristic(adjacency);
        }
    }

    private void updateBridgeScoresHeuristic(int[][] adjacency) {
        for (int node = 0; node < capacity; node++) {
            int start = getOffset(node);
            int end = getOffset(node + 1);
            int deg = end - start;
            if (deg == 0) continue;

            for (int i = 0; i < deg; i++) {
                long edgeOff = (long) (start + i) * EDGE_BYTES;
                int neighbor = edges.get(ValueLayout.JAVA_INT, edgeOff + EDGE_OFF_NEIGHBOR);

                int shared = 0;
                int neighborDegree = 0;
                if (neighbor >= 0 && neighbor < capacity && adjacency[neighbor] != null) {
                    neighborDegree = adjacency[neighbor].length;
                    shared = BridgeDetector.countSharedNeighbors(
                            adjacency[node], deg, adjacency[neighbor], neighborDegree);
                }

                int bridgeScore = BridgeDetector.computeBridgeScore(shared, deg, neighborDegree);
                edges.set(ValueLayout.JAVA_BYTE, edgeOff + EDGE_OFF_BRIDGE_SCORE, (byte) bridgeScore);
            }
        }
    }

    private void activateRecursive(int node, int depth, float attenuation,
                                    List<HebbianEdge> activated, boolean[] visited) {
        if (depth <= 0 || visited[node]) return;
        visited[node] = true;

        for (HebbianEdge edge : neighbors(node)) {
            float compoundWeight = edge.weight() * attenuation;
            if (compoundWeight > 0.01f && !visited[edge.neighborIndex()]) {
                activated.add(new HebbianEdge(edge.neighborIndex(), compoundWeight));
                activateRecursive(edge.neighborIndex(), depth - 1, compoundWeight * 0.5f,
                        activated, visited);
            }
        }
    }

    private int countConnectedComponents() {
        int[] parent = new int[capacity];
        int[] rank = new int[capacity];
        for (int i = 0; i < capacity; i++) parent[i] = i;

        for (int node = 0; node < capacity; node++) {
            int start = getOffset(node);
            int end = getOffset(node + 1);
            for (int i = start; i < end; i++) {
                int neighbor = edges.get(ValueLayout.JAVA_INT, (long) i * EDGE_BYTES + EDGE_OFF_NEIGHBOR);
                if (neighbor >= 0 && neighbor < capacity) {
                    union(parent, rank, node, neighbor);
                }
            }
        }

        boolean[] seen = new boolean[capacity];
        int components = 0;
        for (int node = 0; node < capacity; node++) {
            if (getOffset(node + 1) - getOffset(node) > 0) {
                int root = find(parent, node);
                if (!seen[root]) {
                    seen[root] = true;
                    components++;
                }
            }
        }
        return components;
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    private static void union(int[] parent, int[] rank, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra == rb) return;
        if (rank[ra] < rank[rb]) { int t = ra; ra = rb; rb = t; }
        parent[rb] = ra;
        if (rank[ra] == rank[rb]) rank[ra]++;
    }

    private static HebbianGraphMemory loadV3(Path filePath, int maxDegree,
                                             EdgeImportance edgeImportance) throws IOException {
        try (FileChannel ch = FileChannel.open(filePath, StandardOpenOption.READ)) {
            ByteBuffer header = ByteBuffer.allocate(FILE_HEADER_BYTES);
            ch.read(header);
            header.flip();

            int magic = header.getInt();
            int version = header.getInt();
            int capacity = header.getInt();
            int edgeCap = header.getInt();
            int totalEdges = header.getInt();
            int cycle = header.getInt();

            if (magic != FILE_MAGIC || version != FILE_VERSION) {
                throw new IOException("Invalid CSR file: magic=0x" + Integer.toHexString(magic)
                        + " version=" + version);
            }

            HebbianGraphMemory graph = new HebbianGraphMemory(capacity, edgeCap, maxDegree, edgeImportance);
            graph.currentCycle = cycle;
            graph.totalEdgeCount = totalEdges;

            long offsetBytes = (long) (capacity + 1) * Integer.BYTES;
            readIntoSegment(ch, graph.offsets, offsetBytes);

            long edgeBytes = (long) totalEdges * EDGE_BYTES;
            readIntoSegment(ch, graph.edges, edgeBytes);

            log.info("HebbianGraphMemory loaded V3: capacity={}, edges={}, cycle={}, file={}",
                    capacity, totalEdges, cycle, filePath);

            return graph;
        }
    }

    private static HebbianGraphMemory migrateFromV2(Path filePath, int maxDegree,
                                                    EdgeImportance edgeImportance) throws IOException {
        log.info("HebbianGraph V2 → V3 CSR Migration starting: {}", filePath);

        HebbianGraph legacy = HebbianGraph.load(filePath, 1024, maxDegree, edgeImportance);
        int legacyCapacity = legacy.capacity();

        int totalEdges = legacy.totalEdges();

        int edgeCap = Math.max(totalEdges * 2, legacyCapacity * 2);
        HebbianGraphMemory csr = new HebbianGraphMemory(legacyCapacity, edgeCap, maxDegree, edgeImportance);

        int migratedEdges = 0;
        int writePos = 0;
        int[] newOffsets = new int[legacyCapacity + 1];

        for (int node = 0; node < legacyCapacity; node++) {
            newOffsets[node] = writePos;
            List<HebbianEdge> neighbors = legacy.neighbors(node);
            for (HebbianEdge edge : neighbors) {
                if (writePos < edgeCap) {
                    long edgeOff = (long) writePos * EDGE_BYTES;
                    csr.edges.set(ValueLayout.JAVA_INT, edgeOff + EDGE_OFF_NEIGHBOR, edge.neighborIndex());
                    csr.edges.set(ValueLayout.JAVA_FLOAT, edgeOff + EDGE_OFF_WEIGHT, edge.weight());
                    csr.edges.set(ValueLayout.JAVA_SHORT, edgeOff + EDGE_OFF_LAST_CYCLE, (short) 0);
                    csr.edges.set(ValueLayout.JAVA_BYTE, edgeOff + EDGE_OFF_BRIDGE_SCORE, (byte) edge.bridgeScore());
                    csr.edges.set(ValueLayout.JAVA_BYTE, edgeOff + EDGE_OFF_EDGE_FLAGS, (byte) 0);
                    writePos++;
                    migratedEdges++;
                }
            }
        }

        newOffsets[legacyCapacity] = writePos;
        csr.totalEdgeCount = writePos;

        for (int i = 0; i <= legacyCapacity; i++) {
            csr.offsets.set(ValueLayout.JAVA_INT, (long) i * Integer.BYTES, newOffsets[i]);
        }

        legacy.close();

        Path backupPath = filePath.resolveSibling(filePath.getFileName() + ".v2.bak");
        Files.move(filePath, backupPath);

        csr.save(filePath);

        return csr;
    }

    private static void readIntoSegment(FileChannel ch, MemorySegment segment, long bytes) throws IOException {
        long read = 0;
        int chunkSize = 64 * 1024;
        while (read < bytes) {
            int toRead = (int) Math.min(chunkSize, bytes - read);
            ByteBuffer buf = ByteBuffer.allocate(toRead);
            int n = ch.read(buf);
            if (n <= 0) break;
            buf.flip();
            MemorySegment.copy(MemorySegment.ofBuffer(buf), 0, segment, read, n);
            read += n;
        }
    }

    private static void writeSegmentToChannel(MemorySegment segment, long bytes,
                                               FileChannel ch) throws IOException {
        long written = 0;
        int chunkSize = 64 * 1024;
        while (written < bytes) {
            int toWrite = (int) Math.min(chunkSize, bytes - written);
            ByteBuffer buf = segment.asSlice(written, toWrite).asByteBuffer().asReadOnlyBuffer();
            ch.write(buf);
            written += toWrite;
        }
    }
}
