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
package com.spectrayan.spector.embed;

import java.time.Duration;

/**
 * Configuration for an embedding provider.
 *
 * @param model         the embedding model name (e.g., "nomic-embed-text")
 * @param baseUrl       the API base URL (e.g., "http://localhost:11434")
 * @param timeout       HTTP request timeout
 * @param batchSize     maximum texts per batch request
 * @param maxConcurrent maximum concurrent HTTP calls (0 = unlimited)
 */
public record EmbeddingConfig(
        String model,
        String baseUrl,
        Duration timeout,
        int batchSize,
        int maxConcurrent
) {
    /** Default Ollama configuration. */
    public static final EmbeddingConfig OLLAMA_DEFAULT = new EmbeddingConfig(
            "nomic-embed-text",
            "http://localhost:11434",
            Duration.ofSeconds(30),
            32,
            0
    );

    /**
     * Creates a config with the given model and default Ollama settings.
     */
    public static EmbeddingConfig ollama(String model) {
        return new EmbeddingConfig(model, OLLAMA_DEFAULT.baseUrl, OLLAMA_DEFAULT.timeout,
                OLLAMA_DEFAULT.batchSize, OLLAMA_DEFAULT.maxConcurrent);
    }

    /**
     * Returns a new config with a different base URL.
     */
    public EmbeddingConfig withBaseUrl(String baseUrl) {
        return new EmbeddingConfig(model, baseUrl, timeout, batchSize, maxConcurrent);
    }

    /**
     * Returns a new config with a different timeout.
     */
    public EmbeddingConfig withTimeout(Duration timeout) {
        return new EmbeddingConfig(model, baseUrl, timeout, batchSize, maxConcurrent);
    }

    /**
     * Returns a new config with a different batch size.
     */
    public EmbeddingConfig withBatchSize(int batchSize) {
        return new EmbeddingConfig(model, baseUrl, timeout, batchSize, maxConcurrent);
    }

    /**
     * Returns a new config with a different max concurrent limit.
     *
     * @param maxConcurrent max concurrent HTTP calls (0 = unlimited)
     */
    public EmbeddingConfig withMaxConcurrent(int maxConcurrent) {
        return new EmbeddingConfig(model, baseUrl, timeout, batchSize, maxConcurrent);
    }
}

