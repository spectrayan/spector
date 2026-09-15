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
package com.spectrayan.spector.commons.pathway;

import com.spectrayan.spector.commons.error.ErrorCode;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default {@link AttributeBag} implementation backed by a {@link ConcurrentHashMap}.
 */
public final class DefaultAttributeBag implements AttributeBag {

    private final Map<Key<?>, Object> storage;

    public DefaultAttributeBag() {
        this.storage = new ConcurrentHashMap<>();
    }

    private DefaultAttributeBag(final Map<Key<?>, Object> storage) {
        this.storage = new ConcurrentHashMap<>(storage);
    }

    @Override
    public <T> void put(final Key<T> key, final T value) {
        Objects.requireNonNull(key, "key cannot be null");
        if (value == null) {
            storage.remove(key);
        } else {
            storage.put(key, value);
        }
    }

    @Override
    public <T> T get(final Key<T> key) {
        Objects.requireNonNull(key, "key cannot be null");
        final Object val = storage.get(key);
        if (val == null) {
            throw new CognitivePathwayException(
                    ErrorCode.MEMORY_PATHWAY_FAILED,
                    "AttributeBag",
                    "get",
                    FaultKind.CONTRACT,
                    false,
                    new IllegalArgumentException("Required attribute key not found: " + key));
        }
        return key.type().cast(val);
    }

    @Override
    public <T> Optional<T> find(final Key<T> key) {
        Objects.requireNonNull(key, "key cannot be null");
        final Object val = storage.get(key);
        return val != null ? Optional.of(key.type().cast(val)) : Optional.empty();
    }

    @Override
    public boolean contains(final Key<?> key) {
        Objects.requireNonNull(key, "key cannot be null");
        return storage.containsKey(key);
    }

    @Override
    public AttributeBag snapshot() {
        return new DefaultAttributeBag(storage);
    }
}
