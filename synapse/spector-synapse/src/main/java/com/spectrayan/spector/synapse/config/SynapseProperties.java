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

import com.spectrayan.spector.config.properties.AuthProperties;
import com.spectrayan.spector.config.properties.CorsProperties;
import com.spectrayan.spector.config.properties.MemoryProperties;
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
    private com.spectrayan.spector.synapse.config.cache.SynapseCacheProperties cache = new com.spectrayan.spector.synapse.config.cache.SynapseCacheProperties();

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

    public com.spectrayan.spector.synapse.config.cache.SynapseCacheProperties getCache() { return cache; }
    public void setCache(com.spectrayan.spector.synapse.config.cache.SynapseCacheProperties cache) { if (cache != null) this.cache = cache; }

    // Record-style accessors for backward compatibility across existing call sites
    public int port() { return getPort(); }
    public String apiKey() { return getApiKey(); }
    public String dataDir() { return getDataDir(); }
    public MemoryProperties memory() { return getMemory(); }
    public CorsProperties cors() { return getCors(); }
    public AuthProperties auth() { return getAuth(); }
    public com.spectrayan.spector.synapse.config.cache.SynapseCacheProperties cache() { return getCache(); }
}
