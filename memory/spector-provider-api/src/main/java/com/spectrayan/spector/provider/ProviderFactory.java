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

import com.spectrayan.spector.embed.EmbeddingProvider;
import com.spectrayan.spector.embed.TextGenerationProvider;

import java.util.Optional;

/**
 * Service Provider Interface for discovering and creating LLM providers.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader} and registered
 * in {@code META-INF/services/com.spectrayan.spector.provider.ProviderFactory}.
 * Each factory represents a single provider backend (e.g., OpenAI, Anthropic, Ollama)
 * and can create embedding providers, generation providers, or both.</p>
 *
 * <h3>ServiceLoader Discovery</h3>
 * <pre>{@code
 *   ServiceLoader<ProviderFactory> loader = ServiceLoader.load(ProviderFactory.class);
 *   for (ProviderFactory factory : loader) {
 *       if (factory.supportsEmbedding()) {
 *           EmbeddingProvider ep = factory.createEmbeddingProvider(config).orElseThrow();
 *       }
 *   }
 * }</pre>
 *
 * <h3>Implementation Requirements</h3>
 * <ul>
 *   <li>{@link #name()} must return a unique, lowercase identifier (e.g., "openai")</li>
 *   <li>{@link #displayName()} returns a human-readable label (e.g., "OpenAI")</li>
 *   <li>At least one of {@link #supportsEmbedding()} or {@link #supportsGeneration()} must return {@code true}</li>
 *   <li>Implementations must be thread-safe and have a public no-arg constructor</li>
 * </ul>
 *
 * @see ProviderConfig
 * @see ProviderRegistry
 */
public interface ProviderFactory {

    /**
     * Returns the unique provider identifier.
     *
     * <p>This must be a lowercase, alphanumeric string suitable for use in
     * configuration files and CLI arguments (e.g., "openai", "anthropic", "ollama").</p>
     *
     * @return provider identifier (never null or blank)
     */
    String name();

    /**
     * Returns a human-readable display name.
     *
     * @return display name (e.g., "OpenAI", "Anthropic Claude", "Ollama")
     */
    String displayName();

    /**
     * Returns whether this factory can create embedding providers.
     *
     * @return {@code true} if {@link #createEmbeddingProvider(ProviderConfig)} can return a provider
     */
    boolean supportsEmbedding();

    /**
     * Returns whether this factory can create text generation providers.
     *
     * @return {@code true} if {@link #createGenerationProvider(ProviderConfig)} can return a provider
     */
    boolean supportsGeneration();

    /**
     * Creates an embedding provider with the given configuration.
     *
     * @param config provider configuration
     * @return an embedding provider, or empty if embedding is not supported or config is invalid
     * @throws IllegalArgumentException if config is invalid for this provider type
     */
    Optional<EmbeddingProvider> createEmbeddingProvider(ProviderConfig config);

    /**
     * Creates a text generation provider with the given configuration.
     *
     * @param config provider configuration
     * @return a generation provider, or empty if generation is not supported or config is invalid
     * @throws IllegalArgumentException if config is invalid for this provider type
     */
    Optional<TextGenerationProvider> createGenerationProvider(ProviderConfig config);
}
