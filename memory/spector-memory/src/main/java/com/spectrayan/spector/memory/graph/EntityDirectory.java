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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.memory.DataEncryptor;
import com.spectrayan.spector.memory.error.SpectorEntityGraphException;
import com.spectrayan.spector.memory.error.SpectorGraphPersistenceException;
import com.spectrayan.spector.memory.kernel.MemoryHeader;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryShape;
import com.spectrayan.spector.memory.kernel.SystemMemoryId;
import com.spectrayan.spector.memory.kernel.layout.EntityDirectoryLayout;
import com.spectrayan.spector.memory.kernel.shape.AbstractGraphMemory;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;

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
import java.util.Locale;
import java.util.Map;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.spectrayan.spector.memory.kernel.layout.EntityDirectoryLayout.ADJ_ENTRY_BYTES;
import static com.spectrayan.spector.memory.kernel.layout.EntityDirectoryLayout.ADJ_OFF_MEM_IDX;
import static com.spectrayan.spector.memory.kernel.layout.EntityDirectoryLayout.ADJ_OFF_WEIGHT;
import static com.spectrayan.spector.memory.kernel.layout.EntityDirectoryLayout.DATA_START;
import static com.spectrayan.spector.memory.kernel.layout.EntityDirectoryLayout.ENTITY_NODE_BYTES;
import static com.spectrayan.spector.memory.kernel.layout.EntityDirectoryLayout.ENT_OFF_ADJ_CAPACITY;
import static com.spectrayan.spector.memory.kernel.layout.EntityDirectoryLayout.ENT_OFF_ADJ_COUNT;
import static com.spectrayan.spector.memory.kernel.layout.EntityDirectoryLayout.ENT_OFF_ADJ_OFFSET;
import static com.spectrayan.spector.memory.kernel.layout.EntityDirectoryLayout.ENT_OFF_NAME_HASH;
import static com.spectrayan.spector.memory.kernel.layout.EntityDirectoryLayout.ENT_OFF_TYPE;
import static com.spectrayan.spector.memory.kernel.layout.EntityDirectoryLayout.ENT_OFF_MERGED_INTO;
import static com.spectrayan.spector.memory.kernel.layout.EntityDirectoryLayout.SUB_OFF_ADJ_CAPACITY;
import static com.spectrayan.spector.memory.kernel.layout.EntityDirectoryLayout.SUB_OFF_ADJ_HWM;

/**
 * Kernel-substrate companion that owns entity <b>identity</b> and entity&rarr;memory adjacency,
 * introduced for the hypergraph graduation (ADR-0003, #455).
 *
 * <h3>Why this exists</h3>
 * <p>{@code HyperEntityGraphMemory} stores n-ary hyperedges but does <em>not</em> own entity
 * identity: the name&harr;id index, per-entity type, and the entity&rarr;memory adjacency
 * (crucially including <b>single-entity</b> memories, which never produce a hyperedge because
 * {@code addHyperedge} requires &ge;2 vertices) all lived only in the legacy
 * EntityGraphMemory. {@code EntityDirectory} absorbs exactly that identity surface —
 * it is {@code EntityGraphMemory} minus the binary edge / traversal machinery — so the binary
 * graph can eventually be retired without losing identity or single-entity adjacency.</p>
 *
 * <h3>Ownership split</h3>
 * <ul>
 *   <li><b>EntityDirectory</b> — name&harr;id, entity type, entity&rarr;memory adjacency, the dense
 *       entity-id space (allocated by {@link #intern}).</li>
 *   <li><b>HyperEntityGraphMemory</b> — n-ary topology (hyperedges) over that id space.</li>
 * </ul>
 *
 * <h3>Concurrency (#435 SWMR)</h3>
 * <p>Extends {@link AbstractGraphMemory} and uses the substrate {@link java.util.concurrent.locks.StampedLock}
 * in single-writer / multiple-reader mode. All mutators take the write lock; adjacency readers take a
 * <b>validated</b> read lock (never optimistic) because {@code compactAdjacency}/{@code ensureAdjSegmentCapacity}
 * reassign the {@link #adjacencySegment} field. The lock is non-reentrant, so public wrappers delegate to
 * unlocked {@code *Locked} cores.</p>
 *
 * <h3>Reuse</h3>
 * <p>The region-doubling entity&rarr;memory adjacency mechanics ({@code ADJ_OFF_*}, block-doubling,
 * {@code compactAdjacency}) are the well-tested parts of {@link EntityGraphMemory}, reproduced here over the
 * identity-only {@link EntityDirectoryLayout}. The name-index codec is shared via
 * {@link EntityDirectorySerializer}.</p>
 *
 * @see HyperEntityGraphMemory
 */
public final class EntityDirectory extends AbstractGraphMemory<EntityDirectoryLayout> {

    private static final Logger log = LoggerFactory.getLogger(EntityDirectory.class);

    /** Kernel identity for the entity directory. */
    private static final MemoryId MEMORY_ID = SystemMemoryId.ENTITY_DIRECTORY.id();
    /** Shared record layout — identifies directory records inside an SMKM container. */
    private static final EntityDirectoryLayout LAYOUT = new EntityDirectoryLayout();

    /** Name of the name-index sidecar written next to {@code entity-directory.edir}. */
    static final String NAME_INDEX_SIDECAR = "entity-directory-names.idx";

    /** Default adjacency slots allocated per entity on first link. */
    static final int DEFAULT_ADJ_PER_ENTITY = 8;
    /** Maximum adjacency entries per entity (for mmap pre-allocation). */
    static final int MAX_ADJ_PER_ENTITY = 64;

    /** LTP weight increment when an entity is re-mentioned in a memory. */
    private static final float LTP_REINFORCEMENT = 0.2f;
    /** Initial weight for a new entity→memory link. */
    private static final float INITIAL_LINK_WEIGHT = 1.0f;

    // ── Segments: the entity node slab is the kernel segment(); the adjacency slab is directory-owned. ──
    private final MemorySegment entitySegment;
    private MemorySegment adjacencySegment;
    private final int entityCapacity;
    private int entityCount;
    private int adjSegmentCapacity;  // total entries the adjacency segment can hold
    private int adjHighWaterMark;    // next free entry index in adjacency segment

    /** On-heap name→entityId index for O(1) lookup (case-insensitive). */
    private final ConcurrentHashMap<String, Integer> nameIndex = new ConcurrentHashMap<>();

