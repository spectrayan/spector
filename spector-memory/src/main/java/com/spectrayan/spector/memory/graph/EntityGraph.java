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

import java.io.IOException;

import com.spectrayan.spector.memory.DataEncryptor;
import com.spectrayan.spector.memory.StorageLayout;
import com.spectrayan.spector.memory.error.SpectorGraphPersistenceException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Off-heap entity-relationship graph for multi-hop knowledge traversal.
 *
 * <h3>Biological Analog: Semantic Network</h3>
 * <p>The brain's semantic memory stores knowledge as a network of concepts
 * connected by typed relationships. "Alice manages Project Alpha" is stored
 * as: [Alice]—MANAGES→[Project Alpha]. This graph enables multi-hop reasoning:
 * "Find memories about projects managed by the person I met yesterday."</p>
 *
 * <h3>Architecture</h3>
 * <ul>
 *   <li>Off-heap entity nodes backed by {@link MemorySegment}</li>
 *   <li>Off-heap typed edges with fixed-width adjacency</li>
 *   <li>Separate off-heap adjacency segment for entity→memory references (unlimited)</li>
 *   <li>On-heap name→id index for O(1) entity lookup (case-insensitive)</li>
 *   <li>Max 32 edges per entity, unlimited memory references per entity</li>
 *   <li>Persistence via save/load with "EGPH" magic header</li>
 * </ul>
 *
 * <h3>Entity→Memory Adjacency (V2)</h3>
 * <p>Each entity can reference an unlimited number of memories via a separate
 * adjacency segment. Each adjacency entry carries a weight that supports:</p>
 * <ul>
 *   <li><b>LTP reinforcement</b>: Weight increases when an entity is re-mentioned</li>
 *   <li><b>LTD decay</b>: Weights decay each reflection cycle; weak links are pruned</li>
 *   <li><b>Fan-effect attenuation</b>: Recall boost scales as 1/√(refCount), modeling
 *       ACT-R spreading activation dilution</li>
 * </ul>
 *
 * <h3>Layout</h3>
 * <pre>
 *   Entity Node (64 bytes, 8-byte aligned — V2):
 *     [type:4B][pad:4B][nameHash:8B]
 *     [adjOffset:4B][adjCount:4B][adjCapacity:4B][pad:4B]
 *     [pad:4B][degree:4B][edgeStart:4B][pad:20B]
 *
 *   Entity Edge (12 bytes):
 *     [targetId:4B][relationType:4B][weight:4B]
 *
 *   Adjacency Entry (8 bytes):
 *     [memIdx:4B][weight:4B]
 * </pre>
 */
