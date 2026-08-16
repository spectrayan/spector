/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.commons.cache;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link SpectorCacheManager} implementation providing {@link TtlConcurrentMapCache}
 * instances configured with shared key generation, serialization, and error policies.
 */
public final class TtlConcurrentMapCacheManager implements SpectorCacheManager {

    private final SpectorCacheKeyGenerator keyGenerator;
    private final SpectorCacheSerializer serializer;
    private final SpectorCacheErrorHandler errorHandler;
    private final Duration defaultTtl;
    private final long defaultMaxSize;
    private final ConcurrentHashMap<String, SpectorCache> caches = new ConcurrentHashMap<>();

    public TtlConcurrentMapCacheManager(SpectorCacheKeyGenerator keyGenerator,
                                        SpectorCacheSerializer serializer,
                                        SpectorCacheErrorHandler errorHandler,
                                        Duration defaultTtl,
                                        long defaultMaxSize) {
        this.keyGenerator = keyGenerator != null ? keyGenerator : SpectorCacheKeyGenerator.identity();
        this.serializer = serializer != null ? serializer : PassthroughCacheSerializer.INSTANCE;
        this.errorHandler = errorHandler != null ? errorHandler : SpectorCacheErrorHandler.LOGGING;
        this.defaultTtl = defaultTtl != null ? defaultTtl : Duration.ofSeconds(30);
        this.defaultMaxSize = defaultMaxSize > 0 ? defaultMaxSize : 1_000L;
    }

    /**
     * Creates a default standalone manager with identity keys, passthrough serialization, and 30s TTL.
     */
    public static TtlConcurrentMapCacheManager defaultManager() {
        return (TtlConcurrentMapCacheManager) SpectorCacheManager.builder().build();
    }

    @Override
    public SpectorCache getCache(String name) {
        Objects.requireNonNull(name, "cache name must not be null");
        return caches.computeIfAbsent(name, n ->
                new TtlConcurrentMapCache(n, keyGenerator, serializer, errorHandler, defaultTtl, defaultMaxSize));
    }

    @Override
    public Collection<String> getCacheNames() {
        return Collections.unmodifiableSet(caches.keySet());
    }
}
