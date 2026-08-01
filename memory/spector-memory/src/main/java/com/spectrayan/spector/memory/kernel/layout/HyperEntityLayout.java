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
 * Memory layout for nodes/relations in the Hyper Entity Graph.
 */
public final class HyperEntityLayout implements MemoryLayout {

    private static final int STRIDE = 32;
    private static final int LAYOUT_ID = 0x48594547; // 'HYEG'
    private static final int VERSION = 1;

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
