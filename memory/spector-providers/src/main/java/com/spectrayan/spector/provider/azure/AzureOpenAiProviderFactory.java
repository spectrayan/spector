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
package com.spectrayan.spector.provider.azure;

import com.spectrayan.spector.embed.EmbeddingProvider;
import com.spectrayan.spector.embed.TextGenerationProvider;
import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.ProviderFactory;
import com.spectrayan.spector.provider.langchain4j.LangChain4jEmbeddingAdapter;
import com.spectrayan.spector.provider.langchain4j.LangChain4jGenerationAdapter;

import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.azure.AzureOpenAiEmbeddingModel;

import java.time.Duration;
import java.util.Optional;

/**
 * Factory for creating Azure OpenAI embedding and generation providers.
 *
 * <p>Azure OpenAI requires both an API key and a deployment-specific endpoint.
 * The {@code baseUrl} in config should be the Azure endpoint
 * (e.g., {@code https://my-resource.openai.azure.com/}).</p>
 *
 * <h3>Configuration Properties</h3>
 * <ul>
 *   <li>{@code timeout} — request timeout in seconds (default: 30 for embed, 60 for gen)</li>
 *   <li>{@code deploymentName} — Azure deployment name (required, defaults to model name)</li>
 *   <li>{@code temperature} — sampling temperature (optional)</li>
 *   <li>{@code maxTokens} — maximum output tokens (optional)</li>
 * </ul>
 */
public class AzureOpenAiProviderFactory implements ProviderFactory {

    @Override public String name() { return "azure-openai"; }
    @Override public String displayName() { return "Azure OpenAI"; }
    @Override public boolean supportsEmbedding() { return true; }
    @Override public boolean supportsGeneration() { return true; }

    @Override
    public Optional<EmbeddingProvider> createEmbeddingProvider(ProviderConfig config) {
        String deploymentName = config.property("deploymentName", config.model());

        var builder = AzureOpenAiEmbeddingModel.builder()
                .apiKey(config.apiKey())
                .deploymentName(deploymentName)
                .timeout(Duration.ofSeconds(
                        Long.parseLong(config.property("timeout", "30"))));

        if (config.hasBaseUrl()) {
            builder.endpoint(config.baseUrl());
        }
        if (config.dimensions() > 0) {
            builder.dimensions(config.dimensions());
        }

        AzureOpenAiEmbeddingModel model = builder.build();
        int dims = config.dimensions() > 0 ? config.dimensions() : 1536;
        return Optional.of(new LangChain4jEmbeddingAdapter(model, config.model(), dims));
    }

    @Override
    public Optional<TextGenerationProvider> createGenerationProvider(ProviderConfig config) {
        String deploymentName = config.property("deploymentName", config.model());

        var builder = AzureOpenAiChatModel.builder()
                .apiKey(config.apiKey())
                .deploymentName(deploymentName)
                .timeout(Duration.ofSeconds(
                        Long.parseLong(config.property("timeout", "60"))));

        if (config.hasBaseUrl()) {
            builder.endpoint(config.baseUrl());
        }
        config.property("temperature").ifPresent(t ->
                builder.temperature(Double.parseDouble(t)));
        config.property("maxTokens").ifPresent(t ->
                builder.maxTokens(Integer.parseInt(t)));

        return Optional.of(new LangChain4jGenerationAdapter(builder.build(), config.model()));
    }
}
