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

import com.spectrayan.spector.provider.AbstractProviderFactory;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.langchain4j.LangChain4jEmbeddingAdapter;
import com.spectrayan.spector.provider.langchain4j.LangChain4jGenerationAdapter;
import com.spectrayan.spector.provider.langchain4j.LangChain4jHelper;

import java.util.Map;

import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;

import java.time.Duration;
import java.util.Optional;
import com.spectrayan.spector.commons.ParseUtils;

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
 *
 * <h3>Authentication and Endpoint</h3>
 * <p>The API key is taken from {@link ProviderConfig#apiKey()} and the model from
 * {@link ProviderConfig#model()}. {@link ProviderConfig#baseUrl()} is not applied by this
 * factory, so the LangChain4j Google AI Gemini default endpoint is always used.</p>
 *
 * <h3>Networking</h3>
 * <p>Proxy, mTLS, and HTTP client settings are applied via
 * {@link LangChain4jHelper#resolveHttpClient(ProviderConfig, Duration)} for both embedding and
 * generation. {@code header.*} custom headers are applied to generation only. This factory
 * does not configure retries or fallback.</p>
 *
 * <h3>Embedding Dimensions</h3>
 * <p>If {@link ProviderConfig#dimensions()} is positive it is used as the output
 * dimensionality; otherwise 768 is requested.</p>
 */
public class GoogleProviderFactory extends AbstractProviderFactory {

    /**
     * Creates a factory without a cache manager; embedding providers are returned without caching.
     */
    public GoogleProviderFactory() {
        super();
    }

    /**
     * Creates a factory with the given cache manager.
     *
     * @param cacheManager cache manager used to wrap created embedding providers with caching when
     *                     caching is enabled in the provider configuration; may be {@code null}
     */
    public GoogleProviderFactory(com.spectrayan.spector.commons.cache.SpectorCacheManager cacheManager) {
        super(cacheManager);
    }

    @Override public String name() { return "google"; }
    @Override public String displayName() { return "Google Gemini"; }
    @Override public boolean supportsEmbedding() { return true; }
    @Override public boolean supportsGeneration() { return true; }

    /**
     * Creates a Google Gemini embedding provider.
     *
     * @param config provider configuration supplying the API key, model, optional dimensions,
     *               and the properties listed in the class documentation
     * @return a provider wrapping a {@code GoogleAiEmbeddingModel}; never empty
     */
    @Override
    protected Optional<EmbeddingProvider> createRawEmbeddingProvider(ProviderConfig config) {
        long timeoutSeconds = ParseUtils.parseLongOrDefault(config.property("timeout").orElse(null), 30L);
        int dims = config.dimensions() > 0 ? config.dimensions() : 768;

        var builder = GoogleAiEmbeddingModel.builder()
                .apiKey(config.apiKey())
                .modelName(config.model())
                .outputDimensionality(dims)
                .timeout(Duration.ofSeconds(timeoutSeconds));

        // Apply HTTP client settings (proxy, mTLS)
        dev.langchain4j.http.client.HttpClientBuilder clientBuilder = LangChain4jHelper.resolveHttpClient(
                config, Duration.ofSeconds(timeoutSeconds));
        if (clientBuilder != null) {
            builder.httpClientBuilder(clientBuilder);
        }

        var model = builder.build();

        return Optional.of(new LangChain4jEmbeddingAdapter(model, config.model(), dims));
    }

    /**
     * Creates a Google Gemini text-generation provider.
     *
     * @param config provider configuration supplying the API key, model, and the properties
     *               listed in the class documentation
     * @return a provider wrapping a {@code GoogleAiGeminiChatModel}; never empty
     */
    @Override
    public Optional<LlmProvider> createGenerationProvider(ProviderConfig config) {
        long timeoutSeconds = ParseUtils.parseLongOrDefault(config.property("timeout").orElse(null), 60L);
        var builder = GoogleAiGeminiChatModel.builder()
                .apiKey(config.apiKey())
                .modelName(config.model())
                .timeout(Duration.ofSeconds(timeoutSeconds));

        config.property("temperature")
                .flatMap(ParseUtils::parseDouble)
                .ifPresent(builder::temperature);
        config.property("maxOutputTokens")
                .flatMap(ParseUtils::parseInteger)
                .ifPresent(builder::maxOutputTokens);
        config.property("topP")
                .flatMap(ParseUtils::parseDouble)
                .ifPresent(builder::topP);

        // Apply HTTP client settings (proxy, mTLS)
        dev.langchain4j.http.client.HttpClientBuilder clientBuilderGen = LangChain4jHelper.resolveHttpClient(
                config, Duration.ofSeconds(timeoutSeconds));
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
