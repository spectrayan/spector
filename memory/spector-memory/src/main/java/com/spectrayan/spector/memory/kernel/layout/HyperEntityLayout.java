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

import com.spectrayan.spector.memory.kernel.RegionPreamble;
import com.spectrayan.spector.memory.kernel.MemoryLayout;

/**
 * Memory layout for nodes/relations in the Hyper Entity Graph — the single source of truth for
 * the hyperedge/vertex record strides and field offsets as well as the SMKM v2 container
 * sub-header framing (#435).
 */
public final class HyperEntityLayout implements MemoryLayout {

    private static final int STRIDE = 32;
    private static final int LAYOUT_ID = 0x48594547; // 'HYEG'
    // v1: legacy container ([32B HYEG] pure, or [64B SMKM][32B HYEG] hybrid).
    // v2: kernel SMKM container ([64B kernel header][16B HyperEntity sub-header][hedges][vertices]),
    //     migrated in-class by HyperEntityGraphMemory.load() (#435). The on-disk kernel-header
    //     schemaVersion distinguishes the current SMKM container (>=2) from the legacy hybrid (==1).
    private static final int VERSION = 2;

    public static final int HEDGE_BYTES = 32;
    public static final int HEDGE_OFF_EDGE_ID = 0;
    public static final int HEDGE_OFF_TYPE = 4;
    public static final int HEDGE_OFF_WEIGHT = 8;
    public static final int HEDGE_OFF_VERTEX_COUNT = 12;
    public static final int HEDGE_OFF_VERTEX_OFFSET = 16;
    public static final int HEDGE_OFF_MEMORY_IDX = 20;
    public static final int HEDGE_OFF_TIMESTAMP = 24;

    public static final int VERTEX_BYTES = 8;
    public static final int VERTEX_OFF_ENTITY_ID = 0;
    public static final int VERTEX_OFF_ROLE_ID = 4;

    public static final int MAX_VERTICES_PER_EDGE = 8;
    public static final int MAX_HYPEREDGES_PER_ENTITY = 64;
    public static final int INCIDENCE_ENTRY_BYTES = 4;

    // ── SMKM v2 container: [64B RegionPreamble][16B HyperEntity sub-header][hedges][vertices] ──
    /** Bytes of the HyperEntity sub-header following the 64-byte kernel {@link RegionPreamble}. */
    public static final int GRAPH_SUBHEADER_BYTES = 16;
    /** Sub-header field (relative to {@link RegionPreamble#PREAMBLE_BYTES}): entity capacity (int). */
    public static final int SUB_OFF_ENTITY_CAP = 0;
    /** Sub-header field: next free hyperedge id (int). */
    public static final int SUB_OFF_NEXT_HYPEREDGE_ID = 4;
    /** Sub-header field: next free vertex offset (int). */
    public static final int SUB_OFF_NEXT_VERTEX_OFFSET = 8;
    /** Sub-header field: total (live) hyperedges (int). */
    public static final int SUB_OFF_TOTAL_HYPEREDGES = 12;
    /** Byte offset where the hyperedge slab begins in an SMKM v2 file (64 + 16). */
    public static final long DATA_START = RegionPreamble.PREAMBLE_BYTES + GRAPH_SUBHEADER_BYTES;

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
        return STRIDE;
    }

    @Override
    public boolean crcEnabled() {
        return false;
    }

    @Override
    public String name() {
        return "HyperEntityGraph";
    }
}
