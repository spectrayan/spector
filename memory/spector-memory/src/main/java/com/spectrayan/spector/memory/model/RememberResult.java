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
package com.spectrayan.spector.memory.model;

import com.spectrayan.spector.commons.pathway.ConductionOutcome;
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
 * @param outcome     pathway conduction outcome and degraded/short-circuit telemetry
 */
public record RememberResult(
        String memoryId,
        int memoryIndex,
        boolean dedupHit,
        MemoryType type,
        MemorySource source,
        ConductionOutcome outcome
) {
    public RememberResult(String memoryId, int memoryIndex, boolean dedupHit, MemoryType type, MemorySource source) {
        this(memoryId, memoryIndex, dedupHit, type, source, null);
    }

    public static RememberResult skipped() {
        return new RememberResult(null, -1, false, null, null, null);
    }

    public static RememberResult skipped(ConductionOutcome outcome) {
        return new RememberResult(null, -1, false, null, null, outcome);
    }
}
