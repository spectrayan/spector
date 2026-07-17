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

import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.ProviderFactory;
import com.spectrayan.spector.provider.langchain4j.LangChain4jGenerationAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Factory for creating AWS Bedrock generation providers.
 *
 * <p>AWS Bedrock provides access to foundation models from multiple providers
 * (Anthropic Claude, Meta Llama, Amazon Titan, etc.) via a unified API.
 * Currently supports generation only — embedding support depends on
 * LangChain4j Bedrock module availability.</p>
 *
 * <h3>Configuration Properties</h3>
 * <ul>
 *   <li>{@code region} — AWS region (e.g., "us-east-1")</li>
 *   <li>{@code temperature} — sampling temperature (optional)</li>
 *   <li>{@code maxTokens} — maximum output tokens (optional)</li>
 * </ul>
 *
 * <h3>Authentication</h3>
 * <p>Uses standard AWS credential chain (environment variables, IAM role, etc.).
 * The {@code apiKey} field in config is not used — AWS authentication is
 * handled by the AWS SDK credential provider chain.</p>
 *
 * <p><strong>Note:</strong> This factory is a placeholder. Full Bedrock support
 * requires the {@code langchain4j-amazon-bedrock} module, which may not be
 * available in LangChain4j 1.17.1. When available, this factory will be
 * updated to use {@code BedrockChatModel}.</p>
 */
public class BedrockProviderFactory implements ProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(BedrockProviderFactory.class);

    @Override public String name() { return "bedrock"; }
    @Override public String displayName() { return "AWS Bedrock"; }
    @Override public boolean supportsEmbedding() { return false; }
    @Override public boolean supportsGeneration() { return true; }

    @Override
    public Optional<EmbeddingProvider> createEmbeddingProvider(ProviderConfig config) {
        return Optional.empty(); // Bedrock embedding not yet supported via LangChain4j
    }

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
