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
import com.spectrayan.spector.config.SpectorProperties;
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
            SpectorProperties props = SpectorProperties.load(configPath);
            return configure(props);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to load Spector configuration from " + configPath, e);
        }
    }

    /**
     * Configures and returns a {@link SpectorMemoryBuilder} populated from {@link SpectorProperties}.
     *
     * <p>Allows callers to customize or override settings before calling {@code build()}.</p>
     *
     * @param props configuration properties
     * @return pre-configured SpectorMemoryBuilder
     */
    public static SpectorMemoryBuilder builder(SpectorProperties props) {
        if (props == null) {
            props = SpectorProperties.builder().build();
        }

        MemoryProperties memoryProps = SpectorConfigFactory.memoryProperties(props);
        ProviderProperties providerProps = SpectorConfigFactory.providerProperties(props);
        EmbeddingProperties embProps = providerProps.getEmbedding();
        GenerationProperties genProps = providerProps.getGeneration();
        IngestionProperties ingProps = SpectorConfigFactory.ingestionProperties(props);

        SpectorMemoryBuilder builder = SpectorMemory.builder()
                .fromProperties(memoryProps);

        // 1. Resolve Embedding Provider
        String embType = embProps.getType();
        if (embType != null && !embType.isBlank() && !"none".equalsIgnoreCase(embType)) {
            EmbeddingProvider embedder = resolveEmbeddingProvider(embProps);
            if (embedder != null) {
                builder.embeddingProvider(embedder);
                builder.dimensions(embedder.dimensions());
                log.info("SpectorMemory auto-configured EmbeddingProvider: {} (model={}, dims={})",
                        embType, embProps.getModel(), embedder.dimensions());
            } else {
                log.warn("No EmbeddingProvider found for type '{}'. SpectorMemory will require a manually supplied embedder.", embType);
            }
        }

        // 2. Resolve Generation / LLM Provider & Entity Extraction
        String genType = genProps.getType();
        if (genType != null && !genType.isBlank() && !"none".equalsIgnoreCase(genType)) {
            LlmProvider llm = resolveGenerationProvider(genProps, memoryProps.getLlm());
            if (llm != null) {
                builder.llmProvider(llm);
                builder.entityExtractionMode(EntityExtractionMode.LLM);
                log.info("SpectorMemory auto-configured LlmProvider: {} (model={}) with EntityExtractionMode.LLM",
                        genType, genProps.getModel());
            } else {
                builder.entityExtractionMode(EntityExtractionMode.NONE);
            }
        } else {
            builder.entityExtractionMode(EntityExtractionMode.NONE);
        }

        // 3. Configure Chunker (com.spectrayan.spector.commons.chunker.MarkdownChunker)
        int chunkSize = ingProps.getChunkSize() > 0 ? ingProps.getChunkSize() : 2500;
        int chunkOverlap = ingProps.getChunkOverlap() >= 0 ? ingProps.getChunkOverlap() : 200;
        com.spectrayan.spector.commons.chunker.ChunkConfig chunkConfig =
                com.spectrayan.spector.commons.chunker.ChunkConfig.markdown(chunkSize, chunkOverlap);
        builder.chunker(new com.spectrayan.spector.commons.chunker.MarkdownChunker(), chunkConfig);

        return builder;
    }

    /**
     * Configures and constructs a {@link SpectorMemory} instance from {@link SpectorProperties}.
     *
     * @param props configuration properties
     * @return fully initialized SpectorMemory instance
     */
    public static SpectorMemory configure(SpectorProperties props) {
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
        if ((apiKey == null || apiKey.isBlank()) && ("google".equalsIgnoreCase(type) || "gemini".equalsIgnoreCase(type))) {
            apiKey = System.getProperty("geminiApiKey", System.getenv("GEMINI_API_KEY"));
        }

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
