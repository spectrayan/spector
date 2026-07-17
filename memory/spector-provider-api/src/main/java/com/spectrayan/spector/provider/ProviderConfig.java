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
package com.spectrayan.spector.provider;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable configuration for instantiating a provider.
 *
 * <p>Used by {@link ProviderFactory} to create embedding and/or generation
 * provider instances with the correct API keys, model names, and endpoints.</p>
 *
 * <h3>Required Fields</h3>
 * <ul>
 *   <li>{@code name} — unique provider instance name (e.g., "openai-prod")</li>
 *   <li>{@code type} — provider type identifier (e.g., "openai", "ollama")</li>
 * </ul>
 *
 * <h3>Optional Fields</h3>
 * <ul>
 *   <li>{@code model} — model identifier (e.g., "text-embedding-ada-002")</li>
 *   <li>{@code apiKey} — API key for authentication</li>
 *   <li>{@code baseUrl} — API base URL (overrides default)</li>
 *   <li>{@code dimensions} — embedding vector dimensions (0 = model default)</li>
 *   <li>{@code properties} — additional provider-specific key-value pairs</li>
 * </ul>
 *
 * @param name       unique provider instance name
 * @param type       provider type identifier (matches {@link ProviderFactory#name()})
 * @param model      model identifier
 * @param apiKey     API key for authentication (may be null for local providers)
 * @param baseUrl    API base URL (may be null for cloud providers)
 * @param dimensions embedding vector dimensions (0 = use model default)
 * @param properties additional provider-specific configuration
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

    /**
     * Compact constructor — validates required fields and defensively copies properties.
     */
    public ProviderConfig {
        Objects.requireNonNull(name, "Provider config name must not be null");
        Objects.requireNonNull(type, "Provider config type must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Provider config name must not be blank");
        }
        if (type.isBlank()) {
            throw new IllegalArgumentException("Provider config type must not be blank");
        }
        if (dimensions < 0) {
            throw new IllegalArgumentException("Dimensions must be >= 0, got: " + dimensions);
        }
        // Defensive copy: ensure immutability
        properties = properties == null ? Map.of() : Map.copyOf(properties);
        // Normalize nulls to empty strings for optional fields
        model = model == null ? "" : model;
        apiKey = apiKey == null ? "" : apiKey;
        baseUrl = baseUrl == null ? "" : baseUrl;
    }

    /**
     * Creates a minimal config for local providers (no API key, default dimensions).
     *
     * @param name    provider instance name
     * @param type    provider type
     * @param model   model identifier
     * @param baseUrl API base URL
     * @return a new {@link ProviderConfig}
     */
    public static ProviderConfig local(String name, String type, String model, String baseUrl) {
        return new ProviderConfig(name, type, model, "", baseUrl, 0, Map.of());
    }

    /**
     * Creates a config for cloud providers with an API key.
     *
     * @param name   provider instance name
     * @param type   provider type
     * @param model  model identifier
     * @param apiKey API key
     * @return a new {@link ProviderConfig}
     */
    public static ProviderConfig cloud(String name, String type, String model, String apiKey) {
        return new ProviderConfig(name, type, model, apiKey, "", 0, Map.of());
    }

    /**
     * Returns a property value by key.
     *
     * @param key the property key
     * @return the value, or empty if not present
     */
    public Optional<String> property(String key) {
        return Optional.ofNullable(properties.get(key));
    }

    /**
     * Returns a property value or a default.
     *
     * @param key          the property key
     * @param defaultValue fallback if key is absent
     * @return the value, or {@code defaultValue}
     */
    public String property(String key, String defaultValue) {
        return properties.getOrDefault(key, defaultValue);
    }

    /**
     * Returns {@code true} if an API key is set (non-null, non-blank).
     */
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Returns {@code true} if a custom base URL is set (non-null, non-blank).
     */
    public boolean hasBaseUrl() {
        return baseUrl != null && !baseUrl.isBlank();
    }
}
