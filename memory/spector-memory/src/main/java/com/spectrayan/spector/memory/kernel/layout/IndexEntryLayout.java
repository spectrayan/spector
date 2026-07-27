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
 * Memory layout for the index entry slot table (40 bytes fixed size).
 *
 * <p>Format:
 * - [0:8]   idPoolOffset (long)
 * - [8:4]   idPoolLength (int)
 * - [12:4]  typeOrdinal (int)
 * - [16:8]  offset (long)
 * - [24:4]  partitionIndex (int)
 * - [28:8]  textOffset (long)
 * - [36:4]  textLength (int)
 * </p>
 */
public final class IndexEntryLayout implements MemoryLayout {

    private static final int STRIDE = 40;
    private static final int LAYOUT_ID = 0x4D494458; // 'MIDX'
    private static final int VERSION = 5;

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
