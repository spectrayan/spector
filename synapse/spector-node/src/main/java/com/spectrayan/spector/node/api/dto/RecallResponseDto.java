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
