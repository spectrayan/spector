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
package com.spectrayan.spector.memory.temporal;

import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.layout.TemporalFactLayout;
import com.spectrayan.spector.memory.kernel.shape.AbstractAppendMemory;

import java.nio.file.Path;

/**
 * Durable append-only memory kernel store for 64-byte temporal fact records,
 * extending {@link AbstractAppendMemory} directly.
 */
public final class TemporalFactsAppendMemory extends AbstractAppendMemory<TemporalFactLayout> {

    private static final MemoryId MEMORY_ID = MemoryId.of("temporal", "facts");

    /**
     * Creates an in-memory (heap) TemporalFactsAppendMemory store with default capacity (64 KB).
     */
    public TemporalFactsAppendMemory() {
        this(64L * 1024);
    }

    /**
     * Creates an in-memory (heap) TemporalFactsAppendMemory store.
     *
     * @param initialSize initial byte capacity
     */
    public TemporalFactsAppendMemory(long initialSize) {
        super(MEMORY_ID, new TemporalFactLayout(), 0, initialSize);
    }

    /**
     * Creates a file-backed (mmap) TemporalFactsAppendMemory store.
     *
     * @param filePath    path to the facts data file
     * @param initialSize byte capacity for new files
     */
    public TemporalFactsAppendMemory(Path filePath, long initialSize) {
        super(MEMORY_ID, new TemporalFactLayout(), 0, initialSize, filePath);
    }
}
