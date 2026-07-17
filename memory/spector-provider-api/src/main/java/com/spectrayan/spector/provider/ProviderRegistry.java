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
import java.util.Set;

/**
 * Registry for managing multiple embedding and text generation providers.
 *
 * <p>The registry acts as a facade over all registered providers, allowing
 * the system to switch between providers at runtime without code changes.
 * Each provider is registered under a unique name and one provider of each
 * type (embedding, generation) can be active at a time.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>Implementations must be thread-safe. Provider activation and health
 * checks may be called concurrently from different threads.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   registry.registerEmbedding("openai", openAiEmbedding);
 *   registry.registerEmbedding("ollama", ollamaEmbedding);
 *   registry.activateEmbedding("openai");
 *
 *   EmbeddingProvider active = registry.activeEmbedding().orElseThrow();
 *   EmbeddingResult result = active.embed("Hello world");
 * }</pre>
 *
 * @see ProviderFactory
 * @see ProviderConfig
 */
public interface ProviderRegistry extends AutoCloseable {

    /**
     * Registers an embedding provider under the given name.
     *
     * @param name     unique provider name
     * @param provider the embedding provider instance
     * @throws IllegalArgumentException if name is null, blank, or already registered
     */
    void registerEmbedding(String name, EmbeddingProvider provider);

    /**
     * Registers a text generation provider under the given name.
     *
     * @param name     unique provider name
     * @param provider the generation provider instance
     * @throws IllegalArgumentException if name is null, blank, or already registered
     */
    void registerGeneration(String name, TextGenerationProvider provider);

    /**
     * Returns the currently active embedding provider.
     *
     * @return the active embedding provider, or empty if none is activated
     */
    Optional<EmbeddingProvider> activeEmbedding();

    /**
     * Returns the currently active text generation provider.
     *
     * @return the active generation provider, or empty if none is activated
     */
    Optional<TextGenerationProvider> activeGeneration();

    /**
     * Activates the embedding provider with the given name.
     *
     * @param name the registered provider name
     * @throws IllegalArgumentException if no embedding provider is registered under that name
     */
    void activateEmbedding(String name);

    /**
     * Activates the text generation provider with the given name.
     *
     * @param name the registered provider name
     * @throws IllegalArgumentException if no generation provider is registered under that name
     */
    void activateGeneration(String name);

    /**
     * Returns the names of all registered embedding providers.
     *
     * @return unmodifiable set of registered embedding provider names
     */
    Set<String> embeddingNames();

    /**
     * Returns the names of all registered generation providers.
     *
     * @return unmodifiable set of registered generation provider names
     */
    Set<String> generationNames();

    /**
     * Performs a health check on the provider with the given name.
     *
     * <p>Checks both embedding and generation registrations for the name.</p>
     *
     * @param name the registered provider name
     * @return health check result
     */
    ProviderHealth health(String name);
}
