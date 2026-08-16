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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * No-op implementation of {@link SpectorCache} that performs no caching.
 * Always misses on reads and invokes the value loader directly.
 */
public final class NoOpSpectorCache implements SpectorCache {

    private final String name;

    public NoOpSpectorCache(String name) {
        this.name = Objects.requireNonNull(name, "name must not be null");
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> targetClass) {
        return Optional.empty();
    }

    @Override
    public <T> T get(String key, Class<T> targetClass, Supplier<T> valueLoader) {
        return valueLoader != null ? valueLoader.get() : null;
    }

    @Override
    public void put(String key, Object value) {
        // no-op
    }

    @Override
    public <T> T putIfAbsent(String key, Object value, Class<T> targetClass) {
        return null;
    }

    @Override
    public void evict(String key) {
        // no-op
    }

    @Override
    public void clear() {
        // no-op
    }
}
