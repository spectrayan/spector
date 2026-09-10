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
package com.spectrayan.spector.memory.cortex.index;

import java.nio.file.Path;
import com.spectrayan.spector.kernel.bundle.RegionRef;

/**
 * Backward-compatibility subclass for {@link com.spectrayan.spector.kernel.store.IndexEntryMemory}.
 */
public class IndexEntryMemory extends com.spectrayan.spector.kernel.store.IndexEntryMemory {

    public IndexEntryMemory() {
        super();
    }

    public static IndexEntryMemory load(Path path) {
        return com.spectrayan.spector.kernel.store.IndexEntryMemory.load(IndexEntryMemory::new, path);
    }

    public static IndexEntryMemory fromRegionRefs(RegionRef midxRef, RegionRef idplRef, Path bundlePath, boolean isNew) {
        return com.spectrayan.spector.kernel.store.IndexEntryMemory.fromRegionRefs(IndexEntryMemory::new, midxRef, idplRef, bundlePath, isNew);
    }
}
