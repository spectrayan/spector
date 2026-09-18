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
package com.spectrayan.spector.synapse.agent.graph.spec;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Execution policy for a flow — timeouts, max iterations, limits.
 *
 * @param maxIterations   maximum coordinator loop iterations
 * @param totalTimeoutMs  total flow timeout in milliseconds
 * @param stepTimeoutMs   per-step timeout in milliseconds
 * @param maxTokens       maximum total LLM tokens
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExecutionPolicy(
        @JsonProperty("max_iterations") int maxIterations,
        @JsonProperty("total_timeout_ms") int totalTimeoutMs,
        @JsonProperty("step_timeout_ms") int stepTimeoutMs,
        @JsonProperty("max_tokens") int maxTokens
) {
    public ExecutionPolicy {
        if (maxIterations <= 0) maxIterations = 5;
        if (totalTimeoutMs <= 0) totalTimeoutMs = 300_000;
        if (stepTimeoutMs <= 0) stepTimeoutMs = 60_000;
        if (maxTokens <= 0) maxTokens = 10_000;
    }

    public static ExecutionPolicy defaults() {
        return new ExecutionPolicy(5, 300_000, 60_000, 10_000);
    }
}
