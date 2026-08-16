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
package com.spectrayan.spector.synapse.config.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for the Spector Synapse caching subsystem.
 *
 * <p>Binds to {@code spector.cache.*}, enabling fine-grained control of cache provider type,
 * default TTL/capacity, and individual domain cache overrides (e.g. {@code spector.cache.specs.user-accounts.ttl=15m}).</p>
 */
@ConfigurationProperties(prefix = "spector.cache")
public class SynapseCacheProperties {

    private boolean enabled = true;
    private String type = "caffeine";
    private Duration defaultTtl = Duration.ofMinutes(10);
    private long defaultMaxSize = 1000;

    /**
     * Named per-cache overrides (e.g. {@code jti-blocklist}, {@code user-accounts}).
     */
    private Map<String, CacheSpec> specs = new HashMap<>();

    public SynapseCacheProperties() {
        initDefaults();
    }

    private void initDefaults() {
        specs.put(SynapseCacheConstants.CACHE_JTI_BLOCKLIST, new CacheSpec(Duration.ofMinutes(15), 50_000));
        specs.put(SynapseCacheConstants.CACHE_USER_ACCOUNTS, new CacheSpec(Duration.ofMinutes(10), 5_000));
        specs.put(SynapseCacheConstants.CACHE_DECRYPTED_SECRETS, new CacheSpec(Duration.ofMinutes(1), 1_000));
        specs.put(SynapseCacheConstants.CACHE_CREDENTIAL_RECORDS, new CacheSpec(Duration.ofMinutes(5), 2_000));
        specs.put(SynapseCacheConstants.CACHE_SCOPED_CONFIGS, new CacheSpec(Duration.ofMinutes(15), 1_000));
        specs.put(SynapseCacheConstants.CACHE_CONNECTOR_ROUTES, new CacheSpec(Duration.ofMinutes(15), 1_000));
        specs.put(SynapseCacheConstants.CACHE_COMPILED_SUBGRAPHS, new CacheSpec(Duration.ofHours(1), 500));
        specs.put(SynapseCacheConstants.CACHE_SQL_QUERIES, new CacheSpec(Duration.ofHours(24), 500));
        specs.put(SynapseCacheConstants.CACHE_TOKEN_USAGE, new CacheSpec(Duration.ofDays(7), 50_000));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        if (type != null && !type.isBlank()) {
            this.type = type;
        }
    }

    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    public void setDefaultTtl(Duration defaultTtl) {
        if (defaultTtl != null) {
            this.defaultTtl = defaultTtl;
        }
    }

    public long getDefaultMaxSize() {
        return defaultMaxSize;
    }

    public void setDefaultMaxSize(long defaultMaxSize) {
        if (defaultMaxSize > 0) {
            this.defaultMaxSize = defaultMaxSize;
        }
    }

    public Map<String, CacheSpec> getSpecs() {
        return specs;
    }

    public void setSpecs(Map<String, CacheSpec> specs) {
        if (specs != null) {
            this.specs.putAll(specs);
        }
    }

    /**
     * Resolves the effective TTL for a named cache, falling back to {@link #getDefaultTtl()}.
     */
    public Duration getTtl(String cacheName) {
        CacheSpec spec = specs.get(cacheName);
        return (spec != null && spec.getTtl() != null) ? spec.getTtl() : defaultTtl;
    }

    /**
     * Resolves the effective maximum size for a named cache, falling back to {@link #getDefaultMaxSize()}.
     */
    public long getMaxSize(String cacheName) {
        CacheSpec spec = specs.get(cacheName);
        return (spec != null && spec.getMaxSize() > 0) ? spec.getMaxSize() : defaultMaxSize;
    }

    public boolean enabled() { return isEnabled(); }
    public String type() { return getType(); }
    public Duration defaultTtl() { return getDefaultTtl(); }
    public long defaultMaxSize() { return getDefaultMaxSize(); }
    public Map<String, CacheSpec> specs() { return getSpecs(); }

    /**
     * Configuration specification for a single named cache.
     */
    public static class CacheSpec {
        private Duration ttl;
        private long maxSize;

        public CacheSpec() {}

        public CacheSpec(Duration ttl, long maxSize) {
            this.ttl = ttl;
            this.maxSize = maxSize;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public long getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(long maxSize) {
            this.maxSize = maxSize;
        }

        public Duration ttl() { return getTtl(); }
        public long maxSize() { return getMaxSize(); }
    }
}
