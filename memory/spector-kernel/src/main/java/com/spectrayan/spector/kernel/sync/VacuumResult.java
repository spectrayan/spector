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
package com.spectrayan.spector.kernel.sync;

import com.spectrayan.spector.kernel.api.MemoryType;
import java.util.Map;

/**
 * Result metrics for a completed memory vacuum operation (R9.1).
 */
public record VacuumResult(
        MemoryType tier,
        int recordsScanned,
        int recordsCompacted,
        int tombstonedRecords,
        long bytesReclaimed,
        long durationNanos,
        boolean success,
        Map<String, Long> relocatedOffsets
) {
    public VacuumResult(int recordsScanned, int recordsCompacted, long bytesReclaimed,
                        long durationNanos, boolean success, Map<String, Long> relocatedOffsets) {
        this(MemoryType.SEMANTIC, recordsScanned, recordsCompacted,
             recordsScanned - recordsCompacted, bytesReclaimed, durationNanos, success, relocatedOffsets);
    }

    public static VacuumResult empty() {
        return new VacuumResult(MemoryType.SEMANTIC, 0, 0, 0, 0L, 0L, true, Map.of());
    }
}
