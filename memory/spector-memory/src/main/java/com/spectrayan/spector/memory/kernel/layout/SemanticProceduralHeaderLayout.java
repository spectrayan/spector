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

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

import static com.spectrayan.spector.memory.kernel.layout.SemanticProceduralHeaderFields.OFFSET_V2_CENTROID_ID;
import static com.spectrayan.spector.memory.kernel.layout.SemanticProceduralHeaderFields.OFFSET_V2_EXACT_NORM;
import static com.spectrayan.spector.memory.kernel.layout.SemanticProceduralHeaderFields.OFFSET_V2_SYNAPTIC_TAGS_HI;
import static com.spectrayan.spector.memory.kernel.layout.SemanticProceduralHeaderFields.OFFSET_V2_SYNAPTIC_TAGS_LO;
import static com.spectrayan.spector.memory.kernel.layout.SemanticProceduralHeaderFields.VAR_HANDLE_SYNAPTIC_TAGS;

/**
 * Header layout for Semantic and Procedural memory tiers (ADR-0030).
 *
 * <p>Extends {@link EncodingHeaderLayout} with reads and writes for vector quantization
 * metadata (exact norm, IVF centroid ID) and 128-bit Bloom filter synaptic tags.</p>
 *
 * @since 1.5.0
 * @see EncodingHeaderLayout
 * @see SemanticProceduralHeaderFields
 * @see SemanticHeaderLayout
 * @see ProceduralHeaderLayout
 */
public class SemanticProceduralHeaderLayout extends EncodingHeaderLayout {

    public static final SemanticProceduralHeaderLayout INSTANCE = new SemanticProceduralHeaderLayout();

    public static final VarHandle VAR_HANDLE_SYNAPTIC_TAGS_LO = VAR_HANDLE_SYNAPTIC_TAGS;
    public static final VarHandle VAR_HANDLE_SYNAPTIC_TAGS_HI = VAR_HANDLE_SYNAPTIC_TAGS;

    public SemanticProceduralHeaderLayout() {
        super();
    }

    public static SemanticProceduralHeaderLayout defaultLayout() {
        return INSTANCE;
    }

    // ── Vector & IVF Routing ──

    @Override
    public float readExactNorm(MemorySegment seg, long off) {
        return seg.get(ValueLayout.JAVA_FLOAT_UNALIGNED, off + OFFSET_V2_EXACT_NORM);
    }

    @Override
    public short readCentroidId(MemorySegment seg, long off) {
        return seg.get(ValueLayout.JAVA_SHORT_UNALIGNED, off + OFFSET_V2_CENTROID_ID);
    }

    // ── 128-Bit Synaptic Tags (Bloom Filter) ──

    @Override
    public long readSynapticTags(MemorySegment seg, long off) {
        return seg.get(ValueLayout.JAVA_LONG_UNALIGNED, off + OFFSET_V2_SYNAPTIC_TAGS_LO);
    }

    @Override
    public long readSynapticTagsLo(MemorySegment seg, long off) {
        return seg.get(ValueLayout.JAVA_LONG_UNALIGNED, off + OFFSET_V2_SYNAPTIC_TAGS_LO);
    }

    @Override
    public long readSynapticTagsHi(MemorySegment seg, long off) {
        return seg.get(ValueLayout.JAVA_LONG_UNALIGNED, off + OFFSET_V2_SYNAPTIC_TAGS_HI);
    }

    @Override
    public void writeSynapticTags(MemorySegment seg, long off, long tagsLo, long tagsHi) {
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED, off + OFFSET_V2_SYNAPTIC_TAGS_LO, tagsLo);
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED, off + OFFSET_V2_SYNAPTIC_TAGS_HI, tagsHi);
    }

    @Override
    public void mergeSynapticTags(MemorySegment seg, long off, long additionalTags) {
        VAR_HANDLE_SYNAPTIC_TAGS.getAndBitwiseOr(seg, off + OFFSET_V2_SYNAPTIC_TAGS_LO, additionalTags);
    }

    @Override
    public void mergeSynapticTags128(MemorySegment seg, long off, long tagsLo, long tagsHi) {
        VAR_HANDLE_SYNAPTIC_TAGS.getAndBitwiseOr(seg, off + OFFSET_V2_SYNAPTIC_TAGS_LO, tagsLo);
        VAR_HANDLE_SYNAPTIC_TAGS.getAndBitwiseOr(seg, off + OFFSET_V2_SYNAPTIC_TAGS_HI, tagsHi);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof SemanticProceduralHeaderLayout;
    }

    @Override
    public int hashCode() {
        return SemanticProceduralHeaderLayout.class.hashCode();
    }

    @Override
    public String toString() {
        return "SemanticProceduralHeaderLayout[]";
    }
}
