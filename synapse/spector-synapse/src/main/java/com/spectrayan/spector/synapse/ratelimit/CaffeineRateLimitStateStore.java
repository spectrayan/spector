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
package com.spectrayan.spector.synapse.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * High-performance in-memory {@link RateLimitStateStore} backed by Caffeine.
 *
 * <p>Employs an LRU cache with automatic TTL-based expiration after access to prevent
 * memory leakage from ephemeral IP addresses or client identifiers.</p>
 */
public class CaffeineRateLimitStateStore implements RateLimitStateStore {

    private static final Logger log = LoggerFactory.getLogger(CaffeineRateLimitStateStore.class);

    private final Cache<String, Bucket> bucketCache;
    private final Duration ttl;
    private final long maxSize;

    public CaffeineRateLimitStateStore() {
        this(Duration.ofMinutes(15), 50_000);
    }

    public CaffeineRateLimitStateStore(Duration ttl, long maxSize) {
        this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
        this.maxSize = Math.max(100, maxSize);
        this.bucketCache = Caffeine.newBuilder()
                .expireAfterAccess(this.ttl)
                .maximumSize(this.maxSize)
                .recordStats()
                .build();
    }

    @Override
    public Bucket resolveBucket(String key, Bandwidth bandwidth) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(bandwidth, "bandwidth must not be null");

        return bucketCache.get(key, k -> Bucket.builder().addLimit(bandwidth).build());
    }

    @Override
    public Bucket resolveBucket(String key, Supplier<Bucket> bucketSupplier) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(bucketSupplier, "bucketSupplier must not be null");

        return bucketCache.get(key, k -> bucketSupplier.get());
    }

    @Override
    public boolean reset(String key) {
        if (key == null) return false;
        Bucket existing = bucketCache.getIfPresent(key);
        if (existing != null) {
            bucketCache.invalidate(key);
            log.debug("[RateLimitStore] Reset bucket for key: {}", key);
            return true;
        }
        return false;
    }

    @Override
    public void clear() {
        bucketCache.invalidateAll();
        log.info("[RateLimitStore] Invalidate all buckets in cache");
    }

    @Override
    public long activeBucketCount() {
        return bucketCache.estimatedSize();
    }

    @Override
    public Map<String, Object> stats() {
        var cStats = bucketCache.stats();
        Map<String, Object> map = new HashMap<>();
        map.put("backend", backendType());
        map.put("activeBuckets", activeBucketCount());
        map.put("hitCount", cStats.hitCount());
        map.put("missCount", cStats.missCount());
        map.put("evictionCount", cStats.evictionCount());
        map.put("ttlMinutes", ttl.toMinutes());
        map.put("maxSize", maxSize);
        return map;
    }

    @Override
    public String backendType() {
        return "in-memory-caffeine";
    }
}
