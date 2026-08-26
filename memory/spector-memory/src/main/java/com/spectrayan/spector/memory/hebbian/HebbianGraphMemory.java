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
import com.spectrayan.spector.memory.kernel.MemoryHeader;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.SystemMemoryId;
import com.spectrayan.spector.memory.kernel.codec.Codecs;
import com.spectrayan.spector.memory.kernel.layout.HebbianLayout;
import com.spectrayan.spector.memory.kernel.shape.AbstractGraphMemory;

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
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static com.spectrayan.spector.memory.kernel.layout.HebbianLayout.DATA_START;
import static com.spectrayan.spector.memory.kernel.layout.HebbianLayout.EDGE_BYTES;
import static com.spectrayan.spector.memory.kernel.layout.HebbianLayout.EDGE_OFF_BRIDGE_SCORE;
import static com.spectrayan.spector.memory.kernel.layout.HebbianLayout.EDGE_OFF_EDGE_FLAGS;
import static com.spectrayan.spector.memory.kernel.layout.HebbianLayout.EDGE_OFF_LAST_CYCLE;
import static com.spectrayan.spector.memory.kernel.layout.HebbianLayout.EDGE_OFF_NEIGHBOR;
import static com.spectrayan.spector.memory.kernel.layout.HebbianLayout.EDGE_OFF_WEIGHT;
import static com.spectrayan.spector.memory.kernel.layout.HebbianLayout.SUB_OFF_CURRENT_CYCLE;
import static com.spectrayan.spector.memory.kernel.layout.HebbianLayout.SUB_OFF_EDGE_CAPACITY;

/**
 * Compressed Sparse Row (CSR) layout for the Hebbian association graph, implementing
 * the Spector Memory Kernel {@link com.spectrayan.spector.memory.kernel.shape.AbstractGraphMemory} specification.
 *
 * @see HebbianGraph
 */
