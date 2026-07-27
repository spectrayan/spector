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
 * Memory layout for nodes in the temporal causal chain.
 *
 * <p>Each node is exactly 16 bytes:
 * - 4B prevIdx (int)
 * - 4B nextIdx (int)
 * - 4B sessionId (int)
 * - 4B epochSec (int)
 * </p>
 */
public final class TemporalLayout implements MemoryLayout {

    private static final int STRIDE = 16;
    private static final int LAYOUT_ID = 0x54504348; // 'TPCH'
    private static final int VERSION = 2;

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
        return "TemporalChain";
    }
}
