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

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.spectrayan.spector.synapse.error.SynapseNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Administrative REST Controller for inspecting, managing, and clearing Synapse in-memory caches.
 */
@RestController
@RequestMapping({"/api/v1/admin/cache", "/api/v1/cache"})
public class CacheController {

    private static final Logger log = LoggerFactory.getLogger(CacheController.class);

    private final CacheManager cacheManager;

    public CacheController(CacheManager cacheManager) {
        this.cacheManager = Objects.requireNonNull(cacheManager, "cacheManager must not be null");
    }

    /**
     * DTO for cache statistics summary.
     */
    public record CacheSummaryDto(
            String name,
            long estimatedSize,
            long hitCount,
            long missCount,
            double hitRate,
            long evictionCount
    ) {}

    /**
     * DTO for detailed cache details.
     */
    public record CacheDetailDto(
            String name,
            long estimatedSize,
            long hitCount,
            long missCount,
            double hitRate,
            long evictionCount,
            Set<Object> keys
    ) {}

    /**
     * Lists all registered caches and their real-time hit/miss metrics.
     */
    @GetMapping
    public ResponseEntity<List<CacheSummaryDto>> listCaches() {
        Collection<String> cacheNames = cacheManager.getCacheNames();
        List<CacheSummaryDto> summaries = new ArrayList<>();

        for (String name : cacheNames) {
            Cache cache = cacheManager.getCache(name);
            if (cache instanceof CaffeineCache caffeineCache) {
                var nativeCache = caffeineCache.getNativeCache();
                CacheStats stats = nativeCache.stats();
                summaries.add(new CacheSummaryDto(
                        name,
                        nativeCache.estimatedSize(),
                        stats.hitCount(),
                        stats.missCount(),
                        stats.hitRate(),
                        stats.evictionCount()
                ));
            } else if (cache != null) {
                summaries.add(new CacheSummaryDto(name, -1, 0, 0, 0.0, 0));
            }
        }

        return ResponseEntity.ok(summaries);
    }

    /**
     * Retrieves detailed metrics and cached keys for a specific cache by name.
     */
    @GetMapping("/{cacheName}")
    public ResponseEntity<CacheDetailDto> getCacheDetails(@PathVariable String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            throw new SynapseNotFoundException("Cache", cacheName);
        }

        if (cache instanceof CaffeineCache caffeineCache) {
            var nativeCache = caffeineCache.getNativeCache();
            CacheStats stats = nativeCache.stats();
            return ResponseEntity.ok(new CacheDetailDto(
                    cacheName,
                    nativeCache.estimatedSize(),
                    stats.hitCount(),
                    stats.missCount(),
                    stats.hitRate(),
                    stats.evictionCount(),
                    nativeCache.asMap().keySet()
            ));
        }

        return ResponseEntity.ok(new CacheDetailDto(cacheName, -1, 0, 0, 0.0, 0, Set.of()));
    }

    /**
     * Inspects a specific key inside a given cache.
     */
    @GetMapping("/{cacheName}/{key}")
    public ResponseEntity<Map<String, Object>> getCacheEntry(
            @PathVariable String cacheName,
            @PathVariable String key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            throw new SynapseNotFoundException("Cache", cacheName);
        }

        Cache.ValueWrapper wrapper = cache.get(key);
        if (wrapper == null) {
            throw new SynapseNotFoundException("CacheEntry[" + cacheName + "]", key);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("cache", cacheName);
        response.put("key", key);
        response.put("value", wrapper.get());
        return ResponseEntity.ok(response);
    }

    /**
     * Clears all entries across ALL registered caches.
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> clearAllCaches() {
        Collection<String> cacheNames = cacheManager.getCacheNames();
        for (String name : cacheNames) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
        log.info("[CacheAdmin] Cleared all caches ({})", cacheNames);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Cleared all caches",
                "clearedCaches", cacheNames
        ));
    }

    /**
     * Clears an entire cache by name.
     */
    @DeleteMapping("/{cacheName}")
    public ResponseEntity<Map<String, Object>> clearCache(@PathVariable String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            throw new SynapseNotFoundException("Cache", cacheName);
        }

        cache.clear();
        log.info("[CacheAdmin] Cleared cache '{}'", cacheName);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Cleared cache '" + cacheName + "'"
        ));
    }

    /**
     * Evicts a single key from a specific cache.
     */
    @DeleteMapping("/{cacheName}/{key}")
    public ResponseEntity<Map<String, Object>> evictKey(
            @PathVariable String cacheName,
            @PathVariable String key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            throw new SynapseNotFoundException("Cache", cacheName);
        }

        cache.evict(key);
        log.info("[CacheAdmin] Evicted key '{}' from cache '{}'", key, cacheName);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Evicted key '" + key + "' from cache '" + cacheName + "'"
        ));
    }
}
