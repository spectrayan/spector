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

/**
 * Pluggable cache key resolution SPI.
 *
 * <p>Enables transparent namespace scoping, multi-tenant partitioning, or contextual
 * key decoration without requiring callers in engine code to manually manage prefixes.</p>
 */
@FunctionalInterface
public interface SpectorCacheKeyGenerator {

    /**
     * Resolves the physical cache key from a logical cache key and cache name.
     *
     * @param cacheName  name of the cache (e.g. {@code memory-graph-overview})
     * @param logicalKey logical key requested by the caller (e.g. {@code overview:50})
     * @return physical key to use in the underlying cache store
     */
    String resolve(String cacheName, String logicalKey);

    /**
     * Identity key generator that leaves the logical key unchanged.
     *
     * @return identity key generator
     */
    static SpectorCacheKeyGenerator identity() {
        return (cacheName, logicalKey) -> logicalKey;
    }

    /**
     * Creates a key generator that prefixes all keys with a static namespace identifier.
     *
     * <p>Format: {@code ns:{namespaceId}:{logicalKey}}</p>
     *
     * @param namespaceId namespace or user identifier
     * @return namespace-prefixed key generator
     */
    static SpectorCacheKeyGenerator forNamespace(String namespaceId) {
        if (namespaceId == null || namespaceId.isBlank() || "default".equals(namespaceId)) {
            return identity();
        }
        String prefix = "ns:" + namespaceId + ":";
        return (cacheName, logicalKey) -> prefix + logicalKey;
    }
}
