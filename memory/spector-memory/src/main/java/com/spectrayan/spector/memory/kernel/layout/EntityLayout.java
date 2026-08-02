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

import com.spectrayan.spector.memory.kernel.MemoryLayout;

/**
 * Memory layout for nodes/relations in the Entity Graph — the single source of truth for the
 * entity-node stride and every per-record byte offset (edge, adjacency) as well as the SMKM
 * container sub-header framing (#435). {@code EntityGraphMemory} references these constants
 * rather than declaring its own.
 *
 * <h3>Record layouts</h3>
 * <pre>
 *   Entity Node ({@value #ENTITY_NODE_BYTES} bytes, 8-byte aligned — V2):
 *     [type:4B][pad:4B][nameHash:8B]
 *     [adjOffset:4B][adjCount:4B][adjCapacity:4B][pad:4B]
 *     [pad:4B][degree:4B][edgeStart:4B][pad:20B]
 *
 *   Entity Edge ({@value #EDGE_BYTES} bytes, V2):
 *     [target:4B][relType:4B][weight:4B][lastCycle:2B][bridgeScore:1B][flags:1B]
 *
 *   Adjacency Entry ({@value #ADJ_ENTRY_BYTES} bytes):
 *     [memIdx:4B][weight:4B]
 * </pre>
 */
public final class EntityLayout implements MemoryLayout {

    private static final int LAYOUT_ID = 0x45474D4D; // 'EGMM'
    private static final int VERSION = 2;

    // ── Entity Node record (64 bytes, 8-byte aligned — V2) ──
    /** Bytes per entity-node record; also the substrate record stride. */
    public static final int ENTITY_NODE_BYTES = 64;
    /** Node field: entity type id (int). */
    public static final int ENT_OFF_TYPE = 0;          // 4B (entity type id); pad 4B follows
    /** Node field: normalized-name hash (long, 8-byte aligned). */
    public static final int ENT_OFF_NAME_HASH = 8;     // 8B
    /** Node field: index of this entity's block in the adjacency segment. */
    public static final int ENT_OFF_ADJ_OFFSET = 16;   // 4B
    /** Node field: number of adjacency entries in use. */
    public static final int ENT_OFF_ADJ_COUNT = 20;    // 4B
    /** Node field: allocated adjacency slots for this entity. */
    public static final int ENT_OFF_ADJ_CAPACITY = 24; // 4B; pad 4B (28-31) + pad 4B (32-35, was refCount in V1)
    /** Node field: out-degree (edge count). */
    public static final int ENT_OFF_DEGREE = 36;       // 4B
    /** Node field: index into the edge segment where this entity's edges start. */
    public static final int ENT_OFF_EDGE_START = 40;   // 4B; pad 20B to reach 64B

    // ── Entity Edge record (16 bytes, V2) ──
    /** Bytes per entity-edge record. */
    public static final int EDGE_BYTES = 16;
    /** Edge field: target entity id (int). */
    public static final int EDGE_OFF_TARGET = 0;        // 4B
    /** Edge field: relation type id (int). */
    public static final int EDGE_OFF_REL_TYPE = 4;      // 4B
    /** Edge field: association weight (float). */
    public static final int EDGE_OFF_WEIGHT = 8;        // 4B
    /** Edge field: last reflection cycle the edge was touched (unsigned short). */
    public static final int EDGE_OFF_LAST_CYCLE = 12;   // 2B
    /** Edge field: bridge score for eviction protection (unsigned byte). */
    public static final int EDGE_OFF_BRIDGE_SCORE = 14; // 1B
    /** Edge field: edge flags (byte, reserved). */
    public static final int EDGE_OFF_EDGE_FLAGS = 15;   // 1B

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
        return "EntityGraph";
    }
}
