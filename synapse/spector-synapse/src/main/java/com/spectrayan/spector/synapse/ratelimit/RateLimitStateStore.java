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

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Storage SPI for rate limiter state and token buckets.
 *
 * <p>Enables pluggable in-memory (Caffeine) or distributed cloud backends (Redis/Hazelcast)
 * with zero code changes in upper layers.</p>
 */
public interface RateLimitStateStore {

    /**
     * Resolves or creates a token bucket for the specified cache key using the bandwidth limit.
     *
     * @param key cache key identifying caller/endpoint
     * @param bandwidth bandwidth configuration
     * @return active Bucket4j Bucket
     */
    Bucket resolveBucket(String key, Bandwidth bandwidth);

    /**
     * Resolves or creates a token bucket with a custom bucket supplier.
     *
     * @param key cache key identifying caller/endpoint
     * @param bucketSupplier supplier for creating new Bucket if absent
     * @return active Bucket4j Bucket
     */
    Bucket resolveBucket(String key, Supplier<Bucket> bucketSupplier);

    /**
     * Resets/evicts the bucket for the given key (e.g. via admin action).
     *
     * @param key the key to reset
     * @return true if key was present and removed
     */
    boolean reset(String key);

    /**
     * Clears all stored buckets.
     */
    void clear();

    /**
     * Returns the number of currently active/cached buckets.
     */
    long activeBucketCount();

    /**
     * Returns statistics and metadata about the storage backend.
     */
    Map<String, Object> stats();

    /**
     * Returns backend identifier (e.g. "in-memory-caffeine", "distributed-redis").
     */
    String backendType();
}