    private final boolean fileBacked;
    private final MemorySegment headerSegment;
    private final Path mmapFilePath;

    /** Optional encryptor for name index persistence (set by enterprise layer). */
    private volatile DataEncryptor dataEncryptor;

    /** Open-schema entity type registry (String ↔ int) — shared with the companion graphs. */
    private final TypeRegistryMemory entityTypeRegistryMemory;

    private final MemoryId memoryId;

    // ══════════════════════════════════════════════════════════════
    // CONSTRUCTORS
    // ══════════════════════════════════════════════════════════════

    /** Creates a new heap-backed directory with the given entity type registry. */
    public EntityDirectory(int entityCapacity, TypeRegistryMemory entityTypeRegistry) {
        this(Init.heap(entityCapacity, entityTypeRegistry));
    }

    public EntityDirectory(Path filePath, int entityCapacity, TypeRegistryMemory entityTypeRegistry) {
        this(Init.mmap(filePath, entityCapacity, entityTypeRegistry));
    }

    private transient boolean bundleManaged = false;

    public static EntityDirectory fromBundle(Arena arena, MemorySegment entityRegionSlice, MemorySegment adjacencyRegionSlice,
                                             int entityCapacity, TypeRegistryMemory entityTypeRegistry,
                                             Path bundlePath, boolean isNew) {
        return new EntityDirectory(arena, entityRegionSlice, adjacencyRegionSlice, entityCapacity, entityTypeRegistry, bundlePath, isNew);
    }

    private EntityDirectory(Arena arena, MemorySegment entityRegionSlice, MemorySegment adjacencyRegionSlice,
                            int entityCapacity, TypeRegistryMemory entityTypeRegistry,
                            Path bundlePath, boolean isNew) {
        super(MEMORY_ID, LAYOUT, entityCapacity, arena, entityRegionSlice,
              isNew ? 0 : (int) MemoryHeader.readCount(entityRegionSlice, 0L),
              true, bundlePath, null, true); // bundleManaged=true
        this.bundleManaged = true;
        this.entitySegment = entityRegionSlice;
        this.adjacencySegment = adjacencyRegionSlice;
        this.entityCapacity = entityCapacity;
        this.entityCount = isNew ? 0 : (int) MemoryHeader.readCount(entityRegionSlice, 0L);

        long headerStart = MemoryHeader.HEADER_BYTES;
        int initialAdjCap = adjacencyRegionSlice.get(ValueLayout.JAVA_INT, headerStart + SUB_OFF_ADJ_CAPACITY);
        int adjHwm = adjacencyRegionSlice.get(ValueLayout.JAVA_INT, headerStart + SUB_OFF_ADJ_HWM);

        this.adjSegmentCapacity = initialAdjCap;
        this.adjHighWaterMark = adjHwm;
        this.fileBacked = true;
        this.headerSegment = entityRegionSlice.asSlice(0, MemoryHeader.HEADER_BYTES + 16);
        this.mmapFilePath = bundlePath;
        this.memoryId = MEMORY_ID;
        this.entityTypeRegistryMemory = entityTypeRegistry;

        if (isNew) {
            long now = System.currentTimeMillis();
            MemoryHeader.write(entityRegionSlice, 0L, LAYOUT.schemaVersion(), MemoryShape.GRAPH, 0,
                    (int) entityRegionSlice.byteSize(), 0, 0, LAYOUT.layoutId(), now, now);
            MemoryHeader.write(adjacencyRegionSlice, 0L, LAYOUT.schemaVersion(), MemoryShape.GRAPH, 0,
                    (int) adjacencyRegionSlice.byteSize(), 0, 0, LAYOUT.layoutId(), now, now);

            int adjCap = (int) ((adjacencyRegionSlice.byteSize() - MemoryHeader.HEADER_BYTES - 16) / ADJ_ENTRY_BYTES);
            adjacencyRegionSlice.set(ValueLayout.JAVA_INT, headerStart + SUB_OFF_ADJ_CAPACITY, adjCap);
            adjacencyRegionSlice.set(ValueLayout.JAVA_INT, headerStart + SUB_OFF_ADJ_HWM, 0);
            this.adjSegmentCapacity = adjCap;
            this.adjHighWaterMark = 0;
        }

        // Load names from sidecar
        if (!isNew && bundlePath != null) {
            try {
                ConcurrentHashMap<String, Integer> names = EntityDirectorySerializer.loadNameIndexSidecar(bundlePath, null);
                if (names != null) {
                    this.nameIndex.putAll(names);
                }
            } catch (Exception e) {
                log.warn("Failed to load EntityDirectory name index sidecar: {}", e.getMessage());
            }
        }

        // Migrate legacy standalone EntityDirectory if it exists
        if (isNew && bundlePath != null) {
            Path legacyPath = bundlePath.resolveSibling("entity_directory.dat");
            if (Files.exists(legacyPath)) {
                log.info("Migrating legacy standalone entity_directory.dat to bundle region...");
                try {
                    EntityDirectory legacy = EntityDirectory.load(legacyPath, entityCapacity, entityTypeRegistry, null);
                    MemorySegment.copy(legacy.entitySegment, 0, this.entitySegment, 0, legacy.entitySegment.byteSize());
                    MemorySegment.copy(legacy.adjacencySegment, 0, this.adjacencySegment, 0, legacy.adjacencySegment.byteSize());

                    this.entityCount = legacy.entityCount;
                    this.adjSegmentCapacity = legacy.adjSegmentCapacity;
                    this.adjHighWaterMark = legacy.adjHighWaterMark;
                    this.nameIndex.putAll(legacy.nameIndex);

                    save(legacyPath);
                    legacy.close();
                    Files.deleteIfExists(legacyPath);
                } catch (Exception e) {
                    log.warn("Failed to migrate legacy entity_directory.dat: {}", e.getMessage());
                }
            }
        }

        log.info("EntityDirectory initialized (bundle): entities={}/{}, adjCap={}",
                entityCount, entityCapacity, adjSegmentCapacity);
    }

