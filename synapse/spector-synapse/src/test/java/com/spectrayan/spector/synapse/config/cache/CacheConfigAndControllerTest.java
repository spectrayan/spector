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
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CacheConfigAndControllerTest {

    private CacheConfig cacheConfig;
    private CacheManager cacheManager;
    private CacheController cacheController;

    @BeforeEach
    void setUp() {
        cacheConfig = new CacheConfig();
        cacheManager = cacheConfig.cacheManager();
        cacheController = new CacheController(cacheManager);
    }

    @Test
    @DisplayName("CacheManager initializes all predefined Synapse caches with Caffeine specs")
    void cacheManagerInitializesAllCaches() {
        assertThat(cacheManager.getCacheNames()).contains(
                SynapseCacheConstants.CACHE_JTI_BLOCKLIST,
                SynapseCacheConstants.CACHE_USER_ACCOUNTS,
                SynapseCacheConstants.CACHE_DECRYPTED_SECRETS,
                SynapseCacheConstants.CACHE_CREDENTIAL_RECORDS,
                SynapseCacheConstants.CACHE_SCOPED_CONFIGS,
                SynapseCacheConstants.CACHE_CONNECTOR_ROUTES,
                SynapseCacheConstants.CACHE_SQL_QUERIES
        );
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
