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
package com.spectrayan.spector.metrics.observation;

import com.spectrayan.spector.config.ObservabilityConfig;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import io.micrometer.observation.ObservationRegistry;

import java.util.List;
import java.util.Map;

/**
 * Decorator for {@link EmbeddingProvider} that adds observability signals using Micrometer.
 */
public final class ObservedEmbeddingProvider extends ObservableComponent implements EmbeddingProvider {

    private final EmbeddingProvider delegate;

    public ObservedEmbeddingProvider(EmbeddingProvider delegate, ObservationRegistry registry, ObservabilityConfig config) {
        super(registry, config);
        this.delegate = delegate;
    }

    @Override
    public EmbeddingResult embed(String text) {
        return withObservation(
                SpectorObservationDocumentation.PIPELINE_EMBEDDING,
                Map.of("model", delegate.modelName()),
                () -> delegate.embed(text)
        );
    }

    @Override
    public List<EmbeddingResult> embedBatch(List<String> texts) {
        return withObservation(
                SpectorObservationDocumentation.PIPELINE_EMBEDDING,
                Map.of("model", delegate.modelName(), "batch_size", String.valueOf(texts.size())),
                () -> delegate.embedBatch(texts)
        );
    }

    @Override
    public int dimensions() {
        return delegate.dimensions();
    }

    @Override
    public String modelName() {
        return delegate.modelName();
    }

    @Override
    public int maxTokens() {
        return delegate.maxTokens();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
