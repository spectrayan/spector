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
package com.spectrayan.spector.memory.kernel.layout;

import com.spectrayan.spector.memory.kernel.MemoryHeader;
import com.spectrayan.spector.memory.kernel.MemoryLayout;

/**
 * Memory layout for the {@code EntityDirectory} — the kernel-substrate companion that owns entity
 * <em>identity</em> (name&harr;id index, entity type id) and the authoritative entity&rarr;memory
 * adjacency (including single-entity memories, which have no hyperedge). Introduced for the
 * hypergraph graduation (ADR-0003, #455): {@code HyperEntityGraphMemory} stays a pure hyperedge
 * store over an externally-owned dense entity-id space, and this directory owns that id space.
 *
 * <h3>Reuse of {@code EntityLayout}</h3>
 * <p>The node record deliberately keeps {@code EntityLayout}'s 64-byte stride and the exact
 * identity/adjacency field offsets ({@code ENT_OFF_TYPE}, {@code ENT_OFF_NAME_HASH},
 * {@code ENT_OFF_ADJ_OFFSET/COUNT/CAPACITY}) and the 8-byte adjacency entry
 * ({@code memIdx:4}{@code weight:4}). The binary-edge fields ({@code ENT_OFF_DEGREE},
 * {@code ENT_OFF_EDGE_START}) are simply left unused — the directory has no entity&rarr;entity
 * edges. Keeping the layout identical makes the P3 {@code entity.graph}&rarr;{@code entity-directory.edir}
 * migration a straight byte-copy of the node/adjacency slabs.</p>
 *
 * <h3>Record layouts</h3>
 * <pre>
 *   Entity Node ({@value #ENTITY_NODE_BYTES} bytes, 8-byte aligned):
 *     [type:4B][pad:4B][nameHash:8B]
 *     [adjOffset:4B][adjCount:4B][adjCapacity:4B][pad:4B]
 *     [pad:32B  (unused binary-edge region)]
 *
 *   Adjacency Entry ({@value #ADJ_ENTRY_BYTES} bytes):
 *     [memIdx:4B][weight:4B]
 * </pre>
 *
 * <h3>On-disk container (SMKM {@code EDIR})</h3>
 * <pre>
 *   [64B kernel MemoryHeader (shape=GRAPH, layoutId=EDIR, schemaVersion=1)]
 *   [16B sub-header: adjCapacity, adjHwm, reserved]
 *   [entity node slab][entity&rarr;memory adjacency slab]
 * </pre>
 * The name&harr;id index is persisted as the {@code entity-directory-names.idx} sidecar
 * (reusing the {@code entity-names.idx} codec), not inside this container.
 */
public final class EntityDirectoryLayout implements MemoryLayout {

    private static final int LAYOUT_ID = 0x45444952; // 'EDIR'
    private static final int VERSION = 1;

    // ── SMKM container: [64B MemoryHeader][16B sub-header][node slab][adj slab] ──
    /** Bytes of the directory sub-header following the 64-byte kernel {@link MemoryHeader}. */
    public static final int GRAPH_SUBHEADER_BYTES = 16;
    /** Sub-header field (relative to {@link MemoryHeader#HEADER_BYTES}): adjacency-segment capacity in entries (int). */
    public static final int SUB_OFF_ADJ_CAPACITY = 0;
    /** Sub-header field: adjacency-segment high-water mark in entries (int). */
    public static final int SUB_OFF_ADJ_HWM = 4;
    /** Byte offset where the entity node slab begins in an SMKM file (64 + 16). */
    public static final long DATA_START = MemoryHeader.HEADER_BYTES + GRAPH_SUBHEADER_BYTES;

    // ── Entity Node record (64 bytes, 8-byte aligned — identity-only subset of EntityLayout) ──
    /** Bytes per entity-node record; also the substrate record stride. */
    public static final int ENTITY_NODE_BYTES = 64;
    /** Node field: entity type id (int). */
    public static final int ENT_OFF_TYPE = 0;          // 4B; pad 4B follows
    /** Node field: normalized-name hash (long, 8-byte aligned). */
    public static final int ENT_OFF_NAME_HASH = 8;     // 8B
    /** Node field: index of this entity's block in the adjacency segment. */
    public static final int ENT_OFF_ADJ_OFFSET = 16;   // 4B
    /** Node field: number of adjacency entries in use. */
    public static final int ENT_OFF_ADJ_COUNT = 20;    // 4B
    /** Node field: allocated adjacency slots for this entity. */
    public static final int ENT_OFF_ADJ_CAPACITY = 24; // 4B
    /** Node field: if merged, the id of the canonical entity, else -1. */
    public static final int ENT_OFF_MERGED_INTO = 28;  // 4B; pad to 64B

    // ── Adjacency Entry record (8 bytes) ──
    /** Bytes per adjacency (entity→memory) entry. */
    public static final int ADJ_ENTRY_BYTES = 8;
    /** Adjacency field: memory slot index (int). */
    public static final int ADJ_OFF_MEM_IDX = 0;        // 4B
    /** Adjacency field: link weight (float). */
    public static final int ADJ_OFF_WEIGHT = 4;         // 4B

    @Override
    public int layoutId() {
        return LAYOUT_ID;
    }

    @Override
    public int schemaVersion() {
        return VERSION;
    }

    @Override
    public int recordStride() {
        return ENTITY_NODE_BYTES;
    }

    @Override
    public boolean crcEnabled() {
        return false;
    }

    @Override
    public String name() {
        return "EntityDirectory";
    }
}
