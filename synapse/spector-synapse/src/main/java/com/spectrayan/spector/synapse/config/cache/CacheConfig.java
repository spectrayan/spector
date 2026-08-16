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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.cache.autoconfigure.CacheManagerCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Cache configuration for Spector Synapse.
 *
 * <p>Enables Spring Boot Cache abstraction driven by {@code spector.cache.*} and {@code spring.cache.*}
 * properties, allowing seamless switching between cache providers (e.g. {@code caffeine}, {@code redis},
 * {@code simple}) without code modifications. Applies a non-blocking {@link CacheErrorHandler}
 * so cache failures log warnings without disrupting the primary application flows.</p>
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(SynapseCacheProperties.class)
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    private final SynapseCacheProperties cacheProperties;

    public CacheConfig(SynapseCacheProperties cacheProperties) {
        this.cacheProperties = cacheProperties != null ? cacheProperties : new SynapseCacheProperties();
    }

    public CacheConfig() {
        this(new SynapseCacheProperties());
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    /**
     * Customizes {@link CaffeineCacheManager} with per-cache TTL and capacity specs
     * from {@link SynapseCacheProperties} when Caffeine is configured.
     */
    @Bean
    @ConditionalOnClass(CaffeineCacheManager.class)
    public CacheManagerCustomizer<CaffeineCacheManager> caffeineCacheManagerCustomizer() {
        return cacheManager -> {
            cacheManager.setCaffeine(Caffeine.newBuilder().recordStats());
            for (String cacheName : SynapseCacheConstants.ALL_CACHES) {
                cacheManager.registerCustomCache(cacheName, Caffeine.newBuilder()
                        .expireAfterWrite(cacheProperties.getTtl(cacheName))
                        .maximumSize(cacheProperties.getMaxSize(cacheName))
                        .recordStats()
                        .build());
            }
        };
    }

    /**
     * Non-blocking CacheErrorHandler that logs warnings on cache failures
     * (get, put, evict, clear) to avoid failing business transactions or database mutations.
     */
    public static class LoggingCacheErrorHandler implements CacheErrorHandler {

        private static final Logger log = LoggerFactory.getLogger(LoggingCacheErrorHandler.class);

        @Override
        public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
            log.warn("[CacheErrorHandler] Cache get failed for cache='{}', key='{}' (falling back to database): {}",
                    cacheName(cache), key, exception.getMessage());
        }

        @Override
        public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
            log.warn("[CacheErrorHandler] Cache put failed for cache='{}', key='{}': {}",
                    cacheName(cache), key, exception.getMessage());
        }

        @Override
        public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
            log.warn("[CacheErrorHandler] Cache evict failed for cache='{}', key='{}': {}",
                    cacheName(cache), key, exception.getMessage());
        }

        @Override
        public void handleCacheClearError(RuntimeException exception, Cache cache) {
            log.warn("[CacheErrorHandler] Cache clear failed for cache='{}': {}",
                    cacheName(cache), exception.getMessage());
        }

        private String cacheName(Cache cache) {
            return cache != null ? cache.getName() : "unknown";
        }
    }
}
