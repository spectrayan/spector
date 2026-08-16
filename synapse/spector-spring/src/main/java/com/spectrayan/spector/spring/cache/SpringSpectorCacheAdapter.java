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

import com.spectrayan.spector.commons.cache.PassthroughCacheSerializer;
import com.spectrayan.spector.commons.cache.SpectorCache;
import com.spectrayan.spector.commons.cache.SpectorCacheErrorHandler;
import com.spectrayan.spector.commons.cache.SpectorCacheKeyGenerator;
import com.spectrayan.spector.commons.cache.SpectorCacheSerializer;
import org.springframework.cache.Cache;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Adapter that implements the engine's {@link SpectorCache} SPI by delegating to a Spring
 * {@link org.springframework.cache.Cache} instance while applying key resolution, optional
 * encrypted serialization, and resilient error handling.
 */
public final class SpringSpectorCacheAdapter implements SpectorCache {

    private final Cache delegate;
    private final SpectorCacheKeyGenerator keyGenerator;
    private final SpectorCacheSerializer serializer;
    private final SpectorCacheErrorHandler errorHandler;

    public SpringSpectorCacheAdapter(Cache delegate,
                                     SpectorCacheKeyGenerator keyGenerator,
                                     SpectorCacheSerializer serializer,
                                     SpectorCacheErrorHandler errorHandler) {
        this.delegate = Objects.requireNonNull(delegate, "delegate Cache must not be null");
        this.keyGenerator = keyGenerator != null ? keyGenerator : SpectorCacheKeyGenerator.identity();
        this.serializer = serializer != null ? serializer : PassthroughCacheSerializer.INSTANCE;
        this.errorHandler = errorHandler != null ? errorHandler : SpectorCacheErrorHandler.LOGGING;
    }

    public SpringSpectorCacheAdapter(Cache delegate) {
        this(delegate, SpectorCacheKeyGenerator.identity(), PassthroughCacheSerializer.INSTANCE, SpectorCacheErrorHandler.LOGGING);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> targetClass) {
        String resolved = keyGenerator.resolve(getName(), key);
        try {
            if (serializer.isEncryptionEnabled()) {
                byte[] raw = delegate.get(resolved, byte[].class);
                return raw != null ? Optional.ofNullable(serializer.deserialize(raw, targetClass)) : Optional.empty();
            }
            return Optional.ofNullable(delegate.get(resolved, targetClass));
        } catch (Throwable t) {
            errorHandler.onCacheError(getName(), "get", key, t);
            return Optional.empty();
        }
    }

    @Override
    public <T> T get(String key, Class<T> targetClass, Supplier<T> valueLoader) {
        String resolved = keyGenerator.resolve(getName(), key);
        try {
            if (serializer.isEncryptionEnabled()) {
                byte[] raw = delegate.get(resolved, () -> {
                    T val = valueLoader != null ? valueLoader.get() : null;
                    return val != null ? serializer.serialize(val) : null;
                });
                return raw != null ? serializer.deserialize(raw, targetClass) : (valueLoader != null ? valueLoader.get() : null);
            }
            return delegate.get(resolved, valueLoader != null ? valueLoader::get : () -> null);
        } catch (Throwable t) {
            errorHandler.onCacheError(getName(), "get", key, t);
            return valueLoader != null ? valueLoader.get() : null;
        }
    }

    @Override
    public void put(String key, Object value) {
        if (value == null) {
            return;
        }
        String resolved = keyGenerator.resolve(getName(), key);
        try {
            Object toStore = serializer.isEncryptionEnabled() ? serializer.serialize(value) : value;
            delegate.put(resolved, toStore);
        } catch (Throwable t) {
            errorHandler.onCacheError(getName(), "put", key, t);
        }
    }

    @Override
    public <T> T putIfAbsent(String key, Object value, Class<T> targetClass) {
        if (value == null) {
            return null;
        }
        String resolved = keyGenerator.resolve(getName(), key);
        try {
            Object toStore = serializer.isEncryptionEnabled() ? serializer.serialize(value) : value;
            Cache.ValueWrapper prev = delegate.putIfAbsent(resolved, toStore);
            if (prev != null) {
                Object raw = prev.get();
                if (serializer.isEncryptionEnabled() && raw instanceof byte[] bytes) {
                    return serializer.deserialize(bytes, targetClass);
                }
                return targetClass.cast(raw);
            }
            return null;
        } catch (Throwable t) {
            errorHandler.onCacheError(getName(), "putIfAbsent", key, t);
            return null;
        }
    }

    @Override
    public void evict(String key) {
        String resolved = keyGenerator.resolve(getName(), key);
        try {
            delegate.evict(resolved);
        } catch (Throwable t) {
            errorHandler.onCacheError(getName(), "evict", key, t);
        }
    }

    @Override
    public void clear() {
        try {
            delegate.clear();
        } catch (Throwable t) {
            errorHandler.onCacheError(getName(), "clear", "*", t);
        }
    }
}
