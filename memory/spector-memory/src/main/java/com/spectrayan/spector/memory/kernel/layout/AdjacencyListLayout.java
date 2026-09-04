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

/**
 * Canonical byte-layout constants for the adjacency-list graph wiring shared by every
 * {@code AdjacencyListGraphMemory} subclass (#435, TD-14).
 *
 * <p>Unlike {@link HebbianLayout}/{@link EntityLayout}/{@link HyperEntityLayout}, this is
 * <b>not</b> a pluggable per-record {@link com.spectrayan.spector.memory.kernel.RegionLayout}:
 * the per-edge record stride and payload are defined by the subclass's own {@code RegionLayout}
 * ({@code recordStride()}), while these constants describe the <em>fixed structural wiring</em>
 * that threads a per-vertex singly linked adjacency list through the shared edge slab — the
 * vertex record and the universal base prefix of every edge record. Because that wiring is fixed
 * (never pluggable), a small {@code final} constants class is the right model rather than a
 * {@code RegionLayout} implementation.</p>
 *
 * <pre>
 *   Vertex record ({@value #VERTEX_STRIDE} bytes):
 *     [edgeHead:4B][degree:4B][flags:4B][pad:4B]
 *
 *   Edge record base prefix ({@value #EDGE_HEADER_BYTES} bytes; layout payload follows):
 *     [target:4B][next:4B]
 * </pre>
 */
public final class AdjacencyListLayout {

    // ── Vertex record layout (universal adjacency wiring; 16 bytes) ──
    /** Bytes per vertex record in the vertex offset slab. */
    public static final int VERTEX_STRIDE = 16;
    /** Vertex field: head edge slot index (-1 = no edges). */
    public static final int VERTEX_OFF_EDGE_HEAD = 0;
    /** Vertex field: cached degree (number of outgoing edges). */
    public static final int VERTEX_OFF_DEGREE = 4;
    /** Vertex field: flags (bit0 = allocated). */
    public static final int VERTEX_OFF_FLAGS = 8;

    // ── Edge record base prefix (universal; payload follows) ──
    /** Edge field: target vertex id (-1 = tombstoned). */
    public static final int EDGE_OFF_TARGET = 0;
    /** Edge field: next edge slot in this vertex's list (-1 = end). */
    public static final int EDGE_OFF_NEXT = 4;
    /** Size of the base edge prefix; layout payload begins here. */
    public static final int EDGE_HEADER_BYTES = 8;

    private AdjacencyListLayout() {
    } // constants holder
}
