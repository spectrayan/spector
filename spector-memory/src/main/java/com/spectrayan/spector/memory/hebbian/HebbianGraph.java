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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import com.spectrayan.spector.memory.error.SpectorGraphPersistenceException;
import com.spectrayan.spector.memory.graph.BridgeDetector;
import com.spectrayan.spector.memory.graph.EdgeImportance;
import com.spectrayan.spector.memory.graph.GraphHealthMetrics;
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
 * Off-heap adjacency list for full Hebbian graph associations (V2).
 *
 * <h3>Biological Analog: Cortical Network Wiring</h3>
 * <p>In the cortex, neurons form complex networks where activating one node
 * (memory) spreads activation to connected nodes. This graph stores explicit
 * memory-to-memory edges with association weights.</p>
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>Off-heap adjacency list backed by {@link MemorySegment}</li>
 *   <li>Bounded degree: max 20 neighbors per memory (prevents graph explosion)</li>
 *   <li>Edge weight = co-recall count (strengthened each time both are recalled together)</li>
 *   <li>Enables spreading activation: "if you recalled A, also consider B and C"</li>
 *   <li>Persistence: save/load via raw segment serialization to file</li>
 * </ul>
 */
public final class HebbianGraph implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HebbianGraph.class);

    /** File magic: "HGPH" in ASCII. */
    private static final int FILE_MAGIC = 0x48475048;

    /** File format version (v2: widened edges with metadata). */
    private static final int FILE_VERSION = 2;

    /** V1 file format edge bytes — for migration. */
    private static final int V1_EDGE_BYTES = 8;

    /** File header: 4B magic + 4B version + 4B capacity + 4B maxDegree = 16 bytes. */
    private static final int FILE_HEADER_BYTES = 16;

    /** Default maximum number of Hebbian neighbors per memory (configurable). */
    public static final int DEFAULT_MAX_DEGREE = 24;

    /**
     * Bytes per edge (V2): 4B neighbor + 4B weight + 2B lastCycle + 1B bridgeScore + 1B flags.
     *
     * <p>The 4B metadata field is packed as:</p>
     * <pre>
     *   [0-1] lastCycle  (short, unsigned: 0-65535 cycle counter)
     *   [2]   bridgeScore (byte, unsigned: 0-255)
     *   [3]   flags       (byte: reserved for future use)
     * </pre>
     */
    private static final int EDGE_BYTES = 12;
    private static final int EDGE_OFF_NEIGHBOR = 0;
    private static final int EDGE_OFF_WEIGHT = 4;
    private static final int EDGE_OFF_LAST_CYCLE = 8;
    private static final int EDGE_OFF_BRIDGE_SCORE = 10;
    private static final int EDGE_OFF_EDGE_FLAGS = 11;

    /** Maximum degree for this instance (configurable via constructor). */
    private final int maxDegree;

    /** Bytes per node: 4B (degree) + maxDegree * EDGE_BYTES. */
    private final int nodeBytesPerNode;

    /** Reflection cycle counter — incremented by decayEdges(). */
    private int currentCycle;

    /** Edge importance scorer (configurable weights). */
    private final EdgeImportance edgeImportance;

    /**
     * Optional per-node decay modulator — allows the cortex layer to inject
     * synaptic importance/arousal signals without HebbianGraph knowing the header layout.
     *
     * <p>Returns a multiplier in [0.5, 2.0] applied to the decay factor per node.
     * Values > 1.0 = slower decay (high importance), < 1.0 = faster decay (low importance).
     * Null means uniform decay (no modulation).</p>
     */
    @FunctionalInterface
    public interface DecayModulator {
        /**
         * Returns a decay rate modifier for the given memory node.
         *
         * @param nodeIndex memory slot index
         * @return multiplier applied to decay factor (e.g., 1.2 = 20% slower decay)
         */
        float modulateDecay(int nodeIndex);
    }

    private volatile DecayModulator decayModulator;

    private final Arena arena;
    private final MemorySegment segment;
    private final int capacity;
    /** The file channel for mmap'd graphs (null for in-memory). */
    private final FileChannel mappedChannel;
    /** True if this graph is backed by an mmap'd file (vs. heap allocation). */
    private final boolean fileBacked;

    /**
     * Creates a heap-allocated Hebbian graph with default max degree.
     *
     * @param capacity maximum number of nodes (memories)
     */
    public HebbianGraph(int capacity) {
        this(capacity, DEFAULT_MAX_DEGREE, EdgeImportance.DEFAULT);
    }

    /**
     * Creates a heap-allocated Hebbian graph with configurable max degree.
     *
     * @param capacity      maximum number of nodes (memories)
     * @param maxDegree     maximum edges per node
     * @param edgeImportance edge importance scorer
     */
    public HebbianGraph(int capacity, int maxDegree, EdgeImportance edgeImportance) {
        this.capacity = capacity;
        this.maxDegree = maxDegree;
        this.edgeImportance = edgeImportance;
        this.nodeBytesPerNode = 4 + maxDegree * EDGE_BYTES;
        this.currentCycle = 0;
        this.arena = Arena.ofShared();
        this.segment = arena.allocate((long) nodeBytesPerNode * capacity);
        this.mappedChannel = null;
        this.fileBacked = false;
        segment.fill((byte) 0);

        log.info("HebbianGraph initialized (heap): capacity={}, maxDegree={}, memory={}KB",
                capacity, maxDegree, (long) nodeBytesPerNode * capacity / 1024);
    }

    /**
     * Creates or opens a file-backed (mmap) Hebbian graph.
     *
     * <p>If the file exists, it is mapped directly — no data copy needed.
     * If the file doesn't exist, a new file is created, pre-allocated, and
     * zero-filled. V1 files (EDGE_BYTES=8) are detected and re-initialized
     * as V2 (one-way migration — old data is lost).</p>
     *
     * @param filePath path to the graph file
     * @param capacity maximum number of nodes (used only for new files)
     */
    public HebbianGraph(Path filePath, int capacity) {
        this(filePath, capacity, DEFAULT_MAX_DEGREE, EdgeImportance.DEFAULT);
    }

    /**
     * Creates or opens a file-backed (mmap) Hebbian graph with configurable degree.
     *
     * @param filePath       path to the graph file
     * @param capacity       maximum number of nodes (used only for new files)
     * @param maxDegree      maximum edges per node
     * @param edgeImportance edge importance scorer
     */
    public HebbianGraph(Path filePath, int capacity, int maxDegree, EdgeImportance edgeImportance) {
        this.maxDegree = maxDegree;
        this.edgeImportance = edgeImportance;
        this.nodeBytesPerNode = 4 + maxDegree * EDGE_BYTES;
        this.currentCycle = 0;

        Path parent = filePath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new SpectorGraphPersistenceException("HebbianGraph", parent, e);
            }
        }

        long dataBytes = (long) nodeBytesPerNode * capacity;
        boolean isNew = !Files.exists(filePath);

        try {
            FileChannel ch = FileChannel.open(filePath,
                    StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            this.mappedChannel = ch;

            if (isNew || ch.size() < FILE_HEADER_BYTES) {
                // Brand new file — write V2 header
                writeNewHeader(ch, capacity, maxDegree);
                long totalBytes = FILE_HEADER_BYTES + dataBytes;
                if (ch.size() < totalBytes) {
                    ch.position(totalBytes - 1);
                    ch.write(ByteBuffer.wrap(new byte[]{0}));
                }
                ch.force(true);
                this.capacity = capacity;
            } else {
                // Existing file — read header
                ByteBuffer header = ByteBuffer.allocate(FILE_HEADER_BYTES);
                ch.position(0);
                ch.read(header);
                header.flip();
                int magic = header.getInt();
                int version = header.getInt();
                int fileCapacity = header.getInt();
                int fileDegree = header.getInt();

                if (magic != FILE_MAGIC) {
                    log.warn("Invalid HebbianGraph file (bad magic), reinitializing: {}", filePath);
                    ch.truncate(0);
                    writeNewHeader(ch, capacity, maxDegree);
                    long totalBytes = FILE_HEADER_BYTES + dataBytes;
                    ch.position(totalBytes - 1);
                    ch.write(ByteBuffer.wrap(new byte[]{0}));
                    ch.force(true);
                    this.capacity = capacity;
                } else if (version < FILE_VERSION) {
                    // V1 → V2 data-preserving migration: widen 8B edges → 12B edges
                    int v1MaxDegree = fileDegree > 0 ? fileDegree : 20; // V1 default was 20
                    int v1EdgeBytes = V1_EDGE_BYTES; // 8B: 4B neighbor + 4B weight
                    int v1NodeBytes = 4 + v1MaxDegree * v1EdgeBytes;
                    long v1DataBytes = (long) v1NodeBytes * fileCapacity;

                    log.warn("Migrating HebbianGraph v{} → v{} (data-preserving): {} " +
                                    "(capacity={}, v1Degree={}, v1NodeBytes={})",
                            version, FILE_VERSION, filePath,
                            fileCapacity, v1MaxDegree, v1NodeBytes);

                    // Phase 1: Read all V1 edge data into heap buffer
                    ByteBuffer v1Data = ByteBuffer.allocate((int) Math.min(v1DataBytes, ch.size() - FILE_HEADER_BYTES));
                    ch.position(FILE_HEADER_BYTES);
                    ch.read(v1Data);
                    v1Data.flip();

                    // Phase 2: Reinitialize file with V2 header and layout
                    int v2MaxDegree = Math.max(maxDegree, v1MaxDegree);
                    int v2NodeBytes = 4 + v2MaxDegree * EDGE_BYTES; // 12B per edge
                    long v2DataBytes = (long) v2NodeBytes * fileCapacity;
                    ch.truncate(0);
                    writeNewHeader(ch, fileCapacity, v2MaxDegree);
                    long totalBytes = FILE_HEADER_BYTES + v2DataBytes;
                    ch.position(totalBytes - 1);
                    ch.write(ByteBuffer.wrap(new byte[]{0}));
                    ch.force(true);

                    // Phase 3: Copy V1 edges into V2 layout
                    // Map the data section for direct writes
                    var migrateArena = Arena.ofConfined();
                    var v2Segment = ch.map(FileChannel.MapMode.READ_WRITE,
                            FILE_HEADER_BYTES, v2DataBytes, migrateArena);

                    int migratedEdges = 0;
                    for (int node = 0; node < fileCapacity; node++) {
                        int v1NodeOffset = node * v1NodeBytes;
                        if (v1NodeOffset + 4 > v1Data.limit()) break;
                        int degree = v1Data.getInt(v1NodeOffset);
                        if (degree <= 0 || degree > v1MaxDegree) {
                            // Invalid degree — skip this node
                            continue;
                        }

                        long v2NodeOffset = (long) node * v2NodeBytes;
                        v2Segment.set(ValueLayout.JAVA_INT, v2NodeOffset, degree);

                        for (int e = 0; e < degree; e++) {
                            int v1EdgeOff = v1NodeOffset + 4 + e * v1EdgeBytes;
                            if (v1EdgeOff + v1EdgeBytes > v1Data.limit()) break;

                            int neighbor = v1Data.getInt(v1EdgeOff);
                            float weight = v1Data.getFloat(v1EdgeOff + 4);

                            long v2EdgeOff = v2NodeOffset + 4 + (long) e * EDGE_BYTES;
                            v2Segment.set(ValueLayout.JAVA_INT, v2EdgeOff + EDGE_OFF_NEIGHBOR, neighbor);
                            v2Segment.set(ValueLayout.JAVA_FLOAT, v2EdgeOff + EDGE_OFF_WEIGHT, weight);
                            v2Segment.set(ValueLayout.JAVA_SHORT, v2EdgeOff + EDGE_OFF_LAST_CYCLE, (short) 0);
                            v2Segment.set(ValueLayout.JAVA_BYTE, v2EdgeOff + EDGE_OFF_BRIDGE_SCORE, (byte) 0);
                            v2Segment.set(ValueLayout.JAVA_BYTE, v2EdgeOff + EDGE_OFF_EDGE_FLAGS, (byte) 0);
                            migratedEdges++;
                        }
                    }
                    migrateArena.close();
                    ch.force(true);

                    this.capacity = fileCapacity;
                    dataBytes = v2DataBytes;
                    log.info("HebbianGraph migration complete: {} edges migrated, " +
                                    "v2NodeBytes={}, v2MaxDegree={}",
                            migratedEdges, v2NodeBytes, v2MaxDegree);
                } else {
                    this.capacity = fileCapacity;
                    dataBytes = (long) nodeBytesPerNode * fileCapacity;
                }
            }

            // mmap the data portion (after header)
            this.arena = Arena.ofShared();
            this.segment = ch.map(FileChannel.MapMode.READ_WRITE, FILE_HEADER_BYTES,
                    dataBytes, arena);
            this.fileBacked = true;

            log.info("HebbianGraph initialized (mmap): capacity={}, maxDegree={}, version={}, file={}, memory={}KB",
                    this.capacity, maxDegree, FILE_VERSION, filePath.getFileName(), dataBytes / 1024);

        } catch (IOException e) {
            throw new SpectorGraphPersistenceException("HebbianGraph", filePath, e);
        }
    }

    private static void writeNewHeader(FileChannel ch, int capacity, int maxDegree) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(FILE_HEADER_BYTES);
        header.putInt(FILE_MAGIC);
        header.putInt(FILE_VERSION);
        header.putInt(capacity);
        header.putInt(maxDegree);
        header.flip();
        ch.position(0);
        ch.write(header);
    }

    private HebbianGraph(int capacity, int maxDegree, EdgeImportance edgeImportance,
                          Arena arena, MemorySegment segment,
                          FileChannel mappedChannel, boolean fileBacked) {
        this.capacity = capacity;
        this.maxDegree = maxDegree;
        this.edgeImportance = edgeImportance;
        this.nodeBytesPerNode = 4 + maxDegree * EDGE_BYTES;
        this.currentCycle = 0;
        this.arena = arena;
        this.segment = segment;
        this.mappedChannel = mappedChannel;
        this.fileBacked = fileBacked;
    }

    /**
     * Returns the capacity (maximum number of nodes).
     */
    public int capacity() {
        return capacity;
    }

    private final ReentrantLock graphLock = new ReentrantLock();

    /**
     * Adds or strengthens a bidirectional Hebbian edge between two memories.
     *
     * @param nodeA index of first memory
     * @param nodeB index of second memory
     * @param weightDelta weight to add to the edge (default: 1.0)
     */
    public void strengthen(int nodeA, int nodeB, float weightDelta) {
        graphLock.lock();
        try {
            if (nodeA < 0 || nodeA >= capacity || nodeB < 0 || nodeB >= capacity) return;
            if (nodeA == nodeB) return;
            addOrUpdateEdge(nodeA, nodeB, weightDelta);
            addOrUpdateEdge(nodeB, nodeA, weightDelta);
        } finally {
            graphLock.unlock();
        }
    }

    /**
     * Returns the Hebbian neighbors of a memory, sorted by descending weight.
     *
     * @param node memory index
     * @return list of (neighborIndex, weight) pairs
     */
    public List<HebbianEdge> neighbors(int node) {
        if (node < 0 || node >= capacity) return List.of();
        long nodeOffset = (long) node * nodeBytesPerNode;
        int degree = segment.get(ValueLayout.JAVA_INT, nodeOffset);

        List<HebbianEdge> edges = new ArrayList<>(degree);
        for (int i = 0; i < degree; i++) {
            long edgeOffset = nodeOffset + 4 + (long) i * EDGE_BYTES;
            int neighbor = segment.get(ValueLayout.JAVA_INT, edgeOffset);
            float weight = segment.get(ValueLayout.JAVA_FLOAT, edgeOffset + 4);
            int bridge = Byte.toUnsignedInt(segment.get(ValueLayout.JAVA_BYTE, edgeOffset + EDGE_OFF_BRIDGE_SCORE));
            edges.add(new HebbianEdge(neighbor, weight, bridge));
        }

        edges.sort((a, b) -> Float.compare(b.weight(), a.weight()));
        return edges;
    }

    /**
     * Returns the degree (number of Hebbian edges) for a node.
     */
    public int degree(int node) {
        if (node < 0 || node >= capacity) return 0;
        return segment.get(ValueLayout.JAVA_INT, (long) node * nodeBytesPerNode);
    }

    /**
     * Returns the total number of edges across all nodes.
     */
    public int totalEdges() {
        int total = 0;
        for (int i = 0; i < capacity; i++) {
            total += degree(i);
        }
        return total;
    }

    private void addOrUpdateEdge(int from, int to, float weightDelta) {
        long nodeOffset = (long) from * nodeBytesPerNode;
        int degree = segment.get(ValueLayout.JAVA_INT, nodeOffset);

        // Check if edge already exists
        for (int i = 0; i < degree; i++) {
            long edgeOffset = nodeOffset + 4 + (long) i * EDGE_BYTES;
            int neighbor = segment.get(ValueLayout.JAVA_INT, edgeOffset + EDGE_OFF_NEIGHBOR);
            if (neighbor == to) {
                // Strengthen existing edge — update weight and recency
                float weight = segment.get(ValueLayout.JAVA_FLOAT, edgeOffset + EDGE_OFF_WEIGHT);
                segment.set(ValueLayout.JAVA_FLOAT, edgeOffset + EDGE_OFF_WEIGHT, weight + weightDelta);
                segment.set(ValueLayout.JAVA_SHORT, edgeOffset + EDGE_OFF_LAST_CYCLE, (short) currentCycle);
                return;
            }
        }

        // Add new edge (if room)
        if (degree < maxDegree) {
            long edgeOffset = nodeOffset + 4 + (long) degree * EDGE_BYTES;
            segment.set(ValueLayout.JAVA_INT, edgeOffset + EDGE_OFF_NEIGHBOR, to);
            segment.set(ValueLayout.JAVA_FLOAT, edgeOffset + EDGE_OFF_WEIGHT, weightDelta);
            segment.set(ValueLayout.JAVA_SHORT, edgeOffset + EDGE_OFF_LAST_CYCLE, (short) currentCycle);
            segment.set(ValueLayout.JAVA_BYTE, edgeOffset + EDGE_OFF_BRIDGE_SCORE, (byte) 0);
            segment.set(ValueLayout.JAVA_BYTE, edgeOffset + EDGE_OFF_EDGE_FLAGS, (byte) 0);
            segment.set(ValueLayout.JAVA_INT, nodeOffset, degree + 1);
        } else {
            // Evict lowest-importance edge if new edge scores higher
            replaceLowestImportance(from, nodeOffset, degree, to, weightDelta);
        }
    }

    /**
     * Replaces the lowest-importance edge with a new edge, if the new edge
     * would score higher. Uses the neuroscience-informed EdgeImportance scorer.
     */
    private void replaceLowestImportance(int fromNode, long nodeOffset, int degree,
                                          int newNeighbor, float newWeight) {
        float minScore = Float.MAX_VALUE;
        int minIndex = -1;

        for (int i = 0; i < degree; i++) {
            long edgeOffset = nodeOffset + 4 + (long) i * EDGE_BYTES;
            float weight = segment.get(ValueLayout.JAVA_FLOAT, edgeOffset + EDGE_OFF_WEIGHT);
            short lastCycle = segment.get(ValueLayout.JAVA_SHORT, edgeOffset + EDGE_OFF_LAST_CYCLE);
            byte bridgeScore = segment.get(ValueLayout.JAVA_BYTE, edgeOffset + EDGE_OFF_BRIDGE_SCORE);

            // Use structural-only scoring (no header reads during hot-path insertion)
            float score = edgeImportance.scoreStructural(
                    weight, currentCycle, Short.toUnsignedInt(lastCycle),
                    Byte.toUnsignedInt(bridgeScore), 0);

            if (score < minScore) {
                minScore = score;
                minIndex = i;
            }
        }

        // New edge must score higher than the weakest existing edge
        float newScore = edgeImportance.scoreStructural(
                newWeight, currentCycle, currentCycle, 0, 0);

        if (newScore > minScore && minIndex >= 0) {
            long edgeOffset = nodeOffset + 4 + (long) minIndex * EDGE_BYTES;
            segment.set(ValueLayout.JAVA_INT, edgeOffset + EDGE_OFF_NEIGHBOR, newNeighbor);
            segment.set(ValueLayout.JAVA_FLOAT, edgeOffset + EDGE_OFF_WEIGHT, newWeight);
            segment.set(ValueLayout.JAVA_SHORT, edgeOffset + EDGE_OFF_LAST_CYCLE, (short) currentCycle);
            segment.set(ValueLayout.JAVA_BYTE, edgeOffset + EDGE_OFF_BRIDGE_SCORE, (byte) 0);
            segment.set(ValueLayout.JAVA_BYTE, edgeOffset + EDGE_OFF_EDGE_FLAGS, (byte) 0);
        }
    }

    /**
     * Immutable Hebbian edge record.
     *
     * <p><b>TODO (JDK 28+ / Project Valhalla):</b> Convert to {@code value record}.
     * As a value class, HebbianEdge would be scalarized in the caller's stack frame
     * instead of heap-allocated. With specialized generics, {@code List<HebbianEdge>}
     * would store flattened values instead of boxed pointers.</p>
     *
     * @param neighborIndex index of the connected memory
     * @param weight        association strength
     */
    public record HebbianEdge(int neighborIndex, float weight, int bridgeScore) {

        /** Backward-compatible constructor (bridgeScore defaults to 0). */
        public HebbianEdge(int neighborIndex, float weight) {
            this(neighborIndex, weight, 0);
        }
    }

    /**
     * Sets the per-node decay modulator for arousal-modulated edge decay.
     *
     * <p>Typical usage: the cortex layer reads synaptic header importance/arousal
     * for each memory and returns a modifier that slows decay for important memories.</p>
     *
     * @param modulator per-node modifier (null = uniform decay)
     */
    public void setDecayModulator(DecayModulator modulator) {
        this.decayModulator = modulator;
    }

    // ── V3: Edge Decay + Session Boundaries + Spreading Activation ──

    private long lastActivityMs = System.currentTimeMillis();
    private long sessionBoundaryMs = 5 * 60 * 1000L; // 5 minutes default

    /**
     * Configures the session boundary inactivity threshold.
     *
     * @param durationMs milliseconds of inactivity that defines a session break
     */
    public void setSessionBoundary(long durationMs) {
        this.sessionBoundaryMs = durationMs;
    }

    /**
     * Checks if a new session has started (inactivity exceeded boundary).
     *
     * @return true if a new session has started since the last activity
     */
    public boolean isNewSession() {
        long now = System.currentTimeMillis();
        boolean isNew = (now - lastActivityMs) > sessionBoundaryMs;
        lastActivityMs = now;
        return isNew;
    }

    /**
     * Decays all edge weights by a factor (V3: called during ReflectDaemon cycles).
     *
     * <p>Unused associations weaken over time — edges that are never re-strengthened
     * eventually drop to zero and get replaced by new associations.</p>
     *
     * <p><b>Arousal-modulated decay:</b> If a {@link DecayModulator} is set via
     * {@link #setDecayModulator}, the decay factor is adjusted per-node. High-importance
     * or high-arousal memories receive a modulator &gt; 1.0 (slower decay), while
     * low-importance memories receive &lt; 1.0 (faster decay). This models the
     * amygdala's role in protecting emotionally-charged synaptic pathways.</p>
     *
     * @param decayFactor base multiplier (e.g., 0.9 = 10% decay per cycle)
     * @return number of edges that dropped below threshold and were removed
     */
    public int decayEdges(float decayFactor) {
        return decayEdges(decayFactor, null);
    }

    /**
     * Decays all edge weights, collecting health metrics for observability.
     *
     * <p>Same as {@link #decayEdges(float)} but populates the supplied
     * {@link GraphHealthMetrics} with per-edge statistics during the cycle.</p>
     *
     * @param decayFactor base multiplier (e.g., 0.9 = 10% decay per cycle)
     * @param metrics     optional metrics collector (may be {@code null})
     * @return number of edges that dropped below threshold and were removed
     */
    public int decayEdges(float decayFactor, GraphHealthMetrics metrics) {
        graphLock.lock();
        try {
            currentCycle++;
            int removed = 0;
            float removalThreshold = 0.01f;
            DecayModulator mod = this.decayModulator; // snapshot volatile
            int activeNodes = 0;

            for (int node = 0; node < capacity; node++) {
                long nodeOffset = (long) node * nodeBytesPerNode;
                int degree = segment.get(ValueLayout.JAVA_INT, nodeOffset);
                int newDegree = 0;

                // Per-node arousal-modulated decay factor
                float effectiveDecay = decayFactor;
                boolean arousalModulated = false;
                if (mod != null) {
                    float multiplier = mod.modulateDecay(node);
                    // Clamp to [0.5, 2.0] to prevent runaway protection or destruction
                    multiplier = Math.clamp(multiplier, 0.5f, 2.0f);
                    if (multiplier != 1.0f) {
                        arousalModulated = true;
                    }
                    // Higher multiplier → closer to 1.0 → slower decay
                    effectiveDecay = 1.0f - (1.0f - decayFactor) / multiplier;
                    // Clamp result to valid range
                    effectiveDecay = Math.clamp(effectiveDecay, 0.5f, 0.999f);
                }

                for (int i = 0; i < degree; i++) {
                    long edgeOffset = nodeOffset + 4 + (long) i * EDGE_BYTES;
                    float weight = segment.get(ValueLayout.JAVA_FLOAT, edgeOffset + 4);
                    float decayed = weight * effectiveDecay;

                    if (decayed >= removalThreshold) {
                        // Keep edge — compact if needed
                        short lastCyc;
                        byte bridge;
                        if (newDegree != i) {
                            long newOffset = nodeOffset + 4 + (long) newDegree * EDGE_BYTES;
                            // Copy full 12-byte edge (neighbor + weight + metadata)
                            int neighbor = segment.get(ValueLayout.JAVA_INT, edgeOffset + EDGE_OFF_NEIGHBOR);
                            lastCyc = segment.get(ValueLayout.JAVA_SHORT, edgeOffset + EDGE_OFF_LAST_CYCLE);
                            bridge = segment.get(ValueLayout.JAVA_BYTE, edgeOffset + EDGE_OFF_BRIDGE_SCORE);
                            byte eFlags = segment.get(ValueLayout.JAVA_BYTE, edgeOffset + EDGE_OFF_EDGE_FLAGS);
                            segment.set(ValueLayout.JAVA_INT, newOffset + EDGE_OFF_NEIGHBOR, neighbor);
                            segment.set(ValueLayout.JAVA_FLOAT, newOffset + EDGE_OFF_WEIGHT, decayed);
                            segment.set(ValueLayout.JAVA_SHORT, newOffset + EDGE_OFF_LAST_CYCLE, lastCyc);
                            segment.set(ValueLayout.JAVA_BYTE, newOffset + EDGE_OFF_BRIDGE_SCORE, bridge);
                            segment.set(ValueLayout.JAVA_BYTE, newOffset + EDGE_OFF_EDGE_FLAGS, eFlags);
                        } else {
                            segment.set(ValueLayout.JAVA_FLOAT, edgeOffset + EDGE_OFF_WEIGHT, decayed);
                            lastCyc = segment.get(ValueLayout.JAVA_SHORT, edgeOffset + EDGE_OFF_LAST_CYCLE);
                            bridge = segment.get(ValueLayout.JAVA_BYTE, edgeOffset + EDGE_OFF_BRIDGE_SCORE);
                        }
                        newDegree++;

                        // Record metrics for surviving edge
                        if (metrics != null) {
                            int edgeAge = (currentCycle - Short.toUnsignedInt(lastCyc)) & 0xFFFF;
                            metrics.recordHebbianSurvivor(
                                    Byte.toUnsignedInt(bridge), edgeAge);
                            if (arousalModulated) {
                                metrics.recordHebbianArousalModulation();
                            }
                        }
                    } else {
                        removed++;
                        if (metrics != null) {
                            metrics.recordHebbianDecay();
                        }
                    }
                }

                segment.set(ValueLayout.JAVA_INT, nodeOffset, newDegree);
                if (newDegree > 0) activeNodes++;
            }

            // Phase 2: Update bridge scores for all surviving edges
            updateBridgeScores();

            // Phase 3: Compute fragmentation (connected components via Union-Find)
            if (metrics != null) {
                int components = countConnectedComponents();
                metrics.setHebbianFragmentation(components, activeNodes);
            }

            if (removed > 0) {
                log.debug("Hebbian edge decay: {} edges removed (factor={}, modulated={}), cycle={}",
                        removed, decayFactor, mod != null, currentCycle);
            }
            return removed;
        } finally {
            graphLock.unlock();
        }
    }

    /**
     * Recomputes bridge scores for all edges in the graph.
     *
     * <p>Called during {@link #decayEdges} after compaction. Uses the
     * {@link BridgeDetector} neighbor overlap heuristic to identify edges
     * that serve as critical bridges between otherwise-disconnected neighborhoods.</p>
     *
     * <p><b>Cost:</b> O(N × MAX_DEGREE²) — acceptable during ReflectDaemon cycles
     * (every few seconds), never on the hot recall path.</p>
     */
    private void updateBridgeScores() {
        // Pre-extract neighbor arrays for all active nodes (avoids re-reading)
        int[][] neighborArrays = new int[capacity][];
        int[] degrees = new int[capacity];

        for (int node = 0; node < capacity; node++) {
            long nodeOffset = (long) node * nodeBytesPerNode;
            int degree = segment.get(ValueLayout.JAVA_INT, nodeOffset);
            degrees[node] = degree;
            if (degree > 0) {
                int[] neighbors = new int[degree];
                for (int i = 0; i < degree; i++) {
                    long edgeOffset = nodeOffset + 4 + (long) i * EDGE_BYTES;
                    neighbors[i] = segment.get(ValueLayout.JAVA_INT, edgeOffset + EDGE_OFF_NEIGHBOR);
                }
                neighborArrays[node] = neighbors;
            }
        }

        // Compute and store bridge scores
        for (int node = 0; node < capacity; node++) {
            int degree = degrees[node];
            if (degree == 0) continue;
            long nodeOffset = (long) node * nodeBytesPerNode;

            for (int i = 0; i < degree; i++) {
                long edgeOffset = nodeOffset + 4 + (long) i * EDGE_BYTES;
                int neighbor = segment.get(ValueLayout.JAVA_INT, edgeOffset + EDGE_OFF_NEIGHBOR);

                int shared = 0;
                int neighborDegree = 0;
                if (neighbor >= 0 && neighbor < capacity && neighborArrays[neighbor] != null) {
                    neighborDegree = degrees[neighbor];
                    shared = BridgeDetector.countSharedNeighbors(
                            neighborArrays[node], degree,
                            neighborArrays[neighbor], neighborDegree);
                }

                int bridgeScore = BridgeDetector.computeBridgeScore(shared, degree, neighborDegree);
                segment.set(ValueLayout.JAVA_BYTE, edgeOffset + EDGE_OFF_BRIDGE_SCORE, (byte) bridgeScore);
            }
        }
    }

    /**
     * Counts connected components in the Hebbian graph using Union-Find.
     *
     * <p>Only considers nodes with degree &gt; 0 (non-isolated nodes).
     * Uses path compression and union-by-rank for O(N × α(N)) performance.</p>
     *
     * @return number of distinct connected components among active nodes
     */
    private int countConnectedComponents() {
        // Union-Find parent array: parent[i] = i means root
        int[] parent = new int[capacity];
        int[] rank = new int[capacity];
        for (int i = 0; i < capacity; i++) parent[i] = i;

        for (int node = 0; node < capacity; node++) {
            long nodeOffset = (long) node * nodeBytesPerNode;
            int degree = segment.get(ValueLayout.JAVA_INT, nodeOffset);
            for (int i = 0; i < degree; i++) {
                long edgeOffset = nodeOffset + 4 + (long) i * EDGE_BYTES;
                int neighbor = segment.get(ValueLayout.JAVA_INT, edgeOffset + EDGE_OFF_NEIGHBOR);
                if (neighbor >= 0 && neighbor < capacity) {
                    union(parent, rank, node, neighbor);
                }
            }
        }

        // Count distinct roots among non-isolated nodes
        boolean[] seen = new boolean[capacity];
        int components = 0;
        for (int node = 0; node < capacity; node++) {
            long nodeOffset = (long) node * nodeBytesPerNode;
            int degree = segment.get(ValueLayout.JAVA_INT, nodeOffset);
            if (degree > 0) {
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
        int ra = find(parent, a), rb = find(parent, b);
        if (ra == rb) return;
        if (rank[ra] < rank[rb]) { parent[ra] = rb; }
        else if (rank[ra] > rank[rb]) { parent[rb] = ra; }
        else { parent[rb] = ra; rank[ra]++; }
    }

    /**
     * Returns the Hebbian neighbors of a memory at a given depth (spreading activation).
     *
     * <p>Depth 1 = direct neighbors. Depth 2 = neighbors of neighbors.
     * Activation strength decreases with each hop.</p>
     *
     * @param node  starting memory index
     * @param depth activation depth (1-3 recommended)
     * @return list of activated edges with compound weights
     */
    public List<HebbianEdge> activateNeighbors(int node, int depth) {
        if (node < 0 || node >= capacity) return List.of();
        List<HebbianEdge> activated = new ArrayList<>();
        // Use boolean[] instead of HashSet<Integer> — eliminates autoboxing overhead
        boolean[] visited = new boolean[capacity];
        activateRecursive(node, depth, 1.0f, activated, visited);
        activated.sort((a, b) -> Float.compare(b.weight(), a.weight()));
        return activated;
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

    // ══════════════════════════════════════════════════════════════
    // PERSISTENCE: save / load
    // ══════════════════════════════════════════════════════════════

    /**
     * Saves the graph to a binary file.
     *
     * <h3>File Format (V2)</h3>
     * <pre>
     *   [4B magic: "HGPH"]  [4B version: 2]  [4B capacity]  [4B maxDegree]
     *   [raw segment bytes: capacity × nodeBytesPerNode]
     * </pre>
     *
     * @param filePath path to write the graph file
     */
    public void save(Path filePath) {
        // mmap-backed: force flush dirty pages to disk
        if (fileBacked) {
            try {
                segment.force();
                if (mappedChannel != null) mappedChannel.force(true);
                log.info("HebbianGraph flushed (mmap): capacity={}, edges={}",
                        capacity, totalEdges());
            } catch (IOException e) {
                throw new SpectorGraphPersistenceException("HebbianGraph", filePath, e);
            }
            return;
        }

        // Heap-backed: serialize to file
        Path parent = filePath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new SpectorGraphPersistenceException("HebbianGraph", parent, e);
            }
        }

        try (FileChannel ch = FileChannel.open(filePath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            // Write file header
            ByteBuffer header = ByteBuffer.allocate(FILE_HEADER_BYTES);
            header.putInt(FILE_MAGIC);
            header.putInt(FILE_VERSION);
            header.putInt(capacity);
            header.putInt(maxDegree);
            header.flip();
            ch.write(header);

            // Write raw segment bytes in chunks
            long totalBytes = (long) nodeBytesPerNode * capacity;
            long written = 0;
            int chunkSize = 64 * 1024; // 64KB chunks
            while (written < totalBytes) {
                int toWrite = (int) Math.min(chunkSize, totalBytes - written);
                ByteBuffer buf = segment.asSlice(written, toWrite)
                        .asByteBuffer().asReadOnlyBuffer();
                ch.write(buf);
                written += toWrite;
            }

            ch.force(true);
            log.info("HebbianGraph saved (heap→file): capacity={}, edges={} → {}",
                    capacity, totalEdges(), filePath);

        } catch (IOException e) {
            throw new SpectorGraphPersistenceException("HebbianGraph", filePath, e);
        }
    }

    /**
     * Loads a graph from a binary file, or returns a new empty graph
     * if the file doesn't exist.
     *
     * @param filePath path to the graph file
     * @param defaultCapacity capacity to use if file doesn't exist
     * @return a HebbianGraph (loaded or new)
     */
    public static HebbianGraph load(Path filePath, int defaultCapacity) {
        return load(filePath, defaultCapacity, DEFAULT_MAX_DEGREE, EdgeImportance.DEFAULT);
    }

    /**
     * Loads or creates a HebbianGraph with configurable max degree and importance scorer.
     *
     * @param filePath         path to graph file (null = heap-only)
     * @param defaultCapacity  capacity to use if file doesn't exist
     * @param maxDegree        maximum edges per node
     * @param edgeImportance   edge importance scorer
     * @return a HebbianGraph (loaded or new)
     */
    public static HebbianGraph load(Path filePath, int defaultCapacity,
                                     int maxDegree, EdgeImportance edgeImportance) {
        if (filePath == null || !Files.exists(filePath)) {
            log.info("HebbianGraph file not found, creating fresh: {}", filePath);
            return new HebbianGraph(defaultCapacity, maxDegree, edgeImportance);
        }

        try {
            return new HebbianGraph(filePath, defaultCapacity, maxDegree, edgeImportance);
        } catch (Exception e) {
            log.error("Failed to mmap HebbianGraph from {}, creating fresh: {}",
                    filePath, e.getMessage());
            return new HebbianGraph(defaultCapacity, maxDegree, edgeImportance);
        }
    }

    /**
     * Resets all Hebbian edges by zero-filling the off-heap segment.
     *
     * <p>Unlike {@link #close()}, this does NOT release the arena. The graph
     * remains usable for new edges after the reset. Used by privacy wipe.</p>
     *
     * @return total edges that existed before reset
     */
    public int reset() {
        graphLock.lock();
        try {
            int edgesBefore = totalEdges();
            segment.fill((byte) 0);
            lastActivityMs = System.currentTimeMillis();
            log.info("HebbianGraph reset: {} edges cleared, capacity={}", edgesBefore, capacity);
            return edgesBefore;
        } finally {
            graphLock.unlock();
        }
    }

    @Override
    public void close() {
        log.info("HebbianGraph closing (capacity={}, fileBacked={})", capacity, fileBacked);
        if (fileBacked && mappedChannel != null) {
            try {
                segment.force();
                mappedChannel.close();
            } catch (IOException e) {
                log.warn("Failed to close HebbianGraph channel: {}", e.getMessage());
            }
        }
        arena.close();
    }
}
