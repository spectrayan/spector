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

    /** File magic: "EGPH" in ASCII. */
    private static final int FILE_MAGIC = 0x45475048;
    /** File format version. */
    private static final int FILE_VERSION = 1;
    private static final int FILE_HEADER_BYTES = 24; // magic + version + entityCap + edgeCap + entityCount + edgeCount

    /** Default adjacency slots allocated per entity on first link. */
    static final int DEFAULT_ADJ_PER_ENTITY = 8;

    /** LTP weight increment when an entity is re-mentioned in a memory. */
    private static final float LTP_REINFORCEMENT = 0.2f;

    /** Initial weight for a new entity→memory link. */
    private static final float INITIAL_LINK_WEIGHT = 1.0f;

    /** Maximum edges per entity. */
    public static final int MAX_DEGREE = 32;

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


    // ── Entity Edge Layout (12 bytes) ──
    static final int EDGE_BYTES = 12;
    private static final long EDGE_OFF_TARGET = 0;       // 4B
    private static final long EDGE_OFF_REL_TYPE = 4;     // 4B
    private static final long EDGE_OFF_WEIGHT = 8;       // 4B (float)

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

    /** Optional encryptor for name index persistence (set by enterprise layer). */
    private volatile DataEncryptor dataEncryptor;

    /** Open-schema entity type registry (String ↔ int). */
    private final TypeRegistry entityTypeRegistry;
    /** Open-schema relation type registry (String ↔ int). */
    private final TypeRegistry relationTypeRegistry;

    /**
     * Creates a new entity graph.
     *
     * @param entityCapacity maximum number of entities
     * @param edgeCapacity   maximum number of edges (default: entityCapacity × MAX_DEGREE)
     */
    public EntityGraph(int entityCapacity, int edgeCapacity) {
        this.entityCapacity = entityCapacity;
        this.edgeCapacity = edgeCapacity;
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
        this.entityTypeRegistry = TypeRegistry.seeded("entity-type", EntityType.SEED);
        this.relationTypeRegistry = TypeRegistry.seeded("relation-type", RelationType.SEED);

        log.info("EntityGraph initialized: entities={}, edges={}, adjSlots={}, memory={}KB",
                entityCapacity, edgeCapacity, adjSegmentCapacity,
                ((long) ENTITY_NODE_BYTES * entityCapacity
                        + (long) EDGE_BYTES * edgeCapacity
                        + (long) ADJ_ENTRY_BYTES * adjSegmentCapacity) / 1024);
    }

    /**
     * Creates a new entity graph with default edge capacity.
     *
     * @param entityCapacity maximum number of entities
     */
    public EntityGraph(int entityCapacity) {
        this(entityCapacity, entityCapacity * MAX_DEGREE);
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
        this.arena = arena;
        this.entitySegment = entitySegment;
        this.edgeSegment = edgeSegment;
        this.adjacencySegment = adjacencySegment;
        this.adjSegmentCapacity = adjSegmentCapacity;
        this.adjHighWaterMark = adjHighWaterMark;
        this.nameIndex.putAll(nameIndex);
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

        // Check if relation already exists (strengthen weight)
        if (edgeStart >= 0) {
            for (int i = 0; i < degree; i++) {
                long edgeOffset = (long) (edgeStart + i) * EDGE_BYTES;
                int target = edgeSegment.get(ValueLayout.JAVA_INT, edgeOffset + EDGE_OFF_TARGET);
                int relType = edgeSegment.get(ValueLayout.JAVA_INT, edgeOffset + EDGE_OFF_REL_TYPE);
                if (target == toEntity && relType == typeId) {
                    // Strengthen existing edge
                    float weight = edgeSegment.get(ValueLayout.JAVA_FLOAT, edgeOffset + EDGE_OFF_WEIGHT);
                    edgeSegment.set(ValueLayout.JAVA_FLOAT, edgeOffset + EDGE_OFF_WEIGHT, weight + 1.0f);
                    return;
                }
            }
        }

        // Add new edge
        if (degree >= MAX_DEGREE) {
            log.trace("Entity {} at max degree ({}), rejecting edge to {}", fromEntity, MAX_DEGREE, toEntity);
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

        entitySegment.set(ValueLayout.JAVA_INT, entityOffset + ENT_OFF_DEGREE, degree + 1);
        edgeCount = edgeIdx + 1;
        } finally {
            graphLock.unlock();
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
    // PERSISTENCE: save / load
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
     * <p>When a non-null, enabled {@link DataEncryptor} is provided, the name
     * index section is encrypted as a single AES-256-GCM blob. A 1-byte flag
     * precedes the name index data: {@code 0x00} = plaintext, {@code 0x01} = encrypted.
     * This ensures backward compatibility — files saved without encryption can
     * still be loaded by newer code that expects the flag.</p>
     *
     * @param filePath  path to write
     * @param encryptor optional encryptor for name index (null = no encryption)
     */
    public void save(Path filePath, DataEncryptor encryptor) {
        Path parent = filePath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new SpectorGraphPersistenceException("EntityGraph", parent, e);
            }
        }

        try (FileChannel ch = FileChannel.open(filePath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            // Header: magic + version + entityCap + edgeCap + entityCount + edgeCount
            ByteBuffer header = ByteBuffer.allocate(FILE_HEADER_BYTES);
            header.putInt(FILE_MAGIC);
            header.putInt(FILE_VERSION);
            header.putInt(entityCapacity);
            header.putInt(edgeCapacity);
            header.putInt(entityCount);
            header.putInt(edgeCount);
            header.flip();
            ch.write(header);

            // Write entity segment
            writeSegment(ch, entitySegment, (long) ENTITY_NODE_BYTES * entityCapacity);

            // Write edge segment
            writeSegment(ch, edgeSegment, (long) EDGE_BYTES * edgeCapacity);

            // Write adjacency segment header: [adjSegmentCapacity:4B][adjHighWaterMark:4B]
            ByteBuffer adjHeader = ByteBuffer.allocate(8);
            adjHeader.putInt(adjSegmentCapacity);
            adjHeader.putInt(adjHighWaterMark);
            adjHeader.flip();
            ch.write(adjHeader);

            // Write adjacency segment data (only up to high water mark)
            if (adjHighWaterMark > 0) {
                writeSegment(ch, adjacencySegment, (long) ADJ_ENTRY_BYTES * adjHighWaterMark);
            }

            // Write name index (on-heap → serialized, optionally encrypted)
            boolean encrypt = encryptor != null && encryptor.isEnabled();

            // Serialize name index to a byte array first
            java.io.ByteArrayOutputStream nameStream = new java.io.ByteArrayOutputStream();
            java.nio.ByteBuffer nameCountBuf = ByteBuffer.allocate(4);
            nameCountBuf.putInt(nameIndex.size());
            nameCountBuf.flip();
            nameStream.write(nameCountBuf.array());

            for (Map.Entry<String, Integer> entry : nameIndex.entrySet()) {
                byte[] nameBytes = entry.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                ByteBuffer entryBuf = ByteBuffer.allocate(4 + nameBytes.length + 4);
                entryBuf.putInt(nameBytes.length);
                entryBuf.put(nameBytes);
                entryBuf.putInt(entry.getValue());
                entryBuf.flip();
                nameStream.write(entryBuf.array());
            }

            byte[] nameIndexBytes = nameStream.toByteArray();

            // Write encryption flag: 0x00 = plaintext, 0x01 = encrypted
            ByteBuffer flagBuf = ByteBuffer.allocate(1);
            flagBuf.put(encrypt ? (byte) 0x01 : (byte) 0x00);
            flagBuf.flip();
            ch.write(flagBuf);

            if (encrypt) {
                byte[] encrypted = encryptor.encryptText(nameIndexBytes);
                // Write encrypted blob length + blob
                ByteBuffer blobLenBuf = ByteBuffer.allocate(4);
                blobLenBuf.putInt(encrypted.length);
                blobLenBuf.flip();
                ch.write(blobLenBuf);
                ch.write(ByteBuffer.wrap(encrypted));
                log.info("EntityGraph name index encrypted: {} names, {} plaintext bytes → {} encrypted bytes",
                        nameIndex.size(), nameIndexBytes.length, encrypted.length);
            } else {
                // Write plaintext name index directly
                ch.write(ByteBuffer.wrap(nameIndexBytes));
            }

            ch.force(true);
            log.info("EntityGraph saved: entities={}, edges={}, adjEntries={}, nameIndexEncrypted={} → {}",
                    entityCount, edgeCount, adjHighWaterMark, encrypt, filePath);

        } catch (IOException e) {
            throw new SpectorGraphPersistenceException("EntityGraph", filePath, e);
        }

        // Persist TypeRegistries alongside the graph file
        if (parent != null) {
            try {
                entityTypeRegistry.save(StorageLayout.entityTypes(parent));
                relationTypeRegistry.save(StorageLayout.relationTypes(parent));
            } catch (IOException e) {
                log.error("Failed to save TypeRegistries alongside EntityGraph: {}", e.getMessage());
            }
        }
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
        return load(filePath, defaultEntityCap, defaultEdgeCap, null);
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
        if (filePath == null || !Files.exists(filePath)) {
            log.info("EntityGraph file not found, creating fresh: {}", filePath);
            return new EntityGraph(defaultEntityCap, defaultEdgeCap);
        }

        try (FileChannel ch = FileChannel.open(filePath, StandardOpenOption.READ)) {
            long fileSize = ch.size();
            if (fileSize < FILE_HEADER_BYTES) {
                log.warn("EntityGraph file too small ({}B), creating fresh", fileSize);
                return new EntityGraph(defaultEntityCap, defaultEdgeCap);
            }

            ByteBuffer header = ByteBuffer.allocate(FILE_HEADER_BYTES);
            ch.read(header);
            header.flip();

            int magic = header.getInt();
            int version = header.getInt();
            int entityCap = header.getInt();
            int edgeCap = header.getInt();
            int entCount = header.getInt();
            int edgCount = header.getInt();

            if (magic != FILE_MAGIC || version != FILE_VERSION) {
                log.warn("Incompatible EntityGraph file (magic={}, version={}), creating fresh",
                        Integer.toHexString(magic), version);
                return new EntityGraph(defaultEntityCap, defaultEdgeCap);
            }

            // Validate file has enough data for the segments declared in the header
            long minExpectedBytes = FILE_HEADER_BYTES
                    + (long) ENTITY_NODE_BYTES * entityCap
                    + (long) EDGE_BYTES * edgeCap
                    + 8; // adjacency header (adjCap + adjHwm)
            if (fileSize < minExpectedBytes) {
                log.warn("EntityGraph file truncated ({}B < expected {}B), creating fresh",
                        fileSize, minExpectedBytes);
                return new EntityGraph(defaultEntityCap, defaultEdgeCap);
            }

            Arena arena = Arena.ofShared();

            // Read entity segment
            long entityBytes = (long) ENTITY_NODE_BYTES * entityCap;
            MemorySegment entSeg = arena.allocate(entityBytes);
            readSegment(ch, entSeg, entityBytes);

            // Read edge segment
            long edgeBytes = (long) EDGE_BYTES * edgeCap;
            MemorySegment edgSeg = arena.allocate(edgeBytes);
            readSegment(ch, edgSeg, edgeBytes);

            // Read adjacency header
            ByteBuffer adjHeaderBuf = ByteBuffer.allocate(8);
            ch.read(adjHeaderBuf);
            adjHeaderBuf.flip();
            int adjCap = adjHeaderBuf.getInt();
            int adjHwm = adjHeaderBuf.getInt();

            // Read adjacency segment
            MemorySegment adjSeg = arena.allocate((long) ADJ_ENTRY_BYTES * adjCap);
            adjSeg.fill((byte) 0);
            if (adjHwm > 0) {
                readSegment(ch, adjSeg, (long) ADJ_ENTRY_BYTES * adjHwm);
            }

            // Read name index (with encryption flag detection)
            ConcurrentHashMap<String, Integer> names = readNameIndex(ch, encryptor);

            // Load TypeRegistries — use persisted files if available, else seed from defaults
            Path graphParent = filePath.getParent();
            TypeRegistry entityTypes = TypeRegistry.load(
                    StorageLayout.entityTypes(graphParent),
                    "entity-type", EntityType.SEED);
            TypeRegistry relationTypes = TypeRegistry.load(
                    StorageLayout.relationTypes(graphParent),
                    "relation-type", RelationType.SEED);

            EntityGraph graph = new EntityGraph(entityCap, edgeCap, entCount, edgCount,
                    arena, entSeg, edgSeg, adjSeg, adjCap, adjHwm, names,
                    entityTypes, relationTypes);
            graph.dataEncryptor = encryptor;  // Preserve for subsequent saves
            log.info("EntityGraph loaded: entities={}, edges={}, adjEntries={}, encryptor={} from {}",
                    entCount, edgCount, adjHwm,
                    encryptor != null && encryptor.isEnabled() ? "enabled" : "none", filePath);
            return graph;

        } catch (Exception e) {
            log.error("Failed to load EntityGraph, creating fresh: {}", e.getMessage());
            return new EntityGraph(defaultEntityCap, defaultEdgeCap);
        }
    }

    /**
     * Reads the name index from a file channel.
     */
    /**
     * Reads the name index from a file channel, handling both encrypted and plaintext formats.
     *
     * <p>Detects the encryption flag byte: {@code 0x01} = encrypted (read blob, decrypt, parse),
     * {@code 0x00} = plaintext (parse inline). For backward compatibility with files saved before
     * the encryption flag was added, if the first byte looks like a valid name count (not 0x00 or 0x01),
     * it falls back to legacy parsing.</p>
     */
    private static ConcurrentHashMap<String, Integer> readNameIndex(
            FileChannel ch, DataEncryptor encryptor) throws IOException {
        // Read the encryption flag byte
        ByteBuffer flagBuf = ByteBuffer.allocate(1);
        ch.read(flagBuf);
        flagBuf.flip();
        byte flag = flagBuf.get();

        if (flag == 0x01) {
            // Encrypted name index — read blob length + blob, decrypt, parse
            ByteBuffer blobLenBuf = ByteBuffer.allocate(4);
            ch.read(blobLenBuf);
            blobLenBuf.flip();
            int blobLen = blobLenBuf.getInt();

            ByteBuffer blobBuf = ByteBuffer.allocate(blobLen);
            ch.read(blobBuf);
            blobBuf.flip();
            byte[] encrypted = new byte[blobLen];
            blobBuf.get(encrypted);

            if (encryptor == null || !encryptor.isEnabled()) {
                log.error("EntityGraph name index is encrypted but no encryptor available — names will be empty");
                return new ConcurrentHashMap<>();
            }

            byte[] decrypted = encryptor.decryptText(encrypted);
            return parseNameIndexBytes(decrypted);

        } else if (flag == 0x00) {
            // Plaintext name index with flag — parse from channel
            return readNameIndexFromChannel(ch);

        } else {
            // Legacy format (no flag byte) — the byte we read is actually part of the
            // name count int. Seek back 1 byte and read the full name count.
            ch.position(ch.position() - 1);
            return readNameIndexFromChannel(ch);
        }
    }

    /** Reads a plaintext name index from the current file channel position. */
    private static ConcurrentHashMap<String, Integer> readNameIndexFromChannel(
            FileChannel ch) throws IOException {
        ConcurrentHashMap<String, Integer> names = new ConcurrentHashMap<>();
        ByteBuffer countBuf = ByteBuffer.allocate(4);
        ch.read(countBuf);
        countBuf.flip();
        int nameCount = countBuf.getInt();

        for (int i = 0; i < nameCount; i++) {
            ByteBuffer lenBuf = ByteBuffer.allocate(4);
            ch.read(lenBuf);
            lenBuf.flip();
            int len = lenBuf.getInt();

            ByteBuffer nameBuf = ByteBuffer.allocate(len);
            ch.read(nameBuf);
            nameBuf.flip();
            String name = new String(nameBuf.array(), 0, len, java.nio.charset.StandardCharsets.UTF_8);

            ByteBuffer idBuf = ByteBuffer.allocate(4);
            ch.read(idBuf);
            idBuf.flip();
            int id = idBuf.getInt();

            names.put(name, id);
        }
        return names;
    }

    /** Parses a name index from a decrypted byte array. */
    private static ConcurrentHashMap<String, Integer> parseNameIndexBytes(byte[] data) {
        ConcurrentHashMap<String, Integer> names = new ConcurrentHashMap<>();
        ByteBuffer buf = ByteBuffer.wrap(data);
        int nameCount = buf.getInt();

        for (int i = 0; i < nameCount; i++) {
            int len = buf.getInt();
            byte[] nameBytes = new byte[len];
            buf.get(nameBytes);
            String name = new String(nameBytes, java.nio.charset.StandardCharsets.UTF_8);
            int id = buf.getInt();
            names.put(name, id);
        }
        return names;
    }

    private static void writeSegment(FileChannel ch, MemorySegment seg, long totalBytes)
            throws IOException {
        long written = 0;
        int chunkSize = 64 * 1024;
        while (written < totalBytes) {
            int toWrite = (int) Math.min(chunkSize, totalBytes - written);
            ByteBuffer buf = seg.asSlice(written, toWrite).asByteBuffer().asReadOnlyBuffer();
            ch.write(buf);
            written += toWrite;
        }
    }

    private static void readSegment(FileChannel ch, MemorySegment seg, long totalBytes)
            throws IOException {
        long read = 0;
        int chunkSize = 64 * 1024;
        while (read < totalBytes) {
            int toRead = (int) Math.min(chunkSize, totalBytes - read);
            ByteBuffer buf = ByteBuffer.allocate(toRead);
            ch.read(buf);
            buf.flip();
            MemorySegment.copy(MemorySegment.ofBuffer(buf), 0, seg, read, toRead);
            read += toRead;
        }
    }

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
        log.info("EntityGraph closing (entities={}, edges={}, adjEntries={})",
                entityCount, edgeCount, adjHighWaterMark);
        arena.close();
    }
}
