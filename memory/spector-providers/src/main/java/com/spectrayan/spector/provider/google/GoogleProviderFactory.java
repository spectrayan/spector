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
package com.spectrayan.spector.provider.google;

import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.ProviderFactory;
import com.spectrayan.spector.provider.langchain4j.LangChain4jEmbeddingAdapter;
import com.spectrayan.spector.provider.langchain4j.LangChain4jGenerationAdapter;
import com.spectrayan.spector.provider.langchain4j.LangChain4jHelper;

import java.util.Map;

import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;

import java.time.Duration;
import java.util.Optional;

/**
 * Factory for creating Google AI (Gemini) embedding and generation providers.
 *
 * <p>Supports Gemini models for both embedding (text-embedding-004) and
 * generation (gemini-2.0-flash, gemini-2.5-pro, etc.).</p>
 *
 * <h3>Configuration Properties</h3>
 * <ul>
 *   <li>{@code timeout} — request timeout in seconds (default: 30 for embed, 60 for gen)</li>
 *   <li>{@code temperature} — sampling temperature (optional)</li>
 *   <li>{@code maxOutputTokens} — maximum output tokens (optional)</li>
 *   <li>{@code topP} — nucleus sampling probability (optional)</li>
 * </ul>
 */
public class GoogleProviderFactory implements ProviderFactory {

    @Override public String name() { return "google"; }
    @Override public String displayName() { return "Google Gemini"; }
    @Override public boolean supportsEmbedding() { return true; }
    @Override public boolean supportsGeneration() { return true; }

    @Override
    public Optional<EmbeddingProvider> createEmbeddingProvider(ProviderConfig config) {
        var builder = GoogleAiEmbeddingModel.builder()
                .apiKey(config.apiKey())
                .modelName(config.model())
                .timeout(Duration.ofSeconds(
                        Long.parseLong(config.property("timeout", "30"))));

        // Apply HTTP client settings (proxy, mTLS)
        dev.langchain4j.http.client.HttpClientBuilder clientBuilder = LangChain4jHelper.resolveHttpClient(
                config, Duration.ofSeconds(Long.parseLong(config.property("timeout", "30"))));
        if (clientBuilder != null) {
            builder.httpClientBuilder(clientBuilder);
        }

        var model = builder.build();

        int dims = config.dimensions() > 0 ? config.dimensions() : 768;
        return Optional.of(new LangChain4jEmbeddingAdapter(model, config.model(), dims));
    }

    @Override
    public Optional<LlmProvider> createGenerationProvider(ProviderConfig config) {
        var builder = GoogleAiGeminiChatModel.builder()
                .apiKey(config.apiKey())
                .modelName(config.model())
                .timeout(Duration.ofSeconds(
                        Long.parseLong(config.property("timeout", "60"))));

        config.property("temperature").ifPresent(t ->
                builder.temperature(Double.parseDouble(t)));
        config.property("maxOutputTokens").ifPresent(t ->
                builder.maxOutputTokens(Integer.parseInt(t)));
        config.property("topP").ifPresent(t ->
                builder.topP(Double.parseDouble(t)));

        // Apply HTTP client settings (proxy, mTLS)
        dev.langchain4j.http.client.HttpClientBuilder clientBuilderGen = LangChain4jHelper.resolveHttpClient(
                config, Duration.ofSeconds(Long.parseLong(config.property("timeout", "60"))));
        if (clientBuilderGen != null) {
            builder.httpClientBuilder(clientBuilderGen);
        }

        // Apply custom headers
        Map<String, String> headers = LangChain4jHelper.resolveCustomHeaders(config);
        if (!headers.isEmpty()) {
            builder.customHeaders(headers);
        }

        return Optional.of(new LangChain4jGenerationAdapter(builder.build(), config.model()));
    }
}
