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
package com.spectrayan.spector.provider.langchain4j;

import com.spectrayan.spector.embed.EmbeddingProvider;
import com.spectrayan.spector.embed.EmbeddingResult;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.List;
import java.util.Objects;

/**
 * Adapts a LangChain4j {@link EmbeddingModel} to Spector's {@link EmbeddingProvider} SPI.
 *
 * <p>This bridge allows any LangChain4j-supported embedding model (OpenAI, Cohere, etc.)
 * to be used seamlessly with Spector's ingestion pipeline and cognitive memory.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>Thread-safe if the underlying {@link EmbeddingModel} is thread-safe
 * (all LangChain4j models are documented as thread-safe).</p>
 */
public class LangChain4jEmbeddingAdapter implements EmbeddingProvider {

    private final EmbeddingModel delegate;
    private final String modelName;
    private final int dimensions;

    /**
     * Creates an adapter wrapping a LangChain4j embedding model.
     *
     * @param delegate   the LangChain4j model
     * @param modelName  model identifier for logging/metrics
     * @param dimensions expected vector dimensions
     */
    public LangChain4jEmbeddingAdapter(EmbeddingModel delegate, String modelName, int dimensions) {
        this.delegate = Objects.requireNonNull(delegate, "EmbeddingModel must not be null");
        this.modelName = Objects.requireNonNull(modelName, "modelName must not be null");
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be positive, got: " + dimensions);
        }
        this.dimensions = dimensions;
    }

    @Override
    public EmbeddingResult embed(String text) {
        Objects.requireNonNull(text, "text must not be null");

        Response<dev.langchain4j.data.embedding.Embedding> response = delegate.embed(text);
        float[] vector = response.content().vector();

        int tokens = 0;
        if (response.tokenUsage() != null) {
            tokens = response.tokenUsage().inputTokenCount() != null
                    ? response.tokenUsage().inputTokenCount()
                    : 0;
        }

        return new EmbeddingResult(vector, tokens, modelName);
    }

    @Override
    public List<EmbeddingResult> embedBatch(List<String> texts) {
        Objects.requireNonNull(texts, "texts must not be null");

        List<TextSegment> segments = texts.stream()
                .map(TextSegment::from)
                .toList();

        Response<List<dev.langchain4j.data.embedding.Embedding>> response =
                delegate.embedAll(segments);

        return response.content().stream()
                .map(embedding -> new EmbeddingResult(embedding.vector(), 0, modelName))
                .toList();
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public String modelName() {
        return modelName;
    }

    /** Returns the underlying LangChain4j model for advanced configuration. */
    public EmbeddingModel delegate() {
        return delegate;
    }
}
