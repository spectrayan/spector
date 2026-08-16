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
import java.util.Objects;

/**
 * Central cache manager SPI across the Spector ecosystem.
 *
 * <p>Provides a single configuration point for all caching cross-cutting concerns (key generation,
 * serialization, error handling, default TTLs, capacity) via its {@link Builder}, ensuring
 * no caching policies leak into engine components.</p>
 */
public interface SpectorCacheManager {

    /**
     * Retrieves or creates a named {@link SpectorCache} instance.
     *
     * @param name logical cache name
     * @return cache instance (never null)
     */
    SpectorCache getCache(String name);

    /**
     * Returns the collection of all managed cache names.
     *
     * @return collection of cache names
     */
    Collection<String> getCacheNames();

    /**
     * Creates a new fluent {@link Builder} for constructing a standalone, in-memory {@link SpectorCacheManager}.
     *
     * @return builder instance
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for standalone {@link TtlConcurrentMapCacheManager} instances.
     */
    final class Builder {
        private SpectorCacheKeyGenerator keyGenerator = SpectorCacheKeyGenerator.identity();
        private SpectorCacheSerializer serializer = PassthroughCacheSerializer.INSTANCE;
        private SpectorCacheErrorHandler errorHandler = SpectorCacheErrorHandler.LOGGING;
        private Duration defaultTtl = Duration.ofSeconds(30);
        private long defaultMaxSize = 1_000L;

        private Builder() {}

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

        public Builder defaultTtl(Duration defaultTtl) {
            this.defaultTtl = defaultTtl != null ? defaultTtl : Duration.ofSeconds(30);
            return this;
        }

        public Builder defaultMaxSize(long defaultMaxSize) {
            this.defaultMaxSize = defaultMaxSize > 0 ? defaultMaxSize : 1_000L;
            return this;
        }

        /**
         * Builds a standalone JDK-backed {@link SpectorCacheManager}.
         *
         * @return initialized cache manager
         */
        public SpectorCacheManager build() {
            return new TtlConcurrentMapCacheManager(
                    keyGenerator, serializer, errorHandler, defaultTtl, defaultMaxSize);
        }
    }
}
