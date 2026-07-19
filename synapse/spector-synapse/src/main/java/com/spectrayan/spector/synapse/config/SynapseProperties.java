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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for Spector Synapse.
 *
 * <p>All properties are bound from {@code spector.*} in {@code application.yml}
 * and can be overridden via environment variables (e.g., {@code SPECTOR_PORT=8080}).</p>
 *
 * @param port        HTTP port for the Synapse server (default: 7070)
 * @param apiKey      API key for authenticating REST requests
 * @param dataDir     directory for persistent data (H2 database, indexes)
 * @param ollama      Ollama LLM provider configuration
 * @param memory      cognitive memory engine configuration
 * @param cors        CORS configuration for the Cortex UI
 */
@ConfigurationProperties(prefix = "spector")
public record SynapseProperties(
        int port,
        String apiKey,
        String dataDir,
        OllamaProperties ollama,
        MemoryProperties memory,
        CorsProperties cors
) {

    /**
     * Provides defaults for unset properties.
     */
    public SynapseProperties {
        if (port <= 0) port = 7070;
        if (apiKey == null || apiKey.isBlank()) apiKey = "spector-dev-key";
        if (dataDir == null || dataDir.isBlank()) dataDir = "./spector-data";
        if (ollama == null) ollama = new OllamaProperties(null, null, null);
        if (memory == null) memory = new MemoryProperties(0, 0, null);

        if (cors == null) cors = new CorsProperties(null);
    }

    /**
     * Ollama LLM provider settings.
     *
     * @param baseUrl  Ollama server URL (default: http://localhost:11434)
     * @param model    default model for chat (default: llama3.2)
     * @param embedModel  model for embeddings (default: nomic-embed-text)
     */
    public record OllamaProperties(String baseUrl, String model, String embedModel) {
        public OllamaProperties {
            if (baseUrl == null || baseUrl.isBlank()) baseUrl = "http://localhost:11434";
            if (model == null || model.isBlank()) model = "llama3.2";
            if (embedModel == null || embedModel.isBlank()) embedModel = "nomic-embed-text";
        }
    }

    /**
     * Memory engine settings.
     *
     * @param maxMemories   maximum number of memories (0 = unlimited)
     * @param dimensions    embedding vector dimensions (0 = auto-detect from provider)
     * @param consolidation consolidation settings
     */
    public record MemoryProperties(int maxMemories, int dimensions, ConsolidationProperties consolidation) {
        public MemoryProperties {
            if (maxMemories < 0) maxMemories = 0;
            if (dimensions < 0) dimensions = 0;
            if (consolidation == null) consolidation = new ConsolidationProperties(21600000L); // 6 hours
        }
    }

    /**
     * Consolidation settings.
     *
     * @param interval consolidation run interval in milliseconds (default: 21600000 ms = 6 hours)
     */
    public record ConsolidationProperties(long interval) {
        public ConsolidationProperties {
            if (interval <= 0) interval = 21600000L;
        }
    }


    /**
     * CORS settings for the Cortex UI.
     *
     * @param allowedOrigins  comma-separated origins (default: http://localhost:4200)
     */
    public record CorsProperties(String allowedOrigins) {
        public CorsProperties {
            if (allowedOrigins == null || allowedOrigins.isBlank()) {
                allowedOrigins = "http://localhost:4200";
            }
        }
    }
}
