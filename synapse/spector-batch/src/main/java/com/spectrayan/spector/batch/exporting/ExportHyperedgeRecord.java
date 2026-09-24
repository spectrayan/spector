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

import java.util.List;

/**
 * Data record serialized as JSONL into {@code graph/hyperedges.jsonl} representing
 * typed n-ary hyperedges with semantic roles and grounding memory IDs
 * (ADR-0045, memory-portability R1.3).
 *
 * @param edgeId    hyperedge identifier
 * @param type      hyperedge type (e.g. "TYPE_RELATIONSHIP", "TYPE_CONTRADICTS")
 * @param weight    connection weight
 * @param memoryId  grounding memory ID (caller identifier, not internal index)
 * @param timestamp creation timestamp in epoch milliseconds
 * @param vertices  participating entity vertices and their roles
 */
public record ExportHyperedgeRecord(
        int edgeId,
        String type,
        float weight,
        String memoryId,
        long timestamp,
        List<ExportHyperedgeVertex> vertices
) {
    /**
     * Entity vertex participating in a hyperedge with an assigned semantic role.
     *
     * @param entity     entity name string
     * @param entityType entity type string
     * @param role       role name (e.g. "SUBJECT", "OBJECT", "CORRECTOR", "CORRECTED")
     * @param roleId     raw role identifier
     */
    public record ExportHyperedgeVertex(
            String entity,
            String entityType,
            String role,
            int roleId
    ) {}
}
