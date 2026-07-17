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
 */
public class OllamaEmbeddingProvider implements EmbeddingProvider {

    private final EmbeddingConfig config;
    private final OllamaEmbeddingModel delegate;
    private final LangChain4jEmbeddingAdapter adapter;
    private final Semaphore concurrencyLimiter;
    private volatile int cachedDimensions = -1;

    public OllamaEmbeddingProvider(EmbeddingConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.delegate = OllamaEmbeddingModel.builder()
                .baseUrl(config.baseUrl())
                .modelName(config.model())
                .timeout(config.timeout())
                .build();
        // Uses 1 as a placeholder dimension since OllamaEmbeddingProvider overrides the dimensions() getter
        this.adapter = new LangChain4jEmbeddingAdapter(delegate, config.model(), 1);
        this.concurrencyLimiter = config.maxConcurrent() > 0
                ? new Semaphore(config.maxConcurrent())
                : null;
    }

    public static OllamaEmbeddingProvider create(String model) {
        return new OllamaEmbeddingProvider(EmbeddingConfig.ollama(model));
    }

    public static OllamaEmbeddingProvider createDefault() {
        return new OllamaEmbeddingProvider(EmbeddingConfig.OLLAMA_DEFAULT);
    }

    public EmbeddingConfig config() {
        return config;
    }

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