    /**
     * Single delegating constructor. Wraps the pre-built arena + entity node slab as the kernel
     * substrate {@link #segment()} and adopts the directory-owned region-doubling adjacency slab.
     */
    private EntityDirectory(Init init) {
        super(MEMORY_ID, LAYOUT, init.entityCapacity, init.arena, init.entitySegment, init.entityCount,
                init.persistent, init.filePath, null);
        this.entitySegment = init.entitySegment;
        this.adjacencySegment = init.adjacencySegment;
        this.entityCapacity = init.entityCapacity;
        this.entityCount = init.entityCount;
        this.adjSegmentCapacity = init.adjSegmentCapacity;
        this.adjHighWaterMark = init.adjHighWaterMark;
        this.fileBacked = init.persistent;
        this.headerSegment = init.headerSegment;
        this.mmapFilePath = init.filePath;
        this.memoryId = MEMORY_ID;
        this.entityTypeRegistryMemory = init.entityTypeRegistry;
        if (init.nameIndex != null && !init.nameIndex.isEmpty()) {
            this.nameIndex.putAll(init.nameIndex);
        }
        log.info("EntityDirectory initialized ({}): entities={}/{}, adjCap={}, file={}",
                init.persistent ? "mmap" : "heap", entityCount, entityCapacity, adjSegmentCapacity,
                mmapFilePath != null ? mmapFilePath.getFileName() : "<heap>");
    }

