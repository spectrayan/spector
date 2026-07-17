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
package com.spectrayan.spector.provider.mistral;

import com.spectrayan.spector.embed.EmbeddingProvider;
import com.spectrayan.spector.embed.TextGenerationProvider;
import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.ProviderFactory;
import com.spectrayan.spector.provider.langchain4j.LangChain4jEmbeddingAdapter;
import com.spectrayan.spector.provider.langchain4j.LangChain4jGenerationAdapter;

import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.mistralai.MistralAiEmbeddingModel;

import java.time.Duration;
import java.util.Optional;

/**
 * Factory for creating Mistral AI embedding and generation providers.
 *
 * <p>Supports Mistral models for both embedding (mistral-embed) and
 * generation (mistral-large-latest, mistral-small-latest, etc.).</p>
 *
 * <h3>Configuration Properties</h3>
 * <ul>
 *   <li>{@code timeout} — request timeout in seconds (default: 30 for embed, 60 for gen)</li>
 *   <li>{@code temperature} — sampling temperature (optional)</li>
 *   <li>{@code maxTokens} — maximum output tokens (optional)</li>
 *   <li>{@code topP} — nucleus sampling probability (optional)</li>
 * </ul>
 */
public class MistralProviderFactory implements ProviderFactory {

    @Override public String name() { return "mistral"; }
    @Override public String displayName() { return "Mistral AI"; }
    @Override public boolean supportsEmbedding() { return true; }
    @Override public boolean supportsGeneration() { return true; }

    @Override
    public Optional<EmbeddingProvider> createEmbeddingProvider(ProviderConfig config) {
        var builder = MistralAiEmbeddingModel.builder()
                .apiKey(config.apiKey())
                .modelName(config.model())
                .timeout(Duration.ofSeconds(
                        Long.parseLong(config.property("timeout", "30"))));

        if (config.hasBaseUrl()) {
            builder.baseUrl(config.baseUrl());
        }

        MistralAiEmbeddingModel model = builder.build();
        int dims = config.dimensions() > 0 ? config.dimensions() : 1024;
        return Optional.of(new LangChain4jEmbeddingAdapter(model, config.model(), dims));
    }

    @Override
    public Optional<TextGenerationProvider> createGenerationProvider(ProviderConfig config) {
        var builder = MistralAiChatModel.builder()
                .apiKey(config.apiKey())
                .modelName(config.model())
                .timeout(Duration.ofSeconds(
                        Long.parseLong(config.property("timeout", "60"))));

        if (config.hasBaseUrl()) {
            builder.baseUrl(config.baseUrl());
        }
        config.property("temperature").ifPresent(t ->
                builder.temperature(Double.parseDouble(t)));
        config.property("maxTokens").ifPresent(t ->
                builder.maxTokens(Integer.parseInt(t)));
        config.property("topP").ifPresent(t ->
                builder.topP(Double.parseDouble(t)));

        return Optional.of(new LangChain4jGenerationAdapter(builder.build(), config.model()));
    }
}
