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

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

import static com.spectrayan.spector.synapse.config.cache.SynapseCacheConstants.*;

/**
 * Spring Cache configuration for Spector Synapse backed by high-performance Caffeine caches.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        List<CaffeineCache> caches = new ArrayList<>();

        caches.add(buildCache(CACHE_JTI_BLOCKLIST, TTL_JTI_BLOCKLIST.toSeconds(), MAX_SIZE_JTI_BLOCKLIST));
        caches.add(buildCache(CACHE_USER_ACCOUNTS, TTL_USER_ACCOUNTS.toSeconds(), MAX_SIZE_USER_ACCOUNTS));
        caches.add(buildCache(CACHE_DECRYPTED_SECRETS, TTL_DECRYPTED_SECRETS.toSeconds(), MAX_SIZE_DECRYPTED_SECRETS));
        caches.add(buildCache(CACHE_CREDENTIAL_RECORDS, TTL_CREDENTIAL_RECORDS.toSeconds(), MAX_SIZE_CREDENTIAL_RECORDS));
        caches.add(buildCache(CACHE_SCOPED_CONFIGS, TTL_SCOPED_CONFIGS.toSeconds(), MAX_SIZE_SCOPED_CONFIGS));
        caches.add(buildCache(CACHE_CONNECTOR_ROUTES, TTL_CONNECTOR_ROUTES.toSeconds(), MAX_SIZE_CONNECTOR_ROUTES));
        caches.add(buildCache(CACHE_COMPILED_SUBGRAPHS, TTL_COMPILED_SUBGRAPHS.toSeconds(), MAX_SIZE_COMPILED_SUBGRAPHS));
        caches.add(buildCache(CACHE_SQL_QUERIES, TTL_SQL_QUERIES.toSeconds(), MAX_SIZE_SQL_QUERIES));

        cacheManager.setCaches(caches);
        cacheManager.initializeCaches();
        return cacheManager;
    }

    private CaffeineCache buildCache(String name, long ttlSeconds, long maxSize) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .expireAfterWrite(java.time.Duration.ofSeconds(ttlSeconds))
                .maximumSize(maxSize)
                .recordStats()
                .build());
    }
}
