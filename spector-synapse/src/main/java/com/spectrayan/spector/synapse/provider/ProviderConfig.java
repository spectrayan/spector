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
package com.spectrayan.spector.synapse.provider;

import java.util.Map;
import java.util.Optional;

/**
 * Configuration for an LLM or embedding provider.
 *
 * <p>Parsed from YAML config and used by provider factories to create instances.</p>
 *
 * @param name       provider name (e.g., "openai", "anthropic", "ollama")
 * @param type       provider type (e.g., "embedding", "generation")
 * @param model      model identifier (e.g., "llama3.2", "nomic-embed-text")
 * @param apiKey     API key (nullable — some providers like Ollama don't need one)
 * @param baseUrl    base URL override (nullable — uses provider default)
 * @param dimensions embedding dimensions (0 if not applicable)
 * @param properties additional provider-specific properties
 */
public record ProviderConfig(
        String name,
        String type,
        String model,
        String apiKey,
        String baseUrl,
        int dimensions,
        Map<String, String> properties
) {
    public ProviderConfig {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Provider name must not be blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Provider model must not be blank");
        }
        if (properties == null) {
            properties = Map.of();
        }
    }

    /** Returns a property value by key. */
    public Optional<String> property(String key) {
        return Optional.ofNullable(properties.get(key));
    }

    /** Returns a property value or default. */
    public String property(String key, String defaultValue) {
        return properties.getOrDefault(key, defaultValue);
    }

    /** Returns true if an API key is set. */
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Returns true if a custom base URL is set. */
    public boolean hasBaseUrl() {
        return baseUrl != null && !baseUrl.isBlank();
    }
}
