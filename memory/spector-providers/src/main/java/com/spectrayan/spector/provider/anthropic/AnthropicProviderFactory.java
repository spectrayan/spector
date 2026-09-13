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
package com.spectrayan.spector.provider.anthropic;

import com.spectrayan.spector.provider.AbstractProviderFactory;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.langchain4j.LangChain4jGenerationAdapter;
import com.spectrayan.spector.provider.langchain4j.LangChain4jHelper;

import java.util.Map;

import dev.langchain4j.model.anthropic.AnthropicChatModel;

import java.time.Duration;
import java.util.Optional;

import com.spectrayan.spector.commons.ParseUtils;

/**
 * Factory for creating Anthropic Claude generation providers.
 *
 * <p>Anthropic only supports text generation (not embeddings).
 * Use a separate embedding provider like OpenAI or Ollama for vectors.</p>
 *
 * <h3>Configuration Properties</h3>
 * <ul>
 *   <li>{@code timeout} — request timeout in seconds (default: 60)</li>
 *   <li>{@code maxTokens} — maximum output tokens (optional)</li>
 *   <li>{@code temperature} — sampling temperature (optional)</li>
 *   <li>{@code topP} — nucleus sampling probability (optional)</li>
 * </ul>
 *
 * <h3>Authentication and Endpoint</h3>
 * <p>The API key is taken from {@link ProviderConfig#apiKey()} and the model from
 * {@link ProviderConfig#model()}. When {@link ProviderConfig#baseUrl()} is set it overrides
 * the endpoint; otherwise the LangChain4j {@code AnthropicChatModel} default is used.</p>
 *
 * <h3>Networking</h3>
 * <p>Proxy, mTLS, and HTTP client settings are applied via
 * {@link LangChain4jHelper#resolveHttpClient(ProviderConfig, Duration)}, and {@code header.*}
 * properties are sent as custom headers. This factory does not configure retries or fallback.</p>
 */
public class AnthropicProviderFactory extends AbstractProviderFactory {

    /**
     * Creates a factory without a cache manager.
     */
    public AnthropicProviderFactory() {
        super();
    }

    /**
     * Creates a factory with the given cache manager.
     *
     * @param cacheManager cache manager passed to the base factory; unused because this factory does
     *                     not create embedding providers
     */
    public AnthropicProviderFactory(com.spectrayan.spector.commons.cache.SpectorCacheManager cacheManager) {
        super(cacheManager);
    }

    @Override public String name() { return "anthropic"; }
    @Override public String displayName() { return "Anthropic Claude"; }
    @Override public boolean supportsEmbedding() { return false; }
    @Override public boolean supportsGeneration() { return true; }

    /**
     * Creates an Anthropic Claude text-generation provider.
     *
     * @param config provider configuration supplying the API key, model, optional base URL,
     *               and the properties listed in the class documentation
     * @return a provider wrapping an {@code AnthropicChatModel}; never empty
     */
    @Override
    public Optional<LlmProvider> createGenerationProvider(ProviderConfig config) {
        long timeoutSeconds = ParseUtils.parseLongOrDefault(config.property("timeout").orElse(null), 60L);

        var builder = AnthropicChatModel.builder()
                .apiKey(config.apiKey())
                .modelName(config.model())
                .timeout(Duration.ofSeconds(timeoutSeconds));

        config.property("maxTokens")
                .flatMap(ParseUtils::parseInteger)
                .ifPresent(builder::maxTokens);
        config.property("temperature")
                .flatMap(ParseUtils::parseDouble)
                .ifPresent(builder::temperature);
        config.property("topP")
                .flatMap(ParseUtils::parseDouble)
                .ifPresent(builder::topP);
        if (config.hasBaseUrl()) {
            builder.baseUrl(config.baseUrl());
        }

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
