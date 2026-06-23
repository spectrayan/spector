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
package com.spectrayan.spector.node.api.dto;

import com.spectrayan.spector.memory.model.ReflectReport;

/**
 * Response DTO for {@code POST /memory/reflect}.
 *
 * @param consolidatedCount  number of memories consolidated during the reflect cycle
 * @param tombstonedCount    number of memories tombstoned during the reflect cycle
 * @param compactedPartitions number of storage partitions compacted
 * @param temporalPrunedCount number of temporal links pruned
 * @param durationMs         total duration of the reflect cycle in milliseconds
 */
public record ReflectResponseDto(
        int consolidatedCount,
        int tombstonedCount,
        int compactedPartitions,
        int temporalPrunedCount,
        long durationMs
) {

    /**
     * Creates a response DTO from the domain model.
     */
    public static ReflectResponseDto from(ReflectReport report) {
        return new ReflectResponseDto(
                report.consolidatedCount(),
                report.tombstonedCount(),
                report.compactedPartitions(),
                report.temporalPrunedCount(),
                report.duration().toMillis()
        );
    }
}
