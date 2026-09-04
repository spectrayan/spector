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
 * Region layout descriptor for the log-structured episodic conversation store (ADR-0010 / ADR-0030, D2 Option B).
 *
 * <h3>Record Framing</h3>
 * <p>Unlike {@link EngramLayout} which defines a fixed stride (64B header + quantized vector),
 * the episodic store uses variable-length records with an 80-byte fixed framing overhead:</p>
 * <pre>
 *   +0    prefix          16B   payloadBytes (4) | sequence_id (4) | checksum (4) | magic (4)
 *   +16   EncodingHeader  64B   I, valence, arousal, tags, source, flags, timestamp
 *   +80   payload         N     conversation metadata + CBOR body
 *   next  = 80 + N
 * </pre>
 *
 * <p>The {@link #recordStride()} returns 0 to indicate variable-length
 * records, consistent with the convention used by {@code TextBlobLayout}.</p>
 *
 * @since 1.4.0
 * @see EpisodeCodec
 * @see EpisodicHeaderAccessor
 */
public final class EpisodeLayout implements RegionLayout {

    /** Layout ID: 'EPIL' (Episodic Log, preserved across renames per R7.2). */
    public static final int LAYOUT_ID = 0x4550494C;

    /** Schema version: 2 (Option B with 16B prefix and canonical 64B EncodingHeader). */
    public static final int VERSION = 2;

    /** Length of the record prefix in bytes. */
    public static final int PREFIX_BYTES = 16;

    /** Length of the canonical encoding header in bytes. */
    public static final int HEADER_BYTES = 64;

    /** Total fixed overhead per episode record: 16B prefix + 64B EncodingHeader = 80B. */
    public static final int FIXED_OVERHEAD_BYTES = PREFIX_BYTES + HEADER_BYTES;

    /** Magic identifier in prefix bytes 12..15 for Option B records: 'EPIS' (0x45504953). */
    public static final int MAGIC = 0x45504953;

    /** Singleton instance. */
    public static final EpisodeLayout INSTANCE = new EpisodeLayout();

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
     * Each record's actual stride is {@code 80 + payloadBytes}.
     */
    @Override
    public int recordStride() {
        return 0; // Variable length
    }

    /**
     * CRC enabled for variable-length append records per D3.
     */
    @Override
    public boolean crcEnabled() {
        return true;
    }

    @Override
    public String name() {
        return "EpisodeLayout";
    }

    /**
     * Dedicated episodic encoding header layout.
     */
    public EpisodicHeaderLayout headerLayout() {
        return EpisodicHeaderLayout.defaultLayout();
    }
}
