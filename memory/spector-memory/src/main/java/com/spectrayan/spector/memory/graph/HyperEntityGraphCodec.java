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
package com.spectrayan.spector.memory.graph;

import com.spectrayan.spector.memory.kernel.codec.Codec;
import com.spectrayan.spector.memory.kernel.codec.CodecStep;
import com.spectrayan.spector.memory.kernel.layout.HyperEntityLayout;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Set;

/**
 * Codec binding for HyperEntityGraphMemory format.
 */
public final class HyperEntityGraphCodec implements Codec<HyperEntityLayout> {

    private final HyperEntityLayout layout = new HyperEntityLayout();

    @Override
    public HyperEntityLayout layout() {
        return layout;
    }

    @Override
    public Set<Integer> legacyMagics() {
        return Set.of(0x48594547);
    }

    @Override
    public int versionOf(int magic, MemorySegment headerPrefix) {
        return 1;
    }

    @Override
    public List<CodecStep> steps() {
        return List.of();
    }
}
