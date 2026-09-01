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
package com.spectrayan.spector.memory.kernel.shape;

import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.MemoryLayout;

/**
 * Concrete implementation of RecordMemory shape.
 */
public final class DefaultRecordMemory<L extends MemoryLayout> extends AbstractRecordMemory<L> {

    public DefaultRecordMemory(MemoryId id, L layout, int capacity, long segmentBytes) {
        super(id, layout, capacity, segmentBytes);
    }

    public DefaultRecordMemory(MemoryId id, L layout, int capacity, long segmentBytes, Path filePath) {
        super(id, layout, capacity, segmentBytes, filePath);
    }

    public DefaultRecordMemory(MemoryId id, L layout, int capacity,
                               Arena arena, MemorySegment segment, int count,
                               boolean persistent, Path filePath, FileChannel fileChannel) {
        super(id, layout, capacity, arena, segment, count, persistent, filePath, fileChannel);
    }
}
