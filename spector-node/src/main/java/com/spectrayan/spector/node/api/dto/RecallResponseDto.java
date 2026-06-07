package com.spectrayan.spector.node.api.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.spectrayan.spector.memory.model.CognitiveResult;

/**
 * Response DTO for cognitive recall via the REST API.
 *
 * @param results        scored cognitive results
 * @param totalMemories  total memories count in the subsystem
 * @param queryTimeMs    recall query execution time in milliseconds
 * @param profile        cognitive profile used
 */
public record RecallResponseDto(
        @JsonProperty("results") List<CognitiveResult> results,
        @JsonProperty("totalMemories") int totalMemories,
        @JsonProperty("queryTimeMs") long queryTimeMs,
        @JsonProperty("profile") String profile
) {}
