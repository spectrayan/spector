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

import com.spectrayan.spector.provider.AbstractProviderFactory;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.langchain4j.LangChain4jEmbeddingAdapter;
import com.spectrayan.spector.provider.langchain4j.LangChain4jGenerationAdapter;
import com.spectrayan.spector.provider.langchain4j.LangChain4jHelper;

import java.util.Map;

import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.mistralai.MistralAiEmbeddingModel;

import java.time.Duration;
import java.util.Optional;
import com.spectrayan.spector.commons.ParseUtils;

/**
 * Factory for creating Mistral AI embedding and generation providers.
 *
 * <p>Supports Mistral models: mistral-embed, mistral-small-latest,
 * mistral-medium-latest, mistral-large-latest, open-mistral-7b, etc.</p>
 *
 * <h3>Configuration Properties</h3>
 * <ul>
 *   <li>{@code timeout} — request timeout in seconds (default: 30 for embed, 60 for gen)</li>
 *   <li>{@code temperature} — sampling temperature (optional)</li>
 *   <li>{@code maxTokens} — maximum output tokens (optional)</li>
 *   <li>{@code topP} — nucleus sampling probability (optional)</li>
 * </ul>
 *
 * <h3>Authentication and Endpoint</h3>
 * <p>The API key is taken from {@link ProviderConfig#apiKey()} and the model from
 * {@link ProviderConfig#model()}. When {@link ProviderConfig#baseUrl()} is set it overrides
 * the endpoint; otherwise the LangChain4j Mistral AI default is used.</p>
 *
 * <h3>Networking</h3>
 * <p>Proxy, mTLS, and HTTP client settings are applied via
 * {@link LangChain4jHelper#resolveHttpClient(ProviderConfig, Duration)}, and {@code header.*}
 * properties are sent as custom headers. This factory does not configure retries or fallback.</p>
 *
 * <h3>Embedding Dimensions</h3>
 * <p>The provider reports {@link ProviderConfig#dimensions()} when positive, otherwise 1024.
 * The value is not sent to Mistral.</p>
 */
public class MistralProviderFactory extends AbstractProviderFactory {

    /**
     * Creates a factory without a cache manager; embedding providers are returned without caching.
     */
    public MistralProviderFactory() {
        super();
    }

    /**
     * Creates a factory with the given cache manager.
     *
     * @param cacheManager cache manager used to wrap created embedding providers with caching when
     *                     caching is enabled in the provider configuration; may be {@code null}
     */
    public MistralProviderFactory(com.spectrayan.spector.commons.cache.SpectorCacheManager cacheManager) {
        super(cacheManager);
    }

    @Override public String name() { return "mistral"; }
    @Override public String displayName() { return "Mistral AI"; }
    @Override public boolean supportsEmbedding() { return true; }
    @Override public boolean supportsGeneration() { return true; }

    /**
     * Creates a Mistral AI embedding provider.
     *
     * @param config provider configuration supplying the API key, model, optional base URL and
     *               dimensions, and the properties listed in the class documentation
     * @return a provider wrapping a {@code MistralAiEmbeddingModel}; never empty
     */
    @Override
    protected Optional<EmbeddingProvider> createRawEmbeddingProvider(ProviderConfig config) {
        long timeoutSeconds = ParseUtils.parseLongOrDefault(config.property("timeout").orElse(null), 30L);
        var builder = MistralAiEmbeddingModel.builder()
                .apiKey(config.apiKey())
                .modelName(config.model())
                .timeout(Duration.ofSeconds(timeoutSeconds));

        if (config.hasBaseUrl()) {
            builder.baseUrl(config.baseUrl());
        }

        // Apply HTTP client settings (proxy, mTLS)
        dev.langchain4j.http.client.HttpClientBuilder clientBuilder = LangChain4jHelper.resolveHttpClient(
                config, Duration.ofSeconds(timeoutSeconds));
        if (clientBuilder != null) {
            builder.httpClientBuilder(clientBuilder);
        }

        // Apply custom headers (expects Supplier<Map>)
        Map<String, String> headers = LangChain4jHelper.resolveCustomHeaders(config);
        if (!headers.isEmpty()) {
            builder.customHeaders(() -> headers);
        }

        MistralAiEmbeddingModel model = builder.build();
        int dims = config.dimensions() > 0 ? config.dimensions() : 1024;
        return Optional.of(new LangChain4jEmbeddingAdapter(model, config.model(), dims));
    }

    /**
     * Creates a Mistral AI text-generation provider.
     *
     * @param config provider configuration supplying the API key, model, optional base URL,
     *               and the properties listed in the class documentation
     * @return a provider wrapping a {@code MistralAiChatModel}; never empty
     */
    @Override
    public Optional<LlmProvider> createGenerationProvider(ProviderConfig config) {
        long timeoutSeconds = ParseUtils.parseLongOrDefault(config.property("timeout").orElse(null), 60L);
        var builder = MistralAiChatModel.builder()
                .apiKey(config.apiKey())
                .modelName(config.model())
                .timeout(Duration.ofSeconds(timeoutSeconds));

        if (config.hasBaseUrl()) {
            builder.baseUrl(config.baseUrl());
        }
        config.property("temperature")
                .flatMap(ParseUtils::parseDouble)
                .ifPresent(builder::temperature);
        config.property("maxTokens")
                .flatMap(ParseUtils::parseInteger)
                .ifPresent(builder::maxTokens);
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
