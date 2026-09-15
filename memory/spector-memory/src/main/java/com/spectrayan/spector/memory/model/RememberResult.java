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
package com.spectrayan.spector.memory.model;

import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;

/**
 * Result record produced by conducting a memory consolidation through the Remember pathway.
 *
 * @param memoryId    unique memory identifier assigned to the engram
 * @param memoryIndex memory index slot (or -1 if unindexed/skipped)
 * @param dedupHit    whether the ingestion was deduplicated
 * @param type        target memory tier
 * @param source      provenance source
 */
public record RememberResult(
        String memoryId,
        int memoryIndex,
        boolean dedupHit,
        MemoryType type,
        MemorySource source
) {
    public static RememberResult skipped() {
        return new RememberResult(null, -1, false, null, null);
    }
}
