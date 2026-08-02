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
 * Memory layout for edges in the Hebbian Graph CSR.
 */
public final class HebbianLayout implements MemoryLayout {

    /** Layout id / interim HCSR container magic ('HCSR'). */
    public static final int LAYOUT_ID = 0x48435352;
    private static final int VERSION = 1;

    /** Bytes per CSR edge record: neighbor(4) + weight(4) + lastCycle(2) + bridge(1) + flags(1). */
    public static final int EDGE_BYTES = 12;
    /** Edge field: neighbor (target) vertex index. */
    public static final int EDGE_OFF_NEIGHBOR = 0;
    /** Edge field: association weight (float). */
    public static final int EDGE_OFF_WEIGHT = 4;
    /** Edge field: last reflection cycle the edge was touched (unsigned short). */
    public static final int EDGE_OFF_LAST_CYCLE = 8;
    /** Edge field: bridge score for eviction protection (unsigned byte). */
    public static final int EDGE_OFF_BRIDGE_SCORE = 10;
    /** Edge field: edge flags (byte). */
    public static final int EDGE_OFF_EDGE_FLAGS = 11;

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
        return EDGE_BYTES;
    }

    @Override
    public boolean crcEnabled() {
        return false;
    }

    @Override
    public String name() {
        return "HebbianGraphCsr";
    }
}
