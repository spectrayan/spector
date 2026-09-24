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

/**
 * Data record serialized as JSONL into {@code graph/edges.jsonl} representing
 * associative Hebbian or cognitive links between memories by stable memory IDs
 * (ADR-0045, memory-portability R1.3).
 *
 * @param sourceId    source memory ID (caller identifier, not slot index)
 * @param targetId    target memory ID (caller identifier, not slot index)
 * @param kind        relationship kind (e.g. "HEBBIAN")
 * @param weight      edge association strength
 * @param bridgeScore topological bridge importance score
 */
public record ExportEdgeRecord(
        String sourceId,
        String targetId,
        String kind,
        float weight,
        int bridgeScore
) {
    public ExportEdgeRecord(String sourceId, String targetId, String kind, float weight) {
        this(sourceId, targetId, kind, weight, 0);
    }
}
