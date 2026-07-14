/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.config;

import com.spectrayan.spector.embed.EmbeddingConfig;
import com.spectrayan.spector.embed.EmbeddingProvider;
import com.spectrayan.spector.embed.TextGenerationProvider;
import com.spectrayan.spector.embed.ollama.OllamaEmbeddingProvider;
import com.spectrayan.spector.memory.id.TsidGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the {@link EmbeddingProvider} bean for the Spector Spring auto-configuration.
 *
 * <p>{@code SpectorAutoConfiguration} from {@code spector-spring} requires an
 * {@code EmbeddingProvider} bean to construct the managed {@link com.spectrayan.spector.memory.SpectorMemory}
 * instance. This configuration satisfies that dependency using the Ollama embedding
 * settings from {@link SynapseProperties}.</p>
 *
 * <p>The {@code @ConditionalOnMissingBean} guard ensures this bean is only created
 * if the application has not registered its own provider (e.g., for testing).</p>
 */
@Configuration
public class EmbeddingProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingProviderConfig.class);

    @Bean
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    EmbeddingProvider embeddingProvider(SynapseProperties props) {
        EmbeddingConfig config = EmbeddingConfig
                .ollama(props.ollama().embedModel())
                .withBaseUrl(props.ollama().baseUrl())
                .withTimeout(java.time.Duration.ofSeconds(300));
        log.info("[EmbeddingProvider] Configured Ollama embedding: model={}, baseUrl={}, timeout=300s",
                props.ollama().embedModel(), props.ollama().baseUrl());
        return new OllamaEmbeddingProvider(config);
    }

    @Bean
    @ConditionalOnMissingBean(TsidGenerator.class)
    TsidGenerator tsidGenerator() {
        return new TsidGenerator();
    }

    @Bean
    @ConditionalOnMissingBean(TextGenerationProvider.class)
    TextGenerationProvider textGenerationProvider(com.spectrayan.spector.synapse.provider.ProviderRegistry providerRegistry, SynapseProperties props) {
        try {
            var llm = new com.spectrayan.spector.embed.ollama.OllamaLlmProvider(
                    props.ollama().model(), props.ollama().baseUrl(), java.time.Duration.ofSeconds(300));
            providerRegistry.registerGeneration("ollama", llm);
            log.info("[EmbeddingProvider] Registered default Ollama text generation provider: model={}, baseUrl={}, timeout=300s",
                    props.ollama().model(), props.ollama().baseUrl());
        } catch (Exception e) {
            log.warn("Failed to register default Ollama generation provider: {}", e.getMessage());
        }
        return new com.spectrayan.spector.synapse.provider.DelegatingTextGenerationProvider(providerRegistry);
    }
}
