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
package com.spectrayan.spector.memory.cortex.index;

import java.nio.file.Path;
import com.spectrayan.spector.kernel.bundle.RegionRef;

/**
 * Backward-compatibility alias for {@link IndexEntryMemory}.
 */
public class MemoryIndex extends IndexEntryMemory {

    public MemoryIndex() {
        super();
    }

    public static MemoryIndex fromRegionRefs(RegionRef midxRef, RegionRef idplRef, Path bundlePath, boolean isNew) {
        return com.spectrayan.spector.kernel.store.IndexEntryMemory.fromRegionRefs(MemoryIndex::new, midxRef, idplRef, bundlePath, isNew);
    }

    public static MemoryIndex load(Path filePath) {
        return com.spectrayan.spector.kernel.store.IndexEntryMemory.load(MemoryIndex::new, filePath);
    }
}
