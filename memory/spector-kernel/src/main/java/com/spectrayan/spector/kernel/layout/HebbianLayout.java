/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.kernel.layout;

import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.layout.RegionLayout;

/**
 * Memory layout for edges in the Hebbian Graph CSR — the single source of truth for the CSR
 * edge stride and field offsets as well as the SMKM container sub-header framing (#435).
 */
public final class HebbianLayout implements RegionLayout {

    /** Layout id / interim HCSR container magic ('HCSR'). */
    public static final int LAYOUT_ID = 0x48435352;
    private static final int VERSION = 1;

    // ── SMKM container: [64B RegionPreamble][16B graph sub-header][offset slab][edge slab] ──
    /** Bytes of the Hebbian graph sub-header following the 64-byte kernel {@link RegionPreamble}. */
    public static final int GRAPH_SUBHEADER_BYTES = 16;
    /** Sub-header field (relative to {@link RegionPreamble#PREAMBLE_BYTES}): edge-slab capacity (int). */
    public static final int SUB_OFF_EDGE_CAPACITY = 0;
    /** Sub-header field: current reflection cycle (int). */
    public static final int SUB_OFF_CURRENT_CYCLE = 4;
    /** Byte offset where the CSR offset slab begins in an SMKM file (64 + 16). */
    public static final long DATA_START = RegionPreamble.PREAMBLE_BYTES + GRAPH_SUBHEADER_BYTES;

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

    // ── Structural capacity constants ──

    /** Default edge capacity multiplier relative to vertex count. */
    public static final int DEFAULT_EDGE_CAPACITY_FACTOR = 2;
    /** Default initial capacity for overflow node adjacency lists. */
    public static final int DEFAULT_OVERFLOW_INITIAL_CAPACITY = 4;
    /** Occupancy multiplier for telemetry (degree * factor = max overflow capacity). */
    public static final int OVERFLOW_OCCUPANCY_MAX_DEGREE = 8;
    /** I/O chunk buffer size for persistence operations. */
    public static final int IO_CHUNK_BYTES = 64 * 1024;

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
