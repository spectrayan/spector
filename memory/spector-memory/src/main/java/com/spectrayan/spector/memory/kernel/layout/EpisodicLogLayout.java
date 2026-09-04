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

import com.spectrayan.spector.memory.kernel.RegionLayout;

/**
 * Memory layout for the log-structured episodic conversation store.
 *
 * <p>Unlike {@link CognitiveRecordLayout} which defines a fixed stride
 * (64B header + quantized vector), the episodic log uses variable-length
 * records: each record is a 64B header followed by a CBOR body whose
 * length is stored in the header's {@code body_length} field (offset 56).</p>
 *
 * <p>The {@link #recordStride()} returns 0 to indicate variable-length
 * records, consistent with the convention used by {@link TextBlobLayout}.</p>
 *
 * @since 1.3.0
 * @see EpisodicFieldAccessor
 */
public final class EpisodicLogLayout implements RegionLayout {

    /** Layout ID: 'EPIL' (Episodic Log). */
    private static final int LAYOUT_ID = 0x4550494C;

    /** Schema version for the episodic log format. */
    private static final int VERSION = 1;

    /** Singleton instance. */
    public static final EpisodicLogLayout INSTANCE = new EpisodicLogLayout();

    @Override
    public int layoutId() {
        return LAYOUT_ID;
    }

    @Override
    public int schemaVersion() {
        return VERSION;
    }

    /**
     * Returns 0 to indicate variable-length records.
     *
     * <p>Each record's actual stride is {@code 64 + body_length}, where
     * {@code body_length} is read from the header at offset 56.</p>
     */
    @Override
    public int recordStride() {
        return 0; // Variable length
    }

    @Override
    public boolean crcEnabled() {
        return false;
    }

    @Override
    public String name() {
        return "EpisodicLogLayout";
    }
}
