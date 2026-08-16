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
 * Serialization and encryption SPI for cache values.
 *
 * <p>Enables transparent payload serialization and encryption-at-rest when cache values
 * are stored in an external distributed cache (such as Redis). Integrates with Spector's
 * encryption architecture to support per-user and per-tenant encryption keys.</p>
 */
public interface SpectorCacheSerializer {

    /**
     * Serializes a cache value into raw bytes (optionally encrypting).
     *
     * @param value value object to serialize
     * @return serialized (and optionally encrypted) bytes
     */
    byte[] serialize(Object value);

    /**
     * Deserializes raw bytes back into an object of the target class (optionally decrypting).
     *
     * @param data        raw bytes from cache
     * @param targetClass expected target type
     * @param <T>         target type
     * @return deserialized value
     */
    <T> T deserialize(byte[] data, Class<T> targetClass);

    /**
     * Returns whether this serializer performs active encryption.
     *
     * @return {@code true} if encryption is enabled, {@code false} otherwise
     */
    default boolean isEncryptionEnabled() {
        return false;
    }
}
