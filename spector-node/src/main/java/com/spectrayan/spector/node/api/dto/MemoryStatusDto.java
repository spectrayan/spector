package com.spectrayan.spector.node.api.dto;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for cognitive memory status and health statistics.
 *
 * @param totalMemories  total memory count across all tiers
 * @param tierCounts     breakdown of memories by tier (WORKING, EPISODIC, SEMANTIC, PROCEDURAL)
 * @param hebbianEdges   number of active statistical associations in the Hebbian co-activation graph
 * @param temporalLinks  number of sequence associations in the temporal causal chain
 * @param entityNodes    number of entity nodes in the semantic relationship graph
 * @param entityEdges    number of semantic connections between entities
 */
public record MemoryStatusDto(
        @JsonProperty("totalMemories") int totalMemories,
        @JsonProperty("tierCounts") Map<String, Integer> tierCounts,
        @JsonProperty("hebbianEdges") int hebbianEdges,
        @JsonProperty("temporalLinks") int temporalLinks,
        @JsonProperty("entityNodes") int entityNodes,
        @JsonProperty("entityEdges") int entityEdges
) {}
