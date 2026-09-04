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

import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

/**
 * Dedicated header offset and field definitions for the Episodic memory tier (ADR-0030).
 *
 * <p>Episodic engrams use honest, non-punned offsets for conversation session tracking (TSID),
 * LLM model registry identification, turn role classification, and 128-bit episode context tags
 * within bytes 16–63 of the 64-byte encoding header.</p>
 *
 * @since 1.5.0
 * @see EpisodicHeaderLayout
 * @see EncodingHeaderFields
 */
public final class EpisodicHeaderFields {

    private EpisodicHeaderFields() {}

    // ── Episodic Specific Header Offsets (Bytes 16–63) ──

    /** Offset of session_id int64 (bytes 16–23, conversation session TSID). */
    public static final long OFFSET_SESSION_ID           = 16L;

    /** Offset of model_id int16 (bytes 24–25, LLM model registry ID). */
    public static final long OFFSET_MODEL_ID             = 24L;

    /** Offset of conversation role uint8 (byte 26, ConversationRole ordinal). */
    public static final long OFFSET_ROLE                 = 26L;

    /** Offset of consolidation flags byte (byte 27). */
    public static final long OFFSET_CONSOLIDATION_FLAGS  = 27L;

    /** Offset of encoding-time cognitive profile byte (byte 28). */
    public static final long OFFSET_ENCODING_PROFILE     = 28L;

    /** Offset of quantized alpha weight at encoding time (byte 29). */
    public static final long OFFSET_ENCODING_ALPHA       = 29L;

    /** Offset of quantized beta weight at encoding time (byte 30). */
    public static final long OFFSET_ENCODING_BETA        = 30L;

    /** Alignment padding (byte 31). */
    public static final long OFFSET_PAD1                 = 31L;

    /** Offset of soul configuration version counter (bytes 32–33, uint16). */
    public static final long OFFSET_SOUL_VERSION         = 32L;

    /** Reserved alignment / geodesic coordinates (bytes 34–35). */
    public static final long OFFSET_RESERVED_GEO         = 34L;

    /** Offset of surprise z-score at encoding time (bytes 36–39, float32). */
    public static final long OFFSET_ENCODING_SURPRISE    = 36L;

    /** Reserved block 0 (bytes 40–43). */
    public static final long OFFSET_RESERVED0            = 40L;

    /** Reserved block 1 (bytes 44–47). */
    public static final long OFFSET_RESERVED1            = 44L;

    /** 128-bit episodic context tags low 64 bits (bytes 48–55). */
    public static final long OFFSET_EPISODIC_TAGS_LO     = 48L;

    /** 128-bit episodic context tags high 64 bits (bytes 56–63). */
    public static final long OFFSET_EPISODIC_TAGS_HI     = 56L;

    // ── ValueLayouts ──

    public static final ValueLayout.OfLong  LAYOUT_SESSION_ID    = ValueLayout.JAVA_LONG;
    public static final ValueLayout.OfShort LAYOUT_MODEL_ID      = ValueLayout.JAVA_SHORT;
    public static final ValueLayout.OfByte  LAYOUT_ROLE          = ValueLayout.JAVA_BYTE;
    public static final ValueLayout.OfLong  LAYOUT_EPISODIC_TAGS = ValueLayout.JAVA_LONG;

    // ── VarHandles ──

    /** VarHandle for atomic bitwise episodic tag merging (getAndBitwiseOr) on the low 64 bits. */
    public static final VarHandle VAR_HANDLE_EPISODIC_TAGS = LAYOUT_EPISODIC_TAGS.varHandle();
}
