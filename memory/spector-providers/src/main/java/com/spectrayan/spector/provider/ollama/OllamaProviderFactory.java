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
package com.spectrayan.spector.provider.ollama;

import com.spectrayan.spector.embed.EmbeddingConfig;
import com.spectrayan.spector.embed.EmbeddingProvider;
import com.spectrayan.spector.embed.TextGenerationProvider;
import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.ProviderFactory;

import java.time.Duration;
import java.util.Optional;

/**
 * Factory for creating Ollama embedding and generation providers.
 *
 * <p>Ollama runs locally and requires no API key. Supports any model
 * pulled into the Ollama server, including embedding models
 * ({@code nomic-embed-text}, {@code mxbai-embed-large}) and generation
 * models ({@code qwen3:0.6b}, {@code llama3.1:8b}, {@code gemma3:4b}).</p>
 *
 * <h3>Configuration Properties</h3>
 * <ul>
 *   <li>{@code timeout} — request timeout in seconds (default: 30 for embed, 60 for generation)</li>
 *   <li>{@code maxConcurrent} — max concurrent embedding requests (default: 0 = unlimited)</li>
 *   <li>{@code batchSize} — batch size for embedding requests (default: 32)</li>
 * </ul>
 */
public class OllamaProviderFactory implements ProviderFactory {

    /** Default Ollama base URL. */
    private static final String DEFAULT_BASE_URL = "http://localhost:11434";

    @Override
    public String name() {
        return "ollama";
    }

    @Override
    public String displayName() {
        return "Ollama";
    }

    @Override
    public boolean supportsEmbedding() {
        return true;
    }

    @Override
    public boolean supportsGeneration() {
        return true;
    }

    @Override
    public Optional<EmbeddingProvider> createEmbeddingProvider(ProviderConfig config) {
        String baseUrl = config.hasBaseUrl() ? config.baseUrl() : DEFAULT_BASE_URL;
        int timeout = Integer.parseInt(config.property("timeout", "30"));
        int maxConcurrent = Integer.parseInt(config.property("maxConcurrent", "0"));
        int batchSize = Integer.parseInt(config.property("batchSize", "32"));

        var embeddingConfig = new EmbeddingConfig(
                config.model(),
                baseUrl,
                Duration.ofSeconds(timeout),
                batchSize,
                maxConcurrent
        );

        return Optional.of(new OllamaEmbeddingProvider(embeddingConfig));
    }

    @Override
    public Optional<TextGenerationProvider> createGenerationProvider(ProviderConfig config) {
        String baseUrl = config.hasBaseUrl() ? config.baseUrl() : DEFAULT_BASE_URL;
        int timeout = Integer.parseInt(config.property("timeout", "60"));

        return Optional.of(new OllamaLlmProvider(
                config.model(),
                baseUrl,
                Duration.ofSeconds(timeout)
        ));
    }
}
