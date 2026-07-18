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
 * Compressed Sparse Row (CSR) layout for the Hebbian association graph.
 *
 * <h3>Motivation</h3>
 * <p>The legacy {@link HebbianGraph} uses a fixed-width layout (292B/node at MAX_DEGREE=24).
 * At observed average degree ~2.0, this wastes 91.7% of edge slots. CSR stores only
 * actual edges, reducing memory from 278 MB → ~28 MB at 1M nodes (90% reduction).</p>
 *
 * <h3>CSR Layout (off-heap)</h3>
 * <pre>
 *   Offset Segment:  [off_0, off_1, ..., off_N]  —  4B × (capacity + 1)
 *   Edge Segment:    [e_0, e_1, ..., e_M]         — 12B × edgeCapacity
 *
 *   Node i's edges = edgeSegment[offsets[i] .. offsets[i+1])
 *
 *   Edge format (12 bytes, same as V2):
 *     [0-3]  neighbor (int)
 *     [4-7]  weight   (float)
 *     [8-9]  lastCycle (short, unsigned)
 *     [10]   bridgeScore (byte, unsigned)
 *     [11]   flags (byte)
 * </pre>
 *
 * <h3>Edge Mutation Strategy</h3>
 * <p>CSR is inherently read-optimized. To support edge insertion/deletion:</p>
 * <ul>
 *   <li><b>Insertion:</b> New edges are appended to an overflow region at the
 *       end of the edge segment. Each node's "extra edges" are tracked via a
 *       small per-node overflow list (heap-allocated, ephemeral).</li>
 *   <li><b>Compaction:</b> During {@link #decayEdges}, surviving edges are
 *       rewritten contiguously and offsets are rebuilt. Overflow is merged.</li>
 * </ul>
 *
 * <h3>File Format (V3)</h3>
 * <pre>
 *   Header (24 bytes):
 *     [0-3]   magic: "HCSR" (0x48435352)
 *     [4-7]   version: 3
 *     [8-11]  capacity (max nodes)
 *     [12-15] edgeCapacity (max edges allocated)
 *     [16-19] totalEdges (actual edges stored)
 *     [20-23] currentCycle
 *
 *   Body:
 *     [offset segment]  — 4B × (capacity + 1)
 *     [edge segment]    — 12B × edgeCapacity
 * </pre>
 *
 * <h3>Migration</h3>
 * <p>Detects V2 files (magic "HGPH") and converts to CSR on load. The conversion
 * extracts edges from the fixed-width layout and packs them into CSR. Original V2
 * file is renamed with ".v2.bak" suffix.</p>
 *
 * @see HebbianGraph
 */
public final class HebbianGraphCsr implements HebbianGraphBase {

    private static final Logger log = LoggerFactory.getLogger(HebbianGraphCsr.class);

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
     * Same threshold as legacy HebbianGraph.
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
     * offsets[i] = start index in edge segment for node i's edges.
     * offsets[capacity] = totalEdgeCount (sentinel).
     */
    private final MemorySegment offsets;

    /**
     * Edge segment: 12B × edgeCapacity.
     * Contiguous packed edges in CSR order.
     */
    private final MemorySegment edges;

    // ── Overflow for edge insertion between compaction cycles ──

    /**
     * Overflow edges that haven't been compacted yet.
     * overflow[node] = list of edges added since last compaction.
     * Null entry = no overflow for that node.
     */
    @SuppressWarnings("unchecked")
    private List<int[]>[] overflow; // int[] = {neighbor, Float.floatToRawIntBits(weight)}

    /** Count of edges in overflow (for capacity tracking). */
    private int overflowEdgeCount;

    // ── Thread safety & session ──

    private final ReentrantLock graphLock = new ReentrantLock();
    private volatile long lastActivityMs = System.currentTimeMillis();
    private volatile long sessionBoundaryMs = 30 * 60 * 1000L;
    private volatile HebbianGraph.DecayModulator decayModulator;

    // ══════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ══════════════════════════════════════════════════════════════

    /**
     * Creates a heap-allocated CSR Hebbian graph.
     *
     * @param capacity      maximum number of nodes (memories)
     * @param edgeCapacity  maximum number of edges (total across all nodes)
     * @param maxDegree     maximum edges per node
     * @param edgeImportance edge importance scorer
     */
    @SuppressWarnings("unchecked")
    public HebbianGraphCsr(int capacity, int edgeCapacity, int maxDegree,
                            EdgeImportance edgeImportance) {
        this.capacity = capacity;
        this.edgeCapacity = edgeCapacity;
        this.maxDegree = maxDegree;
        this.edgeImportance = edgeImportance;
        this.currentCycle = 0;
        this.totalEdgeCount = 0;
        this.overflowEdgeCount = 0;
        this.arena = Arena.ofShared();

        // Offset segment: (capacity + 1) × 4B
        long offsetBytes = (long) (capacity + 1) * Integer.BYTES;
        this.offsets = arena.allocate(offsetBytes);
        offsets.fill((byte) 0);

        // Edge segment: edgeCapacity × 12B
        long edgeBytes = (long) edgeCapacity * EDGE_BYTES;
        this.edges = arena.allocate(edgeBytes);
        edges.fill((byte) 0);

        // Overflow storage (heap)
        this.overflow = new List[capacity];

        long totalKB = (offsetBytes + edgeBytes) / 1024;
        log.info("HebbianGraphCsr initialized (heap): capacity={}, edgeCap={}, maxDegree={}, memory={}KB",
                capacity, edgeCapacity, maxDegree, totalKB);
    }

    /**
     * Creates a CSR graph with default edge capacity (2 × capacity).
     */
    public HebbianGraphCsr(int capacity) {
        this(capacity, capacity * 2, HebbianGraph.DEFAULT_MAX_DEGREE, EdgeImportance.DEFAULT);
    }

    // ══════════════════════════════════════════════════════════════
    // PUBLIC API (mirrors HebbianGraph)
    // ══════════════════════════════════════════════════════════════

    /** Returns the maximum number of nodes. */
    public int capacity() { return capacity; }

    /** Returns the current reflection cycle counter. */
    public int currentCycle() { return currentCycle; }

    /**
     * Strengthens (or creates) a bidirectional Hebbian edge between two memories.
     */
    public void strengthen(int nodeA, int nodeB, float weightDelta) {
        graphLock.lock();
        try {
            if (nodeA < 0 || nodeA >= capacity || nodeB < 0 || nodeB >= capacity) return;
            if (nodeA == nodeB) return;
            addOrUpdateEdge(nodeA, nodeB, weightDelta);
            addOrUpdateEdge(nodeB, nodeA, weightDelta);
            lastActivityMs = System.currentTimeMillis();
        } finally {
            graphLock.unlock();
        }
    }

    /**
     * Returns the Hebbian neighbors of a memory, sorted by descending weight.
     * Includes both CSR edges and overflow edges.
     */
    public List<HebbianEdge> neighbors(int node) {
        if (node < 0 || node >= capacity) return List.of();

        List<HebbianEdge> result = new ArrayList<>();

        // CSR edges
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

        // Overflow edges
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

    /**
     * Returns the degree (number of edges) for a node, including overflow.
     */
    public int degree(int node) {
        if (node < 0 || node >= capacity) return 0;
        int csrDegree = getOffset(node + 1) - getOffset(node);
        List<int[]> ov = overflow[node];
        return csrDegree + (ov != null ? ov.size() : 0);
    }

    /**
     * Returns the total number of edges across all nodes.
     */
    public int totalEdges() {
        return totalEdgeCount + overflowEdgeCount;
    }

    /** Sets the per-node decay modulator. */
    public void setDecayModulator(HebbianGraph.DecayModulator modulator) {
        this.decayModulator = modulator;
    }

    /** Sets the session boundary threshold for new-session detection. */
    public void setSessionBoundary(long durationMs) {
        this.sessionBoundaryMs = durationMs;
    }

    /** Returns true if enough time has passed since last activity to constitute a new session. */
    public boolean isNewSession() {
        return (System.currentTimeMillis() - lastActivityMs) > sessionBoundaryMs;
    }

    /**
     * Decays edges, compacts CSR, and recomputes bridge scores.
     *
     * <p>This is the key advantage of CSR: during compaction, surviving edges are
     * written contiguously, reclaiming all wasted space. Overflow is merged.</p>
     */
    public int decayEdges(float decayFactor) {
        return decayEdges(decayFactor, null);
    }

    /**
     * Decays edges with health metrics collection.
     */
    public int decayEdges(float decayFactor, GraphHealthMetrics metrics) {
        graphLock.lock();
        try {
            currentCycle++;
            HebbianGraph.DecayModulator mod = this.decayModulator;
            int removed = 0;
            int activeNodes = 0;

            // Build new CSR by iterating existing CSR + overflow, keeping survivors
            int writePos = 0; // write cursor into edge segment
            int[] newOffsets = new int[capacity + 1];

            for (int node = 0; node < capacity; node++) {
                newOffsets[node] = writePos;

                // Collect all edges for this node (CSR + overflow)
                List<EdgeData> allEdges = collectAllEdges(node);
                if (allEdges.isEmpty()) continue;

                // Apply modulated decay
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

                    // Bridge protection: don't evict critical bridge edges
                    boolean bridgeProtected = false;
                    if (newWeight < 0.1f && bridge >= BRIDGE_PROTECTION_THRESHOLD) {
                        newWeight = 0.1f;
                        bridgeProtected = true;
                        if (metrics != null) metrics.recordHebbianBridgeProtection();
                    }

                    if (newWeight >= 0.1f) {
                        // Surviving edge — write to CSR
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

            // Sentinel
            newOffsets[capacity] = writePos;
            totalEdgeCount = writePos;

            // Write new offsets to segment
            for (int i = 0; i <= capacity; i++) {
                offsets.set(ValueLayout.JAVA_INT, (long) i * Integer.BYTES, newOffsets[i]);
            }

            // Clear overflow (merged during compaction)
            clearOverflow();

            // Recompute bridge scores on compacted CSR
            updateBridgeScores();

            // Compute fragmentation
            if (metrics != null) {
                int components = countConnectedComponents();
                metrics.setHebbianFragmentation(components, activeNodes);
            }

            if (removed > 0) {
                log.debug("HebbianGraphCsr decay: {} edges removed (factor={:.3f}), {} surviving, cycle={}",
                        removed, decayFactor, totalEdgeCount, currentCycle);
            }
            return removed;
        } finally {
            graphLock.unlock();
        }
    }

    /**
     * Returns the Hebbian neighbors at a given depth (spreading activation).
     */
    public List<HebbianEdge> activateNeighbors(int node, int depth) {
        if (node < 0 || node >= capacity) return List.of();
        List<HebbianEdge> activated = new ArrayList<>();
        boolean[] visited = new boolean[capacity];
        activateRecursive(node, depth, 1.0f, activated, visited);
        activated.sort((a, b) -> Float.compare(b.weight(), a.weight()));
        return activated;
    }

    /**
     * Resets all edges by clearing both segments and overflow.
     */
    public int reset() {
        graphLock.lock();
        try {
            int edgesBefore = totalEdges();
            offsets.fill((byte) 0);
            edges.fill((byte) 0);
            clearOverflow();
            totalEdgeCount = 0;
            lastActivityMs = System.currentTimeMillis();
            log.info("HebbianGraphCsr reset: {} edges cleared, capacity={}", edgesBefore, capacity);
            return edgesBefore;
        } finally {
            graphLock.unlock();
        }
    }

    /**
     * Returns memory usage in bytes (off-heap only, excludes overflow heap).
     */
    public long memoryUsageBytes() {
        return offsets.byteSize() + edges.byteSize();
    }

    @Override
    public void close() {
        log.info("HebbianGraphCsr closing (capacity={}, edges={})", capacity, totalEdgeCount);
        arena.close();
    }

    // ══════════════════════════════════════════════════════════════
    // PERSISTENCE
    // ══════════════════════════════════════════════════════════════

    /**
     * Saves the CSR graph to a binary file (V3 format).
     */
    public void save(Path filePath) {
        Path parent = filePath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new SpectorGraphPersistenceException("HebbianGraphCsr", parent, e);
            }
        }

        // Compact before save (merge overflow)
        compactIfNeeded();

        try (FileChannel ch = FileChannel.open(filePath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            // Header
            ByteBuffer header = ByteBuffer.allocate(FILE_HEADER_BYTES);
            header.putInt(FILE_MAGIC);
            header.putInt(FILE_VERSION);
            header.putInt(capacity);
            header.putInt(edgeCapacity);
            header.putInt(totalEdgeCount);
            header.putInt(currentCycle);
            header.flip();
            ch.write(header);

            // Offset segment
            writeSegmentToChannel(offsets, (long) (capacity + 1) * Integer.BYTES, ch);

            // Edge segment (only write actual edges, not full capacity)
            writeSegmentToChannel(edges, (long) totalEdgeCount * EDGE_BYTES, ch);

            ch.force(true);
            log.info("HebbianGraphCsr saved: capacity={}, edges={}, file={}",
                    capacity, totalEdgeCount, filePath);

        } catch (IOException e) {
            throw new SpectorGraphPersistenceException("HebbianGraphCsr", filePath, e);
        }
    }

    /**
     * Loads a CSR graph from file, or creates a new one if the file doesn't exist.
     * Automatically migrates legacy V2 files to CSR.
     */
    @SuppressWarnings("unchecked")
    public static HebbianGraphCsr load(Path filePath, int defaultCapacity) {
        return load(filePath, defaultCapacity, HebbianGraph.DEFAULT_MAX_DEGREE, EdgeImportance.DEFAULT);
    }

    /**
     * Loads or creates with full configuration.
     */
    @SuppressWarnings("unchecked")
    public static HebbianGraphCsr load(Path filePath, int defaultCapacity,
                                        int maxDegree, EdgeImportance edgeImportance) {
        if (filePath == null || !Files.exists(filePath)) {
            log.info("HebbianGraphCsr file not found, creating fresh: {}", filePath);
            return new HebbianGraphCsr(defaultCapacity, defaultCapacity * 2, maxDegree, edgeImportance);
        }

        try {
            // Read magic to determine format
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
                return new HebbianGraphCsr(defaultCapacity, defaultCapacity * 2, maxDegree, edgeImportance);
            }
        } catch (Exception e) {
            log.error("Failed to load HebbianGraphCsr from {}, creating fresh: {}",
                    filePath, e.getMessage());
            return new HebbianGraphCsr(defaultCapacity, defaultCapacity * 2, maxDegree, edgeImportance);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // INTERNAL: Edge Mutation
    // ══════════════════════════════════════════════════════════════

    private void addOrUpdateEdge(int from, int to, float weightDelta) {
        // First check CSR edges
        int start = getOffset(from);
        int end = getOffset(from + 1);
        for (int i = start; i < end; i++) {
            long edgeOff = (long) i * EDGE_BYTES;
            int neighbor = edges.get(ValueLayout.JAVA_INT, edgeOff + EDGE_OFF_NEIGHBOR);
            if (neighbor == to) {
                // Strengthen existing CSR edge
                float weight = edges.get(ValueLayout.JAVA_FLOAT, edgeOff + EDGE_OFF_WEIGHT);
                edges.set(ValueLayout.JAVA_FLOAT, edgeOff + EDGE_OFF_WEIGHT, weight + weightDelta);
                edges.set(ValueLayout.JAVA_SHORT, edgeOff + EDGE_OFF_LAST_CYCLE, (short) currentCycle);
                return;
            }
        }

        // Check overflow edges
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

        // Check max degree
        int currentDegree = degree(from);
        if (currentDegree >= maxDegree) {
            // Evict lowest-importance edge
            replaceLowestImportance(from, to, weightDelta);
            return;
        }

        // Add new edge to overflow
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

        // Scan CSR edges
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

        // Scan overflow edges
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
        if (newScore <= minScore) return; // new edge isn't important enough

        if (minCsrIdx >= 0) {
            // Replace CSR edge in-place
            long edgeOff = (long) minCsrIdx * EDGE_BYTES;
            edges.set(ValueLayout.JAVA_INT, edgeOff + EDGE_OFF_NEIGHBOR, newNeighbor);
            edges.set(ValueLayout.JAVA_FLOAT, edgeOff + EDGE_OFF_WEIGHT, newWeight);
            edges.set(ValueLayout.JAVA_SHORT, edgeOff + EDGE_OFF_LAST_CYCLE, (short) currentCycle);
            edges.set(ValueLayout.JAVA_BYTE, edgeOff + EDGE_OFF_BRIDGE_SCORE, (byte) 0);
            edges.set(ValueLayout.JAVA_BYTE, edgeOff + EDGE_OFF_EDGE_FLAGS, (byte) 0);
        } else if (minOvIdx >= 0) {
            // Replace overflow edge
            ov.set(minOvIdx, new int[]{newNeighbor, Float.floatToRawIntBits(newWeight)});
        }
    }

    // ══════════════════════════════════════════════════════════════
    // INTERNAL: CSR Helpers
    // ══════════════════════════════════════════════════════════════

    private int getOffset(int node) {
        return offsets.get(ValueLayout.JAVA_INT, (long) node * Integer.BYTES);
    }

    /** Internal edge data for compaction. */
    private record EdgeData(int neighbor, float weight, int lastCycle, int bridgeScore, int flags) {}

    private List<EdgeData> collectAllEdges(int node) {
        List<EdgeData> all = new ArrayList<>();

        // CSR edges
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

        // Overflow edges
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
            // Rebuild CSR from current state
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

    // ══════════════════════════════════════════════════════════════
    // BRIDGE SCORE UPDATE (spanning tree + fallback)
    // ══════════════════════════════════════════════════════════════

    private void updateBridgeScores() {
        // Extract adjacency lists from CSR
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

        // Try spanning tree sampling
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
            // Fallback: heuristic
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

    // ══════════════════════════════════════════════════════════════
    // SPREADING ACTIVATION
    // ══════════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════════
    // CONNECTED COMPONENTS (Union-Find)
    // ══════════════════════════════════════════════════════════════

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
            parent[x] = parent[parent[x]]; // path compression
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

    // ══════════════════════════════════════════════════════════════
    // PERSISTENCE: V3 Load
    // ══════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private static HebbianGraphCsr loadV3(Path filePath, int maxDegree,
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

            HebbianGraphCsr graph = new HebbianGraphCsr(capacity, edgeCap, maxDegree, edgeImportance);
            graph.currentCycle = cycle;
            graph.totalEdgeCount = totalEdges;

            // Read offset segment
            long offsetBytes = (long) (capacity + 1) * Integer.BYTES;
            readIntoSegment(ch, graph.offsets, offsetBytes);

            // Read edge segment
            long edgeBytes = (long) totalEdges * EDGE_BYTES;
            readIntoSegment(ch, graph.edges, edgeBytes);

            log.info("HebbianGraphCsr loaded V3: capacity={}, edges={}, cycle={}, file={}",
                    capacity, totalEdges, cycle, filePath);

            return graph;
        }
    }

    // ══════════════════════════════════════════════════════════════
    // MIGRATION: V2 → V3
    // ══════════════════════════════════════════════════════════════

    /**
     * Migrates a legacy V2 HebbianGraph file to CSR format.
     *
     * <p>Reads the fixed-width layout, extracts edges, packs them into CSR,
     * and backs up the original file as ".v2.bak".</p>
     */
    @SuppressWarnings("unchecked")
    private static HebbianGraphCsr migrateFromV2(Path filePath, int maxDegree,
                                                  EdgeImportance edgeImportance) throws IOException {
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("HebbianGraph V2 → V3 CSR Migration starting: {}", filePath);
        log.info("═══════════════════════════════════════════════════════════════");

        // Load legacy graph
        HebbianGraph legacy = HebbianGraph.load(filePath, 1024, maxDegree, edgeImportance);
        int legacyCapacity = legacy.capacity();

        // Count total edges
        int totalEdges = legacy.totalEdges();
        log.info("V2 graph: capacity={}, totalEdges={}", legacyCapacity, totalEdges);

        // Create CSR graph with appropriate edge capacity
        int edgeCap = Math.max(totalEdges * 2, legacyCapacity * 2);
        HebbianGraphCsr csr = new HebbianGraphCsr(legacyCapacity, edgeCap, maxDegree, edgeImportance);

        // Transfer edges
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

        // Write offsets
        for (int i = 0; i <= legacyCapacity; i++) {
            csr.offsets.set(ValueLayout.JAVA_INT, (long) i * Integer.BYTES, newOffsets[i]);
        }

        // Close legacy
        legacy.close();

        // Backup original V2 file
        Path backupPath = filePath.resolveSibling(filePath.getFileName() + ".v2.bak");
        Files.move(filePath, backupPath);
        log.info("V2 file backed up to: {}", backupPath);

        // Save as V3
        csr.save(filePath);

        // Memory comparison
        long v2Bytes = (long) legacyCapacity * (4 + maxDegree * EDGE_BYTES);
        long v3Bytes = csr.memoryUsageBytes();
        float reductionPct = (1.0f - (float) v3Bytes / v2Bytes) * 100f;

        log.info("═══════════════════════════════════════════════════════════════");
        log.info("Migration complete: {} edges migrated", migratedEdges);
        log.info("Memory: V2={}KB → V3={}KB ({}% reduction)",
                v2Bytes / 1024, v3Bytes / 1024, String.format("%.1f", reductionPct));
        log.info("═══════════════════════════════════════════════════════════════");

        return csr;
    }

    // ══════════════════════════════════════════════════════════════
    // IO Helpers
    // ══════════════════════════════════════════════════════════════

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
