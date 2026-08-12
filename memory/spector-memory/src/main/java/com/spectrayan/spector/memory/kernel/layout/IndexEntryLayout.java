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
 * Memory layout for the index entry slot table (48 bytes fixed size, v6).
 *
 * <p>Format (v6 — issue #443 Phase 2):
 * - [0:8]   idPoolOffset (long)
 * - [8:4]   idPoolLength (int)
 * - [12:4]  typeOrdinal (int)
 * - [16:8]  offset (long)
 * - [24:4]  graphSlot (int)            — semantic-HNSW / Hebbian node slot (was misnamed
 *                                        {@code partitionIndex} in v5; behaviour unchanged)
 * - [28:8]  textOffset (long)
 * - [36:4]  textLength (int)
 * - [40:4]  colocatedPartition (int)   — NEW in v6: the DISK partition the record lives in
 * - [44:4]  reserved (int)             — NEW in v6: must be 0 (8-byte alignment)
 * </p>
 *
 * <p><b>Format history:</b> v5 slots were 40 bytes with {@code partitionIndex} at
 * {@code [24:4]} and no colocated-partition dimension. v6 appends
 * {@code colocatedPartition} + {@code reserved} (stride 40 → 48, 8-byte aligned) so
 * restart-correct multi-partition recall can resolve each record to its partition.
 * The loader gates on {@link com.spectrayan.spector.memory.kernel.MemoryHeader#readSchemaVersion}:
 * v6 reads the 48-byte slot, v5 reads the 40-byte slot with {@code colocatedPartition = 0}.</p>
 */
public final class IndexEntryLayout implements MemoryLayout {

    private static final int STRIDE = 48;
    private static final int LAYOUT_ID = 0x4D494458; // 'MIDX'
    private static final int VERSION = 7;

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
        return "IndexEntryLayout";
    }
}
