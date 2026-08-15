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
package com.spectrayan.spector.memory.graph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.memory.error.SpectorGraphPersistenceException;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.PrimitiveIterator;

import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryHeader;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.SystemMemoryId;
import com.spectrayan.spector.memory.kernel.layout.HyperEntityLayout;
import com.spectrayan.spector.memory.kernel.shape.AbstractGraphMemory;

import static com.spectrayan.spector.memory.kernel.layout.HyperEntityLayout.DATA_START;
import static com.spectrayan.spector.memory.kernel.layout.HyperEntityLayout.SUB_OFF_ENTITY_CAP;
import static com.spectrayan.spector.memory.kernel.layout.HyperEntityLayout.SUB_OFF_NEXT_HYPEREDGE_ID;
import static com.spectrayan.spector.memory.kernel.layout.HyperEntityLayout.SUB_OFF_NEXT_VERTEX_OFFSET;
import static com.spectrayan.spector.memory.kernel.layout.HyperEntityLayout.SUB_OFF_TOTAL_HYPEREDGES;

/**
 * Hyperedge-based entity layer for graph compression.
 *
 * <h3>Motivation</h3>
 * <p>Binary entity graphs decompose "Alice manages Project Alpha at Spectrayan"
 * into 3 binary edges (9 graph atoms). Hypergraphs collapse this into a single
 * hyperedge connecting 3 entities with roles (4 graph atoms — 55% reduction).</p>
 *
 * <h3>Kernel Substrate (SUBSTRATE mode, #435)</h3>
 * <p>This graph extends the {@link AbstractGraphMemory} kernel substrate: the substrate owns
 * the shared {@link Arena}, the {@link java.util.concurrent.locks.StampedLock} SWMR guard, the
 * kernel identity/shape/layout, and the WAL binding. The kernel {@link #segment()} is the
 * hyperedge slab; HyperEntity keeps all four of its own segments (hedges, vertices, incidence
 * index, incidence list) and overrides {@link #flush()}/{@link #close()} to cover them.</p>
 *
 * <h3>Off-Heap Layout (Panama FFM)</h3>
 * <pre>
 *   Hyperedge Node (32 bytes):
 *     [edgeId:4B][type:4B][weight:4B][vertexCount:4B]
 *     [vertexOffset:4B][memoryIdx:4B][timestamp:8B]
 *
 *   Vertex Entry (8 bytes):
 *     [entityId:4B][roleId:4B]
 *
 *   Incidence Index (4B × entityCapacity):
 *     [hyperedgeListOffset] → per-entity list of participating hyperedges
 *
 *   Incidence List Entry (4B):
 *     [hyperedgeId]
 * </pre>
 *
 * <h3>On-Disk Container (SMKM v2)</h3>
 * <pre>
 *   [64B kernel MemoryHeader (SMKM, shape=GRAPH, layoutId=HYEG, schemaVersion=2)]
 *   [16B HyperEntity sub-header: entityCap, nextHyperedgeId, nextVertexOffset, totalHyperedges]
 *   [hedges: nextHyperedgeId × HEDGE_BYTES]
 *   [vertices: nextVertexOffset × VERTEX_BYTES]
 * </pre>
 * <p>The incidence structures are not persisted; they are rebuilt from the hyperedge/vertex
 * data on load. {@link #load} is the single in-class migration authority (#435, CEO decision —
 * not the codec): it migrates the legacy pure-HYEG container and the interim
 * {@code [64B SMKM][32B HYEG]} hybrid container to SMKM v2 in place, preserving the original as
 * {@code <name>.bak.hyeg}.</p>
 *
 * <h3>Traversal</h3>
 * <p>"Find everything related to entity X" → find all hyperedges containing X,
 * collect co-occurring entities. Cost: O(hyperedges_per_entity × avg_vertices).</p>
 *
 * <h3>Eviction</h3>
 * <p>Per-entity hyperedge cap (HyperEntityLayout.MAX_HYPEREDGES_PER_ENTITY=64). When exceeded,
 * the weakest hyperedge (by weight) is evicted.</p>
 *
 * @see EntityGraphMemory
 */
public final class HyperEntityGraphMemory extends AbstractGraphMemory<HyperEntityLayout> {

    private static final Logger log = LoggerFactory.getLogger(HyperEntityGraphMemory.class);

    /** Kernel identity for the hyper-entity graph. */
    private static final MemoryId MEMORY_ID = SystemMemoryId.HYPERGRAPH.id();
    /** Shared record layout — identifies hyperedge records inside an SMKM container. */
    private static final HyperEntityLayout LAYOUT = new HyperEntityLayout();

    // ── SMKM v2 container framing: single source of truth is HyperEntityLayout (#435, TD-14). ──
    // GRAPH_SUBHEADER_BYTES / SUB_OFF_* field offsets / DATA_START are static-imported from
    // HyperEntityLayout; this class only references them.

    // ── Legacy on-disk container (migrated in-class, #435) ──
    /** Legacy "HYEG" magic used by the bespoke 32-byte custom header. */
    private static final int FILE_MAGIC = 0x48594547; // "HYEG"
    /** Legacy custom-header schema version (both the pure-HYEG and hybrid forms). */
    private static final int LEGACY_FILE_VERSION = 1;
    /** Legacy custom header size in bytes. */
    private static final int LEGACY_HEADER_BYTES = 32;
    // Legacy custom header (native byte order):
    //   [magic:4B][version:4B][entityCap:4B][hyperedgeCap:4B]
    //   [nextHyperedgeId:4B][nextVertexOffset:4B][totalHyperedges:4B][reserved:4B]

    // ── Capacity ──

    private final int hyperedgeCapacity;
    private final int vertexCapacity;
    private final int entityCapacity;
    private final int incidenceCapacity;

    // ── Off-heap segments (all owned by the substrate arena; the hyperedge slab is segment()) ──

    /** Hyperedge segment: HyperEntityLayout.HEDGE_BYTES × hyperedgeCapacity. Alias of {@link #segment()}. */
    private final MemorySegment hedges;

    /** Vertex segment: HyperEntityLayout.VERTEX_BYTES × vertexCapacity. */
    private final MemorySegment vertices;

    /**
     * Incidence index: (entityCapacity + 1) × 4B offsets.
     * incidenceIndex[entity] = start offset into incidence list.
     */
    private final MemorySegment incidenceIndex;

    /**
     * Incidence list: HyperEntityLayout.INCIDENCE_ENTRY_BYTES × incidenceCapacity.
     * Packed lists of hyperedge IDs per entity.
     */
    private final MemorySegment incidenceList;

    // ── State ──

    private int nextHyperedgeId;
    private int nextVertexOffset;
    private int totalHyperedges;

    /**
     * On-heap incidence tracking (rebuilt during load/compaction).
     * incidence[entityId] = list of hyperedge IDs that entity participates in.
     */
    private final List<List<Integer>> incidenceHeap;

    // ══════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ══════════════════════════════════════════════════════════════

