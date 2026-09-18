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

import java.util.List;

/**
 * Entity/relation type aggregations for topology visualization.
 *
 * <p>Returned by {@link com.spectrayan.spector.memory.graph.CognitiveGraphFacade#topologyStats()}
 * to summarize the structure of the entity-relationship graph.</p>
 *
 * @param entityTypes   per-type stats for entities
 * @param relationTypes per-type stats for relations
 * @since 1.1.0
 */
public record TopologyStats(
        List<EntityTypeStats> entityTypes,
        List<RelationTypeStats> relationTypes
) {

    /** Returns empty topology stats. */
    public static TopologyStats empty() {
        return new TopologyStats(List.of(), List.of());
    }

    /**
     * Statistics for a single entity type.
     *
     * @param type           entity type name (e.g., "PERSON", "ORGANIZATION")
     * @param nodeCount      total entities of this type
     * @param edgeCount      total edges from entities of this type
     * @param memoryRefCount total memory references from entities of this type
     */
    public record EntityTypeStats(String type, int nodeCount, int edgeCount, int memoryRefCount) {}

    /**
     * Statistics for a single relation type.
     *
     * @param type           relation type name (e.g., "WORKS_FOR", "LOCATED_IN")
     * @param edgeCount      total edges of this relation type
     * @param nodeCount      total nodes involved in this relation type
     * @param memoryRefCount total memory references from edges of this relation type
     */
    public record RelationTypeStats(String type, int edgeCount, int nodeCount, int memoryRefCount) {}
}
