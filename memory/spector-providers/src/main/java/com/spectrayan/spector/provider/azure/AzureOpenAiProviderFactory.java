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

import com.spectrayan.spector.provider.AbstractProviderFactory;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.langchain4j.LangChain4jEmbeddingAdapter;
import com.spectrayan.spector.provider.langchain4j.LangChain4jGenerationAdapter;
import com.spectrayan.spector.provider.langchain4j.LangChain4jHelper;

import com.azure.core.http.ProxyOptions;
import java.net.InetSocketAddress;
import java.util.Map;

import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.azure.AzureOpenAiEmbeddingModel;

import java.time.Duration;
import java.util.Optional;

import com.spectrayan.spector.commons.ParseUtils;

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
 *   <li>{@code deploymentName} — Azure deployment name (defaults to the model name)</li>
 *   <li>{@code temperature} — sampling temperature (optional, generation only)</li>
 *   <li>{@code maxTokens} — maximum output tokens (optional, generation only)</li>
 *   <li>{@code proxyHost} / {@code proxyPort} — HTTP proxy (optional; ignored if the port is not a number)</li>
 *   <li>{@code header.*} — custom request headers (optional)</li>
 * </ul>
 *
 * <h3>Authentication and Endpoint</h3>
 * <p>The API key is taken from {@link ProviderConfig#apiKey()}. The endpoint is set from
 * {@link ProviderConfig#baseUrl()} only when a base URL is configured; this factory defines
 * no default endpoint. Unlike the other LangChain4j factories, it does not use
 * {@link LangChain4jHelper#resolveHttpClient(ProviderConfig, Duration)}, so mTLS settings are
 * not applied. This factory does not configure retries or fallback.</p>
 *
 * <h3>Embedding Dimensions</h3>
 * <p>If {@link ProviderConfig#dimensions()} is positive it is sent to Azure and reported by the
 * provider; otherwise the provider reports 1536 dimensions.</p>
 */
public class AzureOpenAiProviderFactory extends AbstractProviderFactory {

    /**
     * Creates a factory without a cache manager; embedding providers are returned without caching.
     */
    public AzureOpenAiProviderFactory() {
        super();
    }

    /**
     * Creates a factory with the given cache manager.
     *
     * @param cacheManager cache manager used to wrap created embedding providers with caching when
     *                     caching is enabled in the provider configuration; may be {@code null}
     */
    public AzureOpenAiProviderFactory(com.spectrayan.spector.commons.cache.SpectorCacheManager cacheManager) {
        super(cacheManager);
    }

    @Override public String name() { return "azure-openai"; }
    @Override public String displayName() { return "Azure OpenAI"; }
    @Override public boolean supportsEmbedding() { return true; }
    @Override public boolean supportsGeneration() { return true; }

    /**
     * Creates an Azure OpenAI embedding provider.
     *
     * @param config provider configuration supplying the API key, deployment/model, endpoint,
     *               optional dimensions, and the properties listed in the class documentation
     * @return a provider wrapping an {@code AzureOpenAiEmbeddingModel}; never empty
     */
    @Override
    protected Optional<EmbeddingProvider> createRawEmbeddingProvider(ProviderConfig config) {
        String deploymentName = config.property("deploymentName", config.model());
        long timeoutSeconds = ParseUtils.parseLongOrDefault(config.property("timeout").orElse(null), 30L);

        var builder = AzureOpenAiEmbeddingModel.builder()
                .apiKey(config.apiKey())
                .deploymentName(deploymentName)
                .timeout(Duration.ofSeconds(timeoutSeconds));

        if (config.hasBaseUrl()) {
            builder.endpoint(config.baseUrl());
        }
        if (config.dimensions() > 0) {
            builder.dimensions(config.dimensions());
        }

        // Apply proxy if specified
        String host = config.properties().get("proxyHost");
        String portStr = config.properties().get("proxyPort");
        if (host != null && !host.isBlank() && portStr != null && !portStr.isBlank()) {
            try {
                int port = Integer.parseInt(portStr.trim());
                builder.proxyOptions(new ProxyOptions(ProxyOptions.Type.HTTP, new InetSocketAddress(host, port)));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        // Apply custom headers
        Map<String, String> headers = LangChain4jHelper.resolveCustomHeaders(config);
        if (!headers.isEmpty()) {
            builder.customHeaders(headers);
        }

        AzureOpenAiEmbeddingModel model = builder.build();
        int dims = config.dimensions() > 0 ? config.dimensions() : 1536;
        return Optional.of(new LangChain4jEmbeddingAdapter(model, config.model(), dims));
    }

    /**
     * Creates an Azure OpenAI text-generation provider.
     *
     * @param config provider configuration supplying the API key, deployment/model, endpoint,
     *               and the properties listed in the class documentation
     * @return a provider wrapping an {@code AzureOpenAiChatModel}; never empty
     */
    @Override
    public Optional<LlmProvider> createGenerationProvider(ProviderConfig config) {
        String deploymentName = config.property("deploymentName", config.model());
        long timeoutSeconds = ParseUtils.parseLongOrDefault(config.property("timeout").orElse(null), 60L);

        var builder = AzureOpenAiChatModel.builder()
                .apiKey(config.apiKey())
                .deploymentName(deploymentName)
                .timeout(Duration.ofSeconds(timeoutSeconds));

        if (config.hasBaseUrl()) {
            builder.endpoint(config.baseUrl());
        }
        config.property("temperature")
                .flatMap(ParseUtils::parseDouble)
                .ifPresent(builder::temperature);
        config.property("maxTokens")
                .flatMap(ParseUtils::parseInteger)
                .ifPresent(builder::maxTokens);

        // Apply proxy if specified
        String genHost = config.properties().get("proxyHost");
        String genPortStr = config.properties().get("proxyPort");
        if (genHost != null && !genHost.isBlank() && genPortStr != null && !genPortStr.isBlank()) {
            try {
                int port = Integer.parseInt(genPortStr.trim());
                builder.proxyOptions(new ProxyOptions(ProxyOptions.Type.HTTP, new InetSocketAddress(genHost, port)));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        // Apply custom headers
        Map<String, String> headers = LangChain4jHelper.resolveCustomHeaders(config);
        if (!headers.isEmpty()) {
            builder.customHeaders(headers);
        }

        return Optional.of(new LangChain4jGenerationAdapter(builder.build(), config.model()));
    }

}
