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
package com.spectrayan.spector.provider.openai;

import com.spectrayan.spector.embed.EmbeddingProvider;
import com.spectrayan.spector.embed.TextGenerationProvider;
import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.ProviderFactory;
import com.spectrayan.spector.provider.langchain4j.LangChain4jEmbeddingAdapter;
import com.spectrayan.spector.provider.langchain4j.LangChain4jGenerationAdapter;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;

import java.time.Duration;
import java.util.Optional;

/**
 * Factory for creating OpenAI embedding and generation providers.
 *
 * <p>Supports all OpenAI models: text-embedding-3-small, text-embedding-3-large,
 * gpt-4o, gpt-4o-mini, o3, etc.</p>
 *
 * <h3>Configuration Properties</h3>
 * <ul>
 *   <li>{@code timeout} — request timeout in seconds (default: 30 for embed, 60 for gen)</li>
 *   <li>{@code organization} — OpenAI organization ID (optional)</li>
 *   <li>{@code temperature} — sampling temperature (optional)</li>
 *   <li>{@code maxTokens} — maximum output tokens (optional)</li>
 * </ul>
 */
public class OpenAiProviderFactory implements ProviderFactory {

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public String displayName() {
        return "OpenAI";
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
        var builder = OpenAiEmbeddingModel.builder()
                .apiKey(config.apiKey())
                .modelName(config.model())
                .timeout(Duration.ofSeconds(
                        Long.parseLong(config.property("timeout", "30"))));

        if (config.dimensions() > 0) {
            builder.dimensions(config.dimensions());
        }
        if (config.hasBaseUrl()) {
            builder.baseUrl(config.baseUrl());
        }
        config.property("organization").ifPresent(builder::organizationId);

        OpenAiEmbeddingModel model = builder.build();
        int dims = config.dimensions() > 0 ? config.dimensions() : inferDimensions(config.model());

        return Optional.of(new LangChain4jEmbeddingAdapter(model, config.model(), dims));
    }

    @Override
    public Optional<TextGenerationProvider> createGenerationProvider(ProviderConfig config) {
        var builder = OpenAiChatModel.builder()
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
        config.property("organization").ifPresent(builder::organizationId);

        OpenAiChatModel model = builder.build();
        return Optional.of(new LangChain4jGenerationAdapter(model, config.model()));
    }

    /** Infers default dimensions based on known OpenAI model names. */
    private static int inferDimensions(String model) {
        return switch (model) {
            case "text-embedding-3-small" -> 1536;
            case "text-embedding-3-large" -> 3072;
            case "text-embedding-ada-002" -> 1536;
            default -> 1536;
        };
    }
}
