/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.kernel.shape;

import com.spectrayan.spector.kernel.id.MemoryId;
import com.spectrayan.spector.kernel.bundle.RegionRef;
import com.spectrayan.spector.kernel.layout.RegistryLayout;

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
