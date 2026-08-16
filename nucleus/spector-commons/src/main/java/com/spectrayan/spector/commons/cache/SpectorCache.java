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

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Pluggable cache contract for Spector subsystems.
 *
 * <p>Provides a clean, type-safe caching abstraction with zero framework dependencies,
 * allowing core memory engine modules to remain standalone while enabling seamless
 * delegation to enterprise distributed cache managers (e.g. Spring Boot Redis/Caffeine).</p>
 */
public interface SpectorCache {

    /**
     * Returns the logical name of this cache.
     *
     * @return cache name
     */
    String getName();

    /**
     * Retrieves a cached value by key, cast to the expected target class.
     *
     * @param key         logical cache key
     * @param targetClass expected value type
     * @param <T>         target type
     * @return Optional containing the cached value, or empty if not found or expired
     */
    <T> Optional<T> get(String key, Class<T> targetClass);

    /**
     * Atomically retrieves a cached value by key or computes and stores it via the provided loader.
     *
     * @param key         logical cache key
     * @param targetClass expected value type
     * @param valueLoader supplier to invoke on cache miss
     * @param <T>         target type
     * @return cached or newly computed value
     */
    <T> T get(String key, Class<T> targetClass, Supplier<T> valueLoader);

    /**
     * Associates the specified value with the specified key in this cache.
     *
     * @param key   logical cache key
     * @param value value to cache (must not be null)
     */
    void put(String key, Object value);

    /**
     * Atomically associates the specified value with the specified key if the key is not already present.
     *
     * @param key         logical cache key
     * @param value       value to cache
     * @param targetClass expected value type
     * @param <T>         target type
     * @return existing cached value if already present, or {@code null} if inserted
     */
    <T> T putIfAbsent(String key, Object value, Class<T> targetClass);

    /**
     * Evicts the mapping for the specified key from this cache if present.
     *
     * @param key logical cache key
     */
    void evict(String key);

    /**
     * Clears all entries from this cache.
     */
    void clear();
}
