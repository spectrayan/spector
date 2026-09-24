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
package com.spectrayan.spector.batch.exporting;

import com.spectrayan.spector.memory.model.CognitiveRecord;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Data record serialized as JSONL into {@code nodes/chunk-NNNNN.jsonl} bundle members
 * (ADR-0045, memory-portability R1.2).
 */
public record ExportNodeRecord(
        String id,
        String text,
        List<String> tags,
        String tier,
        String source,
        long timestampMs,
        long synapticTags,
        float exactNorm,
        float importance,
        int agentRecallCount,
        int spectorRecallCount,
        short centroidId,
        byte valence,
        byte arousal,
        float storageStrength,
        byte flags,
        byte consolidationFlags,
        Map<String, String> metadata,
        boolean suppressed,
        boolean tombstoned
) {
    public static ExportNodeRecord from(CognitiveRecord record) {
        return new ExportNodeRecord(
                record.id(),
                record.text(),
                record.tags() != null ? Arrays.asList(record.tags()) : List.of(),
                record.memoryType() != null ? record.memoryType().name() : "WORKING",
                record.source() != null ? record.source().name() : "USER_STATED",
                record.timestampMs(),
                record.synapticTags(),
                record.exactNorm(),
                record.importance(),
                record.agentRecallCount(),
                record.spectorRecallCount(),
                record.centroidId(),
                record.valence(),
                record.arousal(),
                record.storageStrength(),
                record.flags(),
                record.consolidationFlags(),
                record.metadata() != null ? record.metadata() : Map.of(),
                record.suppressed(),
                record.isTombstoned()
        );
    }
}
