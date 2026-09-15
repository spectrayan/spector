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

import java.util.Optional;

/**
 * Type-safe attribute store for cross-relay and cross-pathway scratchpad state.
 */
public interface AttributeBag {

    /**
     * Associates the specified value with the given key.
     *
     * @param key   typed key
     * @param value value to store
     * @param <T>   value type
     */
    <T> void put(Key<T> key, T value);

    /**
     * Returns the value associated with the specified key, or throws {@link CognitivePathwayException}
     * with {@link FaultKind#CONTRACT} if the key is absent.
     *
     * @param key typed key
     * @param <T> value type
     * @return value associated with key
     * @throws CognitivePathwayException if key is absent
     */
    <T> T get(Key<T> key);

    /**
     * Finds the value associated with the specified key, returning empty if absent.
     *
     * @param key typed key
     * @param <T> value type
     * @return optional containing the value if present
     */
    <T> Optional<T> find(Key<T> key);

    /**
     * Returns true if the specified key exists in this bag.
     *
     * @param key typed key
     * @return true if key exists
     */
    boolean contains(Key<?> key);

    /**
     * Creates a shallow copy of this attribute bag.
     *
     * @return snapshot copy
     */
    AttributeBag snapshot();

    /**
     * Creates a new, empty concurrent attribute bag.
     *
     * @return empty attribute bag
     */
    static AttributeBag create() {
        return new DefaultAttributeBag();
    }
}
