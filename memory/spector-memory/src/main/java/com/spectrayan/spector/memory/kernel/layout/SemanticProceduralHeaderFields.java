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
 * Dedicated header offset and field definitions for the Semantic and Procedural memory tiers (ADR-0030).
 *
 * <p>Semantic and Procedural engrams share vector quantization metadata (exact norm, IVF centroid ID)
 * and 128-bit Bloom filter synaptic tags within bytes 16–39 of the 64-byte encoding header.</p>
 *
 * <p>Unlike the monolithic {@link EncodingHeaderFields}, this class isolates vector and Bloom filter
 * constants to prevent accidental field punning by other tiers (e.g. episodic session IDs).</p>
 *
 * @since 1.5.0
 * @see SemanticProceduralHeaderLayout
 * @see EncodingHeaderFields
 */
public final class SemanticProceduralHeaderFields {

    private SemanticProceduralHeaderFields() {}

    // ── V2 Field Offsets (Bytes 16–39) ──

    /** Offset of exact_norm float (bytes 16–19, L2 norm of unquantized vector). */
    public static final long OFFSET_V2_EXACT_NORM         = 16L;
    /** Alias for OFFSET_V2_EXACT_NORM. */
    public static final long OFFSET_EXACT_NORM            = OFFSET_V2_EXACT_NORM;

    /** Offset of centroid_id short (bytes 20–21, IVF routing cluster ID). */
    public static final long OFFSET_V2_CENTROID_ID        = 20L;
    /** Alias for OFFSET_V2_CENTROID_ID. */
    public static final long OFFSET_CENTROID_ID           = OFFSET_V2_CENTROID_ID;

    /** Alignment padding (bytes 22–23). */
    public static final long OFFSET_V2_PAD0               = 22L;

    /** 128-bit Bloom filter low 64 bits (bytes 24–31). */
    public static final long OFFSET_V2_SYNAPTIC_TAGS_LO   = 24L;
    /** Alias for OFFSET_V2_SYNAPTIC_TAGS_LO. */
    public static final long OFFSET_SYNAPTIC_TAGS_LO      = OFFSET_V2_SYNAPTIC_TAGS_LO;
    /** Legacy alias for the low 64 bits of synaptic tags. */
    public static final long OFFSET_SYNAPTIC_TAGS         = OFFSET_V2_SYNAPTIC_TAGS_LO;

    /** 128-bit Bloom filter high 64 bits (bytes 32–39). */
    public static final long OFFSET_V2_SYNAPTIC_TAGS_HI   = 32L;
    /** Alias for OFFSET_V2_SYNAPTIC_TAGS_HI. */
    public static final long OFFSET_SYNAPTIC_TAGS_HI      = OFFSET_V2_SYNAPTIC_TAGS_HI;

    /** Reserved alignment / geodesic coordinates (bytes 46–47). */
    public static final long OFFSET_V2_RESERVED_GEO       = 46L;

    // ── ValueLayouts ──

    public static final ValueLayout.OfFloat LAYOUT_EXACT_NORM    = ValueLayout.JAVA_FLOAT;
    public static final ValueLayout.OfShort LAYOUT_CENTROID_ID   = ValueLayout.JAVA_SHORT;
    public static final ValueLayout.OfLong  LAYOUT_SYNAPTIC_TAGS = ValueLayout.JAVA_LONG;

    // ── VarHandles ──

    /** VarHandle for atomic bitwise synaptic tag merging (getAndBitwiseOr) on the low 64 bits. */
    public static final VarHandle VAR_HANDLE_SYNAPTIC_TAGS = LAYOUT_SYNAPTIC_TAGS.varHandle();
}