    /**
     * Creates a heap-allocated HyperEntityGraphMemory.
     *
     * @param entityCapacity    max number of entities
     * @param hyperedgeCapacity max number of hyperedges
     */
    public HyperEntityGraphMemory(int entityCapacity, int hyperedgeCapacity) {
        this(Init.heap(entityCapacity, hyperedgeCapacity));
    }

    private transient boolean bundleManaged = false;

    public static HyperEntityGraphMemory fromBundle(Arena arena, MemorySegment regionSlice,
                                                    int entityCapacity, int hyperedgeCapacity,
                                                    Path bundlePath, boolean isNew) {
        return new HyperEntityGraphMemory(arena, regionSlice, entityCapacity, hyperedgeCapacity, bundlePath, isNew);
    }

    private HyperEntityGraphMemory(Arena arena, MemorySegment regionSlice,
                                   int entityCapacity, int hyperedgeCapacity,
                                   Path bundlePath, boolean isNew) {
        super(MEMORY_ID, LAYOUT, hyperedgeCapacity, arena, regionSlice,
              isNew ? 0 : (int) MemoryHeader.readCount(regionSlice, 0L),
              true, bundlePath, null, true); // bundleManaged=true
        this.bundleManaged = true;
        this.entityCapacity = entityCapacity;
        this.hyperedgeCapacity = hyperedgeCapacity;
        this.vertexCapacity = hyperedgeCapacity * HyperEntityLayout.MAX_VERTICES_PER_EDGE;
        this.incidenceCapacity = entityCapacity * HyperEntityLayout.MAX_HYPEREDGES_PER_ENTITY;

        long hedgeBytes = (long) HyperEntityLayout.HEDGE_BYTES * hyperedgeCapacity;
        long vertexBytes = (long) HyperEntityLayout.VERTEX_BYTES * vertexCapacity;

        this.hedges = regionSlice.asSlice(DATA_START, hedgeBytes);
        this.vertices = regionSlice.asSlice(DATA_START + hedgeBytes, vertexBytes);

        this.incidenceIndex = arena.allocate((long) (entityCapacity + 1) * Integer.BYTES);
        this.incidenceIndex.fill((byte) 0);
        this.incidenceList = arena.allocate((long) HyperEntityLayout.INCIDENCE_ENTRY_BYTES * incidenceCapacity);
        this.incidenceList.fill((byte) 0);

        if (isNew) {
            writeSmkmHeaderToSegment(regionSlice, entityCapacity, hyperedgeCapacity, 0, 0, 0);
            hedges.fill((byte) 0);
            vertices.fill((byte) 0);
            this.nextHyperedgeId = 0;
            this.nextVertexOffset = 0;
            this.totalHyperedges = 0;
        } else {
            this.nextHyperedgeId = regionSlice.get(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_NEXT_HYPEREDGE_ID);
            this.nextVertexOffset = regionSlice.get(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_NEXT_VERTEX_OFFSET);
            this.totalHyperedges = regionSlice.get(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_TOTAL_HYPEREDGES);
        }

        this.incidenceHeap = new ArrayList<>(entityCapacity);
        for (int i = 0; i < entityCapacity; i++) {
            incidenceHeap.add(new ArrayList<>(4));
        }
        if (nextHyperedgeId > 0) {
            rebuildIncidenceLists();
        }

        // Migrate legacy standalone HyperEntityGraphMemory if it exists
        if (isNew && bundlePath != null) {
            Path legacyPath = bundlePath.resolveSibling("hypergraph.dat");
            if (Files.exists(legacyPath)) {
                log.info("Migrating legacy standalone hypergraph.dat to bundle region...");
                try {
                    HyperEntityGraphMemory legacy = HyperEntityGraphMemory.load(legacyPath, entityCapacity, hyperedgeCapacity);
                    MemorySegment.copy(legacy.hedges, 0, this.hedges, 0, (long) legacy.nextHyperedgeId * HyperEntityLayout.HEDGE_BYTES);
                    MemorySegment.copy(legacy.vertices, 0, this.vertices, 0, (long) legacy.nextVertexOffset * HyperEntityLayout.VERTEX_BYTES);

                    this.nextHyperedgeId = legacy.nextHyperedgeId;
                    this.nextVertexOffset = legacy.nextVertexOffset;
                    this.totalHyperedges = legacy.totalHyperedges;

                    writeSmkmHeaderToSegment(regionSlice, entityCapacity, hyperedgeCapacity, nextHyperedgeId, nextVertexOffset, totalHyperedges);
                    regionSlice.force();

                    rebuildIncidenceLists();
                    legacy.close();
                    Files.deleteIfExists(legacyPath);
                } catch (Exception e) {
                    log.warn("Failed to migrate legacy hypergraph.dat: {}", e.getMessage());
                }
            }
        }

        log.info("HyperEntityGraphMemory initialized (bundle): entities={}, hyperedges={}/{}",
                entityCapacity, totalHyperedges, hyperedgeCapacity);
    }

    /**
     * Single delegating constructor. Wraps the pre-built arena + hyperedge slab as the kernel
     * substrate {@link #segment()} and adopts the HyperEntity-owned vertex/incidence segments.
     */
    private HyperEntityGraphMemory(Init init) {
        super(MEMORY_ID, LAYOUT, init.hyperedgeCapacity, init.arena, init.hedges,
                init.totalHyperedges, false, null, null);
        this.entityCapacity = init.entityCapacity;
        this.hyperedgeCapacity = init.hyperedgeCapacity;
        this.vertexCapacity = init.vertexCapacity;
        this.incidenceCapacity = init.incidenceCapacity;
        this.hedges = init.hedges;
        this.vertices = init.vertices;
        this.incidenceIndex = init.incidenceIndex;
        this.incidenceList = init.incidenceList;
        this.nextHyperedgeId = init.nextHyperedgeId;
        this.nextVertexOffset = init.nextVertexOffset;
        this.totalHyperedges = init.totalHyperedges;

        // On-heap incidence lists for fast lookup.
        this.incidenceHeap = new ArrayList<>(entityCapacity);
        for (int i = 0; i < entityCapacity; i++) {
            incidenceHeap.add(new ArrayList<>(4));
        }
        if (nextHyperedgeId > 0) {
            rebuildIncidenceLists();
        }

        long totalKB = ((long) HyperEntityLayout.HEDGE_BYTES * hyperedgeCapacity
                + (long) HyperEntityLayout.VERTEX_BYTES * vertexCapacity
                + (long) (entityCapacity + 1) * Integer.BYTES
                + (long) HyperEntityLayout.INCIDENCE_ENTRY_BYTES * incidenceCapacity) / 1024;

        log.info("HyperEntityGraphMemory initialized: entities={}, hyperedges={}/{}, memory={}KB",
                entityCapacity, totalHyperedges, hyperedgeCapacity, totalKB);
    }

    // ══════════════════════════════════════════════════════════════
    // CONSTRUCTION HOLDER + BUILDERS (super() must run first)
    // ══════════════════════════════════════════════════════════════

