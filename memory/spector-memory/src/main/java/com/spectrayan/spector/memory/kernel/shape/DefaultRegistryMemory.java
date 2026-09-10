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

import com.spectrayan.spector.memory.kernel.id.MemoryId;
import com.spectrayan.spector.memory.kernel.bundle.RegionRef;
import com.spectrayan.spector.memory.kernel.layout.RegistryLayout;

import java.nio.file.Path;

/**
 * Standard default implementation of {@link RegistryMemory} backed by a {@link RegionRef}.
 */
public class DefaultRegistryMemory extends AbstractRegistryMemory {

    public DefaultRegistryMemory(MemoryId id, RegistryLayout layout, int capacity,
                                 RegionRef regionRef, int count,
                                 boolean persistent, Path filePath) {
        super(id, layout, capacity, regionRef, count, persistent, filePath);
    }

    public DefaultRegistryMemory(MemoryId id, RegistryLayout layout, int capacity, long segmentBytes, Path filePath) {
        super(id, layout, capacity, segmentBytes, filePath);
    }
}
