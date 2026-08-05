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

import com.spectrayan.spector.config.AuthProperties;
import com.spectrayan.spector.config.CorsProperties;
import com.spectrayan.spector.config.MemoryProperties;
import com.spectrayan.spector.spring.autoconfigure.SpectorConfigProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;

/**
 * Externalized configuration for Spector Synapse server application.
 *
 * <p>Extends {@link SpectorConfigProperties} (Tier 2 Spring Starter properties)
 * to inherit {@code memory}, {@code embedding}, {@code metrics}, and {@code client}
 * configurations with zero field duplication.</p>
 *
 * <p>Adds Synapse server application settings: {@code port}, {@code apiKey},
 * {@code dataDir}, {@code cors}, and {@code auth}.</p>
 */
@Primary
@ConfigurationProperties(prefix = "spector")
public class SynapseProperties extends SpectorConfigProperties {

    private int port = 7070;
    private String apiKey = "spector-dev-key";
    private String dataDir = "./spector-data";
    private CorsProperties cors = new CorsProperties();
    private AuthProperties auth = new AuthProperties();

    @Deprecated(since = "0.1.0-alpha", forRemoval = true)
    private OllamaProperties ollama = new OllamaProperties();

    public SynapseProperties() {}

    public SynapseProperties(
            int port,
            String apiKey,
            String dataDir,
            MemoryProperties memory,
            CorsProperties cors,
            AuthProperties auth
    ) {
        if (port > 0) this.port = port;
        if (apiKey != null && !apiKey.isBlank()) this.apiKey = apiKey;
        if (dataDir != null && !dataDir.isBlank()) this.dataDir = dataDir;
        if (memory != null) setMemory(memory);
        if (cors != null) this.cors = cors;
        if (auth != null) this.auth = auth;
    }

    @Deprecated(since = "0.1.0-alpha", forRemoval = true)
    public SynapseProperties(
            int port,
            String apiKey,
            String dataDir,
            OllamaProperties ollama,
            MemoryProperties memory,
            CorsProperties cors,
            AuthProperties auth
    ) {
        this(port, apiKey, dataDir, memory, cors, auth);
        if (ollama != null) this.ollama = ollama;
    }

    public int getPort() { return port; }
    public void setPort(int port) { if (port > 0) this.port = port; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { if (apiKey != null) this.apiKey = apiKey; }

    public String getDataDir() { return dataDir; }
    public void setDataDir(String dataDir) { if (dataDir != null) this.dataDir = dataDir; }

    public CorsProperties getCors() { return cors; }
    public void setCors(CorsProperties cors) { if (cors != null) this.cors = cors; }

    public AuthProperties getAuth() { return auth; }
    public void setAuth(AuthProperties auth) { if (auth != null) this.auth = auth; }

    @Deprecated(since = "0.1.0-alpha", forRemoval = true)
    public OllamaProperties getOllama() { return ollama; }
    @Deprecated(since = "0.1.0-alpha", forRemoval = true)
    public void setOllama(OllamaProperties ollama) { if (ollama != null) this.ollama = ollama; }
    @Deprecated(since = "0.1.0-alpha", forRemoval = true)
    public OllamaProperties ollama() { return getOllama(); }

    // Record-style accessors for backward compatibility across existing call sites
    public int port() { return getPort(); }
    public String apiKey() { return getApiKey(); }
    public String dataDir() { return getDataDir(); }
    public MemoryProperties memory() { return getMemory(); }
    public CorsProperties cors() { return getCors(); }
    public AuthProperties auth() { return getAuth(); }

    @Deprecated(since = "0.1.0-alpha", forRemoval = true)
    public static class OllamaProperties {
        private String baseUrl = "http://localhost:11434";
        private String model = "llama3.2";
        private String embedModel = "nomic-embed-text";

        public OllamaProperties() {}
        public OllamaProperties(String baseUrl, String model, String embedModel) {
            if (baseUrl != null) this.baseUrl = baseUrl;
            if (model != null) this.model = model;
            if (embedModel != null) this.embedModel = embedModel;
        }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { if (baseUrl != null) this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { if (model != null) this.model = model; }
        public String getEmbedModel() { return embedModel; }
        public void setEmbedModel(String embedModel) { if (embedModel != null) this.embedModel = embedModel; }

        public String baseUrl() { return getBaseUrl(); }
        public String model() { return getModel(); }
        public String embedModel() { return getEmbedModel(); }
    }
}
