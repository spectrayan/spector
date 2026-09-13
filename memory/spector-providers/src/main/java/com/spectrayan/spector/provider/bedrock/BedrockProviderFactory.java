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
package com.spectrayan.spector.provider.bedrock;

import com.spectrayan.spector.provider.AbstractProviderFactory;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.ProviderConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Factory for creating AWS Bedrock generation providers.
 *
 * <p>AWS Bedrock provides access to foundation models from multiple providers.
 * The {@code apiKey} field in config is not read by this factory; AWS authentication
 * is intended to be handled by the AWS SDK credential provider chain once Bedrock
 * support is implemented.</p>
 *
 * <p><strong>Note:</strong> This factory is a placeholder. Full Bedrock support
 * requires the {@code langchain4j-amazon-bedrock} module, which may not be
 * available in LangChain4j 1.17.1. When available, this factory will be
 * updated to use {@code BedrockChatModel}.</p>
 */
public class BedrockProviderFactory extends AbstractProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(BedrockProviderFactory.class);

    /**
     * Creates a factory without a cache manager.
     */
    public BedrockProviderFactory() {
        super();
    }

    /**
     * Creates a factory with the given cache manager.
     *
     * @param cacheManager cache manager passed to the base factory; unused because this factory does
     *                     not create embedding providers
     */
    public BedrockProviderFactory(com.spectrayan.spector.commons.cache.SpectorCacheManager cacheManager) {
        super(cacheManager);
    }

    @Override public String name() { return "bedrock"; }
    @Override public String displayName() { return "AWS Bedrock"; }
    @Override public boolean supportsEmbedding() { return false; }
    @Override public boolean supportsGeneration() { return true; }

    /**
     * Placeholder that does not create a provider.
     *
     * <p>Logs a warning containing the configured model name and returns empty.</p>
     *
     * @param config provider configuration; only the model name is read, for logging
     * @return always {@link Optional#empty()}
     */
    @Override
    public Optional<LlmProvider> createGenerationProvider(ProviderConfig config) {
        // Bedrock requires the langchain4j-amazon-bedrock module.
        // When available, uncomment and use:
        //
        // var builder = BedrockChatModel.builder()
        //         .modelId(config.model())
        //         .region(Region.of(config.property("region", "us-east-1")));
        // config.property("temperature").ifPresent(t ->
        //         builder.temperature(Float.parseFloat(t)));
        // config.property("maxTokens").ifPresent(t ->
        //         builder.maxTokens(Integer.parseInt(t)));
        // return Optional.of(new LangChain4jGenerationAdapter(builder.build(), config.model()));

        log.warn("AWS Bedrock provider factory is a placeholder — " +
                "langchain4j-amazon-bedrock module not yet integrated. " +
                "Model: {}", config.model());
        return Optional.empty();
    }
}
