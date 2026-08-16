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
package com.spectrayan.spector.spring.cache;

import com.spectrayan.spector.commons.cache.NoOpSpectorCache;
import com.spectrayan.spector.commons.cache.PassthroughCacheSerializer;
import com.spectrayan.spector.commons.cache.SpectorCache;
import com.spectrayan.spector.commons.cache.SpectorCacheErrorHandler;
import com.spectrayan.spector.commons.cache.SpectorCacheKeyGenerator;
import com.spectrayan.spector.commons.cache.SpectorCacheManager;
import com.spectrayan.spector.commons.cache.SpectorCacheSerializer;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter that implements the engine's {@link SpectorCacheManager} SPI by wrapping a Spring Boot
 * {@link org.springframework.cache.CacheManager} (Caffeine, Redis, etc.) and providing a fluent
 * builder for per-user and per-tenant scoping.
 */
public final class SpringSpectorCacheManagerAdapter implements SpectorCacheManager {

    private final CacheManager springManager;
    private final SpectorCacheKeyGenerator keyGenerator;
    private final SpectorCacheSerializer serializer;
    private final SpectorCacheErrorHandler errorHandler;
    private final ConcurrentHashMap<String, SpectorCache> caches = new ConcurrentHashMap<>();

    public SpringSpectorCacheManagerAdapter(CacheManager springManager,
                                            SpectorCacheKeyGenerator keyGenerator,
                                            SpectorCacheSerializer serializer,
                                            SpectorCacheErrorHandler errorHandler) {
        this.springManager = Objects.requireNonNull(springManager, "springManager must not be null");
        this.keyGenerator = keyGenerator != null ? keyGenerator : SpectorCacheKeyGenerator.identity();
        this.serializer = serializer != null ? serializer : PassthroughCacheSerializer.INSTANCE;
        this.errorHandler = errorHandler != null ? errorHandler : SpectorCacheErrorHandler.LOGGING;
    }

    public static Builder builder(CacheManager springManager) {
        return new Builder(springManager);
    }

    /**
     * Builder for constructing configured {@link SpringSpectorCacheManagerAdapter} instances.
     */
    public static final class Builder {
        private final CacheManager springManager;
        private SpectorCacheKeyGenerator keyGenerator = SpectorCacheKeyGenerator.identity();
        private SpectorCacheSerializer serializer = PassthroughCacheSerializer.INSTANCE;
        private SpectorCacheErrorHandler errorHandler = SpectorCacheErrorHandler.LOGGING;

        private Builder(CacheManager springManager) {
            this.springManager = Objects.requireNonNull(springManager, "springManager must not be null");
        }

        public Builder keyGenerator(SpectorCacheKeyGenerator keyGenerator) {
            this.keyGenerator = keyGenerator != null ? keyGenerator : SpectorCacheKeyGenerator.identity();
            return this;
        }

        public Builder serializer(SpectorCacheSerializer serializer) {
            this.serializer = serializer != null ? serializer : PassthroughCacheSerializer.INSTANCE;
            return this;
        }

        public Builder errorHandler(SpectorCacheErrorHandler errorHandler) {
            this.errorHandler = errorHandler != null ? errorHandler : SpectorCacheErrorHandler.LOGGING;
            return this;
        }

        public SpectorCacheManager build() {
            return new SpringSpectorCacheManagerAdapter(springManager, keyGenerator, serializer, errorHandler);
        }
    }

    @Override
    public SpectorCache getCache(String name) {
        Objects.requireNonNull(name, "cache name must not be null");
        return caches.computeIfAbsent(name, n -> {
            Cache springCache = springManager.getCache(n);
            return springCache != null
                    ? new SpringSpectorCacheAdapter(springCache, keyGenerator, serializer, errorHandler)
                    : new NoOpSpectorCache(n);
        });
    }

    @Override
    public Collection<String> getCacheNames() {
        return springManager.getCacheNames();
    }
}
