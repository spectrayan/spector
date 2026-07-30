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
 * Memory layout for Write-Ahead Log (WAL) append records.
 * Layout ID: 0x57414C47 ('WALG')
 */
public final class WalRecordLayout implements MemoryLayout {

    public static final int LAYOUT_ID = 0x57414C47;
    public static final int SCHEMA_VERSION = 1;

    @Override
    public int layoutId() {
        return LAYOUT_ID;
    }

    @Override
    public int schemaVersion() {
        return SCHEMA_VERSION;
    }

    @Override
    public int recordStride() {
        return -1; // Variable-length log records
    }

    @Override
    public boolean crcEnabled() {
        return true;
    }

    @Override
    public String name() {
        return "WalRecordLayout";
    }
}
