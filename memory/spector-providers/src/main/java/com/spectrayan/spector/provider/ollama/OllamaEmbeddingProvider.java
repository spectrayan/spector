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

import com.spectrayan.spector.provider.embedding.EmbeddingConfig;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import com.spectrayan.spector.provider.langchain4j.LangChain4jEmbeddingAdapter;
import com.spectrayan.spector.commons.error.SpectorEmbeddingException;
import com.spectrayan.spector.commons.error.ErrorCode;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;

/**
 * Embedding provider backed by a local Ollama server, reusing the core LangChain4j embedding adapter.
 *
 * <p>The underlying LangChain4j model uses the configured timeout (3 minutes if the config has
 * none) and up to 3 retries. When {@link EmbeddingConfig#maxConcurrent()} is positive, concurrent
 * {@link #embed(String)} and {@link #embedBatch(List)} calls are limited to that number.
 * Vector dimensions are learned from the first successful response.</p>
 */
public class OllamaEmbeddingProvider implements EmbeddingProvider {

    private final EmbeddingConfig config;
    private final OllamaEmbeddingModel delegate;
    private final LangChain4jEmbeddingAdapter adapter;
    private final Semaphore concurrencyLimiter;
    private volatile int cachedDimensions = -1;

    /**
     * Creates a provider from the given embedding configuration.
     *
     * @param config configuration supplying the model, base URL, timeout, and maximum concurrency
     * @throws NullPointerException if {@code config} is {@code null}
     */
    public OllamaEmbeddingProvider(EmbeddingConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        Duration timeout = config.timeout() != null ? config.timeout() : Duration.ofMinutes(3);
        this.delegate = OllamaEmbeddingModel.builder()
                .baseUrl(config.baseUrl())
                .modelName(config.model())
                .timeout(timeout)
                .maxRetries(3)
                .build();
        // Uses 1 as a placeholder dimension since OllamaEmbeddingProvider overrides the dimensions() getter
        this.adapter = new LangChain4jEmbeddingAdapter(delegate, config.model(), 1);
        this.concurrencyLimiter = config.maxConcurrent() > 0
                ? new Semaphore(config.maxConcurrent())
                : null;
    }

    /**
     * Creates a provider for the given model using the defaults from {@link EmbeddingConfig#OLLAMA_DEFAULT}.
     *
     * @param model Ollama embedding model name
     * @return a new provider
     */
    public static OllamaEmbeddingProvider create(String model) {
        return new OllamaEmbeddingProvider(EmbeddingConfig.ollama(model));
    }

    /**
     * Creates a provider using {@link EmbeddingConfig#OLLAMA_DEFAULT}
     * ({@code nomic-embed-text} at {@code http://localhost:11434}, 30 second timeout).
     *
     * @return a new provider
     */
    public static OllamaEmbeddingProvider createDefault() {
        return new OllamaEmbeddingProvider(EmbeddingConfig.OLLAMA_DEFAULT);
    }

    /**
     * Returns the configuration this provider was created with.
     *
     * @return the embedding configuration
     */
    public EmbeddingConfig config() {
        return config;
    }

    /**
     * Embeds a single text.
     *
     * @param text the text to embed
     * @return the embedding result
     * @throws SpectorEmbeddingException if the text is null or blank, the thread is interrupted while
     *                                   waiting for a concurrency permit, or the Ollama request fails
     */
    @Override
    public EmbeddingResult embed(String text) {
        if (text == null || text.isBlank()) {
            throw new SpectorEmbeddingException(ErrorCode.EMBEDDING_REQUEST_FAILED, "Prompt text cannot be null or blank");
        }

        if (concurrencyLimiter != null) {
            try {
                concurrencyLimiter.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SpectorEmbeddingException(ErrorCode.EMBEDDING_REQUEST_FAILED, "Interrupted waiting for concurrency limiter", e);
            }
        }

        try {
            var result = adapter.embed(text);
            cachedDimensions = result.vector().length;
            return result;
        } catch (Exception e) {
            throw new SpectorEmbeddingException(ErrorCode.EMBEDDING_UNAVAILABLE, "Ollama server unavailable or error occurred: " + e.getMessage(), e);
        } finally {
            if (concurrencyLimiter != null) {
                concurrencyLimiter.release();
            }
        }
    }

    /**
     * Embeds a batch of texts in a single request.
     *
     * @param texts the texts to embed
     * @return one result per input text, or an empty list if {@code texts} is empty
     * @throws NullPointerException      if {@code texts} is {@code null}
     * @throws SpectorEmbeddingException if the thread is interrupted while waiting for a concurrency
     *                                   permit or the Ollama request fails
     */
    @Override
    public List<EmbeddingResult> embedBatch(List<String> texts) {
        Objects.requireNonNull(texts, "texts must not be null");
        if (texts.isEmpty()) {
            return List.of();
        }

        if (concurrencyLimiter != null) {
            try {
                concurrencyLimiter.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SpectorEmbeddingException(ErrorCode.EMBEDDING_REQUEST_FAILED, "Interrupted waiting for concurrency limiter", e);
            }
        }

        try {
            var results = adapter.embedBatch(texts);
            if (!results.isEmpty()) {
                cachedDimensions = results.getFirst().vector().length;
            }
            return results;
        } catch (Exception e) {
            throw new SpectorEmbeddingException(ErrorCode.EMBEDDING_UNAVAILABLE, "Ollama server unavailable or error occurred: " + e.getMessage(), e);
        } finally {
            if (concurrencyLimiter != null) {
                concurrencyLimiter.release();
            }
        }
    }

    /**
     * Returns the vector dimensions of this model.
     *
     * <p>If no embedding has been produced yet, this sends a probe request to the Ollama server.</p>
     *
     * @return the vector dimensions
     * @throws SpectorEmbeddingException if the probe request fails
     */
    @Override
    public int dimensions() {
        if (cachedDimensions > 0) {
            return cachedDimensions;
        }
        embed("probe");
        return cachedDimensions;
    }

    @Override
    public String modelName() {
        return config.model();
    }

    /** Returns the underlying LangChain4j model for advanced configuration. */
    public dev.langchain4j.model.embedding.EmbeddingModel delegate() {
        return delegate;
    }
}
