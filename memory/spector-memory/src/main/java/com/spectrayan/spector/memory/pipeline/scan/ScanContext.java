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
package com.spectrayan.spector.memory.pipeline.scan;

import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.model.MemoryType;

/**
 * Immutable per-recall context shared by every {@link TierScanStrategy}.
 */
public record ScanContext(
        MemoryType[] targetTypes,
        CognitiveMemoryRouter active,
        boolean singlePartition,
        int activeSeq,
        boolean semanticHnswAvailable
) {
}
