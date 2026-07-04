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

import com.spectrayan.spector.embed.EmbeddingProvider;
import com.spectrayan.spector.embed.TextGenerationProvider;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Registry for managing multiple LLM and embedding providers.
 *
 * <p>Maintains a map of named provider instances and supports switching
 * the active provider at runtime.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>All methods must be safe for concurrent access. Switching the active
 * provider is atomic — in-flight requests complete with the previous provider.</p>
 */
public interface ProviderRegistry {

    // ─────────────── Embedding Providers ───────────────

    /** Registers an embedding provider under the given name. */
    void registerEmbedding(String name, EmbeddingProvider provider);

    /** Returns the currently active embedding provider. */
    Optional<EmbeddingProvider> activeEmbedding();

    /** Switches the active embedding provider. */
    void activateEmbedding(String name);

    /** Returns all registered embedding provider names. */
    Set<String> embeddingProviderNames();

    /** Returns the name of the currently active embedding provider. */
    Optional<String> activeEmbeddingName();

    // ─────────────── Generation Providers ───────────────

    /** Registers a text generation provider under the given name. */
    void registerGeneration(String name, TextGenerationProvider provider);

    /** Returns the currently active generation provider. */
    Optional<TextGenerationProvider> activeGeneration();

    /** Switches the active generation provider. */
    void activateGeneration(String name);

    /** Returns all registered generation provider names. */
    Set<String> generationProviderNames();

    /** Returns the name of the currently active generation provider. */
    Optional<String> activeGenerationName();

    // ─────────────── Health ───────────────

    /** Tests connectivity for a named embedding provider. */
    ProviderHealth checkEmbeddingHealth(String name);

    /** Tests connectivity for a named generation provider. */
    ProviderHealth checkGenerationHealth(String name);

    /** Returns health status for all registered providers. */
    Map<String, ProviderHealth> allHealth();
}
