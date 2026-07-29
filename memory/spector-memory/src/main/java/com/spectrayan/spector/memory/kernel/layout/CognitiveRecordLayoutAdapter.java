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
import com.spectrayan.spector.memory.synapse.CognitiveRecordLayout;
import com.spectrayan.spector.memory.synapse.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.synapse.HeaderLayout;

import java.lang.foreign.MemorySegment;

/**
 * Adapter that bridges the existing {@link CognitiveRecordLayout} to the
 * new {@link MemoryLayout} interface required by the memory kernel.
 */
public final class CognitiveRecordLayoutAdapter implements MemoryLayout {
    
    public static final int LAYOUT_ID = 0x434F4700; // 'COG\0'
    
    private final CognitiveRecordLayout delegate;
    
    /**
     * Constructs a new adapter wrapping the given delegate layout.
     *
     * @param delegate the cognitive record layout to wrap
     */
    public CognitiveRecordLayoutAdapter(CognitiveRecordLayout delegate) {
        this.delegate = delegate;
    }
    
    @Override
    public int layoutId() {
        return LAYOUT_ID;
    }
    
    @Override
    public int schemaVersion() {
        return 1;
    }
    
    @Override
    public int recordStride() {
        return delegate.stride();
    }
    
    @Override
    public boolean crcEnabled() {
        return false; // tier stores use WAL for integrity
    }
    
    @Override
    public String name() {
        return "CognitiveRecordLayout";
    }
    
    /**
     * Returns the underlying {@link CognitiveRecordLayout}.
     *
     * @return the delegate layout
     */
    public CognitiveRecordLayout delegate() {
        return delegate;
    }

    public int stride() {
        return delegate.stride();
    }

    public int quantizedVecBytes() {
        return delegate.quantizedVecBytes();
    }

    public long vectorOffset(long recordOffset) {
        return delegate.vectorOffset(recordOffset);
    }

    public float readImportance(MemorySegment segment, long offset) {
        return delegate.readImportance(segment, offset);
    }

    public CognitiveHeader readHeader(MemorySegment segment, long offset) {
        return delegate.readHeader(segment, offset);
    }

    public void writeHeader(MemorySegment segment, long offset, CognitiveHeader header) {
        delegate.writeHeader(segment, offset, header);
    }

    public byte readFlags(MemorySegment segment, long offset) {
        return delegate.readFlags(segment, offset);
    }

    public long readSynapticTags(MemorySegment segment, long offset) {
        return delegate.readSynapticTags(segment, offset);
    }

    public void tombstone(MemorySegment segment, long offset) {
        delegate.tombstone(segment, offset);
    }

    public HeaderLayout headerLayout() {
        return delegate.headerLayout();
    }
}
