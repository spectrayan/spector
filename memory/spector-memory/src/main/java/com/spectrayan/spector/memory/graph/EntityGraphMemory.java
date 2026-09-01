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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.memory.persist.DataEncryptor;
import com.spectrayan.spector.memory.kernel.StorageLayout;
import com.spectrayan.spector.memory.error.SpectorEntityGraphException;
import com.spectrayan.spector.memory.error.SpectorGraphPersistenceException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import com.spectrayan.spector.memory.kernel.MemoryHeader;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.SystemMemoryId;
import com.spectrayan.spector.memory.kernel.layout.EntityLayout;
import com.spectrayan.spector.memory.kernel.shape.AbstractGraphMemory;
import java.nio.charset.StandardCharsets;

import static com.spectrayan.spector.memory.kernel.layout.EntityLayout.ADJ_ENTRY_BYTES;
import static com.spectrayan.spector.memory.kernel.layout.EntityLayout.ADJ_OFF_MEM_IDX;
import static com.spectrayan.spector.memory.kernel.layout.EntityLayout.DATA_START;
import static com.spectrayan.spector.memory.kernel.layout.EntityLayout.SUB_OFF_ADJ_CAPACITY;
import static com.spectrayan.spector.memory.kernel.layout.EntityLayout.SUB_OFF_ADJ_HWM;
import static com.spectrayan.spector.memory.kernel.layout.EntityLayout.SUB_OFF_EDGE_CAPACITY;
import static com.spectrayan.spector.memory.kernel.layout.EntityLayout.SUB_OFF_EDGE_COUNT;
import static com.spectrayan.spector.memory.kernel.layout.EntityLayout.ADJ_OFF_WEIGHT;
import static com.spectrayan.spector.memory.kernel.layout.EntityLayout.EDGE_BYTES;
import static com.spectrayan.spector.memory.kernel.layout.EntityLayout.EDGE_OFF_BRIDGE_SCORE;
import static com.spectrayan.spector.memory.kernel.layout.EntityLayout.EDGE_OFF_EDGE_FLAGS;
import static com.spectrayan.spector.memory.kernel.layout.EntityLayout.EDGE_OFF_LAST_CYCLE;
import static com.spectrayan.spector.memory.kernel.layout.EntityLayout.EDGE_OFF_REL_TYPE;
import static com.spectrayan.spector.memory.kernel.layout.EntityLayout.EDGE_OFF_TARGET;
import static com.spectrayan.spector.memory.kernel.layout.EntityLayout.EDGE_OFF_WEIGHT;
import static com.spectrayan.spector.memory.kernel.layout.EntityLayout.ENTITY_NODE_BYTES;
import static com.spectrayan.spector.memory.kernel.layout.EntityLayout.ENT_OFF_ADJ_CAPACITY;
import static com.spectrayan.spector.memory.kernel.layout.EntityLayout.ENT_OFF_ADJ_COUNT;
import static com.spectrayan.spector.memory.kernel.layout.EntityLayout.ENT_OFF_ADJ_OFFSET;
import static com.spectrayan.spector.memory.kernel.layout.EntityLayout.ENT_OFF_DEGREE;
import static com.spectrayan.spector.memory.kernel.layout.EntityLayout.ENT_OFF_EDGE_START;
import static com.spectrayan.spector.memory.kernel.layout.EntityLayout.ENT_OFF_NAME_HASH;
import static com.spectrayan.spector.memory.kernel.layout.EntityLayout.ENT_OFF_TYPE;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
 *
 * @deprecated Retired by hypergraph graduation (ADR-0003). Entity identity now lives in
 * {@link EntityDirectory} and topology in {@link HyperEntityGraphMemory}.
 * Retained package-private for {@link EntityGraphMigrationCli} reads and
 * {@link EntityDirectory#deriveFrom(EntityGraphMemory)} only.
 */
@Deprecated(since = "1.2.0", forRemoval = true)
final class EntityGraphMemory extends AbstractGraphMemory<EntityLayout> {

    private static final Logger log = LoggerFactory.getLogger(EntityGraphMemory.class);

    /** Kernel identity for the entity-relationship graph. */
    private static final MemoryId MEMORY_ID = SystemMemoryId.ENTITY.id();
    /** Shared record layout — identifies entity records inside an SMKM container. */
    private static final EntityLayout LAYOUT = new EntityLayout();



    /** Default adjacency slots allocated per entity on first link. */
    static final int DEFAULT_ADJ_PER_ENTITY = 8;

    /** LTP weight increment when an entity is re-mentioned in a memory. */
    private static final float LTP_REINFORCEMENT = com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_ENTITY_LTP_REINFORCEMENT;

    /** Initial weight for a new entity→memory link. */
    private static final float INITIAL_LINK_WEIGHT = 1.0f;

    /** Default maximum edges per entity (configurable). */
    public static final int DEFAULT_MAX_DEGREE = 48;

    /** Maximum adjacency entries per entity (for mmap pre-allocation). */
    static final int MAX_ADJ_PER_ENTITY = 64;

    // ── SMKM container framing: single source of truth is EntityLayout (#435, TD-14). ──
    // GRAPH_SUBHEADER_BYTES / SUB_OFF_* field offsets / DATA_START are static-imported from
    // EntityLayout; this class only references them. Container shape (current):
    // [64B MemoryHeader][16B Entity sub-header][entity slab][edge slab][adj slab].

    // ── Legacy on-disk container magics (migrated in-class, #435) ──
    /** Legacy mmap container magic ('EGMM', 32-byte header), migrated to SMKM. */
    private static final int LEGACY_EGMM_MAGIC = 0x45474D4D; // "EGMM"
    /** Legacy heap-serialized container magic ('EGPH'), migrated to SMKM. */
    private static final int LEGACY_EGPH_MAGIC = 0x45475048; // "EGPH"
    /** Legacy mmap header size in bytes. */
    private static final int LEGACY_MMAP_HEADER_BYTES = 32;
    // Legacy EGMM header: [magic:4B][version:4B][entityCap:4B][edgeCap:4B]
    //                     [entityCount:4B][edgeCount:4B][adjCap:4B][adjHwm:4B]

    // ── Record byte layout: single source of truth is EntityLayout (#435, TD-14). ──
    // ENTITY_NODE_BYTES / EDGE_BYTES / ADJ_ENTRY_BYTES + all *_OFF_ field offsets are
    // static-imported from EntityLayout; this class only references them.

    /**
     * Minimum bridge score (unsigned 0-255) required to protect an entity edge
     * from eviction during decay. Matches {@code HebbianGraph.BRIDGE_PROTECTION_THRESHOLD}.
     */
    static final int BRIDGE_PROTECTION_THRESHOLD = 224;

    /** Maximum entity edge weight — prevents runaway amplification from cross-capture boosts. */
    static final float MAX_EDGE_WEIGHT = 20.0f;

    // ── Segments: the entity node slab is the kernel segment() (owned by the substrate);
    //    the edge slab and region-doubling adjacency slab stay Entity-owned. ──
    /** Alias of the substrate {@link #segment()} — the entity node slab. */
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

    /** True when segments are backed by mmap'd files (DISK mode). */
    private final boolean fileBacked;
    /** The underlying FileChannel for mmap mode (null for heap mode). Mirrors the inherited channel. */
    private final MemorySegment headerSegment;
    /** Path to the mmap file (null for heap mode). Mirrors the inherited path. */
    private final Path mmapFilePath;

    /** Optional encryptor for name index persistence (set by enterprise layer). */
    private volatile DataEncryptor dataEncryptor;

    /** Open-schema entity type registry (String ↔ int). */
    private final TypeRegistryMemory entityTypeRegistryMemory;
    /** Open-schema relation type registry (String ↔ int). */
    private final TypeRegistryMemory relationTypeRegistryMemory;

    /** Maximum edges per entity (configurable via constructor). */
    private final int maxDegree;

    /** Reflection cycle counter — incremented externally. */
    private int currentCycle;

    /** Edge importance scorer (configurable weights). */
    private final EdgeImportance edgeImportance;

    private final MemoryId memoryId;

    /**
     * Creates a new heap-backed entity graph with default max degree.
     *
     * @param entityCapacity maximum number of entities
     * @param edgeCapacity   maximum number of edges
     */
    public EntityGraphMemory(int entityCapacity, int edgeCapacity) {
        this(entityCapacity, edgeCapacity, DEFAULT_MAX_DEGREE, EdgeImportance.DEFAULT);
    }

    /**
     * Creates a new heap-backed entity graph with configurable max degree.
     *
     * @param entityCapacity maximum number of entities
     * @param edgeCapacity   maximum number of edges
     * @param maxDegree      maximum edges per entity
     * @param edgeImportance edge importance scorer
     */
    public EntityGraphMemory(int entityCapacity, int edgeCapacity, int maxDegree, EdgeImportance edgeImportance) {
        this(Init.heap(entityCapacity, edgeCapacity), maxDegree, edgeImportance);
    }

    /**
     * Creates or opens a file-backed (mmap) entity graph with default max degree.
     */
    public EntityGraphMemory(Path filePath, int entityCapacity, int edgeCapacity) {
        this(filePath, entityCapacity, edgeCapacity, DEFAULT_MAX_DEGREE, EdgeImportance.DEFAULT);
    }

    /**
     * Creates or opens a file-backed (mmap) entity graph with configurable max degree.
     *
     * <p>The on-disk container is the kernel SMKM format:
     * <pre>
     *   [64B MemoryHeader (SMKM)][16B Entity sub-header][entity slab][edge slab][adjacency slab]
     * </pre>
     * Legacy EGMM (32-byte header) and EGPH (heap-serialized) files are migrated to SMKM in
     * place — with a {@code .bak} of the original — before mapping (#435).</p>
     *
     * @param filePath       path to the graph file
     * @param entityCapacity maximum number of entities (defaults for a fresh file)
     * @param edgeCapacity   maximum number of edges (defaults for a fresh file)
     * @param maxDegree      maximum edges per entity
     * @param edgeImportance edge importance scorer
     */
    public EntityGraphMemory(Path filePath, int entityCapacity, int edgeCapacity,
                             int maxDegree, EdgeImportance edgeImportance) {
        this(Init.mmap(filePath, entityCapacity, edgeCapacity), maxDegree, edgeImportance);
    }

    public EntityGraphMemory(int entityCapacity) {
        this(entityCapacity, entityCapacity * DEFAULT_MAX_DEGREE);
    }

    static EntityGraphMemory fromLoaded(int entityCapacity, int edgeCapacity, int entityCount, int edgeCount,
                                  Arena arena, MemorySegment entitySegment, MemorySegment edgeSegment,
                                  MemorySegment adjacencySegment, int adjSegmentCapacity, int adjHighWaterMark,
                                  ConcurrentHashMap<String, Integer> nameIndex,
                                  TypeRegistryMemory entityTypeRegistry, TypeRegistryMemory relationTypeRegistry) {
        Init init = new Init(entityCapacity, edgeCapacity, entityCount, edgeCount, arena,
                entitySegment, edgeSegment, adjacencySegment, adjSegmentCapacity, adjHighWaterMark,
                false, null, null, entityTypeRegistry, relationTypeRegistry, nameIndex);
        return new EntityGraphMemory(init, DEFAULT_MAX_DEGREE, EdgeImportance.DEFAULT);
    }

    /**
     * Single delegating constructor. Wraps the pre-built arena + entity node slab as the kernel
     * substrate {@link #segment()}, and adopts the Entity-owned edge and region-doubling
     * adjacency slabs.
     */
    private EntityGraphMemory(Init init, int maxDegree, EdgeImportance edgeImportance) {
        super(MEMORY_ID, LAYOUT, init.entityCapacity, init.arena, init.entitySegment, init.entityCount,
                init.persistent, init.filePath, null);
        this.entitySegment = init.entitySegment;
        this.edgeSegment = init.edgeSegment;
        this.adjacencySegment = init.adjacencySegment;
        this.entityCapacity = init.entityCapacity;
        this.edgeCapacity = init.edgeCapacity;
        this.entityCount = init.entityCount;
        this.edgeCount = init.edgeCount;
        this.adjSegmentCapacity = init.adjSegmentCapacity;
        this.adjHighWaterMark = init.adjHighWaterMark;
        this.fileBacked = init.persistent;
        this.headerSegment = init.headerSegment;
        this.mmapFilePath = init.filePath;
        this.maxDegree = maxDegree;
        this.edgeImportance = edgeImportance;
        this.currentCycle = 0;
        this.memoryId = MEMORY_ID;
        this.entityTypeRegistryMemory = init.entityTypeRegistry;
        this.relationTypeRegistryMemory = init.relationTypeRegistry;
        if (init.nameIndex != null && !init.nameIndex.isEmpty()) {
            this.nameIndex.putAll(init.nameIndex);
        }
        log.info("EntityGraph initialized ({}): entities={}/{}, edges={}/{}, maxDegree={}, adjCap={}, file={}",
                init.persistent ? "mmap" : "heap", entityCount, entityCapacity, edgeCount, edgeCapacity,
                maxDegree, adjSegmentCapacity, mmapFilePath != null ? mmapFilePath.getFileName() : "<heap>");
    }

    // ══════════════════════════════════════════════════════════════
    // CONSTRUCTION HOLDER + BUILDERS (super() must run first)
    // ══════════════════════════════════════════════════════════════

    /** Immutable bundle of everything the delegating constructor needs. */
    private record Init(int entityCapacity, int edgeCapacity, int entityCount, int edgeCount,
                        Arena arena, MemorySegment entitySegment, MemorySegment edgeSegment,
                        MemorySegment adjacencySegment, int adjSegmentCapacity, int adjHighWaterMark,
                        boolean persistent, Path filePath, MemorySegment headerSegment,
                        TypeRegistryMemory entityTypeRegistry, TypeRegistryMemory relationTypeRegistry,
                        ConcurrentHashMap<String, Integer> nameIndex) {

        /** Builds a fresh heap-backed graph. */
        static Init heap(int entityCapacity, int edgeCapacity) {
            Arena arena = Arena.ofShared();
            MemorySegment entitySegment = arena.allocate((long) ENTITY_NODE_BYTES * entityCapacity);
            MemorySegment edgeSegment = arena.allocate((long) EDGE_BYTES * edgeCapacity);
            int adjCap = entityCapacity * DEFAULT_ADJ_PER_ENTITY;
            MemorySegment adjacencySegment = arena.allocate((long) ADJ_ENTRY_BYTES * adjCap);
            entitySegment.fill((byte) 0);
            edgeSegment.fill((byte) 0);
            adjacencySegment.fill((byte) 0);
            return new Init(entityCapacity, edgeCapacity, 0, 0, arena,
                    entitySegment, edgeSegment, adjacencySegment, adjCap, 0,
                    false, null, null,
                    TypeRegistryMemory.seeded(SystemMemoryId.ENTITY_TYPE, EntityType.SEED),
                    TypeRegistryMemory.seeded(SystemMemoryId.RELATION_TYPE, RelationType.SEED),
                    null);
        }

        /** Opens (or creates) an SMKM mmap file, migrating legacy EGMM/EGPH files in place first. */
        static Init mmap(Path filePath, int defaultEntityCap, int defaultEdgeCap) {
            Path parent = filePath.getParent();
            try {
                if (parent != null) {
                    Files.createDirectories(parent);
                }

                boolean exists = Files.exists(filePath) && Files.size(filePath) >= 4;
                if (exists) {
                    int beMagic = peekMagicBE(filePath);
                    int leMagic = Integer.reverseBytes(beMagic);
                    if (leMagic != MemoryHeader.MAGIC) {
                        // Legacy container — migrate in place to SMKM before mapping (#435). This
                        // is the fallback for a direct public-ctor call; load() migrates up front
                        // with the caller's encryptor.
                        migrateLegacyFileToSmkm(filePath, beMagic, defaultEntityCap, defaultEdgeCap, null);
                    }
                }

                int entityCap;
                int edgeCap;
                int entityCount;
                int edgeCount;
                int adjCap;
                int adjHwm;
                FileChannel ch = FileChannel.open(filePath,
                        StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);

                // A pre-existing file that is too small to hold the SMKM header is truncated/corrupt,
                // not "fresh" — surface it rather than silently reinitializing (#432/#433 TD-04).
                if (exists && ch.size() < DATA_START) {
                    throw new IOException("SMKM entity-graph file truncated: size=" + ch.size()
                            + " < " + DATA_START + ": " + filePath);
                }

                if (!exists) {
                    entityCap = defaultEntityCap;
                    edgeCap = defaultEdgeCap;
                    entityCount = 0;
                    edgeCount = 0;
                    adjCap = defaultEntityCap * MAX_ADJ_PER_ENTITY;
                    adjHwm = 0;
                    writeSmkmHeaderToChannel(ch, entityCap, edgeCap, entityCount, edgeCount, adjCap, adjHwm);
                    long total = DATA_START + (long) ENTITY_NODE_BYTES * entityCap
                            + (long) EDGE_BYTES * edgeCap + (long) ADJ_ENTRY_BYTES * adjCap;
                    if (ch.size() < total) {
                        ch.position(total - 1);
                        ch.write(ByteBuffer.wrap(new byte[]{0}));
                    }
                    ch.force(true);
                } else {
                    try (Arena tmpArena = Arena.ofConfined()) {
                        MemorySegment head = tmpArena.allocate(DATA_START);
                        ByteBuffer hb = head.asByteBuffer();
                        ch.position(0);
                        while (hb.hasRemaining() && ch.read(hb) >= 0) {
                            // fill header
                        }
                        if (!MemoryHeader.isValid(head, 0L)
                                || MemoryHeader.readShape(head, 0L) != MemoryShape.GRAPH
                                || MemoryHeader.readLayoutId(head, 0L) != LAYOUT.layoutId()) {
                            throw new IOException("invalid SMKM entity-graph header: " + filePath);
                        }
                        entityCap = (int) MemoryHeader.readCapacity(head, 0L);
                        entityCount = (int) MemoryHeader.readCount(head, 0L);
                        edgeCap = head.get(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_EDGE_CAPACITY);
                        edgeCount = head.get(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_EDGE_COUNT);
                        adjCap = head.get(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_ADJ_CAPACITY);
                        adjHwm = head.get(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_ADJ_HWM);
                    }
                }

                long entityBytes = (long) ENTITY_NODE_BYTES * entityCap;
                long edgeBytes = (long) EDGE_BYTES * edgeCap;
                long adjBytes = (long) ADJ_ENTRY_BYTES * adjCap;
                Arena arena = Arena.ofShared();
                long offset = DATA_START;
                MemorySegment entitySegment = ch.map(FileChannel.MapMode.READ_WRITE, offset, entityBytes, arena);
                offset += entityBytes;
                MemorySegment edgeSegment = ch.map(FileChannel.MapMode.READ_WRITE, offset, edgeBytes, arena);
                offset += edgeBytes;
                MemorySegment adjacencySegment = ch.map(FileChannel.MapMode.READ_WRITE, offset, adjBytes, arena);
                MemorySegment headerSegment = ch.map(FileChannel.MapMode.READ_WRITE, 0, DATA_START, arena);
                ch.close();

                TypeRegistryMemory entityTypes;
                TypeRegistryMemory relationTypes;
                if (parent != null) {
                    entityTypes = TypeRegistryMemory.load(
                            StorageLayout.entityTypesRuntime(parent), SystemMemoryId.ENTITY_TYPE, EntityType.SEED);
                    relationTypes = TypeRegistryMemory.load(
                            StorageLayout.relationTypesRuntime(parent), SystemMemoryId.RELATION_TYPE, RelationType.SEED);
                } else {
                    entityTypes = TypeRegistryMemory.seeded(SystemMemoryId.ENTITY_TYPE, EntityType.SEED);
                    relationTypes = TypeRegistryMemory.seeded(SystemMemoryId.RELATION_TYPE, RelationType.SEED);
                }

                return new Init(entityCap, edgeCap, entityCount, edgeCount, arena,
                        entitySegment, edgeSegment, adjacencySegment, adjCap, adjHwm,
                        true, filePath, headerSegment, entityTypes, relationTypes, null);
            } catch (IOException e) {
                throw new SpectorGraphPersistenceException("EntityGraph", filePath, e);
            }
        }
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
        if (wal != null && !bypassWal) {
            wal.appendGraphAddNode(memoryId.toString(), entityId, normalized, type);
        }
        long offset = (long) entityId * ENTITY_NODE_BYTES;
        int typeId = entityTypeRegistryMemory.getOrRegister(type);

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
        long stamp = lock.writeLock();
        try {
            addRelationLocked(fromEntity, toEntity, type);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /** Core of {@link #addRelation}; the caller must hold the write lock. */
    private void addRelationLocked(int fromEntity, int toEntity, String type) {
        if (fromEntity < 0 || fromEntity >= entityCount) return;
        if (toEntity < 0 || toEntity >= entityCount) return;
        if (fromEntity == toEntity) return;

        if (wal != null && !bypassWal) {
            wal.appendAdjAddEdge(memoryId.toString(), fromEntity, toEntity, (type != null ? type : "OTHER").getBytes(StandardCharsets.UTF_8));
        }

        int typeId = relationTypeRegistryMemory.getOrRegister(type != null ? type : "OTHER");
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
    }

    /**
     * Boosts the weight of an existing entity edge (STC cross-capture).
     *
     * <p>Used during reflection to propagate Hebbian co-activation strength
     * to entity edges between connected memories' entities. Mirrors the
     * biological Synaptic Tagging and Capture mechanism (Frey & Morris, 1997)
     * where strong synapses protect nearby weak synapses through shared
     * plasticity-related proteins.</p>
     *
     * <p>This method only boosts <em>existing</em> edges — it does not create
     * new entity relations. The boost is capped at {@link #MAX_EDGE_WEIGHT}
     * to prevent runaway amplification.</p>
     *
     * @param fromEntity source entity ID
     * @param toEntity   target entity ID
     * @param boost      weight increment (clamped to [0, MAX_EDGE_WEIGHT])
     * @return {@code true} if the edge was found and boosted, {@code false} otherwise
     */
    public boolean boostEdgeWeight(int fromEntity, int toEntity, float boost) {
        if (fromEntity < 0 || fromEntity >= entityCount) return false;
        if (toEntity < 0 || toEntity >= entityCount) return false;
        if (fromEntity == toEntity || boost <= 0.0f) return false;

        long stamp = lock.writeLock();
        try {
            long entityOffset = (long) fromEntity * ENTITY_NODE_BYTES;
            int degree = entitySegment.get(ValueLayout.JAVA_INT, entityOffset + ENT_OFF_DEGREE);
            int edgeStart = entitySegment.get(ValueLayout.JAVA_INT, entityOffset + ENT_OFF_EDGE_START);

            if (edgeStart < 0 || degree == 0) return false;

            for (int i = 0; i < degree; i++) {
                long edgeOffset = (long) (edgeStart + i) * EDGE_BYTES;
                int target = edgeSegment.get(ValueLayout.JAVA_INT, edgeOffset + EDGE_OFF_TARGET);
                if (target == toEntity) {
                    float weight = edgeSegment.get(ValueLayout.JAVA_FLOAT, edgeOffset + EDGE_OFF_WEIGHT);
                    float boosted = Math.min(weight + boost, MAX_EDGE_WEIGHT);
                    edgeSegment.set(ValueLayout.JAVA_FLOAT, edgeOffset + EDGE_OFF_WEIGHT, boosted);
                    // Update recency — cross-capture refreshes the edge's "last seen" cycle
                    edgeSegment.set(ValueLayout.JAVA_SHORT, edgeOffset + EDGE_OFF_LAST_CYCLE,
                            (short) currentCycle);
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlockWrite(stamp);
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
        long stamp = lock.writeLock();
        try {
            linkEntityToMemoryLocked(entityId, memoryIdx);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /** Core of {@link #linkEntityToMemory}; the caller must hold the write lock. */
    private void linkEntityToMemoryLocked(int entityId, int memoryIdx) {
        if (entityId < 0 || entityId >= entityCount) return;
            if (wal != null && !bypassWal) {
                wal.appendGraphLinkMemory(memoryId.toString(), entityId, memoryIdx);
            }
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
    }

    /**
     * Ensures the adjacency segment can hold at least {@code requiredEntries} entries.
     * Doubles the segment capacity if needed, copying existing data.
     *
     * <p>Must be called under the substrate write lock (it reassigns {@link #adjacencySegment}).</p>
     */
    private void ensureAdjSegmentCapacity(int requiredEntries) {
        if (requiredEntries <= adjSegmentCapacity) return;
        if (fileBacked) {
            // mmap mode: pre-allocated at max capacity, should never need to grow.
            // If this triggers, MAX_ADJ_PER_ENTITY needs increasing. Surface it as a
            // domain exception carrying CAPACITY_EXCEEDED rather than the generic
            // GRAPH_ENTITY_FAILED (#433 TD-07): this is a capacity limit, not an
            // arbitrary operation failure.
            throw new SpectorEntityGraphException(
                    ErrorCode.CAPACITY_EXCEEDED,
                    "adjacency segment exhausted (mmap); increase MAX_ADJ_PER_ENTITY (currently "
                            + MAX_ADJ_PER_ENTITY + ")",
                    adjSegmentCapacity, requiredEntries);
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
        // Validated read lock (NOT optimistic): compactAdjacency()/ensureAdjSegmentCapacity()
        // reassign the adjacencySegment field under the write lock, so a reader must not race
        // a compaction and dereference a stale segment (#435 StampedLock hazard).
        long stamp = lock.readLock();
        try {
            return memoriesForEntityLocked(entityId);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    /** Core of {@link #memoriesForEntity}; the caller must hold at least the read lock. */
    private int[] memoriesForEntityLocked(int entityId) {
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
        // Validated read lock: adjacencySegment may be reassigned by a concurrent compaction.
        long stamp = lock.readLock();
        try {
            long entOffset = (long) entityId * ENTITY_NODE_BYTES;
            int adjOff = entitySegment.get(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_OFFSET);
            int adjCnt = entitySegment.get(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_COUNT);
            if (adjOff < 0 || refIndex < 0 || refIndex >= adjCnt) return -1;
            return adjacencySegment.get(ValueLayout.JAVA_INT,
                    (long) (adjOff + refIndex) * ADJ_ENTRY_BYTES + ADJ_OFF_MEM_IDX);
        } finally {
            lock.unlockRead(stamp);
        }
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
        // Validated read lock: adjacencySegment may be reassigned by a concurrent compaction.
        long stamp = lock.readLock();
        try {
            long entOffset = (long) entityId * ENTITY_NODE_BYTES;
            int adjOff = entitySegment.get(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_OFFSET);
            int adjCnt = entitySegment.get(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_COUNT);
            if (adjOff < 0 || refIndex < 0 || refIndex >= adjCnt) return 0f;
            return adjacencySegment.get(ValueLayout.JAVA_FLOAT,
                    (long) (adjOff + refIndex) * ADJ_ENTRY_BYTES + ADJ_OFF_WEIGHT);
        } finally {
            lock.unlockRead(stamp);
        }
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
        return entityTypeRegistryMemory.nameOf(typeId);
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

            String relType = relationTypeRegistryMemory.nameOf(relTypeId);

            result.add(new EntityEdge(target, relType, weight,
                    Byte.toUnsignedInt(edgeSegment.get(ValueLayout.JAVA_BYTE, edgeOffset + EDGE_OFF_BRIDGE_SCORE))));
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
        return decayEdges(decayFactor, minWeight, null);
    }

    /**
     * Decays entity edge weights, collecting health metrics for observability.
     *
     * <p>Same as {@link #decayEdges(float, float)} but populates the supplied
     * {@link GraphHealthMetrics} with per-edge statistics during the cycle.</p>
     *
     * @param decayFactor multiplicative factor (e.g., 0.9 = 10% decay per cycle)
     * @param minWeight   edges with weight below this after decay are pruned
     * @param metrics     optional metrics collector (may be {@code null})
     * @return number of edges pruned
     */
    public int decayEdges(float decayFactor, float minWeight, GraphHealthMetrics metrics) {
        long stamp = lock.writeLock();
        try {
        currentCycle++;
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

                // Read bridge score from previous cycle for eviction decision
                byte bridgeRaw = edgeSegment.get(ValueLayout.JAVA_BYTE, edgeOffset + EDGE_OFF_BRIDGE_SCORE);
                int bridgeUnsigned = Byte.toUnsignedInt(bridgeRaw);

                boolean keep;
                boolean bridgeProtected = false;
                if (decayed >= minWeight) {
                    keep = true;
                } else if (bridgeUnsigned >= BRIDGE_PROTECTION_THRESHOLD) {
                    // Bridge protection: floor weight instead of evicting
                    decayed = minWeight;
                    keep = true;
                    bridgeProtected = true;
                } else {
                    keep = false;
                }

                if (keep) {
                    // Keep edge: compact if needed (copy all 16 bytes including metadata)
                    if (newDegree < i) {
                        long destOffset = (long) (edgeStart + newDegree) * EDGE_BYTES;
                        int target = edgeSegment.get(ValueLayout.JAVA_INT, edgeOffset + EDGE_OFF_TARGET);
                        int relType = edgeSegment.get(ValueLayout.JAVA_INT, edgeOffset + EDGE_OFF_REL_TYPE);
                        short lastCyc = edgeSegment.get(ValueLayout.JAVA_SHORT, edgeOffset + EDGE_OFF_LAST_CYCLE);
                        byte flags = edgeSegment.get(ValueLayout.JAVA_BYTE, edgeOffset + EDGE_OFF_EDGE_FLAGS);
                        edgeSegment.set(ValueLayout.JAVA_INT, destOffset + EDGE_OFF_TARGET, target);
                        edgeSegment.set(ValueLayout.JAVA_INT, destOffset + EDGE_OFF_REL_TYPE, relType);
                        edgeSegment.set(ValueLayout.JAVA_FLOAT, destOffset + EDGE_OFF_WEIGHT, decayed);
                        edgeSegment.set(ValueLayout.JAVA_SHORT, destOffset + EDGE_OFF_LAST_CYCLE, lastCyc);
                        edgeSegment.set(ValueLayout.JAVA_BYTE, destOffset + EDGE_OFF_BRIDGE_SCORE, bridgeRaw);
                        edgeSegment.set(ValueLayout.JAVA_BYTE, destOffset + EDGE_OFF_EDGE_FLAGS, flags);
                    } else {
                        edgeSegment.set(ValueLayout.JAVA_FLOAT, edgeOffset + EDGE_OFF_WEIGHT, decayed);
                    }
                    newDegree++;

                    // Record metrics for surviving edge
                    if (metrics != null) {
                        metrics.recordEntitySurvivor(bridgeUnsigned);
                        if (bridgeProtected) {
                            metrics.recordEntityBridgeProtection();
                        }
                    }
                } else {
                    pruned++;
                    if (metrics != null) {
                        metrics.recordEntityDecay();
                    }
                }
            }
            entitySegment.set(ValueLayout.JAVA_INT, entOffset + ENT_OFF_DEGREE, newDegree);
        }

        // Phase 2: Recompute bridge scores for all surviving edges
        updateEntityBridgeScores();

        // Phase 3: Compute entity hierarchy depth statistics
        computeHierarchyDepth(metrics);

        if (pruned > 0) {
            log.info("EntityGraph decayed edges: {} pruned below threshold {}, cycle={}",
                    pruned, minWeight, currentCycle);
        }
        return pruned;
        } finally {
            lock.unlockWrite(stamp);
        }
    }


    /**
     * Recomputes bridge scores for all entity edges.
     *
     * <p>Called during {@link #decayEdges} after compaction. Uses the
     * {@link BridgeDetector} neighbor overlap heuristic.</p>
     *
     * <p><b>Cost:</b> O(E × MAX_DEGREE²) where E = entityCount.</p>
     */
    private void updateEntityBridgeScores() {
        // Pre-extract neighbor arrays for all entities
        int[][] neighborArrays = new int[entityCount][];
        int[] degrees = new int[entityCount];

        for (int e = 0; e < entityCount; e++) {
            long entOffset = (long) e * ENTITY_NODE_BYTES;
            int degree = entitySegment.get(ValueLayout.JAVA_INT, entOffset + ENT_OFF_DEGREE);
            int edgeStart = entitySegment.get(ValueLayout.JAVA_INT, entOffset + ENT_OFF_EDGE_START);
            degrees[e] = degree;
            if (degree > 0 && edgeStart >= 0) {
                int[] neighbors = new int[degree];
                for (int i = 0; i < degree; i++) {
                    long edgeOffset = (long) (edgeStart + i) * EDGE_BYTES;
                    neighbors[i] = edgeSegment.get(ValueLayout.JAVA_INT, edgeOffset + EDGE_OFF_TARGET);
                }
                neighborArrays[e] = neighbors;
            }
        }

        // Compute and store bridge scores
        for (int e = 0; e < entityCount; e++) {
            int degree = degrees[e];
            if (degree == 0) continue;
            long entOffset = (long) e * ENTITY_NODE_BYTES;
            int edgeStart = entitySegment.get(ValueLayout.JAVA_INT, entOffset + ENT_OFF_EDGE_START);
            if (edgeStart < 0) continue;

            for (int i = 0; i < degree; i++) {
                long edgeOffset = (long) (edgeStart + i) * EDGE_BYTES;
                int target = edgeSegment.get(ValueLayout.JAVA_INT, edgeOffset + EDGE_OFF_TARGET);

                int shared = 0;
                int targetDegree = 0;
                if (target >= 0 && target < entityCount && neighborArrays[target] != null) {
                    targetDegree = degrees[target];
                    shared = BridgeDetector.countSharedNeighbors(
                            neighborArrays[e], degree,
                            neighborArrays[target], targetDegree);
                }

                int bridgeScore = BridgeDetector.computeBridgeScore(shared, degree, targetDegree);
                edgeSegment.set(ValueLayout.JAVA_BYTE, edgeOffset + EDGE_OFF_BRIDGE_SCORE, (byte) bridgeScore);
            }
        }
    }

    /**
     * Computes entity hierarchy depth statistics via BFS from every entity.
     *
     * <p>For each entity, runs a BFS traversal and records the hop distance
     * to every reachable entity. This produces a depth distribution that
     * answers: "Is hierarchy depth &gt; 3 hops common?" — a key factor in
     * deciding whether hyperbolic embeddings are warranted.</p>
     *
     * <p><b>Cost:</b> O(V × (V + E)) where V = entityCount, E = total edges.
     * With V=27 and E=~400, this is negligible (~10K operations).</p>
     *
     * @param metrics the metrics collector to record depth data into
     */
    void computeHierarchyDepth(GraphHealthMetrics metrics) {
        if (metrics == null || entityCount == 0) return;

        // BFS from every entity — record depth to each reachable entity
        for (int startEntity = 0; startEntity < entityCount; startEntity++) {
            boolean[] visited = new boolean[entityCount];
            // Use packed long: upper 32 bits = entityId, lower 32 bits = depth
            java.util.ArrayDeque<Long> queue = new java.util.ArrayDeque<>();
            queue.add(packBfsNode(startEntity, 0));
            visited[startEntity] = true;

            while (!queue.isEmpty()) {
                long packed = queue.poll();
                int entityId = (int) (packed >>> 32);
                int depth = (int) packed;

                if (depth > 0) {
                    // Record this depth (each pair counted once: startEntity < entityId)
                    if (startEntity < entityId) {
                        metrics.recordEntityDepth(depth);
                    }
                }

                // Expand neighbors
                long entOffset = (long) entityId * ENTITY_NODE_BYTES;
                int degree = entitySegment.get(ValueLayout.JAVA_INT, entOffset + ENT_OFF_DEGREE);
                int edgeStart = entitySegment.get(ValueLayout.JAVA_INT, entOffset + ENT_OFF_EDGE_START);
                if (edgeStart < 0 || degree == 0) continue;

                for (int i = 0; i < degree; i++) {
                    long edgeOffset = (long) (edgeStart + i) * EDGE_BYTES;
                    int target = edgeSegment.get(ValueLayout.JAVA_INT, edgeOffset + EDGE_OFF_TARGET);
                    if (target >= 0 && target < entityCount && !visited[target]) {
                        visited[target] = true;
                        queue.add(packBfsNode(target, depth + 1));
                    }
                }
            }
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
        long stamp = lock.writeLock();
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
            lock.unlockWrite(stamp);
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
        long stamp = lock.writeLock();
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
            MemorySegment newSeg;
            Arena tempArena = null;
            if (fileBacked) {
                tempArena = Arena.ofConfined();
                newSeg = tempArena.allocate((long) ADJ_ENTRY_BYTES * adjSegmentCapacity);
            } else {
                newSeg = arena.allocate((long) ADJ_ENTRY_BYTES * newCapacity);
            }
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

            if (fileBacked) {
                // Copy compacted data back to the mapped segment
                MemorySegment.copy(newSeg, 0, adjacencySegment, 0, (long) writePos * ADJ_ENTRY_BYTES);
                // Zero out the remaining unused part
                long unusedOffset = (long) writePos * ADJ_ENTRY_BYTES;
                long unusedBytes = ((long) adjSegmentCapacity * ADJ_ENTRY_BYTES) - unusedOffset;
                if (unusedBytes > 0) {
                    adjacencySegment.asSlice(unusedOffset, unusedBytes).fill((byte) 0);
                }
                tempArena.close();
            } else {
                adjacencySegment = newSeg;
                adjSegmentCapacity = newCapacity;
            }
            adjHighWaterMark = writePos;

            long newUsed = (long) writePos * ADJ_ENTRY_BYTES;
            long reclaimed = oldUsed - newUsed;
            if (reclaimed > 0) {
                log.info("EntityGraph adjacency compacted: {} live entries, reclaimed {}KB",
                        liveEntries, reclaimed / 1024);
            }
            return Math.max(reclaimed, 0);
        } finally {
            lock.unlockWrite(stamp);
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
        long stamp = lock.writeLock();
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
            lock.unlockWrite(stamp);
        }
    }

    /**
     * Redirects all edges and memory refs from {@code from} to {@code to}.
     *
     * <p>Invoked by {@link #mergeSimilarEntities} while it holds the write lock. Because the
     * substrate {@link #lock} is a non-reentrant {@link java.util.concurrent.locks.StampedLock},
     * this method calls the unlocked {@code *Locked} cores rather than the public lock-acquiring
     * methods, and reads edges lock-free (the caller already holds exclusive access).</p>
     */
    private void redirectEntity(int from, int to) {
        // Move memory refs from 'from' to 'to' via adjacency
        int[] fromMemRefs = memoriesForEntityLocked(from);
        for (int memIdx : fromMemRefs) {
            linkEntityToMemoryLocked(to, memIdx);
        }
        // Clear 'from' adjacency
        long fromOffset = (long) from * ENTITY_NODE_BYTES;
        entitySegment.set(ValueLayout.JAVA_INT, fromOffset + ENT_OFF_ADJ_COUNT, 0);

        // Move edges from 'from' to 'to'
        for (EntityEdge edge : edges(from)) {
            if (edge.targetEntityId() != to) {
                addRelationLocked(to, edge.targetEntityId(), edge.relationType());
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
    public record EntityEdge(int targetEntityId, String relationType, float weight, int bridgeScore) {

        /** Backward-compatible constructor (bridgeScore defaults to 0). */
        public EntityEdge(int targetEntityId, String relationType, float weight) {
            this(targetEntityId, relationType, weight, 0);
        }
    }

    /**
     * A BFS traversal result.
     *
     * <p><b>TODO (JDK 28+ / Project Valhalla):</b> Convert to {@code value record}.
     * Same benefits as EntityEdge above.</p>
     */
    public record TraversalResult(int entityId, int hopDistance) {}

    // ══════════════════════════════════════════════════════════════
    // PERSISTENCE: SMKM container + in-class legacy migration (#435)
    // ══════════════════════════════════════════════════════════════

    /** Saves the graph to the SMKM container. */
    public void save(Path filePath) {
        save(filePath, this.dataEncryptor);
    }

    /**
     * Saves the graph to the SMKM container with optional name-index encryption.
     *
     * <p>An mmap-backed graph saved to its own mapped path updates the file's SMKM header +
     * Entity sub-header and forces all three segments; otherwise a fresh SMKM file is serialized.
     * The nameIndex and type registries are written as sidecar files in both cases.</p>
     *
     * @param filePath  path to write
     * @param encryptor optional encryptor for name index (null = no encryption)
     */
    public void save(Path filePath, DataEncryptor encryptor) {
        if (fileBacked && filePath.equals(mmapFilePath)) {
            long stamp = lock.readLock();
            try {
                writeSmkmHeaderToSegment(headerSegment, entityCapacity, edgeCapacity,
                        entityCount, edgeCount, adjSegmentCapacity, adjHighWaterMark);
                headerSegment.force();
                entitySegment.force();
                edgeSegment.force();
                adjacencySegment.force();
            } finally {
                lock.unlockRead(stamp);
            }
            EntityGraphSerializer.saveNameIndexAndRegistries(this, filePath, encryptor);
            log.info("EntityGraph flushed (SMKM mmap): entities={}/{}, edges={}, adjHwm={}",
                    entityCount, entityCapacity, edgeCount, adjHighWaterMark);
            return;
        }
        // Heap-backed (or saving to a different path): write a fresh SMKM file + sidecars.
        writeSmkmFile(filePath);
        EntityGraphSerializer.saveNameIndexAndRegistries(this, filePath, encryptor);
    }

    /**
     * Writes this graph's data as a fresh SMKM container at {@code filePath}:
     * {@code [64B header][16B Entity sub-header][entity slab][edge slab][adjacency slab]}.
     */
    private void writeSmkmFile(Path filePath) {
        Path parent = filePath.getParent();
        long stamp = lock.readLock();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            long entityBytes = (long) ENTITY_NODE_BYTES * entityCapacity;
            long edgeBytes = (long) EDGE_BYTES * edgeCapacity;
            long adjBytes = (long) ADJ_ENTRY_BYTES * adjSegmentCapacity;
            try (FileChannel ch = FileChannel.open(filePath,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                writeSmkmHeaderToChannel(ch, entityCapacity, edgeCapacity,
                        entityCount, edgeCount, adjSegmentCapacity, adjHighWaterMark);
                ch.position(DATA_START);
                writeSegmentFully(ch, entitySegment, entityBytes);
                writeSegmentFully(ch, edgeSegment, edgeBytes);
                writeSegmentFully(ch, adjacencySegment, adjBytes);
                ch.force(true);
            }
            log.info("EntityGraph saved (SMKM): entities={}, edges={}, adjHwm={} -> {}",
                    entityCount, edgeCount, adjHighWaterMark, filePath);
        } catch (IOException e) {
            throw new SpectorGraphPersistenceException("EntityGraph", filePath, e);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    /**
     * Loads a graph from disk, or returns a fresh (heap) graph if the file is absent.
     *
     * @param filePath          path to the graph file
     * @param defaultEntityCap  entity capacity if the file doesn't exist
     * @param defaultEdgeCap    edge capacity if the file doesn't exist
     * @return an EntityGraphMemory (loaded or fresh)
     */
    public static EntityGraphMemory load(Path filePath, int defaultEntityCap, int defaultEdgeCap) {
        return load(filePath, defaultEntityCap, defaultEdgeCap, null);
    }

    /**
     * Loads a graph from disk with optional name-index decryption. This is the single in-class
     * migration authority (#435, CEO decision — not the codec): it classifies the container by
     * magic and self-heals legacy files.
     *
     * <ul>
     *   <li>SMKM ({@code 0x534D4B4D}) — opened directly.</li>
     *   <li>Legacy EGMM ({@code 0x45474D4D}, 32-byte mmap header) — migrated in place to SMKM.</li>
     *   <li>Legacy EGPH ({@code 0x45475048}, heap-serialized) — migrated in place to SMKM.</li>
     *   <li>Present but unreadable/unknown — throws {@link SpectorGraphPersistenceException}
     *       (never a silent empty graph, #432/#433 TD-04).</li>
     *   <li>Absent — a fresh heap graph.</li>
     * </ul>
     */
    public static EntityGraphMemory load(Path filePath, int defaultEntityCap, int defaultEdgeCap,
                                    DataEncryptor encryptor) {
        if (filePath == null || !Files.exists(filePath)) {
            log.info("EntityGraph file not found, creating fresh: {}", filePath);
            return new EntityGraphMemory(defaultEntityCap, defaultEdgeCap);
        }
        try {
            long size = Files.size(filePath);
            if (size < 4) {
                throw new IOException("file too small to contain a magic number: " + size + " bytes");
            }
            int beMagic = peekMagicBE(filePath);
            int leMagic = Integer.reverseBytes(beMagic);
            if (leMagic == MemoryHeader.MAGIC) {
                return openSmkm(filePath, defaultEntityCap, defaultEdgeCap, encryptor);
            }
            if (beMagic == LEGACY_EGMM_MAGIC || beMagic == LEGACY_EGPH_MAGIC) {
                log.info("EntityGraph migrating legacy container 0x{} -> SMKM: {}",
                        Integer.toHexString(beMagic), filePath);
                migrateLegacyFileToSmkm(filePath, beMagic, defaultEntityCap, defaultEdgeCap, encryptor);
                return openSmkm(filePath, defaultEntityCap, defaultEdgeCap, encryptor);
            }
            throw new IOException("Unrecognized EntityGraph file magic: 0x"
                    + Integer.toHexString(beMagic) + " (expected SMKM 0x"
                    + Integer.toHexString(MemoryHeader.MAGIC) + ", EGMM 0x"
                    + Integer.toHexString(LEGACY_EGMM_MAGIC) + " or EGPH 0x"
                    + Integer.toHexString(LEGACY_EGPH_MAGIC) + "): " + filePath);
        } catch (SpectorGraphPersistenceException e) {
            throw e;
        } catch (Exception e) {
            // Present but unreadable is a data-integrity problem, not "start fresh" (#432/#433).
            log.error("Failed to load EntityGraph from {} (file present but unreadable)", filePath, e);
            throw new SpectorGraphPersistenceException("EntityGraph", filePath, e);
        }
    }

    /** Opens an SMKM entity-graph file (mmap) and hydrates the nameIndex sidecar. */
    private static EntityGraphMemory openSmkm(Path filePath, int defaultEntityCap, int defaultEdgeCap,
                                              DataEncryptor encryptor) {
        EntityGraphMemory graph = new EntityGraphMemory(filePath, defaultEntityCap, defaultEdgeCap);
        ConcurrentHashMap<String, Integer> names =
                EntityGraphSerializer.loadNameIndexSidecar(filePath, encryptor);
        if (names != null && !names.isEmpty()) {
            graph.nameIndexInternal().putAll(names);
        }
        graph.setDataEncryptor(encryptor);
        return graph;
    }

    // ── SMKM header + region helpers ──

    /** Reads the leading 4-byte magic in big-endian (the order legacy writers used). */
    private static int peekMagicBE(Path filePath) throws IOException {
        try (FileChannel ch = FileChannel.open(filePath, StandardOpenOption.READ)) {
            ByteBuffer buf = ByteBuffer.allocate(4);
            if (ch.read(buf) < 4) {
                throw new IOException("file too small to contain a magic number: " + filePath);
            }
            buf.flip();
            return buf.getInt();
        }
    }

    /** Writes the 64-byte SMKM header + 16-byte Entity sub-header to the start of {@code ch}. */
    private static void writeSmkmHeaderToChannel(FileChannel ch, int entityCap, int edgeCap,
                                                 int entityCount, int edgeCount, int adjCap, int adjHwm)
            throws IOException {
        try (Arena confined = Arena.ofConfined()) {
            MemorySegment head = confined.allocate(DATA_START);
            long now = System.currentTimeMillis();
            MemoryHeader.write(head, 0L, LAYOUT.schemaVersion(), MemoryShape.GRAPH, 0x01,
                    entityCap, entityCount, ENTITY_NODE_BYTES, LAYOUT.layoutId(), now, now);
            head.set(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_EDGE_CAPACITY, edgeCap);
            head.set(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_EDGE_COUNT, edgeCount);
            head.set(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_ADJ_CAPACITY, adjCap);
            head.set(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_ADJ_HWM, adjHwm);
            ByteBuffer buf = head.asByteBuffer();
            ch.position(0);
            while (buf.hasRemaining()) {
                ch.write(buf);
            }
        }
    }

    /** Writes the 64-byte SMKM header + 16-byte Entity sub-header to mapped segment directly. */
    private static void writeSmkmHeaderToSegment(MemorySegment header, int entityCap, int edgeCap,
                                                 int entityCount, int edgeCount, int adjCap, int adjHwm) {
        long now = System.currentTimeMillis();
        MemoryHeader.write(header, 0L, LAYOUT.schemaVersion(), MemoryShape.GRAPH, 0x01,
                entityCap, entityCount, ENTITY_NODE_BYTES, LAYOUT.layoutId(), now, now);
        header.set(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_EDGE_CAPACITY, edgeCap);
        header.set(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_EDGE_COUNT, edgeCount);
        header.set(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_ADJ_CAPACITY, adjCap);
        header.set(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_ADJ_HWM, adjHwm);
    }

    private static void writeSegmentFully(FileChannel ch, MemorySegment seg, long bytes)
            throws IOException {
        long written = 0;
        int chunk = 64 * 1024;
        while (written < bytes) {
            int toWrite = (int) Math.min(chunk, bytes - written);
            ByteBuffer buf = seg.asSlice(written, toWrite).asByteBuffer().asReadOnlyBuffer();
            ch.write(buf);
            written += toWrite;
        }
    }

    // ── Legacy migration (in-class single authority, #435) ──

    /** Dispatches legacy migration by magic; rewrites {@code file} in place to SMKM with a {@code .bak}. */
    private static void migrateLegacyFileToSmkm(Path file, int beMagic, int defaultEntityCap,
                                                int defaultEdgeCap, DataEncryptor encryptor) throws IOException {
        if (beMagic == LEGACY_EGMM_MAGIC) {
            migrateEgmmToSmkm(file);
        } else if (beMagic == LEGACY_EGPH_MAGIC) {
            migrateEgphToSmkm(file, defaultEntityCap, defaultEdgeCap, encryptor);
        } else {
            throw new IOException("cannot migrate unknown EntityGraph magic 0x"
                    + Integer.toHexString(beMagic) + ": " + file);
        }
    }

    /**
     * Migrates a legacy EGMM (32-byte header) mmap file to the SMKM container in place. The
     * original is preserved as {@code <name>.bak.egmm}; every data region shifts by
     * {@code DATA_START - 32} bytes and the magic flips EGMM -&gt; SMKM.
     */
    private static void migrateEgmmToSmkm(Path file) throws IOException {
        try (FileChannel in = FileChannel.open(file, StandardOpenOption.READ)) {
            ByteBuffer h = ByteBuffer.allocate(LEGACY_MMAP_HEADER_BYTES); // legacy header is big-endian
            in.position(0);
            if (in.read(h) < LEGACY_MMAP_HEADER_BYTES) {
                throw new IOException("EGMM header truncated: " + file);
            }
            h.flip();
            int magic = h.getInt();
            int version = h.getInt();
            int entityCap = h.getInt();
            int edgeCap = h.getInt();
            int entityCount = h.getInt();
            int edgeCount = h.getInt();
            int adjCap = h.getInt();
            int adjHwm = h.getInt();
            if (magic != LEGACY_EGMM_MAGIC || entityCap < 0 || edgeCap < 0 || adjCap < 0) {
                throw new IOException("invalid EGMM header (magic=0x" + Integer.toHexString(magic)
                        + ", version=" + version + "): " + file);
            }
            long entityBytes = (long) ENTITY_NODE_BYTES * entityCap;
            long edgeBytes = (long) EDGE_BYTES * edgeCap;
            long adjBytes = (long) ADJ_ENTRY_BYTES * adjCap;
            long dataBytes = entityBytes + edgeBytes + adjBytes;
            long expected = LEGACY_MMAP_HEADER_BYTES + dataBytes;
            if (in.size() < expected) {
                throw new IOException("EGMM file truncated: size=" + in.size() + " < " + expected);
            }

            Path bak = file.resolveSibling(file.getFileName() + ".bak.egmm");
            Files.copy(file, bak, StandardCopyOption.REPLACE_EXISTING);

            Path tmp = file.resolveSibling(file.getFileName() + ".smkm.tmp");
            try (FileChannel out = FileChannel.open(tmp,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                writeSmkmHeaderToChannel(out, entityCap, edgeCap, entityCount, edgeCount, adjCap, adjHwm);
                out.position(DATA_START);
                long moved = 0;
                while (moved < dataBytes) {
                    long n = in.transferTo(LEGACY_MMAP_HEADER_BYTES + moved, dataBytes - moved, out);
                    if (n <= 0) break;
                    moved += n;
                }
                if (moved < dataBytes) {
                    throw new IOException("EGMM->SMKM region copy short: " + moved + " < " + dataBytes);
                }
                out.force(true);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Migrates a legacy EGPH (heap-serialized) file to the SMKM container in place. The original
     * is preserved as {@code <name>.bak.egph}. The data is read via {@link EntityGraphSerializer}
     * and re-serialized as SMKM.
     */
    private static void migrateEgphToSmkm(Path file, int defaultEntityCap, int defaultEdgeCap,
                                          DataEncryptor encryptor) throws IOException {
        Path bak = file.resolveSibling(file.getFileName() + ".bak.egph");
        // EGPH stores the name index inline; loadEgph hydrates it, and save() re-emits both the
        // SMKM data file and the name-index / registry sidecars the SMKM open path expects.
        EntityGraphMemory heap = EntityGraphSerializer.loadEgph(file, defaultEntityCap, defaultEdgeCap, encryptor);
        try {
            Files.copy(file, bak, StandardCopyOption.REPLACE_EXISTING);
            heap.save(file, encryptor);
        } finally {
            heap.close();
        }
    }

    // ── Package-private accessors for EntityGraphSerializer ──

    int entityCapacity() { return entityCapacity; }
    int edgeCapacity() { return edgeCapacity; }
    MemorySegment entitySegment() { return entitySegment; }
    MemorySegment edgeSegment() { return edgeSegment; }
    MemorySegment adjacencySegment() { return adjacencySegment; }
    int adjSegmentCapacity() { return adjSegmentCapacity; }
    ConcurrentHashMap<String, Integer> nameIndexInternal() { return nameIndex; }
    public TypeRegistryMemory entityTypeRegistry() { return entityTypeRegistryMemory; }
    public TypeRegistryMemory relationTypeRegistry() { return relationTypeRegistryMemory; }

    /**
     * Resets all entities, edges, and adjacency data by zero-filling segments.
     *
     * <p>Unlike {@link #close()}, this does NOT release the arena. The graph
     * remains usable for new entities after the reset. Used by privacy wipe.</p>
     */
    public void reset() {
        long stamp = lock.writeLock();
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
            lock.unlockWrite(stamp);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // KERNEL INTEGRATION
    // ══════════════════════════════════════════════════════════════
    //
    // id(), layout(), arena(), segment() (= the entity node slab), capacity(),
    // schemaVersion(), shape(), and WAL binding are inherited from the substrate
    // (AbstractGraphMemory/AbstractMemory). size() is overridden because entityCount is the
    // authoritative live count; flush()/close() are overridden to cover all three segments.

    @Override
    public int size() {
        return entityCount;
    }

    @Override
    public void flush() {
        if (entitySegment != null) entitySegment.force();
        if (edgeSegment != null) edgeSegment.force();
        if (adjacencySegment != null) adjacencySegment.force();
    }

    @Override
    public int addEdge(int fromNode, int toNode, MemorySegment edgeBytes) {
        addRelation(fromNode, toNode, "related_to");
        return edgeCount();
    }

    @Override
    public void removeEdge(int edgeId) {
        // EntityGraph handles edges in adjacency segments; direct removal by id is managed via decay/compaction.
    }

    @Override
    public java.util.PrimitiveIterator.OfInt neighbours(int nodeId) {
        return edges(nodeId).stream().mapToInt(EntityEdge::targetEntityId).iterator();
    }

    @Override
    public int nodeCount() {
        return entityCount;
    }

    public MemoryId memoryId() {
        return memoryId;
    }

    public MemoryShape kernelShape() {
        return MemoryShape.GRAPH;
    }

    @Override
    public void close() {
        log.info("EntityGraph closing (entities={}, edges={}, adjEntries={}, fileBacked={})",
                entityCount, edgeCount, adjHighWaterMark, fileBacked);
        if (fileBacked && headerSegment != null) {
            entitySegment.force();
            edgeSegment.force();
            adjacencySegment.force();
            headerSegment.force();
        }
        arena.close();
    }

    @Override
    public MemorySegment headerSegment() {
        return headerSegment;
    }

    /** Returns true if this graph is backed by mmap'd files. */
    boolean isFileBacked() { return fileBacked; }

    /** Returns the mmap file path (null for heap mode). */
    Path mmapFilePath() { return mmapFilePath; }
}
