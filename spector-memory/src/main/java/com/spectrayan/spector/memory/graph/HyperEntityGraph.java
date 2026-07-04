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
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Hyperedge-based entity layer for graph compression.
 *
 * <h3>Motivation</h3>
 * <p>Binary entity graphs decompose "Alice manages Project Alpha at Spectrayan"
 * into 3 binary edges (9 graph atoms). Hypergraphs collapse this into a single
 * hyperedge connecting 3 entities with roles (4 graph atoms — 55% reduction).</p>
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
 * <h3>Traversal</h3>
 * <p>"Find everything related to entity X" → find all hyperedges containing X,
 * collect co-occurring entities. Cost: O(hyperedges_per_entity × avg_vertices).</p>
 *
 * <h3>Eviction</h3>
 * <p>Per-entity hyperedge cap (MAX_HYPEREDGES_PER_ENTITY=64). When exceeded,
 * the weakest hyperedge (by weight) is evicted.</p>
 *
 * @see EntityGraph
 */
public final class HyperEntityGraph implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HyperEntityGraph.class);

    // ── File Format ──

    /** File magic: "HYEG" in ASCII. */
    private static final int FILE_MAGIC = 0x48594547;
    private static final int FILE_VERSION = 1;
    private static final int FILE_HEADER_BYTES = 32;

    // ── Hyperedge Layout ──

    /** Bytes per hyperedge node. */
    static final int HEDGE_BYTES = 32;
    private static final int HEDGE_OFF_EDGE_ID = 0;
    private static final int HEDGE_OFF_TYPE = 4;
    private static final int HEDGE_OFF_WEIGHT = 8;
    private static final int HEDGE_OFF_VERTEX_COUNT = 12;
    private static final int HEDGE_OFF_VERTEX_OFFSET = 16;
    private static final int HEDGE_OFF_MEMORY_IDX = 20;
    private static final int HEDGE_OFF_TIMESTAMP = 24;

    /** Bytes per vertex entry. */
    static final int VERTEX_BYTES = 8;
    private static final int VERTEX_OFF_ENTITY_ID = 0;
    private static final int VERTEX_OFF_ROLE_ID = 4;

    /** Max vertices per hyperedge (3-8 entities). */
    public static final int MAX_VERTICES_PER_EDGE = 8;

    /** Max hyperedges per entity (participation cap). */
    public static final int MAX_HYPEREDGES_PER_ENTITY = 64;

    /** Bytes per incidence list entry. */
    private static final int INCIDENCE_ENTRY_BYTES = 4;

    // ── Capacity ──

    private final int hyperedgeCapacity;
    private final int vertexCapacity;
    private final int entityCapacity;
    private final int incidenceCapacity;

    // ── Off-heap segments ──

    private final Arena arena;

    /** Hyperedge segment: HEDGE_BYTES × hyperedgeCapacity. */
    private final MemorySegment hedges;

    /** Vertex segment: VERTEX_BYTES × vertexCapacity. */
    private final MemorySegment vertices;

    /**
     * Incidence index: (entityCapacity + 1) × 4B offsets.
     * incidenceIndex[entity] = start offset into incidence list.
     */
    private final MemorySegment incidenceIndex;

    /**
     * Incidence list: INCIDENCE_ENTRY_BYTES × incidenceCapacity.
     * Packed lists of hyperedge IDs per entity.
     */
    private final MemorySegment incidenceList;

    // ── State ──

    private int nextHyperedgeId;
    private int nextVertexOffset;
    private int totalHyperedges;

    /**
     * On-heap incidence tracking (rebuilt during compaction).
     * incidence[entityId] = list of hyperedge IDs that entity participates in.
     */
    private List<List<Integer>> incidenceHeap;

    private final ReentrantLock graphLock = new ReentrantLock();

    // ══════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ══════════════════════════════════════════════════════════════

    /**
     * Creates a heap-allocated HyperEntityGraph.
     *
     * @param entityCapacity    max number of entities
     * @param hyperedgeCapacity max number of hyperedges
     */
    public HyperEntityGraph(int entityCapacity, int hyperedgeCapacity) {
        this.entityCapacity = entityCapacity;
        this.hyperedgeCapacity = hyperedgeCapacity;
        this.vertexCapacity = hyperedgeCapacity * MAX_VERTICES_PER_EDGE;
        this.incidenceCapacity = entityCapacity * MAX_HYPEREDGES_PER_ENTITY;
        this.nextHyperedgeId = 0;
        this.nextVertexOffset = 0;
        this.totalHyperedges = 0;

        this.arena = Arena.ofShared();

        this.hedges = arena.allocate((long) HEDGE_BYTES * hyperedgeCapacity);
        hedges.fill((byte) 0);

        this.vertices = arena.allocate((long) VERTEX_BYTES * vertexCapacity);
        vertices.fill((byte) 0);

        long indexBytes = (long) (entityCapacity + 1) * Integer.BYTES;
        this.incidenceIndex = arena.allocate(indexBytes);
        incidenceIndex.fill((byte) 0);

        this.incidenceList = arena.allocate((long) INCIDENCE_ENTRY_BYTES * incidenceCapacity);
        incidenceList.fill((byte) 0);

        // On-heap incidence lists for fast lookup
        this.incidenceHeap = new ArrayList<>(entityCapacity);
        for (int i = 0; i < entityCapacity; i++) {
            incidenceHeap.add(new ArrayList<>(4));
        }

        long totalKB = ((long) HEDGE_BYTES * hyperedgeCapacity
                + (long) VERTEX_BYTES * vertexCapacity
                + indexBytes
                + (long) INCIDENCE_ENTRY_BYTES * incidenceCapacity) / 1024;

        log.info("HyperEntityGraph initialized: entities={}, hyperedges={}, memory={}KB",
                entityCapacity, hyperedgeCapacity, totalKB);
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
                || vertexEntities.length > MAX_VERTICES_PER_EDGE) {
            log.warn("Invalid hyperedge: vertex count {} (must be 2-{})",
                    vertexEntities != null ? vertexEntities.length : 0, MAX_VERTICES_PER_EDGE);
            return -1;
        }
        if (vertexRoles == null || vertexRoles.length != vertexEntities.length) {
            log.warn("Vertex roles must match vertex count");
            return -1;
        }

        graphLock.lock();
        try {
            if (nextHyperedgeId >= hyperedgeCapacity) {
                log.warn("HyperEntityGraph full: {} hyperedges at capacity", hyperedgeCapacity);
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
                if (participation.size() >= MAX_HYPEREDGES_PER_ENTITY) {
                    // Evict weakest hyperedge for this entity
                    evictWeakestHyperedge(entityId);
                }
            }

            int edgeId = nextHyperedgeId++;
            totalHyperedges++;

            // Write hyperedge header
            long hedgeOff = (long) edgeId * HEDGE_BYTES;
            hedges.set(ValueLayout.JAVA_INT, hedgeOff + HEDGE_OFF_EDGE_ID, edgeId);
            hedges.set(ValueLayout.JAVA_INT, hedgeOff + HEDGE_OFF_TYPE, type);
            hedges.set(ValueLayout.JAVA_FLOAT, hedgeOff + HEDGE_OFF_WEIGHT, weight);
            hedges.set(ValueLayout.JAVA_INT, hedgeOff + HEDGE_OFF_VERTEX_COUNT, vertexCount);
            hedges.set(ValueLayout.JAVA_INT, hedgeOff + HEDGE_OFF_VERTEX_OFFSET, nextVertexOffset);
            hedges.set(ValueLayout.JAVA_INT, hedgeOff + HEDGE_OFF_MEMORY_IDX, memoryIdx);
            hedges.set(ValueLayout.JAVA_LONG, hedgeOff + HEDGE_OFF_TIMESTAMP, timestamp);

            // Write vertex entries
            for (int i = 0; i < vertexCount; i++) {
                long vOff = (long) (nextVertexOffset + i) * VERTEX_BYTES;
                vertices.set(ValueLayout.JAVA_INT, vOff + VERTEX_OFF_ENTITY_ID, vertexEntities[i]);
                vertices.set(ValueLayout.JAVA_INT, vOff + VERTEX_OFF_ROLE_ID, vertexRoles[i]);
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
            graphLock.unlock();
        }
    }

    /**
     * Gets a hyperedge by ID.
     *
     * @param edgeId hyperedge ID
     * @return the hyperedge record, or null if invalid/deleted
     */
    public HyperEdge getHyperedge(int edgeId) {
        if (edgeId < 0 || edgeId >= nextHyperedgeId) return null;

        long hedgeOff = (long) edgeId * HEDGE_BYTES;
        int type = hedges.get(ValueLayout.JAVA_INT, hedgeOff + HEDGE_OFF_TYPE);
        float weight = hedges.get(ValueLayout.JAVA_FLOAT, hedgeOff + HEDGE_OFF_WEIGHT);
        int vertexCount = hedges.get(ValueLayout.JAVA_INT, hedgeOff + HEDGE_OFF_VERTEX_COUNT);
        int vertexOffset = hedges.get(ValueLayout.JAVA_INT, hedgeOff + HEDGE_OFF_VERTEX_OFFSET);
        int memoryIdx = hedges.get(ValueLayout.JAVA_INT, hedgeOff + HEDGE_OFF_MEMORY_IDX);
        long timestamp = hedges.get(ValueLayout.JAVA_LONG, hedgeOff + HEDGE_OFF_TIMESTAMP);

        if (vertexCount == 0) return null; // Deleted

        List<HyperEdgeVertex> verts = new ArrayList<>(vertexCount);
        for (int i = 0; i < vertexCount; i++) {
            long vOff = (long) (vertexOffset + i) * VERTEX_BYTES;
            int entityId = vertices.get(ValueLayout.JAVA_INT, vOff + VERTEX_OFF_ENTITY_ID);
            int roleId = vertices.get(ValueLayout.JAVA_INT, vOff + VERTEX_OFF_ROLE_ID);
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
        if (entityId < 0 || entityId >= entityCapacity) return List.of();

        List<Integer> edgeIds = incidenceHeap.get(entityId);
        List<HyperEdge> result = new ArrayList<>(edgeIds.size());

        for (int edgeId : edgeIds) {
            HyperEdge edge = getHyperedge(edgeId);
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
        Set<Integer> result = new HashSet<>();

        List<HyperEdge> edges = findHyperedgesForEntity(entityId);
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
     * Strengthens a hyperedge's weight (LTP reinforcement).
     *
     * @param edgeId     hyperedge ID
     * @param weightDelta amount to add to the weight
     */
    public void strengthen(int edgeId, float weightDelta) {
        if (edgeId < 0 || edgeId >= nextHyperedgeId) return;

        graphLock.lock();
        try {
            long hedgeOff = (long) edgeId * HEDGE_BYTES;
            float weight = hedges.get(ValueLayout.JAVA_FLOAT, hedgeOff + HEDGE_OFF_WEIGHT);
            hedges.set(ValueLayout.JAVA_FLOAT, hedgeOff + HEDGE_OFF_WEIGHT, weight + weightDelta);
        } finally {
            graphLock.unlock();
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
        graphLock.lock();
        try {
            int evicted = 0;

            for (int i = 0; i < nextHyperedgeId; i++) {
                long hedgeOff = (long) i * HEDGE_BYTES;
                int vertexCount = hedges.get(ValueLayout.JAVA_INT, hedgeOff + HEDGE_OFF_VERTEX_COUNT);
                if (vertexCount == 0) continue; // already deleted

                float weight = hedges.get(ValueLayout.JAVA_FLOAT, hedgeOff + HEDGE_OFF_WEIGHT);
                float newWeight = weight * decayFactor;

                if (newWeight < minWeight) {
                    // Evict: zero out vertex count (tombstone)
                    deleteHyperedge(i);
                    evicted++;
                } else {
                    hedges.set(ValueLayout.JAVA_FLOAT, hedgeOff + HEDGE_OFF_WEIGHT, newWeight);
                }
            }

            if (evicted > 0) {
                log.debug("HyperEntityGraph decay: {} evicted (factor={}, min={}), {} remaining",
                        evicted, decayFactor, minWeight, totalHyperedges);
            }
            return evicted;
        } finally {
            graphLock.unlock();
        }
    }

    /**
     * Returns memory usage in bytes (off-heap only).
     */
    public long memoryUsageBytes() {
        return hedges.byteSize() + vertices.byteSize()
                + incidenceIndex.byteSize() + incidenceList.byteSize();
    }

    @Override
    public void close() {
        log.info("HyperEntityGraph closing: {} hyperedges", totalHyperedges);
        arena.close();
    }

    // ══════════════════════════════════════════════════════════════
    // PERSISTENCE
    // ══════════════════════════════════════════════════════════════

    /**
     * Saves the hypergraph to a binary file.
     */
    public void save(Path filePath) {
        Path parent = filePath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new SpectorGraphPersistenceException("HyperEntityGraph", parent, e);
            }
        }

        try (FileChannel ch = FileChannel.open(filePath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            ByteBuffer header = ByteBuffer.allocate(FILE_HEADER_BYTES);
            header.putInt(FILE_MAGIC);
            header.putInt(FILE_VERSION);
            header.putInt(entityCapacity);
            header.putInt(hyperedgeCapacity);
            header.putInt(nextHyperedgeId);
            header.putInt(nextVertexOffset);
            header.putInt(totalHyperedges);
            header.putInt(0); // reserved
            header.flip();
            ch.write(header);

            // Write hyperedge segment
            writeSegment(ch, hedges, (long) nextHyperedgeId * HEDGE_BYTES);
            // Write vertex segment
            writeSegment(ch, vertices, (long) nextVertexOffset * VERTEX_BYTES);

            ch.force(true);
            log.info("HyperEntityGraph saved: {} hyperedges, {} vertices, file={}",
                    totalHyperedges, nextVertexOffset, filePath);

        } catch (IOException e) {
            throw new SpectorGraphPersistenceException("HyperEntityGraph", filePath, e);
        }
    }

    /**
     * Loads a hypergraph from file, or creates a new one if not found.
     */
    public static HyperEntityGraph load(Path filePath, int entityCapacity, int hyperedgeCapacity) {
        if (filePath == null || !Files.exists(filePath)) {
            log.info("HyperEntityGraph file not found, creating fresh: {}", filePath);
            return new HyperEntityGraph(entityCapacity, hyperedgeCapacity);
        }

        try (FileChannel ch = FileChannel.open(filePath, StandardOpenOption.READ)) {
            ByteBuffer header = ByteBuffer.allocate(FILE_HEADER_BYTES);
            ch.read(header);
            header.flip();

            int magic = header.getInt();
            int version = header.getInt();
            if (magic != FILE_MAGIC || version != FILE_VERSION) {
                log.warn("Invalid HyperEntityGraph file: magic=0x{}, version={}", 
                         Integer.toHexString(magic), version);
                return new HyperEntityGraph(entityCapacity, hyperedgeCapacity);
            }

            int loadedEntityCap = header.getInt();
            int loadedHedgeCap = header.getInt();
            int loadedNextId = header.getInt();
            int loadedNextVertexOff = header.getInt();
            int loadedTotal = header.getInt();
            header.getInt(); // reserved

            // Create graph with loaded capacities
            HyperEntityGraph graph = new HyperEntityGraph(
                    Math.max(loadedEntityCap, entityCapacity),
                    Math.max(loadedHedgeCap, hyperedgeCapacity));

            // Read data
            readIntoSegment(ch, graph.hedges, (long) loadedNextId * HEDGE_BYTES);
            readIntoSegment(ch, graph.vertices, (long) loadedNextVertexOff * VERTEX_BYTES);

            graph.nextHyperedgeId = loadedNextId;
            graph.nextVertexOffset = loadedNextVertexOff;
            graph.totalHyperedges = loadedTotal;

            // Rebuild incidence lists from loaded data
            graph.rebuildIncidenceLists();

            log.info("HyperEntityGraph loaded: {} hyperedges, file={}", loadedTotal, filePath);
            return graph;

        } catch (Exception e) {
            log.error("Failed to load HyperEntityGraph from {}: {}", filePath, e.getMessage());
            return new HyperEntityGraph(entityCapacity, hyperedgeCapacity);
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
    /** Unspecified role. */
    public static final int ROLE_UNSPECIFIED = 0;

    // ══════════════════════════════════════════════════════════════
    // INTERNAL
    // ══════════════════════════════════════════════════════════════

    private void deleteHyperedge(int edgeId) {
        long hedgeOff = (long) edgeId * HEDGE_BYTES;
        int vertexCount = hedges.get(ValueLayout.JAVA_INT, hedgeOff + HEDGE_OFF_VERTEX_COUNT);
        if (vertexCount == 0) return; // already deleted

        // Remove from incidence lists
        int vertexOffset = hedges.get(ValueLayout.JAVA_INT, hedgeOff + HEDGE_OFF_VERTEX_OFFSET);
        for (int i = 0; i < vertexCount; i++) {
            long vOff = (long) (vertexOffset + i) * VERTEX_BYTES;
            int entityId = vertices.get(ValueLayout.JAVA_INT, vOff + VERTEX_OFF_ENTITY_ID);
            if (entityId >= 0 && entityId < entityCapacity) {
                incidenceHeap.get(entityId).remove(Integer.valueOf(edgeId));
            }
        }

        // Tombstone: zero vertex count
        hedges.set(ValueLayout.JAVA_INT, hedgeOff + HEDGE_OFF_VERTEX_COUNT, 0);
        totalHyperedges--;
    }

    private void evictWeakestHyperedge(int entityId) {
        List<Integer> participation = incidenceHeap.get(entityId);
        if (participation.isEmpty()) return;

        float minWeight = Float.MAX_VALUE;
        int minEdgeId = -1;

        for (int edgeId : participation) {
            long hedgeOff = (long) edgeId * HEDGE_BYTES;
            float weight = hedges.get(ValueLayout.JAVA_FLOAT, hedgeOff + HEDGE_OFF_WEIGHT);
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

    private void rebuildIncidenceLists() {
        // Clear and rebuild from hyperedge data
        for (List<Integer> list : incidenceHeap) {
            list.clear();
        }

        for (int i = 0; i < nextHyperedgeId; i++) {
            long hedgeOff = (long) i * HEDGE_BYTES;
            int vertexCount = hedges.get(ValueLayout.JAVA_INT, hedgeOff + HEDGE_OFF_VERTEX_COUNT);
            if (vertexCount == 0) continue;

            int vertexOffset = hedges.get(ValueLayout.JAVA_INT, hedgeOff + HEDGE_OFF_VERTEX_OFFSET);
            for (int j = 0; j < vertexCount; j++) {
                long vOff = (long) (vertexOffset + j) * VERTEX_BYTES;
                int entityId = vertices.get(ValueLayout.JAVA_INT, vOff + VERTEX_OFF_ENTITY_ID);
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
}
