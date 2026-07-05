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

import java.util.Map;

/**
 * LLM configuration for an agent.
 *
 * @param provider    provider name (ollama, openai, anthropic, google)
 * @param model       model identifier
 * @param temperature sampling temperature
 * @param maxTokens   maximum tokens in response
 * @param params      provider-specific extra parameters
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmSpec(
        String provider,
        String model,
        double temperature,
        @JsonProperty("max_tokens") int maxTokens,
        Map<String, Object> params
) {
    public LlmSpec {
        if (provider == null) provider = "ollama";
        if (model == null) model = "qwen3.5:latest";
        if (maxTokens <= 0) maxTokens = 2048;
        if (params == null) params = Map.of();
    }

    /** Default Ollama config for local testing. */
    public static LlmSpec ollamaDefault() {
        return new LlmSpec("ollama", "qwen3.5:latest", 0.1, 2048, Map.of());
    }
}
