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
package com.spectrayan.spector.memory.hebbian;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.memory.kernel.codec.Codec;
import com.spectrayan.spector.memory.kernel.codec.CodecStep;
import com.spectrayan.spector.memory.kernel.layout.HebbianLayout;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Set;

/**
 * Codec binding for HebbianGraphMemory format.
 */
public final class HebbianGraphCodec implements Codec<HebbianLayout> {

    private final HebbianLayout layout = new HebbianLayout();

    @Override
    public HebbianLayout layout() {
        return layout;
    }

    @Override
    public Set<Integer> legacyMagics() {
        return Set.of(HgphToCsrStep.FROM_MAGIC, HcsrToSmkmStep.FROM_MAGIC);
    }

    @Override
    public int versionOf(int magic, MemorySegment headerPrefix) {
        return 1;
    }

    @Override
    public List<CodecStep> steps() {
        // Two terminal hops to the SMKM CSR container: from the legacy HGPH format and
        // from the interim HCSR format (#432). Both are the single migration authority
        // for Hebbian (#435); load() understands the SMKM output they produce.
        return List.of(new HgphToCsrStep(), new HcsrToSmkmStep());
    }
}
