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
 * Layout constants for CoActivation checkpoint region and metadata serialization.
 *
 * <p>Defines byte offsets for the V4 bundle checkpoint region, tag dictionary
 * entry framing, and bandit statistics record layout. These constants replace
 * hardcoded magic offsets previously scattered across
 * {@code CoActivationMemory}'s save/load/checkpoint methods.</p>
 *
 * <h3>Checkpoint Region Layout</h3>
 * <pre>
 *   [0  .. 16)  : inherited from RegionPreamble checkpoint region
 *   [16 .. 20)  : pairCount       (int, 4B)
 *   [20 .. 24)  : edgeCount       (int, 4B)
 *   [24 .. 28)  : tagNameCount    (int, 4B)
 *   [28 ..    )  : tagDictionary   (variable-length entries)
 * </pre>
 *
 * <h3>Tag Dictionary Entry Layout</h3>
 * <pre>
 *   [0 .. 8)   : tagHash  (long, 8B)
 *   [8 .. 12)  : nameLen  (int, 4B)
 *   [12 .. 12+nameLen) : UTF-8 name bytes
 * </pre>
 *
 * <h3>Bandit Statistics Record Layout (32 bytes)</h3>
 * <pre>
 *   [0  .. 8)   : contextHash     (long, 8B)
 *   [8  .. 12)  : ordinal         (int, 4B)
 *   [12 .. 16)  : ema             (float, 4B)
 *   [16 .. 20)  : totalSignals    (int, 4B)
 *   [20 .. 24)  : positiveSignals (int, 4B)
 *   [24 .. 32)  : lastUpdatedMs   (long, 8B)
 * </pre>
 *
 * @see CoActivationLayout
 */
public final class CoActivationMetadataFields {

    private CoActivationMetadataFields() { /* utility class */ }

    // ── Checkpoint region offsets ──

    /** Checkpoint field: pair count (int). Offset relative to checkpoint region start. */
    public static final int OFF_CHK_PAIR_COUNT = 16;
    /** Checkpoint field: edge count (int). */
    public static final int OFF_CHK_EDGE_COUNT = 20;
    /** Checkpoint field: tag name count (int). */
    public static final int OFF_CHK_NAME_COUNT = 24;
    /** Checkpoint field: start of tag dictionary data (variable). */
    public static final int OFF_CHK_TAG_DATA = 28;

    // ── Tag dictionary entry framing ──

    /** Bytes in a tag dictionary entry header (8B hash + 4B nameLen). */
    public static final int TAG_ENTRY_HEADER_BYTES = 12;
    /** Tag entry field: tag hash (long, 8B). */
    public static final int OFF_TAG_HASH = 0;
    /** Tag entry field: name length (int, 4B). */
    public static final int OFF_TAG_LEN = 8;

    // ── Bandit statistics record framing ──

    /** Bytes per bandit statistics record (including 3B alignment padding). */
    public static final int BANDIT_RECORD_BYTES = 32;
    /** Bandit field: context hash (long, 8B). */
    public static final int OFF_BANDIT_CTX_HASH = 0;
    /** Bandit field: ordinal (int, 4B). */
    public static final int OFF_BANDIT_ORDINAL = 8;
    /** Bandit field: EMA score (float, 4B). */
    public static final int OFF_BANDIT_EMA = 12;
    /** Bandit field: total signals (int, 4B). */
    public static final int OFF_BANDIT_TOTAL_SIGNALS = 16;
    /** Bandit field: positive signals (int, 4B). */
    public static final int OFF_BANDIT_POS_SIGNALS = 20;
    /** Bandit field: last updated timestamp (long, 8B). */
    public static final int OFF_BANDIT_LAST_UPDATED_MS = 24;
    /** Bandit alignment padding bytes at end of record. */
    public static final int BANDIT_PADDING_BYTES = 3;

    // ── File format constants ──

    /** CoActivation file magic number ('COAX'). */
    public static final int FILE_MAGIC = 0x434F4158;
    /** Current file format version. */
    public static final int FILE_VERSION = 2;
    /** V1 file header size in bytes. */
    public static final int FILE_HEADER_V1_BYTES = 24;
    /** Current file header size in bytes. */
    public static final int FILE_HEADER_BYTES = 32;
}