    /** Immutable bundle of everything the delegating constructor needs. */
    private record Init(int entityCapacity, int entityCount, Arena arena,
                        MemorySegment entitySegment, MemorySegment adjacencySegment,
                        int adjSegmentCapacity, int adjHighWaterMark,
                        boolean persistent, Path filePath, MemorySegment headerSegment,
                        TypeRegistryMemory entityTypeRegistry,
                        ConcurrentHashMap<String, Integer> nameIndex) {

        static Init heap(int entityCapacity, TypeRegistryMemory entityTypeRegistry) {
            Arena arena = Arena.ofShared();
            MemorySegment entitySegment = arena.allocate((long) ENTITY_NODE_BYTES * entityCapacity);
            int adjCap = entityCapacity * DEFAULT_ADJ_PER_ENTITY;
            MemorySegment adjacencySegment = arena.allocate((long) ADJ_ENTRY_BYTES * adjCap);
            entitySegment.fill((byte) 0);
            adjacencySegment.fill((byte) 0);
            return new Init(entityCapacity, 0, arena, entitySegment, adjacencySegment, adjCap, 0,
                    false, null, null, entityTypeRegistry, null);
        }

        static Init mmap(Path filePath, int defaultEntityCap, TypeRegistryMemory entityTypeRegistry) {
            Path parent = filePath.getParent();
            try {
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                boolean exists = Files.exists(filePath) && Files.size(filePath) >= 4;
                int entityCap;
                int entityCount;
                int adjCap;
                int adjHwm;
                FileChannel ch = FileChannel.open(filePath,
                        StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);

                if (exists && ch.size() < DATA_START) {
                    throw new IOException("SMKM entity-directory file truncated: size=" + ch.size()
                            + " < " + DATA_START + ": " + filePath);
                }

                if (!exists) {
                    entityCap = defaultEntityCap;
                    entityCount = 0;
                    adjCap = defaultEntityCap * MAX_ADJ_PER_ENTITY;
                    adjHwm = 0;
                    writeSmkmHeaderToChannel(ch, entityCap, entityCount, adjCap, adjHwm);
                    long total = DATA_START + (long) ENTITY_NODE_BYTES * entityCap
                            + (long) ADJ_ENTRY_BYTES * adjCap;
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
                            throw new IOException("invalid SMKM entity-directory header: " + filePath);
                        }
                        entityCap = (int) MemoryHeader.readCapacity(head, 0L);
                        entityCount = (int) MemoryHeader.readCount(head, 0L);
                        adjCap = head.get(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_ADJ_CAPACITY);
                        adjHwm = head.get(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_ADJ_HWM);
                    }
                }

                long entityBytes = (long) ENTITY_NODE_BYTES * entityCap;
                long adjBytes = (long) ADJ_ENTRY_BYTES * adjCap;
                Arena arena = Arena.ofShared();
                long offset = DATA_START;
                MemorySegment entitySegment = ch.map(FileChannel.MapMode.READ_WRITE, offset, entityBytes, arena);
                offset += entityBytes;
                MemorySegment adjacencySegment = ch.map(FileChannel.MapMode.READ_WRITE, offset, adjBytes, arena);
                MemorySegment headerSegment = ch.map(FileChannel.MapMode.READ_WRITE, 0, DATA_START, arena);
                ch.close();

                return new Init(entityCap, entityCount, arena, entitySegment, adjacencySegment, adjCap, adjHwm,
                        true, filePath, headerSegment, entityTypeRegistry, null);
            } catch (IOException e) {
                throw new SpectorGraphPersistenceException("EntityDirectory", filePath, e);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // IDENTITY MUTATORS
    // ══════════════════════════════════════════════════════════════

    /**
     * Interns an entity, allocating a dense id on first sight or returning the existing id.
     *
     * <p>Entity names are case-insensitive and normalized to lowercase. This is the id-provider
     * entry point consumed by {@code HyperEntityGraphMemory} for hyperedge vertices.</p>
     *
     * @param name entity name
     * @param type entity type
     * @return entity id (index into the node slab), or -1 if rejected
     */
    public int intern(String name, String type) {
        if (name == null || name.isBlank()) return -1;
        if (type == null || type.isBlank()) type = "OTHER";

        String normalized = name.trim().toLowerCase(Locale.ROOT);
        Integer existing = nameIndex.get(normalized);
        if (existing != null) return existing;

        long stamp = lock.writeLock();
        try {
            // Re-check under the lock (another writer may have interned the same name).
            existing = nameIndex.get(normalized);
            if (existing != null) return existing;
            if (entityCount >= entityCapacity) {
                log.warn("EntityDirectory full ({} entities), rejecting '{}'", entityCapacity, name);
                return -1;
            }
            int entityId = entityCount;
            // Write-ahead: log the mutation before applying it (matches EntityGraphMemory).
            if (wal != null && !bypassWal) {
                wal.appendGraphAddNode(memoryId.toString(), entityId, normalized, type);
            }
            writeEntityNode(entityId, normalized, type);
            entityCount++;
            persistCount();
            nameIndex.put(normalized, entityId);
            log.trace("Directory entity interned: id={}, name='{}', type={}", entityId, name, type);
            return entityId;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /** Writes the identity fields of an entity node at {@code entityId} (caller holds the write lock). */
    private void writeEntityNode(int entityId, String normalizedName, String type) {
        long offset = (long) entityId * ENTITY_NODE_BYTES;
        int typeId = entityTypeRegistryMemory.getOrRegister(type);
        entitySegment.set(ValueLayout.JAVA_INT, offset + ENT_OFF_TYPE, typeId);
        entitySegment.set(ValueLayout.JAVA_LONG, offset + ENT_OFF_NAME_HASH, normalizedName.hashCode());
        entitySegment.set(ValueLayout.JAVA_INT, offset + ENT_OFF_ADJ_OFFSET, -1); // no adj block yet
        entitySegment.set(ValueLayout.JAVA_INT, offset + ENT_OFF_ADJ_COUNT, 0);
        entitySegment.set(ValueLayout.JAVA_INT, offset + ENT_OFF_ADJ_CAPACITY, 0);
        entitySegment.set(ValueLayout.JAVA_INT, offset + ENT_OFF_MERGED_INTO, -1);
    }

    /**
     * Links an entity to a memory index (unlimited associations, region-doubling growth).
     *
     * <p>If the entity is already linked to this memory, the link weight is reinforced by
     * {@value #LTP_REINFORCEMENT} (LTP). Otherwise a new adjacency entry is created with weight
     * {@value #INITIAL_LINK_WEIGHT}. This is what preserves single-entity adjacency.</p>
     *
     * @param entityId  entity id
     * @param memoryIdx index of the memory that mentions this entity
     */
    public void linkEntityToMemory(int entityId, int memoryIdx) {
        long stamp = lock.writeLock();
        try {
            if (wal != null && !bypassWal) {
                wal.appendGraphLinkMemory(memoryId.toString(), entityId, memoryIdx);
            }
            linkEntityToMemoryLocked(entityId, memoryIdx, INITIAL_LINK_WEIGHT, true);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * Core of {@link #linkEntityToMemory}; the caller must hold the write lock.
     *
     * @param reinforceOnDuplicate when {@code true}, a re-mention reinforces the existing weight (LTP);
     *                             when {@code false}, a duplicate is ignored (used by derive/copy paths)
     */
    private void linkEntityToMemoryLocked(int entityId, int memoryIdx, float initialWeight,
                                          boolean reinforceOnDuplicate) {
        if (entityId < 0 || entityId >= entityCount) return;
        long entOffset = (long) entityId * ENTITY_NODE_BYTES;
        int adjOff = entitySegment.get(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_OFFSET);
        int adjCnt = entitySegment.get(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_COUNT);
        int adjCap = entitySegment.get(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_CAPACITY);

        if (adjOff >= 0) {
            for (int i = 0; i < adjCnt; i++) {
                long adjEntryOff = (long) (adjOff + i) * ADJ_ENTRY_BYTES;
                int existingIdx = adjacencySegment.get(ValueLayout.JAVA_INT, adjEntryOff + ADJ_OFF_MEM_IDX);
                if (existingIdx == memoryIdx) {
                    if (reinforceOnDuplicate) {
                        float w = adjacencySegment.get(ValueLayout.JAVA_FLOAT, adjEntryOff + ADJ_OFF_WEIGHT);
                        adjacencySegment.set(ValueLayout.JAVA_FLOAT, adjEntryOff + ADJ_OFF_WEIGHT,
                                w + LTP_REINFORCEMENT);
                    }
                    return;
                }
            }
        }

        if (adjCap == 0) {
            adjOff = adjHighWaterMark;
            adjCap = DEFAULT_ADJ_PER_ENTITY;
            ensureAdjSegmentCapacity(adjHighWaterMark + adjCap);
            entitySegment.set(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_OFFSET, adjOff);
            entitySegment.set(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_CAPACITY, adjCap);
            adjHighWaterMark += adjCap;
        } else if (adjCnt >= adjCap) {
            int newCap = adjCap * 2;
            int newOff = adjHighWaterMark;
            ensureAdjSegmentCapacity(adjHighWaterMark + newCap);
            MemorySegment.copy(adjacencySegment, (long) adjOff * ADJ_ENTRY_BYTES,
                    adjacencySegment, (long) newOff * ADJ_ENTRY_BYTES,
                    (long) adjCnt * ADJ_ENTRY_BYTES);
            adjOff = newOff;
            adjCap = newCap;
            entitySegment.set(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_OFFSET, adjOff);
            entitySegment.set(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_CAPACITY, adjCap);
            adjHighWaterMark += newCap;
        }

        long entryOff = (long) (adjOff + adjCnt) * ADJ_ENTRY_BYTES;
        adjacencySegment.set(ValueLayout.JAVA_INT, entryOff + ADJ_OFF_MEM_IDX, memoryIdx);
        adjacencySegment.set(ValueLayout.JAVA_FLOAT, entryOff + ADJ_OFF_WEIGHT, initialWeight);
        entitySegment.set(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_COUNT, adjCnt + 1);
    }

    /**
     * Ensures the adjacency segment can hold at least {@code requiredEntries} entries, doubling
     * (heap) or throwing (mmap, pre-allocated at max). Must be called under the write lock — it
     * reassigns {@link #adjacencySegment}.
     */
    private void ensureAdjSegmentCapacity(int requiredEntries) {
        if (requiredEntries <= adjSegmentCapacity) return;
        if (fileBacked) {
            throw new SpectorEntityGraphException(
                    ErrorCode.CAPACITY_EXCEEDED,
                    "adjacency segment exhausted (mmap); increase MAX_ADJ_PER_ENTITY (currently "
                            + MAX_ADJ_PER_ENTITY + ")",
                    adjSegmentCapacity, requiredEntries);
        }
        int newCapacity = Math.max(adjSegmentCapacity * 2, requiredEntries);
        MemorySegment newSeg = arena.allocate((long) ADJ_ENTRY_BYTES * newCapacity);
        newSeg.fill((byte) 0);
        MemorySegment.copy(adjacencySegment, 0, newSeg, 0, (long) ADJ_ENTRY_BYTES * adjHighWaterMark);
        adjacencySegment = newSeg;
        int oldCap = adjSegmentCapacity;
        adjSegmentCapacity = newCapacity;
        log.info("EntityDirectory adjacency segment grown: {} → {} entries", oldCap, newCapacity);
    }

    // ══════════════════════════════════════════════════════════════
    // IDENTITY READS
    // ══════════════════════════════════════════════════════════════

    /**
     * Finds an entity by name (case-insensitive).
     *
     * @param name entity name
     * @return entity id, or -1 if not found
     */
    public int findEntity(String name) {
        if (name == null || name.isBlank()) return -1;
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        Integer id = nameIndex.get(normalized);
        return id != null ? id : -1;
    }

    /** Returns the memory indices that reference an entity. */
    public int[] memoriesForEntity(int entityId) {
        // Validated read lock (NOT optimistic): compactAdjacency()/ensureAdjSegmentCapacity()
        // reassign the adjacencySegment field under the write lock (#435 hazard).
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

    /** Returns the number of memory references for an entity (zero-alloc). */
    public int memoryRefCount(int entityId) {
        if (entityId < 0 || entityId >= entityCount) return 0;
        return entitySegment.get(ValueLayout.JAVA_INT,
                (long) entityId * ENTITY_NODE_BYTES + ENT_OFF_ADJ_COUNT);
    }

    /** Returns the memory index at a specific reference position, or -1 if out of bounds. */
    public int memoryRefAt(int entityId, int refIndex) {
        if (entityId < 0 || entityId >= entityCount) return -1;
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

    /** Returns the weight of a specific entity→memory reference, or 0 if out of bounds. */
    public float memoryRefWeight(int entityId, int refIndex) {
        if (entityId < 0 || entityId >= entityCount) return 0f;
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
     * Returns the ACT-R fan-effect attenuation factor {@code 1/sqrt(refCount)} for an entity.
     * Reproduces {@link EntityGraphMemory#fanFactor(int)} exactly (degree-derived) so expansion
     * scoring is unchanged.
     */
    public float fanFactor(int entityId) {
        int refCnt = memoryRefCount(entityId);
        if (refCnt <= 1) return 1.0f;
        return 1.0f / (float) Math.sqrt(refCnt);
    }

    /** Returns the entity type name for an entity id. */
    public String entityType(int entityId) {
        if (entityId < 0 || entityId >= entityCount) return "OTHER";
        int typeId = entitySegment.get(ValueLayout.JAVA_INT,
                (long) entityId * ENTITY_NODE_BYTES + ENT_OFF_TYPE);
        return entityTypeRegistryMemory.nameOf(typeId);
    }

    /** Returns the number of entities in the directory. */
    public int entityCount() {
        return entityCount;
    }

    /** Returns a copy of the name index for inspection/debugging. */
    public Map<String, Integer> nameIndex() {
        return Map.copyOf(nameIndex);
    }

    /** Package-private mutable view for the serializer sidecar hydrate path. */
    ConcurrentHashMap<String, Integer> nameIndexInternal() {
        return nameIndex;
    }

    /** Returns the shared entity type registry. */
    public TypeRegistryMemory entityTypeRegistry() {
        return entityTypeRegistryMemory;
    }

    /** Returns the adjacency segment high water mark (for diagnostics). */
    public int adjHighWaterMark() {
        return adjHighWaterMark;
    }

    /** Sets the data encryptor for name index encryption. */
    public void setDataEncryptor(DataEncryptor encryptor) {
        this.dataEncryptor = encryptor;
    }

    /** Returns the current data encryptor (for diagnostics). */
    public DataEncryptor dataEncryptor() {
        return dataEncryptor;
    }

    // ══════════════════════════════════════════════════════════════
    // REFLECTION-CYCLE OPERATIONS (identity-level dedup + LTD)
    // ══════════════════════════════════════════════════════════════

    /**
     * Decays all entity→memory adjacency weights and prunes weak links (LTD). Mirrors
     * the legacy EntityGraphMemory#decayAdjacencyWeights(float, float).
     *
     * @param decayFactor    multiplicative factor per cycle
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
                log.info("EntityDirectory LTD: pruned {} weak links below {}", totalPruned, pruneThreshold);
            }
            return totalPruned;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * Compacts the adjacency segment by defragmenting per-entity blocks. Mirrors
     * the legacy EntityGraphMemory#compactAdjacency().
     *
     * @return bytes reclaimed by compaction
     */
    public long compactAdjacency() {
        long stamp = lock.writeLock();
        try {
            long oldUsed = (long) adjHighWaterMark * ADJ_ENTRY_BYTES;
            int liveEntries = 0;
            for (int e = 0; e < entityCount; e++) {
                liveEntries += entitySegment.get(ValueLayout.JAVA_INT,
                        (long) e * ENTITY_NODE_BYTES + ENT_OFF_ADJ_COUNT);
            }
            if (liveEntries == 0) {
                adjHighWaterMark = 0;
                return oldUsed;
            }
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
                    entitySegment.set(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_OFFSET, -1);
                    entitySegment.set(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_CAPACITY, 0);
                    continue;
                }
                MemorySegment.copy(adjacencySegment, (long) adjOff * ADJ_ENTRY_BYTES,
                        newSeg, (long) writePos * ADJ_ENTRY_BYTES,
                        (long) adjCnt * ADJ_ENTRY_BYTES);
                entitySegment.set(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_OFFSET, writePos);
                int newEntityCap = Math.max(adjCnt, DEFAULT_ADJ_PER_ENTITY);
                entitySegment.set(ValueLayout.JAVA_INT, entOffset + ENT_OFF_ADJ_CAPACITY, newEntityCap);
                writePos += newEntityCap;
            }

            if (fileBacked) {
                MemorySegment.copy(newSeg, 0, adjacencySegment, 0, (long) writePos * ADJ_ENTRY_BYTES);
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
            long reclaimed = oldUsed - (long) writePos * ADJ_ENTRY_BYTES;
            if (reclaimed > 0) {
                log.info("EntityDirectory adjacency compacted: {} live entries, reclaimed {}KB",
                        liveEntries, reclaimed / 1024);
            }
            return Math.max(reclaimed, 0);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * Merges entities with similar names using Levenshtein distance (identity-level dedup). Moved
     * verbatim from the legacy EntityGraphMemory#mergeSimilarEntities(int) (the directory now owns
     * identity). Only entity&rarr;memory adjacency is redirected — the directory has no binary edges.
     *
     * @param maxEditDistance maximum Levenshtein distance for merge
     * @return number of entities merged
     */
    public int mergeSimilarEntities(int maxEditDistance, TypeNormalizer typeNormalizer) {
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
                    String typeA = entityType(idA);
                    String typeB = entityType(idB);
                    if (typeNormalizer != null) {
                        if (!typeNormalizer.areMergeCompatible(typeA, typeB)) continue;
                    } else {
                        if (!typeA.equals(typeB)) continue;
                    }
                    int dist = levenshteinDistance(nameA, nameB);
                    if (dist > 0 && dist <= maxEditDistance) {
                        int canonical = nameA.length() <= nameB.length() ? idA : idB;
                        int duplicate = canonical == idA ? idB : idA;
                        int[] dupRefs = memoriesForEntityLocked(duplicate);
                        for (int memIdx : dupRefs) {
                            linkEntityToMemoryLocked(canonical, memIdx, INITIAL_LINK_WEIGHT, true);
                        }
                        long dupOffset = (long) duplicate * ENTITY_NODE_BYTES;
                        entitySegment.set(ValueLayout.JAVA_INT, dupOffset + ENT_OFF_ADJ_COUNT, 0);
                        merged.add(duplicate);
                        mergeCount++;
                    }
                }
            }
            if (mergeCount > 0) {
                log.info("EntityDirectory merged {} similar entities", mergeCount);
            }
            return mergeCount;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * Set the mergedInto pointer on a duplicate entity and re-link its memories.
     */
    public void mergeEntity(int duplicateId, int canonicalId) {
        long stamp = lock.writeLock();
        try {
            if (duplicateId < 0 || duplicateId >= entityCount || canonicalId < 0 || canonicalId >= entityCount) return;
            if (duplicateId == canonicalId) return;

            long dupOffset = (long) duplicateId * ENTITY_NODE_BYTES;
            entitySegment.set(ValueLayout.JAVA_INT, dupOffset + ENT_OFF_MERGED_INTO, canonicalId);

            int[] dupRefs = memoriesForEntityLocked(duplicateId);
            for (int memIdx : dupRefs) {
                linkEntityToMemoryLocked(canonicalId, memIdx, INITIAL_LINK_WEIGHT, true);
            }
            entitySegment.set(ValueLayout.JAVA_INT, dupOffset + ENT_OFF_ADJ_COUNT, 0);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * Clear the mergedInto pointer.
     */
    public void unmergeEntity(int entityId) {
        long stamp = lock.writeLock();
        try {
            if (entityId < 0 || entityId >= entityCount) return;
            long offset = (long) entityId * ENTITY_NODE_BYTES;
            entitySegment.set(ValueLayout.JAVA_INT, offset + ENT_OFF_MERGED_INTO, -1);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * Resolves the canonical entity ID by following mergedInto pointers.
     */
    public int resolveEntity(int entityId) {
        if (entityId < 0 || entityId >= entityCount) return -1;
        long stamp = lock.readLock();
        try {
            int current = entityId;
            int iterations = 0;
            while (iterations++ < 10) { // Limit to prevent cycles
                long offset = (long) current * ENTITY_NODE_BYTES;
                int mergedInto = entitySegment.get(ValueLayout.JAVA_INT, offset + ENT_OFF_MERGED_INTO);
                if (mergedInto == -1) {
                    return current;
                }
                current = mergedInto;
            }
            return current;
        } finally {
            lock.unlockRead(stamp);
        }
    }

    /**
     * Merges entities using embeddings and LLM adjudication.
     */
    public int mergeSimilarEntities(EmbeddingProvider embedder, LlmProvider adjudicator, float cosineThreshold, boolean shadowMode, TypeNormalizer typeNormalizer) {
        if (embedder == null || adjudicator == null || entityCount < 2) return 0;
        
        long stamp = lock.writeLock();
        try {
            LlmEntityAdjudicator llmAdjudicator = new LlmEntityAdjudicator(adjudicator);
            Set<Integer> merged = new HashSet<>();
            int mergeCount = 0;
            List<Map.Entry<String, Integer>> entries = new ArrayList<>(nameIndex.entrySet());
            
            // Collect embeddings for all names
            List<String> namesToEmbed = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : entries) {
                namesToEmbed.add(entry.getKey());
            }
            
            float[][] embeddings = null;
            try {
                java.util.List<com.spectrayan.spector.provider.embedding.EmbeddingResult> results = embedder.embedBatch(namesToEmbed);
                embeddings = new float[results.size()][];
                for (int i = 0; i < results.size(); i++) {
                    embeddings[i] = results.get(i).vector();
                }
            } catch (Exception e) {
                log.warn("Failed to embed entity names for resolution", e);
                return 0;
            }
            
            for (int i = 0; i < entries.size(); i++) {
                int idA = entries.get(i).getValue();
                if (merged.contains(idA)) continue;
                String nameA = entries.get(i).getKey();
                String typeA = entityType(idA);
                
                for (int j = i + 1; j < entries.size(); j++) {
                    int idB = entries.get(j).getValue();
                    if (merged.contains(idB)) continue;
                    String typeB = entityType(idB);
                    if (!typeA.equals(typeB)) continue;
                    
                    float sim = cosineSimilarity(embeddings[i], embeddings[j]);
                    if (sim >= cosineThreshold) {
                        String nameB = entries.get(j).getKey();
                        
                        if (shadowMode) {
                            log.info("[EntityResolution:Shadow] Proposed merge: '{}' and '{}' (sim={})", nameA, nameB, sim);
                            continue;
                        }
                        
                        var result = llmAdjudicator.adjudicate(nameA, typeA, nameB, typeB, List.of());
                        if (result.shouldMerge()) {
                            int canonical = nameA.length() <= nameB.length() ? idA : idB;
                            int duplicate = canonical == idA ? idB : idA;
                            
                            // Re-link manually as we hold the write lock
                            int[] dupRefs = memoriesForEntityLocked(duplicate);
                            for (int memIdx : dupRefs) {
                                linkEntityToMemoryLocked(canonical, memIdx, INITIAL_LINK_WEIGHT, true);
                            }
                            long dupOffset = (long) duplicate * ENTITY_NODE_BYTES;
                            entitySegment.set(ValueLayout.JAVA_INT, dupOffset + ENT_OFF_ADJ_COUNT, 0);
                            entitySegment.set(ValueLayout.JAVA_INT, dupOffset + ENT_OFF_MERGED_INTO, canonical);
                            
                            merged.add(duplicate);
                            mergeCount++;
                            log.info("Entity resolution merged '{}' into '{}'", 
                                    canonical == idA ? nameB : nameA, 
                                    canonical == idA ? nameA : nameB);
                        }
                    }
                }
            }
            return mergeCount;
        } finally {
            lock.unlockWrite(stamp);
        }
    }
    
    private float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0f;
        float dot = 0f, normA = 0f, normB = 0f;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0f || normB == 0f) return 0f;
        return (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    private static final ThreadLocal<int[]> LEV_PREV = ThreadLocal.withInitial(() -> new int[256]);
    private static final ThreadLocal<int[]> LEV_CURR = ThreadLocal.withInitial(() -> new int[256]);

    static int levenshteinDistance(String a, String b) {
        int lenA = a.length(), lenB = b.length();
        if (lenA == 0) return lenB;
        if (lenB == 0) return lenA;
        if (Math.abs(lenA - lenB) > 5) return Math.abs(lenA - lenB);
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
     * Resets all entities and adjacency data by zero-filling segments. The arena is retained
     * (unlike {@link #close()}) so the directory remains usable. Used by privacy wipe.
     */
    public void reset() {
        long stamp = lock.writeLock();
        try {
            int entitiesBefore = entityCount;
            entitySegment.fill((byte) 0);
            adjacencySegment.fill((byte) 0);
            nameIndex.clear();
            entityCount = 0;
            adjHighWaterMark = 0;
            persistCount();
            log.info("EntityDirectory reset: {} entities cleared", entitiesBefore);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // DERIVE-ON-LOAD (P1 transition — mirror an existing EntityGraphMemory)
    // ══════════════════════════════════════════════════════════════

    /**
     * Derives directory contents from a loaded legacy EntityGraphMemory, preserving the exact
     * entity-id&harr;name alignment (so hyperedge vertex ids stay valid) and the entity&rarr;memory
     * adjacency including single-entity memories.
     *
     * <p>Used during the P1 transition (ADR-0003) when {@code entity-directory.edir} is absent: the
     * directory is a read-through mirror of the still-present binary graph. Entities are written at
     * their original ids (0..entityCount-1) so {@link #findEntity(String)} returns the same id the
     * hypergraph was built with.</p>
     *
     * @param source the loaded legacy entity graph
     * @return the number of entities derived
     */
    public int deriveFrom(EntityGraphMemory source) {
        if (source == null) return 0;
        long stamp = lock.writeLock();
        try {
            int srcCount = source.entityCount();
            if (srcCount > entityCapacity) {
                log.warn("EntityDirectory capacity {} < source entities {} — deriving a prefix",
                        entityCapacity, srcCount);
                srcCount = entityCapacity;
            }
            // Reverse the name index: entityId -> normalized name.
            String[] idToName = new String[srcCount];
            for (Map.Entry<String, Integer> e : source.nameIndex().entrySet()) {
                int id = e.getValue();
                if (id >= 0 && id < srcCount) {
                    idToName[id] = e.getKey();
                }
            }
            // Establish the id space: write every node at its original id, then populate adjacency.
            entityCount = srcCount;
            persistCount();
            for (int id = 0; id < srcCount; id++) {
                String name = idToName[id];
                if (name == null) {
                    // No name recorded for this id — write an empty node to keep the id slot aligned.
                    writeEntityNode(id, "", "OTHER");
                    continue;
                }
                nameIndex.put(name, id);
                writeEntityNode(id, name, source.entityType(id));
            }
            for (int id = 0; id < srcCount; id++) {
                int refCount = source.memoryRefCount(id);
                for (int r = 0; r < refCount; r++) {
                    int memIdx = source.memoryRefAt(id, r);
                    if (memIdx < 0) continue;
                    float w = source.memoryRefWeight(id, r);
                    linkEntityToMemoryLocked(id, memIdx, w, false);
                }
            }
            log.info("EntityDirectory derived from EntityGraph: {} entities, {} adj entries",
                    entityCount, adjHighWaterMark);
            return entityCount;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // PERSISTENCE: SMKM container + name-index sidecar
    // ══════════════════════════════════════════════════════════════

    /** Saves the directory to its SMKM container plus the {@code entity-directory-names.idx} sidecar. */
    public void save(Path filePath) {
        save(filePath, this.dataEncryptor);
    }

    /** Saves the directory with optional name-index encryption. */
    public void save(Path filePath, DataEncryptor encryptor) {
        if (bundleManaged) {
            long stamp = lock.readLock();
            try {
                // For bundleManaged, we don't have standard 64B headers mapped in the same segments,
                // but wait, headerSegment was mapped as entitySegment.asSlice(0, 64)!
                // So yes, writeSmkmHeaderToSegment on headerSegment works perfectly!
                writeSmkmHeaderToSegment(headerSegment, entityCapacity, entityCount,
                        adjSegmentCapacity, adjHighWaterMark);
                headerSegment.force();
                entitySegment.force();
                
                // Write sub-header to adjacencySegment too
                long headerStart = MemoryHeader.HEADER_BYTES;
                adjacencySegment.set(ValueLayout.JAVA_INT, headerStart + SUB_OFF_ADJ_CAPACITY, adjSegmentCapacity);
                adjacencySegment.set(ValueLayout.JAVA_INT, headerStart + SUB_OFF_ADJ_HWM, adjHighWaterMark);
                adjacencySegment.force();

                Path path = filePath != null ? filePath : mmapFilePath;
                if (path != null) {
                    EntityDirectorySerializer.saveNameIndexSidecar(this, path, encryptor);
                }
            } finally {
                lock.unlockRead(stamp);
            }
            return;
        }
        if (fileBacked && filePath.equals(mmapFilePath)) {
            long stamp = lock.readLock();
            try {
                writeSmkmHeaderToSegment(headerSegment, entityCapacity, entityCount,
                        adjSegmentCapacity, adjHighWaterMark);
                headerSegment.force();
                entitySegment.force();
                adjacencySegment.force();
            } finally {
                lock.unlockRead(stamp);
            }
            EntityDirectorySerializer.saveNameIndexSidecar(this, filePath, encryptor);
            log.info("EntityDirectory flushed (SMKM mmap): entities={}/{}, adjHwm={}",
                    entityCount, entityCapacity, adjHighWaterMark);
            return;
        }
        writeSmkmFile(filePath);
        EntityDirectorySerializer.saveNameIndexSidecar(this, filePath, encryptor);
    }

    /** Writes this directory as a fresh SMKM container at {@code filePath}. */
    private void writeSmkmFile(Path filePath) {
        Path parent = filePath.getParent();
        long stamp = lock.readLock();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            long entityBytes = (long) ENTITY_NODE_BYTES * entityCapacity;
            long adjBytes = (long) ADJ_ENTRY_BYTES * adjSegmentCapacity;
            try (FileChannel ch = FileChannel.open(filePath,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                writeSmkmHeaderToChannel(ch, entityCapacity, entityCount,
                        adjSegmentCapacity, adjHighWaterMark);
                ch.position(DATA_START);
                writeSegmentFully(ch, entitySegment, entityBytes);
                writeSegmentFully(ch, adjacencySegment, adjBytes);
                ch.force(true);
            }
            log.info("EntityDirectory saved (SMKM): entities={}, adjHwm={} -> {}",
                    entityCount, adjHighWaterMark, filePath);
        } catch (IOException e) {
            throw new SpectorGraphPersistenceException("EntityDirectory", filePath, e);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    /**
     * Loads a directory from disk, or returns a fresh (heap) directory if the file is absent.
     * A present-but-unreadable file throws {@link SpectorGraphPersistenceException} — never a silent
     * empty directory (#432/#433 discipline).
     *
     * @param filePath           path to the {@code entity-directory.edir} container
     * @param defaultEntityCap   entity capacity if the file doesn't exist
     * @param entityTypeRegistry the shared entity type registry
     * @return an EntityDirectory (loaded or fresh)
     */
    public static EntityDirectory load(Path filePath, int defaultEntityCap,
                                       TypeRegistryMemory entityTypeRegistry) {
        return load(filePath, defaultEntityCap, entityTypeRegistry, null);
    }

    /** Loads a directory with optional name-index decryption. */
    public static EntityDirectory load(Path filePath, int defaultEntityCap,
                                       TypeRegistryMemory entityTypeRegistry, DataEncryptor encryptor) {
        if (filePath == null || !Files.exists(filePath)) {
            log.info("EntityDirectory file not found, creating fresh: {}", filePath);
            return new EntityDirectory(defaultEntityCap, entityTypeRegistry);
        }
        try {
            long size = Files.size(filePath);
            if (size < 4) {
                throw new IOException("file too small to contain a magic number: " + size + " bytes");
            }
            int beMagic = peekMagicBE(filePath);
            int leMagic = Integer.reverseBytes(beMagic);
            if (leMagic != MemoryHeader.MAGIC) {
                throw new IOException("Unrecognized EntityDirectory file magic: 0x"
                        + Integer.toHexString(beMagic) + " (expected SMKM 0x"
                        + Integer.toHexString(MemoryHeader.MAGIC) + "): " + filePath);
            }
            EntityDirectory dir = new EntityDirectory(filePath, defaultEntityCap, entityTypeRegistry);
            ConcurrentHashMap<String, Integer> names =
                    EntityDirectorySerializer.loadNameIndexSidecar(filePath, encryptor);
            if (names != null && !names.isEmpty()) {
                dir.nameIndexInternal().putAll(names);
            }
            dir.setDataEncryptor(encryptor);
            return dir;
        } catch (SpectorGraphPersistenceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to load EntityDirectory from {} (file present but unreadable)", filePath, e);
            throw new SpectorGraphPersistenceException("EntityDirectory", filePath, e);
        }
    }

    // ── SMKM header helpers ──

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

    private static void writeSmkmHeaderToChannel(FileChannel ch, int entityCap, int entityCount,
                                                 int adjCap, int adjHwm) throws IOException {
        try (Arena confined = Arena.ofConfined()) {
            MemorySegment head = confined.allocate(DATA_START);
            long now = System.currentTimeMillis();
            MemoryHeader.write(head, 0L, LAYOUT.schemaVersion(), MemoryShape.GRAPH, 0x01,
                    entityCap, entityCount, ENTITY_NODE_BYTES, LAYOUT.layoutId(), now, now);
            head.set(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_ADJ_CAPACITY, adjCap);
            head.set(ValueLayout.JAVA_INT, MemoryHeader.HEADER_BYTES + SUB_OFF_ADJ_HWM, adjHwm);
            ByteBuffer buf = head.asByteBuffer();
            ch.position(0);
            while (buf.hasRemaining()) {
                ch.write(buf);
            }
        }
    }

    /** Writes the SMKM header directly to a memory-mapped header segment (no FileChannel needed). */
    private static void writeSmkmHeaderToSegment(MemorySegment header, int entityCap, int entityCount,
                                                 int adjCap, int adjHwm) {
        long now = System.currentTimeMillis();
        MemoryHeader.write(header, 0L, LAYOUT.schemaVersion(), MemoryShape.GRAPH, 0x01,
                entityCap, entityCount, ENTITY_NODE_BYTES, LAYOUT.layoutId(), now, now);
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

    // ── Package-private accessors for EntityDirectorySerializer ──

    int entityCapacityInternal() { return entityCapacity; }

    // ══════════════════════════════════════════════════════════════
    // KERNEL INTEGRATION — the directory has no entity↔entity edges
    // ══════════════════════════════════════════════════════════════

    @Override
    public int size() {
        return entityCount;
    }

    @Override
    public void flush() {
        if (entitySegment != null && entitySegment.isMapped()) entitySegment.force();
        if (adjacencySegment != null && adjacencySegment.isMapped()) adjacencySegment.force();
    }

    @Override
    public int addEdge(int fromNode, int toNode, MemorySegment edgeBytes) {
        // The directory owns identity + entity→memory adjacency only; it has no entity→entity edges.
        return -1;
    }

    @Override
    public void removeEdge(int edgeId) {
        // No entity→entity edges to remove.
    }

    @Override
    public PrimitiveIterator.OfInt neighbours(int nodeId) {
        return java.util.stream.IntStream.empty().iterator();
    }

    @Override
    public int edgeCount() {
        return 0;
    }

    @Override
    public int nodeCount() {
        return entityCount;
    }

    public MemoryId memoryId() {
        return memoryId;
    }

    @Override
    public MemorySegment headerSegment() {
        return headerSegment;
    }

    @Override
    public void close() {
        log.info("EntityDirectory closing (entities={}, adjEntries={}, fileBacked={})",
                entityCount, adjHighWaterMark, fileBacked);
        if (fileBacked && headerSegment != null) {
            entitySegment.force();
            adjacencySegment.force();
            headerSegment.force();
        }
        arena.close();
    }
}
