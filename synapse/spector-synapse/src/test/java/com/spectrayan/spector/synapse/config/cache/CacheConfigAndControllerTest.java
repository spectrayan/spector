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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class CacheConfigAndControllerTest {

    private SynapseCacheProperties cacheProperties;
    private CacheConfig cacheConfig;
    private CaffeineCacheManager cacheManager;
    private CacheController cacheController;

    @BeforeEach
    void setUp() {
        cacheProperties = new SynapseCacheProperties();
        cacheConfig = new CacheConfig(cacheProperties);
        cacheManager = new CaffeineCacheManager(SynapseCacheConstants.ALL_CACHES);
        cacheConfig.caffeineCacheManagerCustomizer().customize(cacheManager);
        cacheController = new CacheController(cacheManager);
    }

    @Test
    @DisplayName("SynapseCacheProperties binds defaults and resolves custom cache specs")
    void cachePropertiesDefaultsAndOverrides() {
        assertThat(cacheProperties.isEnabled()).isTrue();
        assertThat(cacheProperties.getType()).isEqualTo("caffeine");
        assertThat(cacheProperties.getTtl(SynapseCacheConstants.CACHE_DECRYPTED_SECRETS)).isEqualTo(Duration.ofMinutes(1));
        assertThat(cacheProperties.getMaxSize(SynapseCacheConstants.CACHE_JTI_BLOCKLIST)).isEqualTo(50_000);

        // Test custom override
        cacheProperties.getSpecs().put("custom-cache", new SynapseCacheProperties.CacheSpec(Duration.ofMinutes(45), 250));
        assertThat(cacheProperties.getTtl("custom-cache")).isEqualTo(Duration.ofMinutes(45));
        assertThat(cacheProperties.getMaxSize("custom-cache")).isEqualTo(250);

        // Fallback for unconfigured cache
        assertThat(cacheProperties.getTtl("unknown-cache")).isEqualTo(Duration.ofMinutes(10));
        assertThat(cacheProperties.getMaxSize("unknown-cache")).isEqualTo(1000);
    }

    @Test
    @DisplayName("LoggingCacheErrorHandler swallows and logs cache errors non-destructively")
    void cacheErrorHandlerHandlesErrorsGracefully() {
        CacheErrorHandler errorHandler = cacheConfig.errorHandler();
        assertThat(errorHandler).isInstanceOf(CacheConfig.LoggingCacheErrorHandler.class);

        Cache cache = cacheManager.getCache(SynapseCacheConstants.CACHE_USER_ACCOUNTS);
        RuntimeException ex = new RuntimeException("Simulated Redis timeout");

        assertDoesNotThrow(() -> errorHandler.handleCacheGetError(ex, cache, "testKey"));
        assertDoesNotThrow(() -> errorHandler.handleCachePutError(ex, cache, "testKey", "testVal"));
        assertDoesNotThrow(() -> errorHandler.handleCacheEvictError(ex, cache, "testKey"));
        assertDoesNotThrow(() -> errorHandler.handleCacheClearError(ex, cache));
        assertDoesNotThrow(() -> errorHandler.handleCacheGetError(ex, null, "testKey"));
    }

    @Test
    @DisplayName("CacheController returns summary stats for all active caches")
    void getCacheStats() {
        Cache userCache = cacheManager.getCache(SynapseCacheConstants.CACHE_USER_ACCOUNTS);
        assertThat(userCache).isNotNull();
        userCache.put("admin", "UserAccountData");

        ResponseEntity<java.util.List<CacheController.CacheSummaryDto>> response = cacheController.listCaches();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().stream().map(CacheController.CacheSummaryDto::name))
                .contains(SynapseCacheConstants.CACHE_USER_ACCOUNTS);
    }

    @Test
    @DisplayName("CacheController can retrieve and evict specific keys in cache")
    void getAndEvictSpecificKey() {
        Cache credCache = cacheManager.getCache(SynapseCacheConstants.CACHE_CREDENTIAL_RECORDS);
        assertThat(credCache).isNotNull();
        credCache.put("default:openai", "EncryptedCredRecord");

        ResponseEntity<Map<String, Object>> getResp = cacheController.getCacheEntry(
                SynapseCacheConstants.CACHE_CREDENTIAL_RECORDS, "default:openai");
        assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResp.getBody()).containsEntry("value", "EncryptedCredRecord");

        ResponseEntity<Map<String, Object>> evictResp = cacheController.evictKey(
                SynapseCacheConstants.CACHE_CREDENTIAL_RECORDS, "default:openai");
        assertThat(evictResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.spectrayan.spector.synapse.error.SynapseNotFoundException.class,
                () -> cacheController.getCacheEntry(SynapseCacheConstants.CACHE_CREDENTIAL_RECORDS, "default:openai")
        );
    }

    @Test
    @DisplayName("CacheController clears individual and all caches cleanly")
    void clearCaches() {
        Cache routeCache = cacheManager.getCache(SynapseCacheConstants.CACHE_CONNECTOR_ROUTES);
        assertThat(routeCache).isNotNull();
        routeCache.put("route-1", "RouteConfig1");

        ResponseEntity<Map<String, Object>> clearSingle = cacheController.clearCache(
                SynapseCacheConstants.CACHE_CONNECTOR_ROUTES);
        assertThat(clearSingle.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map<String, Object>> clearAll = cacheController.clearAllCaches();
        assertThat(clearAll.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(clearAll.getBody()).containsEntry("status", "success");
    }
}
