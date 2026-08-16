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
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Thread-safe, bounded, TTL-expiring in-memory implementation of {@link SpectorCache} backed
 * by pure JDK {@link ConcurrentHashMap}.
 *
 * <p>Used as the zero-dependency default cache in standalone / CLI / embedded deployments
 * where Spring Boot or external cache infrastructure is not present.</p>
 */
public final class TtlConcurrentMapCache implements SpectorCache {

    private record Entry(Object value, long expiresAtMs) {
        boolean isExpired(long now) {
            return expiresAtMs > 0 && now >= expiresAtMs;
        }
    }

    private final String name;
    private final SpectorCacheKeyGenerator keyGenerator;
    private final SpectorCacheSerializer serializer;
    private final SpectorCacheErrorHandler errorHandler;
    private final long ttlMs;
    private final long maxSize;
    private final ConcurrentHashMap<String, Entry> store;

    public TtlConcurrentMapCache(String name,
                                 SpectorCacheKeyGenerator keyGenerator,
                                 SpectorCacheSerializer serializer,
                                 SpectorCacheErrorHandler errorHandler,
                                 Duration ttl,
                                 long maxSize) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.keyGenerator = keyGenerator != null ? keyGenerator : SpectorCacheKeyGenerator.identity();
        this.serializer = serializer != null ? serializer : PassthroughCacheSerializer.INSTANCE;
        this.errorHandler = errorHandler != null ? errorHandler : SpectorCacheErrorHandler.LOGGING;
        this.ttlMs = ttl != null ? ttl.toMillis() : 0L;
        this.maxSize = maxSize > 0 ? maxSize : 1_000L;
        this.store = new ConcurrentHashMap<>();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> targetClass) {
        String resolvedKey = keyGenerator.resolve(name, key);
        try {
            long now = System.currentTimeMillis();
            Entry entry = store.get(resolvedKey);
            if (entry == null) {
                return Optional.empty();
            }
            if (entry.isExpired(now)) {
                store.remove(resolvedKey, entry);
                return Optional.empty();
            }
            return Optional.ofNullable(castValue(entry.value(), targetClass));
        } catch (Throwable t) {
            errorHandler.onCacheError(name, "get", key, t);
            return Optional.empty();
        }
    }

    @Override
    public <T> T get(String key, Class<T> targetClass, Supplier<T> valueLoader) {
        String resolvedKey = keyGenerator.resolve(name, key);
        try {
            long now = System.currentTimeMillis();
            Entry entry = store.get(resolvedKey);
            if (entry != null && !entry.isExpired(now)) {
                return castValue(entry.value(), targetClass);
            }

            // Compute outside lock or via atomic update
            T loaded = valueLoader != null ? valueLoader.get() : null;
            if (loaded != null) {
                put(key, loaded);
            }
            return loaded;
        } catch (Throwable t) {
            errorHandler.onCacheError(name, "get", key, t);
            return valueLoader != null ? valueLoader.get() : null;
        }
    }

    @Override
    public void put(String key, Object value) {
        if (value == null) {
            return;
        }
        String resolvedKey = keyGenerator.resolve(name, key);
        try {
            ensureCapacity();
            long now = System.currentTimeMillis();
            long expiresAt = ttlMs > 0 ? now + ttlMs : 0L;

            Object storedValue = serializer.isEncryptionEnabled()
                    ? serializer.serialize(value)
                    : value;

            store.put(resolvedKey, new Entry(storedValue, expiresAt));
        } catch (Throwable t) {
            errorHandler.onCacheError(name, "put", key, t);
        }
    }

    @Override
    public <T> T putIfAbsent(String key, Object value, Class<T> targetClass) {
        if (value == null) {
            return null;
        }
        String resolvedKey = keyGenerator.resolve(name, key);
        try {
            long now = System.currentTimeMillis();
            Entry existing = store.get(resolvedKey);
            if (existing != null && !existing.isExpired(now)) {
                return castValue(existing.value(), targetClass);
            }

            ensureCapacity();
            long expiresAt = ttlMs > 0 ? now + ttlMs : 0L;
            Object storedValue = serializer.isEncryptionEnabled()
                    ? serializer.serialize(value)
                    : value;

            Entry prev = store.putIfAbsent(resolvedKey, new Entry(storedValue, expiresAt));
            if (prev != null && !prev.isExpired(now)) {
                return castValue(prev.value(), targetClass);
            }
            return null;
        } catch (Throwable t) {
            errorHandler.onCacheError(name, "putIfAbsent", key, t);
            return null;
        }
    }

    @Override
    public void evict(String key) {
        String resolvedKey = keyGenerator.resolve(name, key);
        try {
            store.remove(resolvedKey);
        } catch (Throwable t) {
            errorHandler.onCacheError(name, "evict", key, t);
        }
    }

    @Override
    public void clear() {
        try {
            store.clear();
        } catch (Throwable t) {
            errorHandler.onCacheError(name, "clear", "*", t);
        }
    }

    /**
     * Returns the number of currently active (non-expired) entries.
     */
    public int size() {
        long now = System.currentTimeMillis();
        evictExpired(now);
        return store.size();
    }

    private void ensureCapacity() {
        if (store.size() >= maxSize) {
            long now = System.currentTimeMillis();
            evictExpired(now);
            if (store.size() >= maxSize) {
                // Remove oldest entry
                Iterator<String> it = store.keySet().iterator();
                if (it.hasNext()) {
                    store.remove(it.next());
                }
            }
        }
    }

    private void evictExpired(long now) {
        store.entrySet().removeIf(e -> e.getValue().isExpired(now));
    }

    @SuppressWarnings("unchecked")
    private <T> T castValue(Object raw, Class<T> targetClass) {
        if (raw == null) {
            return null;
        }
        if (serializer.isEncryptionEnabled() && raw instanceof byte[] bytes) {
            return serializer.deserialize(bytes, targetClass);
        }
        if (targetClass.isInstance(raw)) {
            return (T) raw;
        }
        throw new ClassCastException("Cannot cast cached value of type " + raw.getClass().getName()
                + " to " + targetClass.getName());
    }
}
