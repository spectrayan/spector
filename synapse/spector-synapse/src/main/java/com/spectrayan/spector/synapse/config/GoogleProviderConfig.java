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

import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.ProviderRegistry;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.google.GoogleProviderFactory;
import com.spectrayan.spector.synapse.config.GoogleProviderConfig.EmbeddingProps;
import com.spectrayan.spector.synapse.config.GoogleProviderConfig.GenerationProps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import jakarta.annotation.PostConstruct;

import java.util.Map;

/**
 * Registers Google Gemini as an {@link LlmProvider} / {@link EmbeddingProvider}
 * when {@code spector.provider.generation.type=google} (resp. {@code embedding.type=google}).
 *
 * <p>Mirrors {@link EmbeddingProviderConfig}'s Ollama wiring, but delegates
 * construction to {@link GoogleProviderFactory} instead of hardcoding Ollama.
 * The {@code @ConditionalOnMissingBean} guard on the default Ollama beans means
 * this backs Ollama off automatically once these beans are present.</p>
 */
@Configuration
@EnableConfigurationProperties({
        GoogleProviderConfig.GenerationProps.class,
        GoogleProviderConfig.EmbeddingProps.class
})
public class GoogleProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(GoogleProviderConfig.class);
    private static final GoogleProviderFactory FACTORY = new GoogleProviderFactory();
    private final Environment env;

    public GoogleProviderConfig(Environment env) {
        this.env = env;
    }

    @PostConstruct
    void debugProviderType() {
        log.info("[DEBUG] spector.provider.generation.type = '{}'", env.getProperty("spector.provider.generation.type"));
        log.info("[DEBUG] GEMINI_API_KEY present = {}", System.getenv("GEMINI_API_KEY") != null);
    }

    @Bean
    @ConditionalOnProperty(prefix = "spector.provider.generation", name = "type", havingValue = "google")
    @ConditionalOnMissingBean(LlmProvider.class)
    LlmProvider googleLlmProvider(ProviderRegistry registry, GenerationProps props) {
        String apiKey = (props.apiKey != null && !props.apiKey.isBlank()) ? props.apiKey : System.getenv("GEMINI_API_KEY");
        String model = (props.model != null && !props.model.isBlank()) ? props.model : "gemini-2.0-flash";

        ProviderConfig config = new ProviderConfig(
                "google", "google", model, apiKey, "", 0, props.properties);
        LlmProvider llm = FACTORY.createGenerationProvider(config)
                .orElseThrow(() -> new IllegalStateException("GoogleProviderFactory returned no generation provider"));
        registry.registerGeneration("google", llm);
        registry.activateGeneration("google");
        log.info("[GoogleProviderConfig] Registered + activated Gemini generation provider: model={}", model);
        return new com.spectrayan.spector.synapse.provider.DelegatingLlmProvider(registry);
    }

    @Bean
    @ConditionalOnProperty(prefix = "spector.provider.embedding", name = "type", havingValue = "google")
    @ConditionalOnMissingBean(EmbeddingProvider.class)
    EmbeddingProvider googleEmbeddingProvider(ProviderRegistry registry, EmbeddingProps props) {
        String apiKey = (props.apiKey != null && !props.apiKey.isBlank()) ? props.apiKey : System.getenv("GEMINI_API_KEY");
        String model = (props.model != null && !props.model.isBlank()) ? props.model : "text-embedding-004";
        int dimensions = props.dimensions > 0 ? props.dimensions : 768;

        ProviderConfig config = new ProviderConfig(
                "google", "google", model, apiKey, "", dimensions, Map.of());
        EmbeddingProvider embedder = FACTORY.createEmbeddingProvider(config)
                .orElseThrow(() -> new IllegalStateException("GoogleProviderFactory returned no embedding provider"));
        registry.registerEmbedding("google", embedder);
        registry.activateEmbedding("google");
        log.info("[GoogleProviderConfig] Registered + activated Gemini embedding provider: model={}", model);
        return embedder;
    }

    @ConfigurationProperties(prefix = "spector.provider.generation")
    public static class GenerationProps {
        private String model;
        private String apiKey;
        private Map<String, String> properties = Map.of();

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public Map<String, String> getProperties() { return properties; }
        public void setProperties(Map<String, String> properties) { this.properties = properties; }
    }

    @ConfigurationProperties(prefix = "spector.provider.embedding")
    public static class EmbeddingProps {
        private String model;
        private String apiKey;
        private int dimensions;

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public int getDimensions() { return dimensions; }
        public void setDimensions(int dimensions) { this.dimensions = dimensions; }
    }
}