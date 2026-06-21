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
