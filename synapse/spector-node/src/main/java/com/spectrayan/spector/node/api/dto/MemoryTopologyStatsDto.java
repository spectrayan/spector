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

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for entity and relationship topology statistics.
 */
public record MemoryTopologyStatsDto(
        @JsonProperty("entityTypes") List<EntityTypeStatsDto> entityTypes,
        @JsonProperty("relationTypes") List<RelationTypeStatsDto> relationTypes
) {
    /**
     * Stats for a single entity type.
     */
    public record EntityTypeStatsDto(
            @JsonProperty("type") String type,
            @JsonProperty("nodes") int nodes,
            @JsonProperty("edges") int edges,
            @JsonProperty("memories") int memories
    ) {}

    /**
     * Stats for a single relationship type.
     */
    public record RelationTypeStatsDto(
            @JsonProperty("type") String type,
            @JsonProperty("edges") int edges,
            @JsonProperty("nodes") int nodes,
            @JsonProperty("memories") int memories
    ) {}
}
