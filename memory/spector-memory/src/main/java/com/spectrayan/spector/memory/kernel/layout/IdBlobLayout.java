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
 * Memory layout for the variable-length ID/metadata payload pool.
 * Stride is 0 since records are variable length.
 */
public final class IdBlobLayout implements MemoryLayout {

    private static final int STRIDE = 0; // Variable length
    private static final int LAYOUT_ID = 0x4944504C; // 'IDPL'
    private static final int VERSION = 1;

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
        return "IdBlobLayout";
    }
}
