/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.config;

import com.spectrayan.spector.config.SpectorConfigFactory;
import com.spectrayan.spector.config.SpectorConfigSource;
import com.spectrayan.spector.config.properties.EmbeddingProperties;
import com.spectrayan.spector.config.properties.GenerationProperties;
import com.spectrayan.spector.config.properties.IngestionProperties;
import com.spectrayan.spector.config.properties.LlmProperties;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.config.properties.ProviderProperties;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryBuilder;
import com.spectrayan.spector.memory.graph.EntityExtractionMode;
import com.spectrayan.spector.provider.ProviderConfig;
import com.spectrayan.spector.provider.ProviderFactory;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Declarative configuration loader and factory for {@link SpectorMemory}.
 *
 * <p>Loads canonical YAML or properties configurations (matching {@code spector.yml.example})
 * and automatically initializes providers, chunkers, extractors, and memory storage
 * through {@link SpectorMemoryBuilder} without requiring manual code wiring.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   SpectorMemory memory = SpectorMemoryConfigurator.configure(Path.of("spector.yml"));
 * }</pre>
 */
public final class SpectorMemoryConfigurator {

    private static final Logger log = LoggerFactory.getLogger(SpectorMemoryConfigurator.class);

    private SpectorMemoryConfigurator() {}

    /**
     * Loads configuration from a file path and constructs a configured {@link SpectorMemory}.
     *
     * @param configPath path to the YAML or properties configuration file
     * @return fully initialized SpectorMemory instance
     */
    public static SpectorMemory configure(Path configPath) {
        try {
            SpectorConfigSource props = SpectorConfigSource.load(configPath);
            return configure(props);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to load Spector configuration from " + configPath, e);
        }
    }

    /**
     * Configures and returns a {@link SpectorMemoryBuilder} populated from {@link SpectorConfigSource}.
     *
     * <p>Allows callers to customize or override settings before calling {@code build()}.</p>
     *
     * @param props configuration properties
     * @return pre-configured SpectorMemoryBuilder
     */
    public static SpectorMemoryBuilder builder(SpectorConfigSource props) {
        return builder(com.spectrayan.spector.config.SpectorProperties.from(props));
    }

    /**
     * Creates a fully-configured {@link SpectorMemory} builder from the
     * aggregate {@link SpectorProperties} POJO.
     *
     * @param props the aggregate root configuration
     * @return a configured SpectorMemoryBuilder
     */
    public static SpectorMemoryBuilder builder(com.spectrayan.spector.config.SpectorProperties props) {
        var builder = SpectorMemory.builder()
                .fromProperties(props);

        // Auto-resolve providers from typed config
        var embProps = props.provider() != null ? props.provider().getEmbedding() : null;
        if (embProps != null && builder.embeddingProvider() == null) {
            var embProvider = resolveEmbeddingProvider(embProps);
            if (embProvider != null) {
                builder.embeddingProvider(embProvider);
            }
        }

        var genProps = props.provider() != null ? props.provider().getGeneration() : null;
        var llmProps = props.memory() != null ? props.memory().getLlm() : null;
        if (genProps != null) {
            var llmProvider = resolveGenerationProvider(genProps, llmProps);
            if (llmProvider != null) {
                builder.llmProvider(llmProvider);
            }
        }

        return builder;
    }

    /**
     * Configures and constructs a {@link SpectorMemory} instance from {@link SpectorConfigSource}.
     *
     * @param props configuration properties
     * @return fully initialized SpectorMemory instance
     */
    public static SpectorMemory configure(SpectorConfigSource props) {
        return builder(props).build();
    }

    public static EmbeddingProvider resolveEmbeddingProvider(EmbeddingProperties props) {
        ServiceLoader<ProviderFactory> loader = ServiceLoader.load(ProviderFactory.class);
        for (ProviderFactory factory : loader) {
            if (factory.supportsEmbedding() && factory.name().equalsIgnoreCase(props.getType())) {
                ProviderConfig config = new ProviderConfig(
                        factory.name() + "-embedding",
                        factory.name(),
                        props.getModel(),
                        props.getApiKey(),
                        props.getBaseUrl(),
                        props.getDimensions(),
                        Map.of()
                );
                return factory.createEmbeddingProvider(config).orElse(null);
            }
        }
        return null;
    }

    public static LlmProvider resolveGenerationProvider(GenerationProperties genProps, LlmProperties llmProps) {
        if (genProps == null) {
            return null;
        }
        String type = genProps.getType();
        if (type == null || type.isBlank() || "none".equalsIgnoreCase(type)) {
            return null;
        }

        String apiKey = genProps.getApiKey();

        float temperature = llmProps != null ? llmProps.getTemperature() : com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_LLM_TEMPERATURE;
        int maxTokens = llmProps != null ? llmProps.getMaxTokens() : com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_LLM_MAX_TOKENS;
        float topP = llmProps != null ? llmProps.getTopP() : com.spectrayan.spector.config.SpectorPropertyConstants.DEFAULT_MEMORY_LLM_TOP_P;

        java.util.Map<String, String> providerOptions = new java.util.HashMap<>();
        providerOptions.put("temperature", String.valueOf(temperature));
        providerOptions.put("maxOutputTokens", String.valueOf(maxTokens));
        providerOptions.put("topP", String.valueOf(topP));
        if (genProps.getProperties() != null) {
            providerOptions.putAll(genProps.getProperties());
        }

        ServiceLoader<ProviderFactory> loader = ServiceLoader.load(ProviderFactory.class);
        for (ProviderFactory factory : loader) {
            if (factory.supportsGeneration() && factory.name().equalsIgnoreCase(type)) {
                ProviderConfig config = new ProviderConfig(
                        factory.name() + "-generation",
                        factory.name(),
                        genProps.getModel(),
                        apiKey != null ? apiKey : "",
                        genProps.getBaseUrl() != null ? genProps.getBaseUrl() : "",
                        0,
                        providerOptions
                );
                return factory.createGenerationProvider(config).orElse(null);
            }
        }
        return null;
    }

    public static LlmProvider resolveGenerationProvider(String type, String model, String apiKey, String baseUrl) {
        GenerationProperties gen = new GenerationProperties();
        gen.setType(type);
        gen.setModel(model);
        gen.setApiKey(apiKey);
        gen.setBaseUrl(baseUrl);
        return resolveGenerationProvider(gen, null);
    }
}