    /** Immutable bundle of everything the delegating constructor needs. */
    private record Init(int entityCapacity, int hyperedgeCapacity, int vertexCapacity, int incidenceCapacity,
                        Arena arena, MemorySegment hedges, MemorySegment vertices,
                        MemorySegment incidenceIndex, MemorySegment incidenceList,
                        int nextHyperedgeId, int nextVertexOffset, int totalHyperedges) {

        /** Allocates a fresh set of zeroed segments in a new shared arena. */
        static Init heap(int entityCapacity, int hyperedgeCapacity) {
            int vertexCapacity = hyperedgeCapacity * HyperEntityLayout.MAX_VERTICES_PER_EDGE;
            int incidenceCapacity = entityCapacity * HyperEntityLayout.MAX_HYPEREDGES_PER_ENTITY;
            Arena arena = Arena.ofShared();

            MemorySegment hedges = arena.allocate((long) HyperEntityLayout.HEDGE_BYTES * hyperedgeCapacity);
            hedges.fill((byte) 0);
            MemorySegment vertices = arena.allocate((long) HyperEntityLayout.VERTEX_BYTES * vertexCapacity);
            vertices.fill((byte) 0);
            MemorySegment incidenceIndex = arena.allocate((long) (entityCapacity + 1) * Integer.BYTES);
            incidenceIndex.fill((byte) 0);
            MemorySegment incidenceList =
                    arena.allocate((long) HyperEntityLayout.INCIDENCE_ENTRY_BYTES * incidenceCapacity);
            incidenceList.fill((byte) 0);

            return new Init(entityCapacity, hyperedgeCapacity, vertexCapacity, incidenceCapacity,
                    arena, hedges, vertices, incidenceIndex, incidenceList, 0, 0, 0);
        }

        /**
         * Reads an SMKM v2 container into a fresh set of segments. Capacities are the max of the
         * on-disk values and the caller-supplied defaults so a re-opened graph never shrinks.
         */
        static Init fromSmkmFile(Path filePath, int defaultEntityCap, int defaultHedgeCap)
                throws IOException {
            try (FileChannel ch = FileChannel.open(filePath, StandardOpenOption.READ);
                 Arena headerArena = Arena.ofConfined()) {
                long fileSize = ch.size();
                if (fileSize < DATA_START) {
                    throw new IOException("SMKM hyper-entity file truncated: size=" + fileSize
                            + " < " + DATA_START + ": " + filePath);
                }
                MemorySegment head = headerArena.allocate(DATA_START);
                ByteBuffer hb = head.asByteBuffer();
                ch.position(0);
                while (hb.hasRemaining() && ch.read(hb) >= 0) {
                    // fill header
                }
                if (!MemoryHeader.isValid(head, 0L)
                        || MemoryHeader.readShape(head, 0L) != MemoryShape.GRAPH
                        || MemoryHeader.readLayoutId(head, 0L) != LAYOUT.layoutId()) {
                    throw new IOException("invalid SMKM hyper-entity header: " + filePath);
                }

                int loadedHedgeCap = (int) MemoryHeader.readCapacity(head, 0L);
                int loadedTotal = (int) MemoryHeader.readCount(head, 0L);
                int loadedEntityCap = head.get(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_ENTITY_CAP);
                int nextId = head.get(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_NEXT_HYPEREDGE_ID);
                int nextVertexOff = head.get(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_NEXT_VERTEX_OFFSET);
                int totalHedges = head.get(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_TOTAL_HYPEREDGES);

                if (loadedHedgeCap < 0 || loadedEntityCap < 0 || nextId < 0 || nextVertexOff < 0) {
                    throw new IOException("invalid SMKM hyper-entity sub-header: " + filePath);
                }

                int entityCapacity = Math.max(loadedEntityCap, defaultEntityCap);
                int hyperedgeCapacity = Math.max(loadedHedgeCap, defaultHedgeCap);
                int vertexCapacity = hyperedgeCapacity * HyperEntityLayout.MAX_VERTICES_PER_EDGE;
                int incidenceCapacity = entityCapacity * HyperEntityLayout.MAX_HYPEREDGES_PER_ENTITY;

                long hedgeBytes = (long) nextId * HyperEntityLayout.HEDGE_BYTES;
                long vertexBytes = (long) nextVertexOff * HyperEntityLayout.VERTEX_BYTES;
                if (fileSize < DATA_START + hedgeBytes + vertexBytes) {
                    throw new IOException("SMKM hyper-entity data truncated: size=" + fileSize
                            + " < " + (DATA_START + hedgeBytes + vertexBytes) + ": " + filePath);
                }
                if (nextId > hyperedgeCapacity || nextVertexOff > vertexCapacity) {
                    throw new IOException("SMKM hyper-entity counts exceed capacity: nextId=" + nextId
                            + " (cap " + hyperedgeCapacity + "), nextVertexOffset=" + nextVertexOff
                            + " (cap " + vertexCapacity + "): " + filePath);
                }

                Arena arena = Arena.ofShared();
                MemorySegment hedges =
                        arena.allocate((long) HyperEntityLayout.HEDGE_BYTES * hyperedgeCapacity);
                hedges.fill((byte) 0);
                MemorySegment vertices =
                        arena.allocate((long) HyperEntityLayout.VERTEX_BYTES * vertexCapacity);
                vertices.fill((byte) 0);
                MemorySegment incidenceIndex = arena.allocate((long) (entityCapacity + 1) * Integer.BYTES);
                incidenceIndex.fill((byte) 0);
                MemorySegment incidenceList =
                        arena.allocate((long) HyperEntityLayout.INCIDENCE_ENTRY_BYTES * incidenceCapacity);
                incidenceList.fill((byte) 0);

                ch.position(DATA_START);
                readIntoSegment(ch, hedges, hedgeBytes);
                readIntoSegment(ch, vertices, vertexBytes);

                return new Init(entityCapacity, hyperedgeCapacity, vertexCapacity, incidenceCapacity,
                        arena, hedges, vertices, incidenceIndex, incidenceList,
                        nextId, nextVertexOff, totalHedges);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════════════════════════

    /**
     * Returns the maximum entity capacity.
     */
    public int entityCapacity() { return entityCapacity; }

    /**
     * Returns the total number of active hyperedges.
     */
    public int totalHyperedges() { return totalHyperedges; }

    /**
     * Adds a hyperedge connecting multiple entities.
     *
     * <p>Each vertex is an (entityId, roleId) pair. The roleId encodes the
     * entity's role in the relationship (e.g., SUBJECT=1, OBJECT=2, CONTEXT=3).</p>
     *
     * @param vertexEntities entity IDs participating in this hyperedge
     * @param vertexRoles    role IDs for each entity (must have same length)
     * @param type           relationship type ID
     * @param weight         initial edge weight
     * @param memoryIdx      index of the source memory
     * @param timestamp      creation timestamp (epoch millis)
     * @return hyperedge ID, or -1 if the graph is full
     */
    public int addHyperedge(int[] vertexEntities, int[] vertexRoles,
                             int type, float weight, int memoryIdx, long timestamp) {
        if (vertexEntities == null || vertexEntities.length < 2
                || vertexEntities.length > HyperEntityLayout.MAX_VERTICES_PER_EDGE) {
            log.warn("Invalid hyperedge: vertex count {} (must be 2-{})",
                    vertexEntities != null ? vertexEntities.length : 0, HyperEntityLayout.MAX_VERTICES_PER_EDGE);
            return -1;
        }
        if (vertexRoles == null || vertexRoles.length != vertexEntities.length) {
            log.warn("Vertex roles must match vertex count");
            return -1;
        }

        long stamp = lock.writeLock();
        try {
            if (nextHyperedgeId >= hyperedgeCapacity) {
                log.warn("HyperEntityGraphMemory full: {} hyperedges at capacity", hyperedgeCapacity);
                return -1;
            }

            int vertexCount = vertexEntities.length;
            if (nextVertexOffset + vertexCount > vertexCapacity) {
                log.warn("Vertex segment full: {} at capacity {}", nextVertexOffset, vertexCapacity);
                return -1;
            }

            // Check per-entity participation cap
            for (int entityId : vertexEntities) {
                if (entityId < 0 || entityId >= entityCapacity) continue;
                List<Integer> participation = incidenceHeap.get(entityId);
                if (participation.size() >= HyperEntityLayout.MAX_HYPEREDGES_PER_ENTITY) {
                    // Evict weakest hyperedge for this entity
                    evictWeakestHyperedge(entityId);
                }
            }

            int edgeId = nextHyperedgeId++;
            totalHyperedges++;

            // Write-ahead durability (ADR-0003 #460 / #417): log the full hyperedge before writing
            // the segments so a crash between checkpoints can replay it. Replaced the previous dead
            // appendRecordWrite path (HyperEntity is a GraphMemory, not a RecordMemory).
            if (wal != null && !bypassWal) {
                wal.appendHyperedgeAdd(id().toString(), vertexEntities, vertexRoles,
                        type, weight, memoryIdx, timestamp);
            }

            // Write hyperedge header
            long hedgeOff = (long) edgeId * HyperEntityLayout.HEDGE_BYTES;
            hedges.set(ValueLayout.JAVA_INT, hedgeOff + HyperEntityLayout.HEDGE_OFF_EDGE_ID, edgeId);
            hedges.set(ValueLayout.JAVA_INT, hedgeOff + HyperEntityLayout.HEDGE_OFF_TYPE, type);
            hedges.set(ValueLayout.JAVA_FLOAT, hedgeOff + HyperEntityLayout.HEDGE_OFF_WEIGHT, weight);
            hedges.set(ValueLayout.JAVA_INT, hedgeOff + HyperEntityLayout.HEDGE_OFF_VERTEX_COUNT, vertexCount);
            hedges.set(ValueLayout.JAVA_INT, hedgeOff + HyperEntityLayout.HEDGE_OFF_VERTEX_OFFSET, nextVertexOffset);
            hedges.set(ValueLayout.JAVA_INT, hedgeOff + HyperEntityLayout.HEDGE_OFF_MEMORY_IDX, memoryIdx);
            hedges.set(ValueLayout.JAVA_LONG, hedgeOff + HyperEntityLayout.HEDGE_OFF_TIMESTAMP, timestamp);

            // Write vertex entries
            for (int i = 0; i < vertexCount; i++) {
                long vOff = (long) (nextVertexOffset + i) * HyperEntityLayout.VERTEX_BYTES;
                vertices.set(ValueLayout.JAVA_INT, vOff + HyperEntityLayout.VERTEX_OFF_ENTITY_ID, vertexEntities[i]);
                vertices.set(ValueLayout.JAVA_INT, vOff + HyperEntityLayout.VERTEX_OFF_ROLE_ID, vertexRoles[i]);
            }
            nextVertexOffset += vertexCount;

            // Update incidence lists
            for (int entityId : vertexEntities) {
                if (entityId >= 0 && entityId < entityCapacity) {
                    incidenceHeap.get(entityId).add(edgeId);
                }
            }

            return edgeId;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * Gets a hyperedge by ID.
     *
     * @param edgeId hyperedge ID
     * @return the hyperedge record, or null if invalid/deleted
     */
    public HyperEdge getHyperedge(int edgeId) {
        // Validated read lock (NOT optimistic): a concurrent writer mutates hyperedge/vertex
        // contents in place, so a reader must observe a consistent record (#435 SWMR).
        long stamp = lock.readLock();
        try {
            return getHyperedgeLocked(edgeId);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    /** Core of {@link #getHyperedge}; the caller must hold at least the read lock. */
    private HyperEdge getHyperedgeLocked(int edgeId) {
        if (edgeId < 0 || edgeId >= nextHyperedgeId) return null;

        long hedgeOff = (long) edgeId * HyperEntityLayout.HEDGE_BYTES;
        int type = hedges.get(ValueLayout.JAVA_INT, hedgeOff + HyperEntityLayout.HEDGE_OFF_TYPE);
        float weight = hedges.get(ValueLayout.JAVA_FLOAT, hedgeOff + HyperEntityLayout.HEDGE_OFF_WEIGHT);
        int vertexCount = hedges.get(ValueLayout.JAVA_INT, hedgeOff + HyperEntityLayout.HEDGE_OFF_VERTEX_COUNT);
        int vertexOffset = hedges.get(ValueLayout.JAVA_INT, hedgeOff + HyperEntityLayout.HEDGE_OFF_VERTEX_OFFSET);
        int memoryIdx = hedges.get(ValueLayout.JAVA_INT, hedgeOff + HyperEntityLayout.HEDGE_OFF_MEMORY_IDX);
        long timestamp = hedges.get(ValueLayout.JAVA_LONG, hedgeOff + HyperEntityLayout.HEDGE_OFF_TIMESTAMP);

        if (vertexCount == 0) return null; // Deleted

        List<HyperEdgeVertex> verts = new ArrayList<>(vertexCount);
        for (int i = 0; i < vertexCount; i++) {
            long vOff = (long) (vertexOffset + i) * HyperEntityLayout.VERTEX_BYTES;
            int entityId = vertices.get(ValueLayout.JAVA_INT, vOff + HyperEntityLayout.VERTEX_OFF_ENTITY_ID);
            int roleId = vertices.get(ValueLayout.JAVA_INT, vOff + HyperEntityLayout.VERTEX_OFF_ROLE_ID);
            verts.add(new HyperEdgeVertex(entityId, roleId));
        }

        return new HyperEdge(edgeId, type, weight, memoryIdx, timestamp, verts);
    }

    /**
     * Finds all hyperedges that a given entity participates in.
     *
     * @param entityId entity ID
     * @return list of hyperedge records, sorted by descending weight
     */
    public List<HyperEdge> findHyperedgesForEntity(int entityId) {
        // Validated read lock: incidenceHeap is a plain ArrayList mutated by writers
        // (addHyperedge/deleteHyperedge), so a reader must not iterate it concurrently.
        long stamp = lock.readLock();
        try {
            return findHyperedgesForEntityLocked(entityId);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    /** Core of {@link #findHyperedgesForEntity}; the caller must hold at least the read lock. */
    private List<HyperEdge> findHyperedgesForEntityLocked(int entityId) {
        if (entityId < 0 || entityId >= entityCapacity) return List.of();

        List<Integer> edgeIds = incidenceHeap.get(entityId);
        List<HyperEdge> result = new ArrayList<>(edgeIds.size());

        for (int edgeId : edgeIds) {
            HyperEdge edge = getHyperedgeLocked(edgeId);
            if (edge != null) {
                result.add(edge);
            }
        }

        result.sort((a, b) -> Float.compare(b.weight(), a.weight()));
        return result;
    }

    /**
     * Finds all entities co-occurring with a given entity via hyperedges.
     *
     * <p>This is the hypergraph equivalent of "neighbors" in a binary graph.</p>
     *
     * @param entityId entity ID
     * @return set of co-occurring entity IDs (excluding the query entity)
     */
    public Set<Integer> findCoOccurringEntities(int entityId) {
        long stamp = lock.readLock();
        try {
            return findCoOccurringEntitiesLocked(entityId);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    /** Core of {@link #findCoOccurringEntities}; the caller must hold at least the read lock. */
    private Set<Integer> findCoOccurringEntitiesLocked(int entityId) {
        Set<Integer> result = new HashSet<>();

        List<HyperEdge> edges = findHyperedgesForEntityLocked(entityId);
        for (HyperEdge edge : edges) {
            for (HyperEdgeVertex v : edge.vertices()) {
                if (v.entityId() != entityId) {
                    result.add(v.entityId());
                }
            }
        }

        return result;
    }

    /**
     * Finds all memory indices related to a given starting entity via hyperedges recursively up to maxHops.
     *
     * @param startEntity starting entity ID
     * @param maxHops     maximum traversal hops
     * @return set of memory indices
     */
    public Set<Integer> collectMemories(int startEntity, int maxHops) {
        Set<Integer> memories = new HashSet<>();
        Set<Integer> visitedEntities = new HashSet<>();
        long stamp = lock.readLock();
        try {
            collectMemoriesRecursive(startEntity, maxHops, visitedEntities, memories);
        } finally {
            lock.unlockRead(stamp);
        }
        return memories;
    }

    /** Recursive traversal core; the caller must hold at least the read lock. */
    private void collectMemoriesRecursive(int entityId, int hopsLeft, Set<Integer> visited, Set<Integer> memories) {
        if (hopsLeft < 0 || !visited.add(entityId)) {
            return;
        }
        List<HyperEdge> edges = findHyperedgesForEntityLocked(entityId);
        for (HyperEdge edge : edges) {
            if (edge.memoryIdx() >= 0) {
                memories.add(edge.memoryIdx());
            }
            if (hopsLeft > 0) {
                for (HyperEdgeVertex v : edge.vertices()) {
                    collectMemoriesRecursive(v.entityId(), hopsLeft - 1, visited, memories);
                }
            }
        }
    }

    /**
     * Strengthens a hyperedge's weight (LTP reinforcement).
     *
     * @param edgeId     hyperedge ID
     * @param weightDelta amount to add to the weight
     */
    public void strengthen(int edgeId, float weightDelta) {
        if (edgeId < 0 || edgeId >= nextHyperedgeId) return;

        long stamp = lock.writeLock();
        try {
            if (edgeId >= nextHyperedgeId) return;
            long hedgeOff = (long) edgeId * HyperEntityLayout.HEDGE_BYTES;
            float weight = hedges.get(ValueLayout.JAVA_FLOAT, hedgeOff + HyperEntityLayout.HEDGE_OFF_WEIGHT);
            hedges.set(ValueLayout.JAVA_FLOAT, hedgeOff + HyperEntityLayout.HEDGE_OFF_WEIGHT, weight + weightDelta);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * Decays all hyperedge weights and evicts those below threshold.
     *
     * @param decayFactor multiplicative decay (e.g., 0.9 = 10% decay)
     * @param minWeight   minimum weight to survive eviction
     * @return number of hyperedges evicted
     */
    public int decayHyperedges(float decayFactor, float minWeight) {
        long stamp = lock.writeLock();
        try {
            int evicted = 0;

            for (int i = 0; i < nextHyperedgeId; i++) {
                long hedgeOff = (long) i * HyperEntityLayout.HEDGE_BYTES;
                int vertexCount = hedges.get(ValueLayout.JAVA_INT, hedgeOff + HyperEntityLayout.HEDGE_OFF_VERTEX_COUNT);
                if (vertexCount == 0) continue; // already deleted

                float weight = hedges.get(ValueLayout.JAVA_FLOAT, hedgeOff + HyperEntityLayout.HEDGE_OFF_WEIGHT);
                float newWeight = weight * decayFactor;

                if (newWeight < minWeight) {
                    // Evict: zero out vertex count (tombstone)
                    deleteHyperedge(i);
                    evicted++;
                } else {
                    hedges.set(ValueLayout.JAVA_FLOAT, hedgeOff + HyperEntityLayout.HEDGE_OFF_WEIGHT, newWeight);
                }
            }

            if (evicted > 0) {
                log.debug("HyperEntityGraphMemory decay: {} evicted (factor={}, min={}), {} remaining",
                        evicted, decayFactor, minWeight, totalHyperedges);
            }
            return evicted;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * Boosts the weight of existing hyperedges connecting two entities (ADR-0003 #459).
     *
     * <p>Scans hyperedges incident to {@code entityA} for 2-vertex edges that also contain
     * {@code entityB}. For each match, the weight is increased by {@code boost} (capped at
     * the maximum float value). This replaces the legacy {@code EntityGraphMemory.boostEdgeWeight}
     * for the STC cross-capture use case in reflection.</p>
     *
     * @param entityA first entity id
     * @param entityB second entity id
     * @param boost   additive weight increase
     * @return {@code true} if at least one matching hyperedge was boosted
     */
    public boolean boostHyperedgeWeight(int entityA, int entityB, float boost) {
        long stamp = lock.writeLock();
        try {
            boolean boosted = false;
            // Walk the incidence list for entityA
            if (entityA < 0 || entityA >= entityCapacity) return false;
            long idxOff = (long) entityA * 2L * HyperEntityLayout.INCIDENCE_ENTRY_BYTES;
            int start = incidenceIndex.get(ValueLayout.JAVA_INT, idxOff);
            int count = incidenceIndex.get(ValueLayout.JAVA_INT, idxOff + HyperEntityLayout.INCIDENCE_ENTRY_BYTES);

            for (int i = start; i < start + count; i++) {
                int edgeId = incidenceList.get(ValueLayout.JAVA_INT,
                        (long) i * HyperEntityLayout.INCIDENCE_ENTRY_BYTES);
                long hedgeOff = (long) edgeId * HyperEntityLayout.HEDGE_BYTES;
                int vc = hedges.get(ValueLayout.JAVA_INT, hedgeOff + HyperEntityLayout.HEDGE_OFF_VERTEX_COUNT);
                if (vc != 2) continue; // only boost binary (2-vertex) relationship edges

                int vOff = hedges.get(ValueLayout.JAVA_INT, hedgeOff + HyperEntityLayout.HEDGE_OFF_VERTEX_OFFSET);
                // Check both vertices for entityB
                boolean foundB = false;
                for (int v = 0; v < vc; v++) {
                    long vertOff = (long) (vOff + v) * HyperEntityLayout.VERTEX_BYTES;
                    int eid = vertices.get(ValueLayout.JAVA_INT, vertOff + HyperEntityLayout.VERTEX_OFF_ENTITY_ID);
                    if (eid == entityB) { foundB = true; break; }
                }
                if (foundB) {
                    float current = hedges.get(ValueLayout.JAVA_FLOAT,
                            hedgeOff + HyperEntityLayout.HEDGE_OFF_WEIGHT);
                    float newWeight = Math.min(current + boost, Float.MAX_VALUE);
                    hedges.set(ValueLayout.JAVA_FLOAT,
                            hedgeOff + HyperEntityLayout.HEDGE_OFF_WEIGHT, newWeight);
                    boosted = true;
                }
            }
            return boosted;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * Returns memory usage in bytes (off-heap only).
     */
    public long memoryUsageBytes() {
        return hedges.byteSize() + vertices.byteSize()
                + incidenceIndex.byteSize() + incidenceList.byteSize();
    }

    // ══════════════════════════════════════════════════════════════
    // PERSISTENCE: SMKM v2 container + in-class legacy migration (#435)
    // ══════════════════════════════════════════════════════════════

    /**
     * Saves the hypergraph as a fresh SMKM v2 container:
     * {@code [64B kernel header][16B sub-header][hedges][vertices]}. The incidence structures
     * are not persisted — they are rebuilt on load.
     */
    public void save(Path filePath) {
        if (bundleManaged) {
            long stamp = lock.readLock();
            try {
                writeSmkmHeaderToSegment(segment(), entityCapacity, hyperedgeCapacity,
                        nextHyperedgeId, nextVertexOffset, totalHyperedges);
                segment().force();
                log.info("HyperEntityGraphMemory saved to bundle: {} hyperedges, {} vertices",
                        totalHyperedges, nextVertexOffset);
            } finally {
                lock.unlockRead(stamp);
            }
            return;
        }
        Path parent = filePath.getParent();
        long stamp = lock.readLock();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            long hedgeBytes = (long) nextHyperedgeId * HyperEntityLayout.HEDGE_BYTES;
            long vertexBytes = (long) nextVertexOffset * HyperEntityLayout.VERTEX_BYTES;
            try (FileChannel ch = FileChannel.open(filePath,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                writeSmkmHeaderToChannel(ch, entityCapacity, hyperedgeCapacity,
                        nextHyperedgeId, nextVertexOffset, totalHyperedges);
                ch.position(DATA_START);
                writeSegment(ch, hedges, hedgeBytes);
                writeSegment(ch, vertices, vertexBytes);
                ch.force(true);
            }
            log.info("HyperEntityGraphMemory saved (SMKM v2): {} hyperedges, {} vertices -> {}",
                    totalHyperedges, nextVertexOffset, filePath);
        } catch (IOException e) {
            throw new SpectorGraphPersistenceException("HyperEntityGraphMemory", filePath, e);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    /**
     * Loads a hypergraph from disk, or creates a fresh (heap) graph if the file is absent.
     *
     * <p>This is the single in-class migration authority (#435, CEO decision — not the codec):
     * it classifies the container by magic and self-heals legacy files.</p>
     *
     * <ul>
     *   <li>SMKM v2 ({@code 0x534D4B4D} magic, kernel-header schemaVersion &ge; 2) — opened directly.</li>
     *   <li>Legacy hybrid ({@code 0x534D4B4D} magic, schemaVersion == 1, a 32-byte "HYEG" custom
     *       header at offset 64) — migrated in place to SMKM v2.</li>
     *   <li>Legacy pure ({@code 0x48594547} "HYEG" magic, 32-byte header) — migrated in place.</li>
     *   <li>Present but unreadable/unknown/truncated — throws {@link SpectorGraphPersistenceException}
     *       (never a silent empty graph, #432/#433 TD-04).</li>
     *   <li>Absent — a fresh heap graph.</li>
     * </ul>
     */
    public static HyperEntityGraphMemory load(Path filePath, int entityCapacity, int hyperedgeCapacity) {
        if (filePath == null || !Files.exists(filePath)) {
            log.info("HyperEntityGraphMemory file not found, creating fresh: {}", filePath);
            return new HyperEntityGraphMemory(entityCapacity, hyperedgeCapacity);
        }

        try {
            long size = Files.size(filePath);
            if (size < 8) {
                throw new IOException("file too small to contain a header: " + size + " bytes");
            }
            int firstInt = peekIntNative(filePath, 0);
            if (firstInt == MemoryHeader.MAGIC) {
                int schemaVersion = peekIntNative(filePath, 4);
                if (schemaVersion >= 2) {
                    return openSmkm(filePath, entityCapacity, hyperedgeCapacity);
                }
                // Interim hybrid: [64B SMKM header (schemaVersion==1)][32B HYEG custom header][data].
                log.info("HyperEntityGraphMemory migrating legacy hybrid container -> SMKM v2: {}", filePath);
                migrateLegacyToSmkm(filePath, MemoryHeader.HEADER_BYTES);
                return openSmkm(filePath, entityCapacity, hyperedgeCapacity);
            }
            if (firstInt == FILE_MAGIC) {
                log.info("HyperEntityGraphMemory migrating legacy pure-HYEG container -> SMKM v2: {}", filePath);
                migrateLegacyToSmkm(filePath, 0);
                return openSmkm(filePath, entityCapacity, hyperedgeCapacity);
            }
            // Legacy files may have been written with big-endian byte order (ASCII: "HYEG")
            // while peekIntNative reads with native order (little-endian on x86), producing
            // the byte-swapped value. Handle both endiannesses defensively.
            if (firstInt == Integer.reverseBytes(FILE_MAGIC)) {
                log.info("HyperEntityGraphMemory migrating legacy pure-HYEG container (BE magic) -> SMKM v2: {}", filePath);
                migrateLegacyToSmkm(filePath, 0);
                return openSmkm(filePath, entityCapacity, hyperedgeCapacity);
            }
            // File present but unrecognized magic — corrupt, not absent. Returning a fresh empty
            // graph would silently discard the user's data (#432/#433 TD-04).
            throw new IOException("unrecognized HyperEntityGraph file magic: 0x"
                    + Integer.toHexString(firstInt) + " (expected SMKM 0x"
                    + Integer.toHexString(MemoryHeader.MAGIC) + " or HYEG 0x"
                    + Integer.toHexString(FILE_MAGIC) + "): " + filePath);
        } catch (SpectorGraphPersistenceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to load HyperEntityGraphMemory (file present but unreadable): {}", filePath, e);
            throw new SpectorGraphPersistenceException("HyperEntityGraphMemory", filePath, e);
        }
    }

    /** Opens an SMKM v2 hyper-entity file and rebuilds the incidence structures. */
    private static HyperEntityGraphMemory openSmkm(Path filePath, int defaultEntityCap, int defaultHedgeCap)
            throws IOException {
        HyperEntityGraphMemory graph = new HyperEntityGraphMemory(
                Init.fromSmkmFile(filePath, defaultEntityCap, defaultHedgeCap));
        log.info("HyperEntityGraphMemory loaded (SMKM v2): {} hyperedges, file={}",
                graph.totalHyperedges, filePath);
        return graph;
    }

    /**
     * Migrates a legacy container to SMKM v2 in place, preserving the original as
     * {@code <name>.bak.hyeg}. {@code customHeaderPos} is the byte offset of the 32-byte "HYEG"
     * custom header: 0 for the pure-HYEG container, 64 for the {@code [64B SMKM][32B HYEG]} hybrid.
     */
    private static void migrateLegacyToSmkm(Path file, int customHeaderPos) throws IOException {
        try (FileChannel in = FileChannel.open(file, StandardOpenOption.READ)) {
            long fileSize = in.size();
            long legacyDataStart = (long) customHeaderPos + LEGACY_HEADER_BYTES;
            if (fileSize < legacyDataStart) {
                throw new IOException("legacy HYEG header truncated: size=" + fileSize
                        + " < " + legacyDataStart + ": " + file);
            }
            ByteBuffer hb = ByteBuffer.allocate(LEGACY_HEADER_BYTES).order(ByteOrder.nativeOrder());
            in.position(customHeaderPos);
            readFully(in, hb);
            hb.flip();
            int magic = hb.getInt();
            // Legacy files may have been written with either native or big-endian byte order.
            // Detect which order the magic matches and re-read the header fields accordingly.
            ByteOrder headerOrder;
            if (magic == FILE_MAGIC) {
                headerOrder = ByteOrder.nativeOrder();
            } else if (magic == Integer.reverseBytes(FILE_MAGIC)) {
                headerOrder = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN
                        ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
                hb.order(headerOrder);
                hb.position(0);
                magic = hb.getInt(); // re-read with correct order
            } else {
                throw new IOException("invalid legacy HYEG header (magic=0x" + Integer.toHexString(magic)
                        + "): " + file);
            }
            int version = hb.getInt();
            int entityCap = hb.getInt();
            int hedgeCap = hb.getInt();
            int nextId = hb.getInt();
            int nextVertexOff = hb.getInt();
            int totalHedges = hb.getInt();
            // remaining 4 bytes reserved

            if (entityCap < 0 || hedgeCap < 0 || nextId < 0 || nextVertexOff < 0) {
                throw new IOException("invalid legacy HYEG header (magic=0x" + Integer.toHexString(magic)
                        + ", version=" + version + "): " + file);
            }

            long hedgeBytes = (long) nextId * HyperEntityLayout.HEDGE_BYTES;
            long vertexBytes = (long) nextVertexOff * HyperEntityLayout.VERTEX_BYTES;
            long dataBytes = hedgeBytes + vertexBytes;
            if (fileSize < legacyDataStart + dataBytes) {
                throw new IOException("legacy HYEG file truncated: size=" + fileSize
                        + " < " + (legacyDataStart + dataBytes) + ": " + file);
            }

            Path bak = file.resolveSibling(file.getFileName() + ".bak.hyeg");
            Files.copy(file, bak, StandardCopyOption.REPLACE_EXISTING);

            Path tmp = file.resolveSibling(file.getFileName() + ".smkm.tmp");
            try (FileChannel out = FileChannel.open(tmp,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                writeSmkmHeaderToChannel(out, entityCap, hedgeCap, nextId, nextVertexOff, totalHedges);
                out.position(DATA_START);
                long moved = 0;
                while (moved < dataBytes) {
                    long n = in.transferTo(legacyDataStart + moved, dataBytes - moved, out);
                    if (n <= 0) break;
                    moved += n;
                }
                if (moved < dataBytes) {
                    throw new IOException("legacy HYEG -> SMKM region copy short: " + moved + " < " + dataBytes);
                }
                out.force(true);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void writeSmkmHeaderToSegment(MemorySegment head, int entityCap, int hedgeCap,
                                                 int nextId, int nextVertexOff, int totalHedges) {
        long now = System.currentTimeMillis();
        MemoryHeader.write(head, 0L, LAYOUT.schemaVersion(), MemoryShape.GRAPH, 0x00,
                hedgeCap, totalHedges, HyperEntityLayout.HEDGE_BYTES, LAYOUT.layoutId(), now, now);
        head.set(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_ENTITY_CAP, entityCap);
        head.set(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_NEXT_HYPEREDGE_ID, nextId);
        head.set(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_NEXT_VERTEX_OFFSET, nextVertexOff);
        head.set(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_TOTAL_HYPEREDGES, totalHedges);
    }

    /** Writes the 64-byte kernel header + 16-byte HyperEntity sub-header to the start of {@code ch}. */
    private static void writeSmkmHeaderToChannel(FileChannel ch, int entityCap, int hedgeCap,
                                                 int nextId, int nextVertexOff, int totalHedges)
            throws IOException {
        try (Arena confined = Arena.ofConfined()) {
            MemorySegment head = confined.allocate(DATA_START);
            writeSmkmHeaderToSegment(head, entityCap, hedgeCap, nextId, nextVertexOff, totalHedges);
            ByteBuffer buf = head.asByteBuffer();
            ch.position(0);
            while (buf.hasRemaining()) {
                ch.write(buf);
            }
        }
    }

    /** Reads a native-order int at {@code offset} from the start of {@code filePath}. */
    private static int peekIntNative(Path filePath, int offset) throws IOException {
        try (FileChannel ch = FileChannel.open(filePath, StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder());
            ch.position(offset);
            readFully(ch, buf);
            buf.flip();
            return buf.getInt();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // RECORDS
    // ══════════════════════════════════════════════════════════════

    /**
     * A hyperedge connecting multiple entities with typed relationships.
     */
    public record HyperEdge(int edgeId, int type, float weight, int memoryIdx,
                              long timestamp, List<HyperEdgeVertex> vertices) {}

    /**
     * A vertex in a hyperedge — an entity with a role.
     */
    public record HyperEdgeVertex(int entityId, int roleId) {}

    // ══════════════════════════════════════════════════════════════
    // ROLE CONSTANTS
    // ══════════════════════════════════════════════════════════════

    /** Entity is the subject/agent of the relationship. */
    public static final int ROLE_SUBJECT = 1;
    /** Entity is the object/patient of the relationship. */
    public static final int ROLE_OBJECT = 2;
    /** Entity provides context (location, time, etc.). */
    public static final int ROLE_CONTEXT = 3;
    /** Entity is an instrument or method. */
    public static final int ROLE_INSTRUMENT = 4;
    /** Entity belongs to the correcting (winner) memory in a CONTRADICTS edge (CADP #507). */
    public static final int ROLE_CORRECTOR = 5;
    /** Entity belongs to the corrected (loser) memory in a CONTRADICTS edge (CADP #507). */
    public static final int ROLE_CORRECTED = 6;
    /** Unspecified role. */
    public static final int ROLE_UNSPECIFIED = 0;

    // ══════════════════════════════════════════════════════════════
    // KERNEL INTEGRATION
    // ══════════════════════════════════════════════════════════════
    //
    // id(), layout(), arena(), segment() (= the hyperedge slab), capacity(), schemaVersion(),
    // shape(), and WAL binding are inherited from the substrate (AbstractGraphMemory/
    // AbstractMemory). size()/flush()/close() are overridden below to reflect the live hyperedge
    // count and to cover all four HyperEntity-owned segments.

    @Override
    public int size() {
        return totalHyperedges;
    }

    @Override
    public void flush() {
        // Heap-backed graph: force() is only valid on mapped segments. Guard each so a
        // heap-only graph is a no-op while still covering all four segments if ever mapped.
        forceIfMapped(hedges);
        forceIfMapped(vertices);
        forceIfMapped(incidenceIndex);
        forceIfMapped(incidenceList);
    }

    private static void forceIfMapped(MemorySegment seg) {
        if (seg != null && seg.isMapped()) {
            seg.force();
        }
    }

    @Override
    public int addEdge(int fromNode, int toNode, MemorySegment edgeBytes) { return -1; }

    @Override
    public void removeEdge(int edgeId) {
        long stamp = lock.writeLock();
        try {
            deleteHyperedge(edgeId);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public PrimitiveIterator.OfInt neighbours(int nodeId) {
        return findCoOccurringEntities(nodeId).stream().mapToInt(Integer::intValue).iterator();
    }

    @Override
    public int edgeCount() { return totalHyperedges; }

    @Override
    public int nodeCount() { return entityCapacity; }

    @Override
    public void close() {
        log.info("HyperEntityGraphMemory closing: {} hyperedges", totalHyperedges);
        if (!bundleManaged && arena != null && arena.scope().isAlive()) {
            arena.close();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // INTERNAL — mutators; caller MUST hold the substrate write lock
    // ══════════════════════════════════════════════════════════════

    /** Tombstones a hyperedge and removes it from the incidence lists. Requires the write lock. */
    private void deleteHyperedge(int edgeId) {
        if (edgeId < 0 || edgeId >= nextHyperedgeId) return;
        long hedgeOff = (long) edgeId * HyperEntityLayout.HEDGE_BYTES;
        int vertexCount = hedges.get(ValueLayout.JAVA_INT, hedgeOff + HyperEntityLayout.HEDGE_OFF_VERTEX_COUNT);
        if (vertexCount == 0) return; // already deleted

        // Remove from incidence lists
        int vertexOffset = hedges.get(ValueLayout.JAVA_INT, hedgeOff + HyperEntityLayout.HEDGE_OFF_VERTEX_OFFSET);
        for (int i = 0; i < vertexCount; i++) {
            long vOff = (long) (vertexOffset + i) * HyperEntityLayout.VERTEX_BYTES;
            int entityId = vertices.get(ValueLayout.JAVA_INT, vOff + HyperEntityLayout.VERTEX_OFF_ENTITY_ID);
            if (entityId >= 0 && entityId < entityCapacity) {
                incidenceHeap.get(entityId).remove(Integer.valueOf(edgeId));
            }
        }

        // Tombstone: zero vertex count
        hedges.set(ValueLayout.JAVA_INT, hedgeOff + HyperEntityLayout.HEDGE_OFF_VERTEX_COUNT, 0);
        totalHyperedges--;
    }

    /** Evicts the weakest hyperedge an entity participates in. Requires the write lock. */
    private void evictWeakestHyperedge(int entityId) {
        List<Integer> participation = incidenceHeap.get(entityId);
        if (participation.isEmpty()) return;

        float minWeight = Float.MAX_VALUE;
        int minEdgeId = -1;

        for (int edgeId : participation) {
            long hedgeOff = (long) edgeId * HyperEntityLayout.HEDGE_BYTES;
            float weight = hedges.get(ValueLayout.JAVA_FLOAT, hedgeOff + HyperEntityLayout.HEDGE_OFF_WEIGHT);
            if (weight < minWeight) {
                minWeight = weight;
                minEdgeId = edgeId;
            }
        }

        if (minEdgeId >= 0) {
            log.debug("Evicting weakest hyperedge {} (weight={}) for entity {}",
                    minEdgeId, minWeight, entityId);
            deleteHyperedge(minEdgeId);
        }
    }

    /**
     * Rebuilds the on-heap incidence lists from the hyperedge/vertex segments. Invoked during
     * construction from a loaded file (single-threaded), so it does not acquire the lock.
     */
    private void rebuildIncidenceLists() {
        for (List<Integer> list : incidenceHeap) {
            list.clear();
        }

        for (int i = 0; i < nextHyperedgeId; i++) {
            long hedgeOff = (long) i * HyperEntityLayout.HEDGE_BYTES;
            int vertexCount = hedges.get(ValueLayout.JAVA_INT, hedgeOff + HyperEntityLayout.HEDGE_OFF_VERTEX_COUNT);
            if (vertexCount == 0) continue;

            int vertexOffset = hedges.get(ValueLayout.JAVA_INT, hedgeOff + HyperEntityLayout.HEDGE_OFF_VERTEX_OFFSET);
            for (int j = 0; j < vertexCount; j++) {
                long vOff = (long) (vertexOffset + j) * HyperEntityLayout.VERTEX_BYTES;
                int entityId = vertices.get(ValueLayout.JAVA_INT, vOff + HyperEntityLayout.VERTEX_OFF_ENTITY_ID);
                if (entityId >= 0 && entityId < entityCapacity) {
                    incidenceHeap.get(entityId).add(i);
                }
            }
        }
    }

    // ── IO Helpers ──

    private static void writeSegment(FileChannel ch, MemorySegment segment, long bytes) throws IOException {
        if (bytes <= 0) return;
        long written = 0;
        int chunkSize = 64 * 1024;
        while (written < bytes) {
            int toWrite = (int) Math.min(chunkSize, bytes - written);
            ByteBuffer buf = segment.asSlice(written, toWrite).asByteBuffer().asReadOnlyBuffer();
            ch.write(buf);
            written += toWrite;
        }
    }

    private static void readIntoSegment(FileChannel ch, MemorySegment segment, long bytes) throws IOException {
        if (bytes <= 0) return;
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

    /** Reads until {@code buf} is full or EOF; throws if EOF is hit early. */
    private static void readFully(FileChannel ch, ByteBuffer buf) throws IOException {
        while (buf.hasRemaining()) {
            if (ch.read(buf) < 0) {
                throw new IOException("unexpected EOF while reading " + buf.capacity() + " bytes");
            }
        }
    }
}