public final class HebbianGraphMemory extends AbstractGraphMemory<HebbianLayout>
        implements HebbianGraphBase {

    private static final Logger log = LoggerFactory.getLogger(HebbianGraphMemory.class);

    /** Shared record layout — single source of truth for stride + edge offsets (TD-14). */
    private static final HebbianLayout LAYOUT = new HebbianLayout();

    /** Kernel identity for the Hebbian association graph. */
    private static final MemoryId MEMORY_ID = SystemMemoryId.HEBBIAN_CSR.id();

    // ── On-disk container magics (logical values, as read big-endian) ──
    /** Legacy fixed-width container magic ('HGPH'), migrated via the codec. */
    static final int LEGACY_HGPH_MAGIC = 0x48475048;
    /** Interim CSR container magic ('HCSR', #432), migrated via the codec. */
    static final int INTERIM_HCSR_MAGIC = 0x48435352;
    /** Bytes of the interim HCSR header: magic+version+capacity+edgeCap+totalEdges+cycle. */
    static final int HCSR_HEADER_BYTES = 24;

    // ── SMKM container framing: single source of truth is HebbianLayout (#435, TD-14). ──
    // GRAPH_SUBHEADER_BYTES / SUB_OFF_* field offsets / DATA_START are static-imported from
    // HebbianLayout; this class only references them.

    /** Minimum bridge score to protect an edge from eviction during decay. */
    static final int BRIDGE_PROTECTION_THRESHOLD = 224;

    /** Maximum degree per node (prevents graph explosion). */
    private final int maxDegree;

    /** Edge importance scorer. */
    private final EdgeImportance edgeImportance;

    /** Current reflection cycle. */
    private int currentCycle;

    /** Total edges stored in CSR. */
    private int totalEdgeCount;

    /** Maximum edge slots allocated in the edge slab. */
    private final int edgeCapacity;

    // ── Off-heap slabs (views over the single kernel-owned segment) ──
    /** Offset slab view: 4B × (capacity + 1). */
    private final MemorySegment offsets;
    /** Edge slab view: {@code EDGE_BYTES} × edgeCapacity. */
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
    private volatile long lastCompactionEpochMs = 0L;
    private volatile long bytesReclaimedLastCycle = 0L;

    // ══════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ══════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    public HebbianGraphMemory(int capacity, int edgeCapacity, int maxDegree,
                               EdgeImportance edgeImportance) {
        // Substrate mode: the kernel base owns one off-heap segment + arena and the SMKM
        // identity/shape. This class lays the segment out as an offset slab followed by an
        // edge slab and drives the specialized prefix-sum CSR algorithms itself.
        super(MEMORY_ID, LAYOUT, capacity,
                (long) (capacity + 1) * Integer.BYTES + (long) edgeCapacity * EDGE_BYTES);
        this.edgeCapacity = edgeCapacity;
        this.maxDegree = maxDegree;
        this.edgeImportance = edgeImportance;
        this.currentCycle = 0;
        this.totalEdgeCount = 0;
        this.overflowEdgeCount = 0;

        long offsetBytes = (long) (capacity + 1) * Integer.BYTES;
        long edgeBytes = (long) edgeCapacity * EDGE_BYTES;
        // Arena.allocate zero-fills, so both slabs start empty.
        this.offsets = segment().asSlice(0, offsetBytes);
        this.edges = segment().asSlice(offsetBytes, edgeBytes);

        this.overflow = new List[capacity];

        long totalKB = (offsetBytes + edgeBytes) / 1024;
        log.info("HebbianGraphMemory initialized: capacity={}, edgeCap={}, maxDegree={}, memory={}KB",
                capacity, edgeCapacity, maxDegree, totalKB);
    }

    private transient boolean bundleManaged = false;

    public static HebbianGraphMemory fromBundle(Arena arena, MemorySegment regionSlice,
                                                 int capacity, int edgeCapacity, int maxDegree,
                                                 EdgeImportance edgeImportance, Path bundlePath, boolean isNew) {
        return new HebbianGraphMemory(arena, regionSlice, capacity, edgeCapacity, maxDegree, edgeImportance, bundlePath, isNew);
    }

    private HebbianGraphMemory(Arena arena, MemorySegment regionSlice,
                               int capacity, int edgeCapacity, int maxDegree,
                               EdgeImportance edgeImportance, Path bundlePath, boolean isNew) {
        super(MEMORY_ID, LAYOUT, capacity, arena, regionSlice,
              isNew ? 0 : (int) MemoryHeader.readCount(regionSlice, 0L),
              true, bundlePath, null, true); // bundleManaged=true
        this.bundleManaged = true;
        this.edgeCapacity = edgeCapacity;
        this.maxDegree = maxDegree;
        this.edgeImportance = edgeImportance;

        long offsetBytes = (long) (capacity + 1) * Integer.BYTES;
        long edgeBytes = (long) edgeCapacity * EDGE_BYTES;

        this.offsets = segment().asSlice(DATA_START, offsetBytes);
        this.edges = segment().asSlice(DATA_START + offsetBytes, edgeBytes);

        this.overflow = new List[capacity];

        if (isNew) {
            writeSmkmHeader(segment(), capacity, edgeCapacity, 0, 0);
            segment().asSlice(DATA_START, offsetBytes + edgeBytes).fill((byte) 0);
            this.currentCycle = 0;
            this.totalEdgeCount = 0;
        } else {
            this.currentCycle = segment().get(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_CURRENT_CYCLE);
            this.totalEdgeCount = (int) MemoryHeader.readCount(segment(), 0L);
        }

        // Migrate legacy standalone hebbian graph if it exists
        if (isNew && bundlePath != null) {
            Path legacyPath = bundlePath.resolveSibling("hebbian.dat");
            if (Files.exists(legacyPath)) {
                log.info("Migrating legacy standalone hebbian.dat to bundle region...");
                HebbianGraphMemory legacy = HebbianGraphMemory.load(legacyPath, capacity, maxDegree, edgeImportance);
                legacy.compactIfNeeded();

                MemorySegment.copy(legacy.offsets, 0, this.offsets, 0, (long) (capacity + 1) * Integer.BYTES);
                MemorySegment.copy(legacy.edges, 0, this.edges, 0, (long) legacy.totalEdgeCount * EDGE_BYTES);

                this.currentCycle = legacy.currentCycle;
                this.totalEdgeCount = legacy.totalEdgeCount;

                writeSmkmHeader(segment(), capacity, edgeCapacity, totalEdgeCount, currentCycle);
                segment().force();
                try {
                    Files.deleteIfExists(legacyPath);
                } catch (IOException e) {
                    log.warn("Failed to delete legacy hebbian.dat after migration: {}", e.getMessage());
                }
            }
        }

        log.info("HebbianGraphMemory initialized (bundle): capacity={}, edgeCap={}, maxDegree={}, edges={}, cycle={}",
                capacity, edgeCapacity, maxDegree, totalEdgeCount, currentCycle);
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
                wal.appendAdjAddEdge(id().toString(), nodeA, nodeB, buf.array());
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

            try (Arena tmpArena = Arena.ofConfined()) {
                MemorySegment tmpEdges = tmpArena.allocate((long) edgeCapacity * EDGE_BYTES);

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
                                tmpEdges.set(ValueLayout.JAVA_INT, edgeOff + EDGE_OFF_NEIGHBOR, e.neighbor);
                                tmpEdges.set(ValueLayout.JAVA_FLOAT, edgeOff + EDGE_OFF_WEIGHT, newWeight);
                                tmpEdges.set(ValueLayout.JAVA_SHORT, edgeOff + EDGE_OFF_LAST_CYCLE, (short) e.lastCycle);
                                tmpEdges.set(ValueLayout.JAVA_BYTE, edgeOff + EDGE_OFF_BRIDGE_SCORE, (byte) bridge);
                                tmpEdges.set(ValueLayout.JAVA_BYTE, edgeOff + EDGE_OFF_EDGE_FLAGS, (byte) e.flags);
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

                MemorySegment.copy(tmpEdges, 0, edges, 0, (long) writePos * EDGE_BYTES);
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
                    log.debug("HebbianGraphMemory decay: {} edges removed (factor={}), {} surviving, cycle={}",
                            removed, String.format("%.3f", decayFactor), totalEdgeCount, currentCycle);
                }
                return removed;
            }
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

    // id(), arena(), segment(), shape(), flush(), close() and the WAL-binding methods are
    // inherited from AbstractGraphMemory/AbstractMemory. schemaVersion() delegates to
    // HebbianLayout.VERSION (the record schema version); the on-disk container is now the
    // kernel SMKM format (magic 0x534D4B4D) rather than the bespoke 24-byte HCSR header.

    @Override
    public HebbianLayout layout() {
        return LAYOUT;
    }

    @Override
    public int size() {
        return totalEdges();
    }

    @Override
    public int addEdge(int fromNode, int toNode, MemorySegment edgeBytes) {
        strengthen(fromNode, toNode, 1.0f);
        return totalEdges();
    }

    @Override
    public void removeEdge(int edgeId) {
        // CSR edges are pruned structurally via decay/compaction, not by edge id.
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
        int active = 0;
        for (int i = 0; i < capacity; i++) {
            if (degree(i) > 0) {
                active++;
            }
        }
        return active;
    }

    public MemoryId memoryId() {
        return id();
    }

    public MemoryShape kernelShape() {
        return MemoryShape.GRAPH;
    }

    // ══════════════════════════════════════════════════════════════
    // PERSISTENCE
    // ══════════════════════════════════════════════════════════════

    @Override
    public void save(Path filePath) {
        if (bundleManaged) {
            graphLock.lock();
            try {
                compactIfNeeded();
                writeSmkmHeader(segment(), capacity, edgeCapacity, totalEdgeCount, currentCycle);
                segment().force();
                log.info("HebbianGraphMemory saved to bundle: capacity={}, edges={}, cycle={}",
                        capacity, totalEdgeCount, currentCycle);
            } finally {
                graphLock.unlock();
            }
            return;
        }

        Path parent = filePath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new SpectorGraphPersistenceException("HebbianGraphMemory", parent, e);
            }
        }

        compactIfNeeded();

        long offsetBytes = (long) (capacity + 1) * Integer.BYTES;
        long edgeBytes = (long) totalEdgeCount * EDGE_BYTES;

        try (FileChannel ch = FileChannel.open(filePath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING);
             Arena confined = Arena.ofConfined()) {

            // [64B SMKM MemoryHeader][16B Hebbian graph sub-header]
            MemorySegment head = confined.allocate(DATA_START);
            writeSmkmHeader(head, capacity, edgeCapacity, totalEdgeCount, currentCycle);
            ch.write(head.asByteBuffer());

            writeSegmentToChannel(offsets, offsetBytes, ch);
            writeSegmentToChannel(edges, edgeBytes, ch);

            ch.force(true);
            log.info("HebbianGraphMemory saved (SMKM): capacity={}, edges={}, file={}",
                    capacity, totalEdgeCount, filePath);

        } catch (IOException e) {
            throw new SpectorGraphPersistenceException("HebbianGraphMemory", filePath, e);
        }
    }

    /**
     * Writes the SMKM 64-byte kernel header plus the 16-byte Hebbian graph sub-header
     * into the first {@link #DATA_START} bytes of {@code head}. Shared by {@link #save}
     * and the {@code HcsrToSmkmStep} codec.
     */
    static void writeSmkmHeader(MemorySegment head, int capacity, int edgeCapacity,
                                int totalEdges, int currentCycle) {
        long now = System.currentTimeMillis();
        MemoryHeader.write(head, 0L, LAYOUT.schemaVersion(), MemoryShape.GRAPH, 0x01,
                capacity, totalEdges, EDGE_BYTES, LAYOUT.layoutId(), now, now);
        head.set(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_EDGE_CAPACITY, edgeCapacity);
        head.set(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_CURRENT_CYCLE, currentCycle);
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
            // SMKM stores its magic in native (little-endian) order via the kernel
            // MemoryHeader, whereas the legacy HGPH/HCSR containers wrote their magic
            // big-endian (ByteBuffer). Read both interpretations to classify the file.
            int beMagic = readMagic(filePath);
            int leMagic = Integer.reverseBytes(beMagic);
            if (leMagic == MemoryHeader.MAGIC) {
                return loadSmkm(filePath, maxDegree, edgeImportance);
            }
            int magic = beMagic;
            if (magic == LEGACY_HGPH_MAGIC || magic == INTERIM_HCSR_MAGIC) {
                // The codec is the single migration authority (#435): it rewrites the file
                // in place to the SMKM CSR container, which loadSmkm then reads. This is the
                // self-healing path for direct load() calls; the factory also runs
                // Codecs.ensureCurrent up front for the same effect.
                log.info("HebbianGraphMemory migrating legacy container 0x{} -> SMKM: {}",
                        Integer.toHexString(magic), filePath);
                Codecs.ensureCurrent(Codecs.defaultRegistry(), MEMORY_ID, LAYOUT,
                        filePath, null, Map.of());
                return loadSmkm(filePath, maxDegree, edgeImportance);
            }
            // File present (checked above) but unrecognized magic — never silently drop (#432).
            throw new IOException("Unrecognized HebbianGraph file magic: 0x"
                    + Integer.toHexString(magic) + " (expected SMKM 0x"
                    + Integer.toHexString(MemoryHeader.MAGIC) + ", HCSR 0x"
                    + Integer.toHexString(INTERIM_HCSR_MAGIC) + " or HGPH 0x"
                    + Integer.toHexString(LEGACY_HGPH_MAGIC) + "): " + filePath);
        } catch (SpectorGraphPersistenceException e) {
            throw e;
        } catch (Exception e) {
            // A file that is present but unreadable is a data-integrity problem, not a
            // "start fresh" signal (#432/#433 TD-04).
            log.error("Failed to load HebbianGraphMemory from {} (file present but unreadable)",
                    filePath, e);
            throw new SpectorGraphPersistenceException("HebbianGraphMemory", filePath, e);
        }
    }

    /** Reads the leading 4-byte magic in big-endian (the order legacy writers used). */
    private static int readMagic(Path filePath) throws IOException {
        try (FileChannel ch = FileChannel.open(filePath, StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocate(4);
            int n = ch.read(buf);
            if (n < 4) {
                throw new IOException("File too small to contain a magic header: " + filePath);
            }
            buf.flip();
            return buf.getInt();
        }
    }

    /** Loads a native SMKM CSR container. */
    private static HebbianGraphMemory loadSmkm(Path filePath, int maxDegree,
                                               EdgeImportance edgeImportance) throws IOException {
        long fileSize = Files.size(filePath);
        if (fileSize < DATA_START) {
            throw new SpectorGraphPersistenceException("HebbianGraphMemory", filePath,
                    new IOException("SMKM file truncated: size=" + fileSize + " < " + DATA_START));
        }
        try (FileChannel ch = FileChannel.open(filePath, StandardOpenOption.READ);
             Arena confined = Arena.ofConfined()) {
            MemorySegment head = confined.allocate(DATA_START);
            ByteBuffer headBuf = head.asByteBuffer();
            while (headBuf.hasRemaining()) {
                if (ch.read(headBuf) < 0) break;
            }
            if (!MemoryHeader.isValid(head, 0L)) {
                throw new SpectorGraphPersistenceException("HebbianGraphMemory", filePath,
                        new IOException("SMKM header magic/CRC invalid"));
            }
            int capacity = (int) MemoryHeader.readCapacity(head, 0L);
            int totalEdges = (int) MemoryHeader.readCount(head, 0L);
            int edgeCap = head.get(ValueLayout.JAVA_INT,
                    MemoryHeader.HEADER_BYTES + SUB_OFF_EDGE_CAPACITY);
            int cycle = head.get(ValueLayout.JAVA_INT,
                    MemoryHeader.HEADER_BYTES + SUB_OFF_CURRENT_CYCLE);

            if (capacity < 0 || totalEdges < 0 || edgeCap < 0) {
                throw new SpectorGraphPersistenceException("HebbianGraphMemory", filePath,
                        new IOException("SMKM header has negative dimensions"));
            }
            long expected = DATA_START + (long) (capacity + 1) * Integer.BYTES
                    + (long) totalEdges * EDGE_BYTES;
            if (fileSize < expected) {
                throw new SpectorGraphPersistenceException("HebbianGraphMemory", filePath,
                        new IOException("SMKM file truncated: size=" + fileSize
                                + " < expected " + expected));
            }

            int effectiveEdgeCap = Math.max(edgeCap, totalEdges);
            HebbianGraphMemory graph =
                    new HebbianGraphMemory(capacity, effectiveEdgeCap, maxDegree, edgeImportance);
            graph.currentCycle = cycle;
            graph.totalEdgeCount = totalEdges;

            readIntoSegment(ch, graph.offsets, (long) (capacity + 1) * Integer.BYTES);
            readIntoSegment(ch, graph.edges, (long) totalEdges * EDGE_BYTES);

            log.info("HebbianGraphMemory loaded (SMKM): capacity={}, edges={}, cycle={}, file={}",
                    capacity, totalEdges, cycle, filePath);
            return graph;
        }
    }

    /**
     * Builds a populated CSR graph from any {@link HebbianGraphBase} by copying its
     * neighbour lists. Used by the {@code HgphToCsrStep} codec to convert legacy HGPH
     * files into the SMKM CSR container. Package-visible for the codec.
     */
    static HebbianGraphMemory fromNeighbors(HebbianGraphBase legacy, int maxDegree,
                                            EdgeImportance edgeImportance) {
        int cap = legacy.capacity();
        int totalEdges = legacy.totalEdges();
        int edgeCap = Math.max(totalEdges * 2, cap * 2);
        HebbianGraphMemory csr = new HebbianGraphMemory(cap, edgeCap, maxDegree, edgeImportance);

        int writePos = 0;
        int[] newOffsets = new int[cap + 1];
        for (int node = 0; node < cap; node++) {
            newOffsets[node] = writePos;
            for (HebbianEdge edge : legacy.neighbors(node)) {
                if (writePos < edgeCap) {
                    long edgeOff = (long) writePos * EDGE_BYTES;
                    csr.edges.set(ValueLayout.JAVA_INT, edgeOff + EDGE_OFF_NEIGHBOR, edge.neighborIndex());
                    csr.edges.set(ValueLayout.JAVA_FLOAT, edgeOff + EDGE_OFF_WEIGHT, edge.weight());
                    csr.edges.set(ValueLayout.JAVA_SHORT, edgeOff + EDGE_OFF_LAST_CYCLE, (short) 0);
                    csr.edges.set(ValueLayout.JAVA_BYTE, edgeOff + EDGE_OFF_BRIDGE_SCORE, (byte) edge.bridgeScore());
                    csr.edges.set(ValueLayout.JAVA_BYTE, edgeOff + EDGE_OFF_EDGE_FLAGS, (byte) 0);
                    writePos++;
                }
            }
        }
        newOffsets[cap] = writePos;
        csr.totalEdgeCount = writePos;
        for (int i = 0; i <= cap; i++) {
            csr.offsets.set(ValueLayout.JAVA_INT, (long) i * Integer.BYTES, newOffsets[i]);
        }
        return csr;
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

            try (Arena tmpArena = Arena.ofConfined()) {
                MemorySegment tmpEdges = tmpArena.allocate((long) edgeCapacity * EDGE_BYTES);

                for (int node = 0; node < capacity; node++) {
                    newOffsets[node] = writePos;
                    List<EdgeData> all = collectAllEdges(node);
                    for (EdgeData e : all) {
                        if (writePos < edgeCapacity) {
                            long edgeOff = (long) writePos * EDGE_BYTES;
                            tmpEdges.set(ValueLayout.JAVA_INT, edgeOff + EDGE_OFF_NEIGHBOR, e.neighbor);
                            tmpEdges.set(ValueLayout.JAVA_FLOAT, edgeOff + EDGE_OFF_WEIGHT, e.weight);
                            tmpEdges.set(ValueLayout.JAVA_SHORT, edgeOff + EDGE_OFF_LAST_CYCLE, (short) e.lastCycle);
                            tmpEdges.set(ValueLayout.JAVA_BYTE, edgeOff + EDGE_OFF_BRIDGE_SCORE, (byte) e.bridgeScore);
                            tmpEdges.set(ValueLayout.JAVA_BYTE, edgeOff + EDGE_OFF_EDGE_FLAGS, (byte) e.flags);
                            writePos++;
                        }
                    }
                }

                int oldTotal = totalEdgeCount + overflowEdgeCount;
                newOffsets[capacity] = writePos;
                totalEdgeCount = writePos;

                MemorySegment.copy(tmpEdges, 0, edges, 0, (long) writePos * EDGE_BYTES);
                for (int i = 0; i <= capacity; i++) {
                    offsets.set(ValueLayout.JAVA_INT, (long) i * Integer.BYTES, newOffsets[i]);
                }

                clearOverflow();
                this.lastCompactionEpochMs = System.currentTimeMillis();
                this.bytesReclaimedLastCycle = Math.max(0L, (long) (oldTotal - writePos) * EDGE_BYTES);
            }
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

    /**
     * Captures a read-only telemetry snapshot of CSR health, overflow occupancy, and compaction stats (MR-08).
     */
    public com.spectrayan.spector.memory.graph.GraphStructureHealthSnapshot structureHealthSnapshot() {
        graphLock.lock();
        try {
            long allocBytes = (long) (capacity + 1) * Integer.BYTES + (long) edgeCapacity * EDGE_BYTES;
            long liveBytes = (long) totalEdgeCount * EDGE_BYTES;
            float fragRatio = allocBytes > 0 ? 1.0f - ((float) liveBytes / (float) allocBytes) : 0.0f;
            float csrOverflowOccupancy = capacity > 0 ? (float) overflowEdgeCount / (float) (capacity * 8) : 0.0f;

            return new com.spectrayan.spector.memory.graph.GraphStructureHealthSnapshot(
                    "hebbian-csr",
                    allocBytes,
                    liveBytes,
                    Math.max(0.0f, fragRatio),
                    Float.NaN,
                    0,
                    csrOverflowOccupancy,
                    lastCompactionEpochMs,
                    bytesReclaimedLastCycle
            );
        } finally {
            graphLock.unlock();
        }
    }
}
