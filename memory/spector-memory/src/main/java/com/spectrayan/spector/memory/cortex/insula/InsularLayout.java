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
package com.spectrayan.spector.memory.cortex.insula;

import com.spectrayan.spector.memory.kernel.RegionLayout;

/**
 * On-disk layout for the {@link InsularCortex} self-model region.
 *
 * <p>The insular header occupies 32 bytes immediately after the standard 64-byte
 * {@code RegionPreamble}, giving an overall 96-byte overhead before the self-model
 * JSON payload begins.</p>
 *
 * <h3>Insular Header Layout (32 bytes at offset 64)</h3>
 * <pre>
 * ┌──────────┬──────────┬──────────┬──────────┬──────────┬──────────┐
 * │ version  │dataLength│       updatedAt      │ checksum │  flags   │
 * │  (4B)    │  (4B)    │       (8B)           │  (4B)    │  (4B)    │
 * ├──────────┴──────────┴──────────┴──────────┴──────────┴──────────┤
 * │                    reserved (8B)                                 │
 * └─────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * @since 1.2.0
 * @see InsularCortex
 */
public final class InsularLayout implements RegionLayout {

    /** Singleton instance — stateless, safe to share. */
    public static final InsularLayout SINGLETON = new InsularLayout();

    /** Four-char layout identifier: {@code 'INSL'} (0x494E534C). */
    public static final int LAYOUT_ID = 0x494E534C;

    /** Current schema version for the insular header. */
    public static final int SCHEMA_VERSION = 1;

    /** Size of the insular sub-header in bytes. */
    public static final int INSULAR_HEADER_BYTES = 32;

    // ── Insular sub-header field offsets (relative to insular header start) ──

    /** Monotonically increasing version counter (int, 4 bytes). */
    public static final long OFF_VERSION = 0;

    /** Length of the self-model JSON payload in bytes (int, 4 bytes). */
    public static final long OFF_DATA_LENGTH = 4;

    /** Epoch milliseconds of last update (long, 8 bytes). */
    public static final long OFF_UPDATED_AT = 8;

    /** CRC32C checksum of the JSON payload (int, 4 bytes). */
    public static final long OFF_CHECKSUM = 16;

    /**
     * Presence flags (int, 4 bytes).
     * <ul>
     *   <li>{@code 0} — EMPTY (no self-model written or cleared)</li>
     *   <li>{@code 1} — PRESENT (valid self-model exists)</li>
     * </ul>
     */
    public static final long OFF_FLAGS = 20;

    // Bytes 24..31 reserved for future use.

    /** Flag value indicating no self-model is present. */
    public static final int FLAG_EMPTY = 0;

    /** Flag value indicating a valid self-model is present. */
    public static final int FLAG_PRESENT = 1;

    private InsularLayout() {}

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
        return 0; // variable-length (single JSON blob)
    }

    @Override
    public boolean crcEnabled() {
        return true;
    }

    @Override
    public String name() {
        return "Insular";
    }
}
