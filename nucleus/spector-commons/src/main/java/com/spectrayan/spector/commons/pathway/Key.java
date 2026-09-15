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

import java.util.Objects;

/**
 * Type-safe key for values stored in {@link AttributeBag} and {@link PathwayContext}.
 *
 * @param <T> the type of value associated with this key
 */
public final class Key<T> {

    private final String id;
    private final Class<T> type;

    private Key(final String id, final Class<T> type) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
    }

    /**
     * Creates a new typed key.
     *
     * @param id   unique identifier
     * @param type value class
     * @param <T>  value type
     * @return key instance
     */
    public static <T> Key<T> of(final String id, final Class<T> type) {
        return new Key<>(id, type);
    }

    /**
     * Returns the key identifier.
     *
     * @return id
     */
    public String id() {
        return id;
    }

    /**
     * Returns the expected value type.
     *
     * @return class
     */
    public Class<T> type() {
        return type;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Key<?> key = (Key<?>) o;
        return Objects.equals(id, key.id) && Objects.equals(type, key.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type);
    }

    @Override
    public String toString() {
        return "Key[" + id + ":" + type.getSimpleName() + "]";
    }
}