public final class EntityGraph implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EntityGraph.class);



    /** Default adjacency slots allocated per entity on first link. */
    static final int DEFAULT_ADJ_PER_ENTITY = 8;

    /** LTP weight increment when an entity is re-mentioned in a memory. */
    private static final float LTP_REINFORCEMENT = 0.2f;

    /** Initial weight for a new entity→memory link. */
    private static final float INITIAL_LINK_WEIGHT = 1.0f;

    /** Default maximum edges per entity (configurable). */
    public static final int DEFAULT_MAX_DEGREE = 48;

    /** Maximum adjacency entries per entity (for mmap pre-allocation). */
    static final int MAX_ADJ_PER_ENTITY = 64;

    // ── mmap File Header (32 bytes) ──
    private static final int MMAP_MAGIC = 0x45474D4D; // "EGMM"
    private static final int MMAP_VERSION = 2;
    /** V1 edge bytes — for migration detection. */
    private static final int V1_EDGE_BYTES = 12;
    private static final int MMAP_HEADER_BYTES = 32;
    // Header layout: [magic:4B][version:4B][entityCap:4B][edgeCap:4B]
    //                [entityCount:4B][edgeCount:4B][adjCap:4B][adjHwm:4B]

    // ── Entity Node Layout (64 bytes, 8-byte aligned — V2) ──
    static final int ENTITY_NODE_BYTES = 64;
    private static final long ENT_OFF_TYPE = 0;             // 4B (entity type id)
    // pad: 4B for alignment
    private static final long ENT_OFF_NAME_HASH = 8;        // 8B (8-byte aligned)
    private static final long ENT_OFF_ADJ_OFFSET = 16;      // 4B (index into adjacency segment)
    private static final long ENT_OFF_ADJ_COUNT = 20;       // 4B (number of adjacency entries)
    private static final long ENT_OFF_ADJ_CAPACITY = 24;    // 4B (allocated adjacency slots)
    // pad: 4B (28-31)
    // pad: 4B (32-35, was refCount in V1)
    private static final long ENT_OFF_DEGREE = 36;           // 4B
    private static final long ENT_OFF_EDGE_START = 40;       // 4B (index into edge segment)
    // pad: 20B to reach 64B


    /**
     * Entity Edge Layout (16 bytes, V2).
     *
     * <pre>
     *   [0-3]   target      (4B int: target entity ID)
     *   [4-7]   relType     (4B int: relation type ID)
     *   [8-11]  weight      (4B float)
     *   [12-13] lastCycle   (2B short, unsigned: 0-65535)
     *   [14]    bridgeScore (1B unsigned: 0-255)
     *   [15]    flags       (1B reserved)
     * </pre>
     */
    static final int EDGE_BYTES = 16;
    private static final long EDGE_OFF_TARGET = 0;        // 4B
    private static final long EDGE_OFF_REL_TYPE = 4;      // 4B
    private static final long EDGE_OFF_WEIGHT = 8;        // 4B (float)
    private static final long EDGE_OFF_LAST_CYCLE = 12;   // 2B (short)
    private static final long EDGE_OFF_BRIDGE_SCORE = 14; // 1B
    private static final long EDGE_OFF_EDGE_FLAGS = 15;   // 1B

    // ── Adjacency Entry Layout (8 bytes) ──
    static final int ADJ_ENTRY_BYTES = 8;
    private static final long ADJ_OFF_MEM_IDX = 0;      // 4B (memory slot index)
    private static final long ADJ_OFF_WEIGHT = 4;        // 4B (float weight)

    private final Arena arena;
    private final MemorySegment entitySegment;
    private final MemorySegment edgeSegment;
    private MemorySegment adjacencySegment;
    private final int entityCapacity;
    private final int edgeCapacity;
    private int entityCount;
    private int edgeCount;
    private int adjSegmentCapacity;  // total entries the adjacency segment can hold
    private int adjHighWaterMark;    // next free entry index in adjacency segment

    /** On-heap name→entityId index for O(1) lookup (case-insensitive). */
    private final ConcurrentHashMap<String, Integer> nameIndex = new ConcurrentHashMap<>();
    private final ReentrantLock graphLock = new ReentrantLock();

    /** True when segments are backed by mmap'd files (DISK mode). */
    private final boolean fileBacked;
    /** The underlying FileChannel for mmap mode (null for heap mode). */
    private final FileChannel mappedChannel;
    /** Path to the mmap file (null for heap mode). */
    private final Path mmapFilePath;

    /** Optional encryptor for name index persistence (set by enterprise layer). */
    private volatile DataEncryptor dataEncryptor;

    /** Open-schema entity type registry (String ↔ int). */
    private final TypeRegistry entityTypeRegistry;
    /** Open-schema relation type registry (String ↔ int). */
    private final TypeRegistry relationTypeRegistry;

    /** Maximum edges per entity (configurable via constructor). */
    private final int maxDegree;

    /** Reflection cycle counter — incremented externally. */
    private int currentCycle;

    /** Edge importance scorer (configurable weights). */
    private final EdgeImportance edgeImportance;

    /**
     * Creates a new entity graph with default max degree.
     *
     * @param entityCapacity maximum number of entities
     * @param edgeCapacity   maximum number of edges
     */
    public EntityGraph(int entityCapacity, int edgeCapacity) {
        this(entityCapacity, edgeCapacity, DEFAULT_MAX_DEGREE, EdgeImportance.DEFAULT);
    }

    /**
     * Creates a new entity graph with configurable max degree.
     *
     * @param entityCapacity maximum number of entities
     * @param edgeCapacity   maximum number of edges
     * @param maxDegree      maximum edges per entity
     * @param edgeImportance edge importance scorer
     */
    public EntityGraph(int entityCapacity, int edgeCapacity, int maxDegree, EdgeImportance edgeImportance) {
        this.entityCapacity = entityCapacity;
        this.edgeCapacity = edgeCapacity;
        this.maxDegree = maxDegree;
        this.edgeImportance = edgeImportance;
        this.currentCycle = 0;
        this.entityCount = 0;
        this.edgeCount = 0;
        this.arena = Arena.ofShared();
        this.entitySegment = arena.allocate((long) ENTITY_NODE_BYTES * entityCapacity);
        this.edgeSegment = arena.allocate((long) EDGE_BYTES * edgeCapacity);
        this.adjSegmentCapacity = entityCapacity * DEFAULT_ADJ_PER_ENTITY;
        this.adjHighWaterMark = 0;
        this.adjacencySegment = arena.allocate((long) ADJ_ENTRY_BYTES * adjSegmentCapacity);
        entitySegment.fill((byte) 0);
        edgeSegment.fill((byte) 0);
        adjacencySegment.fill((byte) 0);
        this.fileBacked = false;
        this.mappedChannel = null;
        this.mmapFilePath = null;
        this.entityTypeRegistry = TypeRegistry.seeded("entity-type", EntityType.SEED);
        this.relationTypeRegistry = TypeRegistry.seeded("relation-type", RelationType.SEED);

        log.info("EntityGraph initialized (heap): entities={}, edges={}, maxDegree={}, adjSlots={}, memory={}KB",
                entityCapacity, edgeCapacity, maxDegree, adjSegmentCapacity,
                ((long) ENTITY_NODE_BYTES * entityCapacity
                        + (long) EDGE_BYTES * edgeCapacity
                        + (long) ADJ_ENTRY_BYTES * adjSegmentCapacity) / 1024);
    }

    /**
     * Creates or opens a file-backed (mmap) entity graph with default max degree.
     */
    public EntityGraph(Path filePath, int entityCapacity, int edgeCapacity) {
        this(filePath, entityCapacity, edgeCapacity, DEFAULT_MAX_DEGREE, EdgeImportance.DEFAULT);
    }

    /**
     * Creates or opens a file-backed (mmap) entity graph with configurable max degree.
     *
     * <p>Uses a single contiguous mmap file with layout:
     * <pre>
     *   [Header: 32B][EntitySegment][EdgeSegment][AdjacencySegment]
     * </pre>
     * V1 files (EDGE_BYTES=12) are detected and re-initialized as V2 (one-way migration).</p>
     *
     * @param filePath       path to the graph file
     * @param entityCapacity maximum number of entities
     * @param edgeCapacity   maximum number of edges
     * @param maxDegree      maximum edges per entity
     * @param edgeImportance edge importance scorer
     */
    public EntityGraph(Path filePath, int entityCapacity, int edgeCapacity,
                       int maxDegree, EdgeImportance edgeImportance) {
        this.maxDegree = maxDegree;
        this.edgeImportance = edgeImportance;
        this.currentCycle = 0;

        Path parent = filePath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new SpectorGraphPersistenceException("EntityGraph", parent, e);
            }
        }

        this.mmapFilePath = filePath;
        int adjCap = entityCapacity * MAX_ADJ_PER_ENTITY;
        long entityBytes = (long) ENTITY_NODE_BYTES * entityCapacity;
        long edgeBytes = (long) EDGE_BYTES * edgeCapacity;
        long adjBytes = (long) ADJ_ENTRY_BYTES * adjCap;
        boolean isNew = !Files.exists(filePath);

        try {
            FileChannel ch = FileChannel.open(filePath,
                    StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            this.mappedChannel = ch;

            if (isNew || ch.size() < MMAP_HEADER_BYTES) {
                // Brand new file — write V2 header
                writeNewMmapHeader(ch, entityCapacity, edgeCapacity, adjCap);

                long totalBytes = MMAP_HEADER_BYTES + entityBytes + edgeBytes + adjBytes;
                if (ch.size() < totalBytes) {
                    ch.position(totalBytes - 1);
                    ch.write(ByteBuffer.wrap(new byte[]{0}));
                }
                ch.force(true);
                this.entityCapacity = entityCapacity;
                this.edgeCapacity = edgeCapacity;
                this.entityCount = 0;
                this.edgeCount = 0;
                this.adjSegmentCapacity = adjCap;
                this.adjHighWaterMark = 0;
            } else {
                // Existing file — read header
                ByteBuffer header = ByteBuffer.allocate(MMAP_HEADER_BYTES);
                ch.position(0);
                ch.read(header);
                header.flip();
                int magic = header.getInt();
                int version = header.getInt();
                int fileEntityCap = header.getInt();
                int fileEdgeCap = header.getInt();
                int fileEntityCount = header.getInt();
                int fileEdgeCount = header.getInt();
                int fileAdjCap = header.getInt();
                int fileAdjHwm = header.getInt();

                if (magic != MMAP_MAGIC) {
                    log.warn("Invalid EntityGraph mmap file (bad magic), reinitializing: {}", filePath);
                    ch.truncate(0);
                    writeNewMmapHeader(ch, entityCapacity, edgeCapacity, adjCap);
                    long totalBytes = MMAP_HEADER_BYTES + entityBytes + edgeBytes + adjBytes;
                    ch.position(totalBytes - 1);
                    ch.write(ByteBuffer.wrap(new byte[]{0}));
                    ch.force(true);
                    this.entityCapacity = entityCapacity;
                    this.edgeCapacity = edgeCapacity;
                    this.entityCount = 0;
                    this.edgeCount = 0;
                    this.adjSegmentCapacity = adjCap;
                    this.adjHighWaterMark = 0;
                } else if (version < MMAP_VERSION) {
                    // V1 → V2 one-way migration: reinitialize with wider edges
                    log.warn("Migrating EntityGraph v{} → v{} (one-way): {}", version, MMAP_VERSION, filePath);
                    ch.truncate(0);
                    writeNewMmapHeader(ch, fileEntityCap, fileEdgeCap, fileAdjCap);
                    entityBytes = (long) ENTITY_NODE_BYTES * fileEntityCap;
                    edgeBytes = (long) EDGE_BYTES * fileEdgeCap;
                    adjBytes = (long) ADJ_ENTRY_BYTES * fileAdjCap;
                    long totalBytes = MMAP_HEADER_BYTES + entityBytes + edgeBytes + adjBytes;
                    ch.position(totalBytes - 1);
                    ch.write(ByteBuffer.wrap(new byte[]{0}));
                    ch.force(true);
                    this.entityCapacity = fileEntityCap;
                    this.edgeCapacity = fileEdgeCap;
                    this.entityCount = 0;
                    this.edgeCount = 0;
                    this.adjSegmentCapacity = fileAdjCap;
                    this.adjHighWaterMark = 0;
                } else {
                    this.entityCapacity = fileEntityCap;
                    this.edgeCapacity = fileEdgeCap;
                    this.entityCount = fileEntityCount;
                    this.edgeCount = fileEdgeCount;
                    this.adjSegmentCapacity = fileAdjCap;
                    this.adjHighWaterMark = fileAdjHwm;
                    entityBytes = (long) ENTITY_NODE_BYTES * fileEntityCap;
                    edgeBytes = (long) EDGE_BYTES * fileEdgeCap;
                    adjBytes = (long) ADJ_ENTRY_BYTES * fileAdjCap;
                }
            }

            // mmap the three regions after the header
            this.arena = Arena.ofShared();
            long offset = MMAP_HEADER_BYTES;
            this.entitySegment = ch.map(FileChannel.MapMode.READ_WRITE, offset, entityBytes, arena);
            offset += entityBytes;
            this.edgeSegment = ch.map(FileChannel.MapMode.READ_WRITE, offset, edgeBytes, arena);
            offset += edgeBytes;
            this.adjacencySegment = ch.map(FileChannel.MapMode.READ_WRITE, offset, adjBytes, arena);
            this.fileBacked = true;

            // Load TypeRegistries from sidecar files if present
            if (parent != null) {
                this.entityTypeRegistry = TypeRegistry.load(
                        StorageLayout.entityTypes(parent), "entity-type", EntityType.SEED);
                this.relationTypeRegistry = TypeRegistry.load(
                        StorageLayout.relationTypes(parent), "relation-type", RelationType.SEED);
            } else {
                this.entityTypeRegistry = TypeRegistry.seeded("entity-type", EntityType.SEED);
                this.relationTypeRegistry = TypeRegistry.seeded("relation-type", RelationType.SEED);
            }

            log.info("EntityGraph initialized (mmap): entities={}/{}, edges={}/{}, maxDegree={}, version={}, file={}",
                    this.entityCount, this.entityCapacity, this.edgeCount, this.edgeCapacity,
                    maxDegree, MMAP_VERSION, filePath.getFileName());

        } catch (IOException e) {
            throw new SpectorGraphPersistenceException("EntityGraph", filePath, e);
        }
    }

    private static void writeNewMmapHeader(FileChannel ch, int entityCap, int edgeCap, int adjCap) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(MMAP_HEADER_BYTES);
        header.putInt(MMAP_MAGIC);
        header.putInt(MMAP_VERSION);
        header.putInt(entityCap);
        header.putInt(edgeCap);
        header.putInt(0); // entityCount
        header.putInt(0); // edgeCount
        header.putInt(adjCap);
        header.putInt(0); // adjHighWaterMark
        header.flip();
        ch.position(0);
        ch.write(header);
    }

    /**
     * Creates a new entity graph with default edge capacity.
     *
     * @param entityCapacity maximum number of entities
     */
    public EntityGraph(int entityCapacity) {
        this(entityCapacity, entityCapacity * DEFAULT_MAX_DEGREE);
    }

    /**
     * Package-private factory for constructing a graph from pre-loaded segments.
     * Used by {@link EntityGraphSerializer#load}.
     */
    static EntityGraph fromLoaded(int entityCapacity, int edgeCapacity, int entityCount, int edgeCount,
                                  Arena arena, MemorySegment entitySegment, MemorySegment edgeSegment,
                                  MemorySegment adjacencySegment, int adjSegmentCapacity, int adjHighWaterMark,
                                  ConcurrentHashMap<String, Integer> nameIndex,
                                  TypeRegistry entityTypeRegistry, TypeRegistry relationTypeRegistry) {
        return new EntityGraph(entityCapacity, edgeCapacity, entityCount, edgeCount,
                arena, entitySegment, edgeSegment, adjacencySegment,
                adjSegmentCapacity, adjHighWaterMark, nameIndex,
                entityTypeRegistry, relationTypeRegistry);
    }


    /**
     * Private constructor for loading from pre-existing segments.
     */
    private EntityGraph(int entityCapacity, int edgeCapacity, int entityCount, int edgeCount,
                         Arena arena, MemorySegment entitySegment, MemorySegment edgeSegment,
                         MemorySegment adjacencySegment, int adjSegmentCapacity, int adjHighWaterMark,
                         ConcurrentHashMap<String, Integer> nameIndex,
                         TypeRegistry entityTypeRegistry, TypeRegistry relationTypeRegistry) {
        this.entityCapacity = entityCapacity;
        this.edgeCapacity = edgeCapacity;
        this.entityCount = entityCount;
        this.edgeCount = edgeCount;
        this.maxDegree = DEFAULT_MAX_DEGREE;
        this.edgeImportance = EdgeImportance.DEFAULT;
        this.currentCycle = 0;
        this.arena = arena;
        this.entitySegment = entitySegment;
        this.edgeSegment = edgeSegment;
        this.adjacencySegment = adjacencySegment;
        this.adjSegmentCapacity = adjSegmentCapacity;
        this.adjHighWaterMark = adjHighWaterMark;
        this.nameIndex.putAll(nameIndex);
        this.fileBacked = false;
        this.mappedChannel = null;
        this.mmapFilePath = null;
        this.entityTypeRegistry = entityTypeRegistry;
        this.relationTypeRegistry = relationTypeRegistry;
    }

    /**
     * Adds an entity to the graph, or returns the existing ID if already present.
     *
     * <p>Entity names are case-insensitive and normalized to lowercase.</p>
     *
     * @param name entity name
     * @param type entity type
     * @return entity ID (index into entity segment)
     */
    public int addEntity(String name, String type) {
        if (name == null || name.isBlank()) return -1;
        if (type == null || type.isBlank()) type = "OTHER";

        String normalized = name.trim().toLowerCase(Locale.ROOT);
        Integer existing = nameIndex.get(normalized);
        if (existing != null) return existing;

        if (entityCount >= entityCapacity) {
            log.warn("EntityGraph full ({} entities), rejecting '{}'", entityCapacity, name);
            return -1;
        }

        int entityId = entityCount++;
        long offset = (long) entityId * ENTITY_NODE_BYTES;
        int typeId = entityTypeRegistry.getOrRegister(type);

        entitySegment.set(ValueLayout.JAVA_INT, offset + ENT_OFF_TYPE, typeId);
        entitySegment.set(ValueLayout.JAVA_LONG, offset + ENT_OFF_NAME_HASH, normalized.hashCode());
        entitySegment.set(ValueLayout.JAVA_INT, offset + ENT_OFF_ADJ_OFFSET, -1); // no adj block yet
        entitySegment.set(ValueLayout.JAVA_INT, offset + ENT_OFF_ADJ_COUNT, 0);
        entitySegment.set(ValueLayout.JAVA_INT, offset + ENT_OFF_ADJ_CAPACITY, 0);
        entitySegment.set(ValueLayout.JAVA_INT, offset + ENT_OFF_DEGREE, 0);
        entitySegment.set(ValueLayout.JAVA_INT, offset + ENT_OFF_EDGE_START, -1);

        nameIndex.put(normalized, entityId);

        log.trace("Entity added: id={}, name='{}', type={}", entityId, name, type);
        return entityId;
    }

    /**
     * Adds a typed relation between two entities.
     *
     * @param fromEntity source entity ID
     * @param toEntity   target entity ID
     * @param type       relation type
     */
    public void addRelation(int fromEntity, int toEntity, String type) {
        graphLock.lock();
        try {
        if (fromEntity < 0 || fromEntity >= entityCount) return;
        if (toEntity < 0 || toEntity >= entityCount) return;
        if (fromEntity == toEntity) return;

        int typeId = relationTypeRegistry.getOrRegister(type != null ? type : "OTHER");
        long entityOffset = (long) fromEntity * ENTITY_NODE_BYTES;
        int degree = entitySegment.get(ValueLayout.JAVA_INT, entityOffset + ENT_OFF_DEGREE);
        int edgeStart = entitySegment.get(ValueLayout.JAVA_INT, entityOffset + ENT_OFF_EDGE_START);

        // Check if relation already exists (strengthen weight + update recency)
        if (edgeStart >= 0) {
            for (int i = 0; i < degree; i++) {
                long edgeOffset = (long) (edgeStart + i) * EDGE_BYTES;
                int target = edgeSegment.get(ValueLayout.JAVA_INT, edgeOffset + EDGE_OFF_TARGET);
                int relType = edgeSegment.get(ValueLayout.JAVA_INT, edgeOffset + EDGE_OFF_REL_TYPE);
                if (target == toEntity && relType == typeId) {
                    // Strengthen existing edge and update recency
                    float weight = edgeSegment.get(ValueLayout.JAVA_FLOAT, edgeOffset + EDGE_OFF_WEIGHT);
                    edgeSegment.set(ValueLayout.JAVA_FLOAT, edgeOffset + EDGE_OFF_WEIGHT, weight + 1.0f);
                    edgeSegment.set(ValueLayout.JAVA_SHORT, edgeOffset + EDGE_OFF_LAST_CYCLE, (short) currentCycle);
                    return;
                }
            }
        }

        // Add new edge — evict lowest-importance if at capacity
        if (degree >= maxDegree) {
            evictLowestImportanceEdge(fromEntity, entityOffset, edgeStart, degree, toEntity, typeId);
            return;
        }

        // Allocate edge block if first edge for this entity, or relocate if non-contiguous
        if (edgeStart < 0) {
            if (edgeCount >= edgeCapacity) {
                log.warn("EntityGraph edge capacity full ({}), rejecting edge", edgeCapacity);
                return;
            }
            edgeStart = edgeCount;
            entitySegment.set(ValueLayout.JAVA_INT, entityOffset + ENT_OFF_EDGE_START, edgeStart);
        } else if (edgeStart + degree != edgeCount) {
            // Relocate existing edges to the end of the segment to keep them contiguous
            int newEdgeStart = edgeCount;
            if (newEdgeStart + degree + 1 > edgeCapacity) {
                log.warn("EntityGraph edge capacity full ({}), rejecting edge", edgeCapacity);
                return;
            }
            MemorySegment.copy(
                    edgeSegment, (long) edgeStart * EDGE_BYTES,
                    edgeSegment, (long) newEdgeStart * EDGE_BYTES,
                    (long) degree * EDGE_BYTES);
            edgeStart = newEdgeStart;
            entitySegment.set(ValueLayout.JAVA_INT, entityOffset + ENT_OFF_EDGE_START, edgeStart);
        } else {
            // Contiguous space at the end of the segment, check capacity
            if (edgeCount >= edgeCapacity) {
                log.warn("EntityGraph edge capacity full ({}), rejecting edge", edgeCapacity);
                return;
            }
        }

        int edgeIdx = edgeStart + degree;
        long edgeOffset = (long) edgeIdx * EDGE_BYTES;
        edgeSegment.set(ValueLayout.JAVA_INT, edgeOffset + EDGE_OFF_TARGET, toEntity);
        edgeSegment.set(ValueLayout.JAVA_INT, edgeOffset + EDGE_OFF_REL_TYPE, typeId);
        edgeSegment.set(ValueLayout.JAVA_FLOAT, edgeOffset + EDGE_OFF_WEIGHT, 1.0f);
        edgeSegment.set(ValueLayout.JAVA_SHORT, edgeOffset + EDGE_OFF_LAST_CYCLE, (short) currentCycle);
        edgeSegment.set(ValueLayout.JAVA_BYTE, edgeOffset + EDGE_OFF_BRIDGE_SCORE, (byte) 0);
        edgeSegment.set(ValueLayout.JAVA_BYTE, edgeOffset + EDGE_OFF_EDGE_FLAGS, (byte) 0);

        entitySegment.set(ValueLayout.JAVA_INT, entityOffset + ENT_OFF_DEGREE, degree + 1);
        edgeCount = edgeIdx + 1;
        } finally {
            graphLock.unlock();
        }
    }

    /**
     * Evicts the lowest-importance edge from an entity at max degree, replacing
     * it with a new edge if the new edge would score higher.
     *
     * <p>Called instead of the old silent rejection. Uses structural-only scoring
     * (no synaptic header reads) for hot-path performance.</p>
     */
    private void evictLowestImportanceEdge(int fromEntity, long entityOffset,
                                            int edgeStart, int degree,
                                            int newTarget, int newTypeId) {
        if (edgeStart < 0) return;

        float minScore = Float.MAX_VALUE;
        int minIndex = -1;

        for (int i = 0; i < degree; i++) {
            long edgeOffset = (long) (edgeStart + i) * EDGE_BYTES;
            float weight = edgeSegment.get(ValueLayout.JAVA_FLOAT, edgeOffset + EDGE_OFF_WEIGHT);
            short lastCyc = edgeSegment.get(ValueLayout.JAVA_SHORT, edgeOffset + EDGE_OFF_LAST_CYCLE);
            byte bridge = edgeSegment.get(ValueLayout.JAVA_BYTE, edgeOffset + EDGE_OFF_BRIDGE_SCORE);

            float score = edgeImportance.scoreStructural(
                    weight, currentCycle, Short.toUnsignedInt(lastCyc),
                    Byte.toUnsignedInt(bridge), 0);

            if (score < minScore) {
                minScore = score;
                minIndex = i;
            }
        }

        // New edge score (fresh edge: weight=1.0, recency=now, no bridge)
        float newScore = edgeImportance.scoreStructural(1.0f, currentCycle, currentCycle, 0, 0);

        if (newScore > minScore && minIndex >= 0) {
            long evictOffset = (long) (edgeStart + minIndex) * EDGE_BYTES;
            edgeSegment.set(ValueLayout.JAVA_INT, evictOffset + EDGE_OFF_TARGET, newTarget);
            edgeSegment.set(ValueLayout.JAVA_INT, evictOffset + EDGE_OFF_REL_TYPE, newTypeId);
            edgeSegment.set(ValueLayout.JAVA_FLOAT, evictOffset + EDGE_OFF_WEIGHT, 1.0f);
            edgeSegment.set(ValueLayout.JAVA_SHORT, evictOffset + EDGE_OFF_LAST_CYCLE, (short) currentCycle);
            edgeSegment.set(ValueLayout.JAVA_BYTE, evictOffset + EDGE_OFF_BRIDGE_SCORE, (byte) 0);
            edgeSegment.set(ValueLayout.JAVA_BYTE, evictOffset + EDGE_OFF_EDGE_FLAGS, (byte) 0);

            log.trace("Entity {} evicted edge at slot {} (score={}) for new edge to {} (score={})",
                    fromEntity, minIndex, minScore, newTarget, newScore);
        } else {
            log.trace("Entity {} at max degree: new edge to {} (score={}) too weak to evict (min={})",
                    fromEntity, newTarget, newScore, minScore);
        }
    }

    /**
     * Links an entity to a memory index (unlimited associations).
     *
     * <p>If the entity is already linked to this memory, the link weight is
     * reinforced by {@value #LTP_REINFORCEMENT} (LTP — long-term potentiation).
     * Otherwise, a new adjacency entry is created with weight {@value #INITIAL_LINK_WEIGHT}.</p>
     *
     * <p>Unlike the V1 layout which limited each entity to 4 memory refs,
     * the V2 adjacency segment has no hard limit. Growth is amortized O(1)
     * via block doubling when the per-entity allocation fills.</p>
     *
     * @param entityId  entity ID
     * @param memoryIdx index of the memory that mentions this entity
     */
    public void linkEntityToMemory(int entityId, int memoryIdx) {
        if (entityId < 0 || entityId >= entityCount) return;

        graphLock.lock();
        try {
            long entOffset = (long) entityId * ENTITY_NODE_BYTES;
            int adjOff = entitySegment.get(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_OFFSET);
            int adjCnt = entitySegment.get(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_COUNT);
            int adjCap = entitySegment.get(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_CAPACITY);

            // Check for duplicate — reinforce weight (LTP)
            if (adjOff >= 0) {
                for (int i = 0; i < adjCnt; i++) {
                    long adjEntryOff = (long) (adjOff + i) * ADJ_ENTRY_BYTES;
                    int existingIdx = adjacencySegment.get(ValueLayout.JAVA_INT, adjEntryOff + ADJ_OFF_MEM_IDX);
                    if (existingIdx == memoryIdx) {
                        // LTP: reinforce existing link
                        float w = adjacencySegment.get(ValueLayout.JAVA_FLOAT, adjEntryOff + ADJ_OFF_WEIGHT);
                        adjacencySegment.set(ValueLayout.JAVA_FLOAT, adjEntryOff + ADJ_OFF_WEIGHT,
                                w + LTP_REINFORCEMENT);
                        return;
                    }
                }
            }

            // Need new slot — allocate or grow adjacency block
            if (adjCap == 0) {
                // First link for this entity — allocate initial block
                adjOff = adjHighWaterMark;
                adjCap = DEFAULT_ADJ_PER_ENTITY;
                ensureAdjSegmentCapacity(adjHighWaterMark + adjCap);
                entitySegment.set(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_OFFSET, adjOff);
                entitySegment.set(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_CAPACITY, adjCap);
                adjHighWaterMark += adjCap;
            } else if (adjCnt >= adjCap) {
                // Block full — allocate new block with 2× capacity at end of segment
                int newCap = adjCap * 2;
                int newOff = adjHighWaterMark;
                ensureAdjSegmentCapacity(adjHighWaterMark + newCap);
                // Copy existing entries to new block
                MemorySegment.copy(adjacencySegment, (long) adjOff * ADJ_ENTRY_BYTES,
                        adjacencySegment, (long) newOff * ADJ_ENTRY_BYTES,
                        (long) adjCnt * ADJ_ENTRY_BYTES);
                adjOff = newOff;
                adjCap = newCap;
                entitySegment.set(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_OFFSET, adjOff);
                entitySegment.set(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_CAPACITY, adjCap);
                adjHighWaterMark += newCap;
            }

            // Write new adjacency entry
            long entryOff = (long) (adjOff + adjCnt) * ADJ_ENTRY_BYTES;
            adjacencySegment.set(ValueLayout.JAVA_INT, entryOff + ADJ_OFF_MEM_IDX, memoryIdx);
            adjacencySegment.set(ValueLayout.JAVA_FLOAT, entryOff + ADJ_OFF_WEIGHT, INITIAL_LINK_WEIGHT);
            entitySegment.set(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_COUNT, adjCnt + 1);
        } finally {
            graphLock.unlock();
        }
    }

    /**
     * Ensures the adjacency segment can hold at least {@code requiredEntries} entries.
     * Doubles the segment capacity if needed, copying existing data.
     *
     * <p>Must be called under {@link #graphLock}.</p>
     */
    private void ensureAdjSegmentCapacity(int requiredEntries) {
        if (requiredEntries <= adjSegmentCapacity) return;
        if (fileBacked) {
            // mmap mode: pre-allocated at max capacity, should never need to grow.
            // If this triggers, MAX_ADJ_PER_ENTITY needs increasing.
            throw new IllegalStateException(
                    "EntityGraph mmap adjacency segment exhausted: required=" + requiredEntries
                    + ", capacity=" + adjSegmentCapacity
                    + ". Increase MAX_ADJ_PER_ENTITY (currently " + MAX_ADJ_PER_ENTITY + ")");
        }
        int newCapacity = Math.max(adjSegmentCapacity * 2, requiredEntries);
        MemorySegment newSeg = arena.allocate((long) ADJ_ENTRY_BYTES * newCapacity);
        newSeg.fill((byte) 0);
        // Copy existing data
        MemorySegment.copy(adjacencySegment, 0, newSeg, 0,
                (long) ADJ_ENTRY_BYTES * adjHighWaterMark);
        adjacencySegment = newSeg;
        int oldCap = adjSegmentCapacity;
        adjSegmentCapacity = newCapacity;
        log.info("EntityGraph adjacency segment grown: {} → {} entries", oldCap, newCapacity);
    }

    /**
     * Finds an entity by name (case-insensitive).
     *
     * @param name entity name
     * @return entity ID, or -1 if not found
     */
    public int findEntity(String name) {
        if (name == null || name.isBlank()) return -1;
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        Integer id = nameIndex.get(normalized);
        return id != null ? id : -1;
    }

    /**
     * Returns the memory indices that reference an entity.
     *
     * @param entityId entity ID
     * @return array of memory indices
     */
    public int[] memoriesForEntity(int entityId) {
        if (entityId < 0 || entityId >= entityCount) return new int[0];

        long entOffset = (long) entityId * ENTITY_NODE_BYTES;
        int adjOff = entitySegment.get(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_OFFSET);
        int adjCnt = entitySegment.get(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_COUNT);
        if (adjOff < 0 || adjCnt == 0) return new int[0];

        int[] result = new int[adjCnt];
        for (int i = 0; i < adjCnt; i++) {
            long adjEntryOff = (long) (adjOff + i) * ADJ_ENTRY_BYTES;
            result[i] = adjacencySegment.get(ValueLayout.JAVA_INT, adjEntryOff + ADJ_OFF_MEM_IDX);
        }
        return result;
    }

    /**
     * Returns the number of memory references for an entity.
     * Zero-alloc alternative to {@link #memoriesForEntity(int)}.length.
     */
    public int memoryRefCount(int entityId) {
        if (entityId < 0 || entityId >= entityCount) return 0;
        return entitySegment.get(ValueLayout.JAVA_INT,
                (long) entityId * ENTITY_NODE_BYTES + ENT_OFF_ADJ_COUNT);
    }

    /**
     * Returns the memory index at a specific reference position.
     * Zero-alloc alternative to {@code memoriesForEntity(id)[index]}.
     *
     * @param refIndex reference index (0-based, must be &lt; memoryRefCount)
     * @return memory index, or -1 if out of bounds
     */
    public int memoryRefAt(int entityId, int refIndex) {
        if (entityId < 0 || entityId >= entityCount) return -1;
        long entOffset = (long) entityId * ENTITY_NODE_BYTES;
        int adjOff = entitySegment.get(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_OFFSET);
        int adjCnt = entitySegment.get(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_COUNT);
        if (adjOff < 0 || refIndex < 0 || refIndex >= adjCnt) return -1;
        return adjacencySegment.get(ValueLayout.JAVA_INT,
                (long) (adjOff + refIndex) * ADJ_ENTRY_BYTES + ADJ_OFF_MEM_IDX);
    }

    /**
     * Returns the weight of a specific entity→memory reference.
     *
     * @param entityId entity ID
     * @param refIndex reference index (0-based)
     * @return weight, or 0.0f if out of bounds
     */
    public float memoryRefWeight(int entityId, int refIndex) {
        if (entityId < 0 || entityId >= entityCount) return 0f;
        long entOffset = (long) entityId * ENTITY_NODE_BYTES;
        int adjOff = entitySegment.get(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_OFFSET);
        int adjCnt = entitySegment.get(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_COUNT);
        if (adjOff < 0 || refIndex < 0 || refIndex >= adjCnt) return 0f;
        return adjacencySegment.get(ValueLayout.JAVA_FLOAT,
                (long) (adjOff + refIndex) * ADJ_ENTRY_BYTES + ADJ_OFF_WEIGHT);
    }

    /**
     * Returns the fan-effect attenuation factor for an entity.
     *
     * <p>Models ACT-R spreading activation dilution: when a concept is linked to
     * many memories (high "fan"), retrieval boost per link is reduced. The factor
     * is {@code 1.0 / sqrt(refCount)}, so:</p>
     * <ul>
     *   <li>1 memory ref → factor 1.0 (full boost)</li>
     *   <li>4 memory refs → factor 0.5</li>
     *   <li>16 memory refs → factor 0.25</li>
     *   <li>100 memory refs → factor 0.1</li>
     * </ul>
     *
     * @param entityId entity ID
     * @return attenuation factor (0.0 to 1.0)
     */
    public float fanFactor(int entityId) {
        int refCnt = memoryRefCount(entityId);
        if (refCnt <= 1) return 1.0f;
        return 1.0f / (float) Math.sqrt(refCnt);
    }

    /**
     * Returns the entity type for an entity ID.
     */
    public String entityType(int entityId) {
        if (entityId < 0 || entityId >= entityCount) return "OTHER";
        int typeId = entitySegment.get(ValueLayout.JAVA_INT,
                (long) entityId * ENTITY_NODE_BYTES + ENT_OFF_TYPE);
        return entityTypeRegistry.nameOf(typeId);
    }

    /**
     * Returns the edges for an entity.
     */
    public List<EntityEdge> edges(int entityId) {
        if (entityId < 0 || entityId >= entityCount) return List.of();

        long offset = (long) entityId * ENTITY_NODE_BYTES;
        int degree = entitySegment.get(ValueLayout.JAVA_INT, offset + ENT_OFF_DEGREE);
        int edgeStart = entitySegment.get(ValueLayout.JAVA_INT, offset + ENT_OFF_EDGE_START);

        if (edgeStart < 0 || degree == 0) return List.of();

        List<EntityEdge> result = new ArrayList<>(degree);
        for (int i = 0; i < degree; i++) {
            long edgeOffset = (long) (edgeStart + i) * EDGE_BYTES;
            int target = edgeSegment.get(ValueLayout.JAVA_INT, edgeOffset + EDGE_OFF_TARGET);
            int relTypeId = edgeSegment.get(ValueLayout.JAVA_INT, edgeOffset + EDGE_OFF_REL_TYPE);
            float weight = edgeSegment.get(ValueLayout.JAVA_FLOAT, edgeOffset + EDGE_OFF_WEIGHT);

            String relType = relationTypeRegistry.nameOf(relTypeId);

            result.add(new EntityEdge(target, relType, weight));
        }
        return result;
    }

    /**
     * BFS traversal from a starting entity with optional relation type filter.
     *
     * @param startEntity entity ID to start from
     * @param filter      relation type filter (null = accept all)
     * @param maxHops     maximum traversal depth
     * @return list of reached entity IDs with their hop distances
     */
    public List<TraversalResult> traverse(int startEntity, String filter, int maxHops) {
        if (startEntity < 0 || startEntity >= entityCount) return List.of();

        List<TraversalResult> results = new ArrayList<>();
        // boolean[] instead of HashSet<Integer> — eliminates autoboxing overhead
        boolean[] visited = new boolean[entityCount];
        // ArrayDeque instead of LinkedList — better cache locality, fewer allocations
        // Pack (entityId, depth) into a single long to avoid int[] allocations per BFS node
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(packBfsNode(startEntity, 0));
        visited[startEntity] = true;

        while (!queue.isEmpty()) {
            long packed = queue.poll();
            int entityId = (int) (packed >>> 32);
            int depth = (int) packed;

            if (depth > 0) {
                results.add(new TraversalResult(entityId, depth));
            }

            if (depth >= maxHops) continue;

            for (EntityEdge edge : edges(entityId)) {
                if (filter != null && !filter.equals(edge.relationType())) continue;
                int target = edge.targetEntityId();
                if (target >= 0 && target < entityCount && !visited[target]) {
                    visited[target] = true;
                    queue.add(packBfsNode(target, depth + 1));
                }
            }
        }

        return results;
    }

    /** Packs entityId and depth into a single long to avoid int[] allocation per BFS node. */
    private static long packBfsNode(int entityId, int depth) {
        return ((long) entityId << 32) | (depth & 0xFFFFFFFFL);
    }

    /**
     * Collects all memory indices reachable from a starting entity within maxHops.
     *
     * @param startEntity starting entity ID
     * @param filter      optional relation type filter
     * @param maxHops     maximum traversal depth
     * @return set of memory indices
     */
    public Set<Integer> collectMemories(int startEntity, String filter, int maxHops) {
        Set<Integer> memories = new HashSet<>();

        // Include start entity's memories
        for (int memIdx : memoriesForEntity(startEntity)) {
            memories.add(memIdx);
        }

        // Traverse and collect
        for (TraversalResult tr : traverse(startEntity, filter, maxHops)) {
            for (int memIdx : memoriesForEntity(tr.entityId())) {
                memories.add(memIdx);
            }
        }

        return memories;
    }

    // ── Decay & Merge Operations (for reflect() sleep cycle) ──

    /**
     * Decays all entity edge weights by the given factor and prunes edges below a minimum weight.
     *
     * <p>Analogous to {@link com.spectrayan.spector.memory.hebbian.HebbianGraph#decayEdges(float)}
     * but operates on the entity-relationship graph. Weak relations (e.g., promoted via
     * cross-layer from Hebbian but never reinforced) naturally fade over reflection cycles.</p>
     *
     * @param decayFactor multiplicative factor (e.g., 0.9 = 10% decay per cycle)
     * @param minWeight   edges with weight below this after decay are pruned (e.g., 0.5)
     * @return number of edges pruned
     */
    public int decayEdges(float decayFactor, float minWeight) {
        graphLock.lock();
        try {
        int pruned = 0;
        for (int e = 0; e < entityCount; e++) {
            long entOffset = (long) e * ENTITY_NODE_BYTES;
            int degree = entitySegment.get(ValueLayout.JAVA_INT, entOffset + ENT_OFF_DEGREE);
            int edgeStart = entitySegment.get(ValueLayout.JAVA_INT, entOffset + ENT_OFF_EDGE_START);
            if (edgeStart < 0 || degree == 0) continue;

            int newDegree = 0;
            for (int i = 0; i < degree; i++) {
                long edgeOffset = (long) (edgeStart + i) * EDGE_BYTES;
                float weight = edgeSegment.get(ValueLayout.JAVA_FLOAT, edgeOffset + EDGE_OFF_WEIGHT);
                float decayed = weight * decayFactor;

                if (decayed >= minWeight) {
                    // Keep edge: compact if needed
                    if (newDegree < i) {
                        long destOffset = (long) (edgeStart + newDegree) * EDGE_BYTES;
                        int target = edgeSegment.get(ValueLayout.JAVA_INT, edgeOffset + EDGE_OFF_TARGET);
                        int relType = edgeSegment.get(ValueLayout.JAVA_INT, edgeOffset + EDGE_OFF_REL_TYPE);
                        edgeSegment.set(ValueLayout.JAVA_INT, destOffset + EDGE_OFF_TARGET, target);
                        edgeSegment.set(ValueLayout.JAVA_INT, destOffset + EDGE_OFF_REL_TYPE, relType);
                        edgeSegment.set(ValueLayout.JAVA_FLOAT, destOffset + EDGE_OFF_WEIGHT, decayed);
                    } else {
                        edgeSegment.set(ValueLayout.JAVA_FLOAT, edgeOffset + EDGE_OFF_WEIGHT, decayed);
                    }
                    newDegree++;
                } else {
                    pruned++;
                }
            }
            entitySegment.set(ValueLayout.JAVA_INT, entOffset + ENT_OFF_DEGREE, newDegree);
        }
        if (pruned > 0) {
            log.info("EntityGraph decayed edges: {} pruned below threshold {}", pruned, minWeight);
        }
        return pruned;
        } finally {
            graphLock.unlock();
        }
    }

    /**
     * Decays all entity→memory adjacency weights and prunes weak links (LTD).
     *
     * <p>Biological analog: Long-Term Depression (LTD) — synapses that are not
     * activated weaken and eventually retract. This ensures entities that were
     * mentioned once but never reinforced gradually lose their memory links.</p>
     *
     * @param decayFactor    multiplicative factor per cycle (e.g., 0.95 = 5% decay)
     * @param pruneThreshold links with weight below this after decay are removed
     * @return number of adjacency entries pruned
     */
    public int decayAdjacencyWeights(float decayFactor, float pruneThreshold) {
        graphLock.lock();
        try {
            int totalPruned = 0;
            for (int e = 0; e < entityCount; e++) {
                long entOffset = (long) e * ENTITY_NODE_BYTES;
                int adjOff = entitySegment.get(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_OFFSET);
                int adjCnt = entitySegment.get(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_COUNT);
                if (adjOff < 0 || adjCnt == 0) continue;

                int newCount = 0;
                for (int i = 0; i < adjCnt; i++) {
                    long srcOff = (long) (adjOff + i) * ADJ_ENTRY_BYTES;
                    float weight = adjacencySegment.get(ValueLayout.JAVA_FLOAT, srcOff + ADJ_OFF_WEIGHT);
                    float decayed = weight * decayFactor;

                    if (decayed >= pruneThreshold) {
                        // Keep: compact in-place if needed
                        if (newCount < i) {
                            long dstOff = (long) (adjOff + newCount) * ADJ_ENTRY_BYTES;
                            int memIdx = adjacencySegment.get(ValueLayout.JAVA_INT, srcOff + ADJ_OFF_MEM_IDX);
                            adjacencySegment.set(ValueLayout.JAVA_INT, dstOff + ADJ_OFF_MEM_IDX, memIdx);
                            adjacencySegment.set(ValueLayout.JAVA_FLOAT, dstOff + ADJ_OFF_WEIGHT, decayed);
                        } else {
                            adjacencySegment.set(ValueLayout.JAVA_FLOAT, srcOff + ADJ_OFF_WEIGHT, decayed);
                        }
                        newCount++;
                    } else {
                        totalPruned++;
                    }
                }
                entitySegment.set(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_COUNT, newCount);
            }
            if (totalPruned > 0) {
                log.info("EntityGraph LTD: decayed adjacency weights, pruned {} weak links below {}",
                        totalPruned, pruneThreshold);
            }
            return totalPruned;
        } finally {
            graphLock.unlock();
        }
    }

    /**
     * Compacts the adjacency segment by defragmenting per-entity blocks.
     *
     * <p>When entities' adjacency blocks are relocated (due to growth), their old
     * blocks become dead space. This method copies all live adjacency data into a
     * fresh contiguous segment, eliminating fragmentation.</p>
     *
     * <p>Should be called during {@link com.spectrayan.spector.memory.ReflectionOrchestrator#reflect}
     * after {@link #decayAdjacencyWeights} has pruned weak links.</p>
     *
     * @return bytes reclaimed by compaction
     */
    public long compactAdjacency() {
        graphLock.lock();
        try {
            long oldUsed = (long) adjHighWaterMark * ADJ_ENTRY_BYTES;

            // Allocate fresh segment sized to actual live data
            int liveEntries = 0;
            for (int e = 0; e < entityCount; e++) {
                int adjCnt = entitySegment.get(ValueLayout.JAVA_INT,
                        (long) e * ENTITY_NODE_BYTES + ENT_OFF_ADJ_COUNT);
                liveEntries += adjCnt;
            }

            if (liveEntries == 0) {
                adjHighWaterMark = 0;
                return oldUsed;
            }

            // Add headroom for future growth (50% extra capacity)
            int newCapacity = Math.max(adjSegmentCapacity, (int) (liveEntries * 1.5));
            MemorySegment newSeg = arena.allocate((long) ADJ_ENTRY_BYTES * newCapacity);
            newSeg.fill((byte) 0);

            int writePos = 0;
            for (int e = 0; e < entityCount; e++) {
                long entOffset = (long) e * ENTITY_NODE_BYTES;
                int adjOff = entitySegment.get(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_OFFSET);
                int adjCnt = entitySegment.get(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_COUNT);

                if (adjOff < 0 || adjCnt == 0) {
                    // Reset to no block
                    entitySegment.set(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_OFFSET, -1);
                    entitySegment.set(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_CAPACITY, 0);
                    continue;
                }

                // Copy live entries contiguously
                MemorySegment.copy(adjacencySegment, (long) adjOff * ADJ_ENTRY_BYTES,
                        newSeg, (long) writePos * ADJ_ENTRY_BYTES,
                        (long) adjCnt * ADJ_ENTRY_BYTES);

                // Update entity's adj pointer and capacity (tight fit + growth room)
                entitySegment.set(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_OFFSET, writePos);
                int newEntityCap = Math.max(adjCnt, DEFAULT_ADJ_PER_ENTITY);
                entitySegment.set(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_CAPACITY, newEntityCap);
                writePos += newEntityCap; // Reserve capacity slots
            }

            adjacencySegment = newSeg;
            adjSegmentCapacity = newCapacity;
            adjHighWaterMark = writePos;

            long newUsed = (long) writePos * ADJ_ENTRY_BYTES;
            long reclaimed = oldUsed - newUsed;
            if (reclaimed > 0) {
                log.info("EntityGraph adjacency compacted: {} live entries, reclaimed {}KB",
                        liveEntries, reclaimed / 1024);
            }
            return Math.max(reclaimed, 0);
        } finally {
            graphLock.unlock();
        }
    }

    /**
     * Merges entities with similar names using Levenshtein distance.
     *
     * <p>Entities whose names are within {@code maxEditDistance} edits of each other
     * (and share the same EntityType) are merged. The shorter name is kept as the
     * canonical entity. All edges and memory refs from the duplicate are redirected
     * to the canonical entity.</p>
     *
     * <p>This addresses typos and near-duplicates like "kubernetes" / "kubernets"
     * that arise from NER over noisy text.</p>
     *
     * @param maxEditDistance maximum Levenshtein distance for merge (e.g., 2)
     * @return number of entities merged
     */
    public int mergeSimilarEntities(int maxEditDistance) {
        graphLock.lock();
        try {
        if (maxEditDistance <= 0 || entityCount < 2) return 0;

        Set<Integer> merged = new HashSet<>();
        int mergeCount = 0;
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(nameIndex.entrySet());

        for (int i = 0; i < entries.size(); i++) {
            if (merged.contains(entries.get(i).getValue())) continue;
            String nameA = entries.get(i).getKey();
            int idA = entries.get(i).getValue();

            for (int j = i + 1; j < entries.size(); j++) {
                if (merged.contains(entries.get(j).getValue())) continue;
                String nameB = entries.get(j).getKey();
                int idB = entries.get(j).getValue();

                // Only merge same-type entities
                if (entityType(idA) != entityType(idB)) continue;

                int dist = levenshteinDistance(nameA, nameB);
                if (dist > 0 && dist <= maxEditDistance) {
                    // Keep the shorter name as canonical
                    int canonical = nameA.length() <= nameB.length() ? idA : idB;
                    int duplicate = canonical == idA ? idB : idA;

                    redirectEntity(duplicate, canonical);
                    merged.add(duplicate);
                    mergeCount++;

                    log.debug("EntityGraph merged '{}' → '{}' (edit distance={})",
                            canonical == idA ? nameB : nameA,
                            canonical == idA ? nameA : nameB, dist);
                }
            }
        }

        if (mergeCount > 0) {
            log.info("EntityGraph merged {} similar entities", mergeCount);
        }
        return mergeCount;
        } finally {
            graphLock.unlock();
        }
    }

    /**
     * Redirects all edges and memory refs from {@code from} to {@code to}.
     */
    private void redirectEntity(int from, int to) {
        // Move memory refs from 'from' to 'to' via adjacency
        int[] fromMemRefs = memoriesForEntity(from);
        for (int memIdx : fromMemRefs) {
            linkEntityToMemory(to, memIdx);
        }
        // Clear 'from' adjacency
        long fromOffset = (long) from * ENTITY_NODE_BYTES;
        entitySegment.set(ValueLayout.JAVA_INT, fromOffset + ENT_OFF_ADJ_COUNT, 0);

        // Move edges from 'from' to 'to'
        for (EntityEdge edge : edges(from)) {
            if (edge.targetEntityId() != to) {
                addRelation(to, edge.targetEntityId(), edge.relationType());
            }
        }
        // Clear 'from' edges
        entitySegment.set(ValueLayout.JAVA_INT, fromOffset + ENT_OFF_DEGREE, 0);
    }

    /**
     * Computes Levenshtein edit distance between two strings.
     *
     * <p>Uses thread-local reusable arrays to avoid heap allocation per call.
     * The previous implementation allocated two {@code int[]} arrays per call,
     * which at O(n²) merge comparisons caused significant GC pressure.</p>
     */
    private static final ThreadLocal<int[]> LEV_PREV = ThreadLocal.withInitial(() -> new int[256]);
    private static final ThreadLocal<int[]> LEV_CURR = ThreadLocal.withInitial(() -> new int[256]);

    static int levenshteinDistance(String a, String b) {
        int lenA = a.length(), lenB = b.length();
        if (lenA == 0) return lenB;
        if (lenB == 0) return lenA;
        // Quick reject: if length difference exceeds max realistic distance, skip
        if (Math.abs(lenA - lenB) > 5) return Math.abs(lenA - lenB);

        // Reuse thread-local arrays (grow if needed)
        int[] prev = LEV_PREV.get();
        int[] curr = LEV_CURR.get();
        if (prev.length <= lenB) {
            prev = new int[lenB + 1];
            curr = new int[lenB + 1];
            LEV_PREV.set(prev);
            LEV_CURR.set(curr);
        }

        for (int j = 0; j <= lenB; j++) prev[j] = j;
        for (int i = 1; i <= lenA; i++) {
            curr[0] = i;
            for (int j = 1; j <= lenB; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[lenB];
    }

    /**
     * Returns the number of entities in the graph.
     */
    public int entityCount() {
        return entityCount;
    }

    /**
     * Returns the number of edges in the graph.
     */
    public int edgeCount() {
        return edgeCount;
    }

    /**
     * Sets the data encryptor for name index encryption.
     *
     * <p>When set, all subsequent {@link #save(Path)} calls will encrypt the
     * name index section. The enterprise layer calls this after construction
     * to enable encryption without modifying constructor chains.</p>
     *
     * @param encryptor the encryptor to use (null = no encryption)
     */
    public void setDataEncryptor(DataEncryptor encryptor) {
        this.dataEncryptor = encryptor;
    }

    /**
     * Returns the current data encryptor (for diagnostics).
     */
    public DataEncryptor dataEncryptor() {
        return dataEncryptor;
    }

    /**
     * Returns the name index for inspection/debugging.
     */
    public Map<String, Integer> nameIndex() {
        return Map.copyOf(nameIndex);
    }

    /**
     * Returns the adjacency segment high water mark (for diagnostics).
     */
    public int adjHighWaterMark() {
        return adjHighWaterMark;
    }

    /**
     * An edge in the entity graph.
     *
     * <p><b>TODO (JDK 28+ / Project Valhalla):</b> Convert to {@code value record}.
     * As a value class, EntityEdge would be scalarized by the JIT — zero heap
     * allocation. With specialized generics, {@code List<EntityEdge>} would store
     * flat values instead of boxed pointers.</p>
     */
    public record EntityEdge(int targetEntityId, String relationType, float weight) {}

    /**
     * A BFS traversal result.
     *
     * <p><b>TODO (JDK 28+ / Project Valhalla):</b> Convert to {@code value record}.
     * Same benefits as EntityEdge above.</p>
     */
    public record TraversalResult(int entityId, int hopDistance) {}

    // ══════════════════════════════════════════════════════════════
    // PERSISTENCE: save / load (delegated to EntityGraphSerializer)
    // ══════════════════════════════════════════════════════════════

    /**
     * Saves the graph to a binary file.
     *
     * @param filePath path to write
     */
    public void save(Path filePath) {
        save(filePath, this.dataEncryptor);
    }

    /**
     * Saves the graph to a binary file with optional name index encryption.
     *
     * <p>For mmap-backed graphs: flushes dirty pages via {@code force()},
     * updates the header with current counts, and serializes the nameIndex
     * as a sidecar file. The segments themselves are already on disk.</p>
     *
     * @param filePath  path to write
     * @param encryptor optional encryptor for name index (null = no encryption)
     */
    public void save(Path filePath, DataEncryptor encryptor) {
        if (fileBacked) {
            try {
                // Update header with current counts
                ByteBuffer header = ByteBuffer.allocate(MMAP_HEADER_BYTES);
                header.putInt(MMAP_MAGIC);
                header.putInt(MMAP_VERSION);
                header.putInt(entityCapacity);
                header.putInt(edgeCapacity);
                header.putInt(entityCount);
                header.putInt(edgeCount);
                header.putInt(adjSegmentCapacity);
                header.putInt(adjHighWaterMark);
                header.flip();
                mappedChannel.position(0);
                mappedChannel.write(header);

                // Force-flush all segments
                entitySegment.force();
                edgeSegment.force();
                adjacencySegment.force();
                mappedChannel.force(true);

                // Serialize nameIndex + TypeRegistries (sidecar files)
                EntityGraphSerializer.saveNameIndexAndRegistries(this, filePath, encryptor);

                log.info("EntityGraph flushed (mmap): entities={}/{}, edges={}, adjHwm={}",
                        entityCount, entityCapacity, edgeCount, adjHighWaterMark);
            } catch (IOException e) {
                throw new SpectorGraphPersistenceException("EntityGraph", filePath, e);
            }
            return;
        }

        // Heap-backed: full serialization
        EntityGraphSerializer.save(this, filePath, encryptor);
    }

    /**
     * Loads a graph from a binary file, or returns a new empty graph.
     *
     * @param filePath          path to the graph file
     * @param defaultEntityCap  entity capacity if file doesn't exist
     * @param defaultEdgeCap    edge capacity if file doesn't exist
     * @return an EntityGraph (loaded or new)
     */
    public static EntityGraph load(Path filePath, int defaultEntityCap, int defaultEdgeCap) {
        return EntityGraphSerializer.load(filePath, defaultEntityCap, defaultEdgeCap, null);
    }

    /**
     * Loads a graph from a binary file with optional name index decryption.
     *
     * @param filePath          path to the graph file
     * @param defaultEntityCap  entity capacity if file doesn't exist
     * @param defaultEdgeCap    edge capacity if file doesn't exist
     * @param encryptor         optional encryptor for name index decryption (null = no encryption)
     * @return an EntityGraph (loaded or new)
     */
    public static EntityGraph load(Path filePath, int defaultEntityCap, int defaultEdgeCap,
                                    DataEncryptor encryptor) {
        return EntityGraphSerializer.load(filePath, defaultEntityCap, defaultEdgeCap, encryptor);
    }

    // ── Package-private accessors for EntityGraphSerializer ──

    int entityCapacity() { return entityCapacity; }
    int edgeCapacity() { return edgeCapacity; }
    MemorySegment entitySegment() { return entitySegment; }
    MemorySegment edgeSegment() { return edgeSegment; }
    MemorySegment adjacencySegment() { return adjacencySegment; }
    int adjSegmentCapacity() { return adjSegmentCapacity; }
    ConcurrentHashMap<String, Integer> nameIndexInternal() { return nameIndex; }
    TypeRegistry entityTypeRegistry() { return entityTypeRegistry; }
    TypeRegistry relationTypeRegistry() { return relationTypeRegistry; }

    /**
     * Resets all entities, edges, and adjacency data by zero-filling segments.
     *
     * <p>Unlike {@link #close()}, this does NOT release the arena. The graph
     * remains usable for new entities after the reset. Used by privacy wipe.</p>
     */
    public void reset() {
        graphLock.lock();
        try {
            int entitiesBefore = entityCount;
            int edgesBefore = edgeCount;
            entitySegment.fill((byte) 0);
            edgeSegment.fill((byte) 0);
            adjacencySegment.fill((byte) 0);
            nameIndex.clear();
            entityCount = 0;
            edgeCount = 0;
            adjHighWaterMark = 0;
            log.info("EntityGraph reset: {} entities, {} edges cleared", entitiesBefore, edgesBefore);
        } finally {
            graphLock.unlock();
        }
    }

    @Override
    public void close() {
        log.info("EntityGraph closing (entities={}, edges={}, adjEntries={}, fileBacked={})",
                entityCount, edgeCount, adjHighWaterMark, fileBacked);
        if (fileBacked && mappedChannel != null) {
            try {
                entitySegment.force();
                edgeSegment.force();
                adjacencySegment.force();
                mappedChannel.force(true);
                mappedChannel.close();
            } catch (IOException e) {
                log.warn("Error closing EntityGraph mmap channel: {}", e.getMessage());
            }
        }
        arena.close();
    }

    /** Returns true if this graph is backed by mmap'd files. */
    boolean isFileBacked() { return fileBacked; }

    /** Returns the mmap file path (null for heap mode). */
    Path mmapFilePath() { return mmapFilePath; }
}
