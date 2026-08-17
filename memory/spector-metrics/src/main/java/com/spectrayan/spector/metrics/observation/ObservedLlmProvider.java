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
import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.model.LlmRequest;
import com.spectrayan.spector.provider.model.LlmResponse;
import io.micrometer.observation.ObservationRegistry;

import java.util.Map;

/**
 * Decorator for {@link LlmProvider} that adds observability signals using Micrometer.
 */
public final class ObservedLlmProvider extends ObservableComponent implements LlmProvider {

    private final LlmProvider delegate;

    public ObservedLlmProvider(LlmProvider delegate, ObservationRegistry registry, ObservabilityConfig config) {
        super(registry, config);
        this.delegate = delegate;
    }

    @Override
    public LlmResponse generate(LlmRequest request, GenerationOptions options) {
        return withObservation(
                SpectorObservationDocumentation.PIPELINE_LLM,
                Map.of("model", delegate.modelName()),
                () -> delegate.generate(request, options)
        );
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        return withObservation(
                SpectorObservationDocumentation.PIPELINE_LLM,
                Map.of("model", delegate.modelName()),
                () -> delegate.generate(request)
        );
    }

    @Override
    public String generate(String prompt) {
        return withObservation(
                SpectorObservationDocumentation.PIPELINE_LLM,
                Map.of("model", delegate.modelName()),
                () -> delegate.generate(prompt)
        );
    }

    @Override
    public String generate(String prompt, GenerationOptions options) {
        return withObservation(
                SpectorObservationDocumentation.PIPELINE_LLM,
                Map.of("model", delegate.modelName()),
                () -> delegate.generate(prompt, options)
        );
    }

    @Override
    public String generateStructured(String prompt, String jsonSchema) {
        return withObservation(
                SpectorObservationDocumentation.PIPELINE_LLM,
                Map.of("model", delegate.modelName(), "structured", "true"),
                () -> delegate.generateStructured(prompt, jsonSchema)
        );
    }

    @Override
    public String generateStructured(String prompt, String jsonSchema, GenerationOptions options) {
        return withObservation(
                SpectorObservationDocumentation.PIPELINE_LLM,
                Map.of("model", delegate.modelName(), "structured", "true"),
                () -> delegate.generateStructured(prompt, jsonSchema, options)
        );
    }

    @Override
    public String modelName() {
        return delegate.modelName();
    }

    @Override
    public boolean isAvailable() {
        return delegate.isAvailable();
    }
}
