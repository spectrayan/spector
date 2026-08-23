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
 * Memory layout descriptor for the co-activation tracker's compound hash tables.
 *
 * <p>The co-activation data region contains a sub-header followed by two
 * open-addressing hash tables:</p>
 * <pre>
 *   [dataOffset + 0 .. + 4)   : pairCapacity  (int, 4B)
 *   [dataOffset + 4 .. + 8)   : edgeCapacity  (int, 4B)
 *   [dataOffset + 8 .. + 8 + PAIR_SLOT_BYTES × pairCap)  : OffHeapPairTable
 *   [dataOffset + 8 + PAIR_SLOT_BYTES × pairCap .. end)   : OffHeapEdgeTable
 * </pre>
 *
 * <p>Upgraded from v2 (generics-satisfying stub) to v3 (real descriptor with
 * sub-table offset computation) as part of ADR-0009.</p>
 */
public final class CoActivationLayout implements MemoryLayout {

    /** Sub-header: 4B pairCapacity + 4B edgeCapacity. */
    public static final int SUB_HEADER_BYTES = 8;

    /**
     * Bytes per slot in the {@code OffHeapPairTable} (undirected co-occurrence).
     * <pre>
     *   hashA(8B) + hashB(8B) + count(4B) + flags(4B) + pad(8B) = 32B
     * </pre>
     */
    public static final int PAIR_SLOT_BYTES = 32;

    /**
     * Bytes per slot in the {@code OffHeapEdgeTable} (directed STDP).
     * <pre>
     *   srcHash(8B) + tgtHash(8B) + weight(4B) + pad(4B)
     *   + lastActivatedMs(8B) + activationCount(4B) + flags(4B) = 40B
     * </pre>
     */
    public static final int EDGE_SLOT_BYTES = 40;

    private static final int LAYOUT_ID = 0x434F4158; // 'COAX'
    private static final int VERSION = 3;

    @Override
    public int layoutId() {
        return LAYOUT_ID;
    }

    @Override
    public int schemaVersion() {
        return VERSION;
    }

    /**
     * Returns 1 for backward compatibility with the SMKM header's {@code recordStride}
     * field. Hash-table memories do not use stride-based access — sub-table offsets
     * are computed via {@link #pairTableOffset()} and {@link #edgeTableOffset(int)}.
     */
    @Override
    public int recordStride() {
        return 1;
    }

    @Override
    public boolean crcEnabled() {
        return false;
    }

    @Override
    public String name() {
        return "CoActivationLayout";
    }

    // ── Sub-table offset computation ──

    /**
     * Returns the byte offset of the pair table within the data region.
     * The pair table begins immediately after the 8-byte sub-header.
     *
     * @return byte offset from start of data region
     */
    public int pairTableOffset() {
        return SUB_HEADER_BYTES;
    }

    /**
     * Returns the byte offset of the edge table within the data region.
     *
     * @param pairCapacity the capacity (slot count) of the pair table
     * @return byte offset from start of data region
     */
    public int edgeTableOffset(int pairCapacity) {
        return SUB_HEADER_BYTES + pairCapacity * PAIR_SLOT_BYTES;
    }

    /**
     * Computes the total bytes needed for the data region
     * (sub-header + pair table + edge table).
     *
     * @param pairCapacity the capacity (slot count) of the pair table
     * @param edgeCapacity the capacity (slot count) of the edge table
     * @return total data region size in bytes
     */
    public int totalDataBytes(int pairCapacity, int edgeCapacity) {
        return SUB_HEADER_BYTES
                + pairCapacity * PAIR_SLOT_BYTES
                + edgeCapacity * EDGE_SLOT_BYTES;
    }
}
