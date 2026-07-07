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

import java.util.Optional;

/**
 * Factory SPI for creating provider instances from configuration.
 *
 * <p>Each provider module (Ollama, OpenAI, Anthropic, etc.) implements this interface
 * and registers via {@link java.util.ServiceLoader}. The Synapse runtime discovers
 * all available factories at startup and uses them to create provider instances
 * from user configuration.</p>
 *
 * <h3>ServiceLoader Registration</h3>
 * <p>Provider modules must include a file at:
 * {@code META-INF/services/com.spectrayan.spector.synapse.provider.ProviderFactory}</p>
 */
public interface ProviderFactory {

    /**
     * Unique name identifying this factory (e.g., "openai", "anthropic", "ollama").
     *
     * <p>Must match the provider name used in configuration files.</p>
     */
    String name();

    /**
     * Human-readable display name (e.g., "OpenAI", "Anthropic Claude").
     */
    String displayName();

    /**
     * Whether this factory can produce embedding providers.
     */
    boolean supportsEmbedding();

    /**
     * Whether this factory can produce generation providers.
     */
    boolean supportsGeneration();

    /**
     * Creates an embedding provider from the given configuration.
     *
     * @param config provider configuration
     * @return the provider, or empty if this factory doesn't support embedding
     */
    Optional<EmbeddingProvider> createEmbeddingProvider(ProviderConfig config);

    /**
     * Creates a text generation provider from the given configuration.
     *
     * @param config provider configuration
     * @return the provider, or empty if this factory doesn't support generation
     */
    Optional<TextGenerationProvider> createGenerationProvider(ProviderConfig config);
}
