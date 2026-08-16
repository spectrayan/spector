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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.commons.cache.SpectorCacheSerializer;
import com.spectrayan.spector.memory.DataEncryptor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * {@link SpectorCacheSerializer} implementation that serializes objects to JSON via Jackson
 * and optionally encrypts payload bytes using Spector's {@link DataEncryptor}.
 *
 * <p>Flow:
 * <ul>
 *   <li>Serialize: {@code Object -> JSON bytes -> DataEncryptor.encryptPayload(bytes) -> store}</li>
 *   <li>Deserialize: {@code stored bytes -> DataEncryptor.decryptPayload(bytes) -> JSON -> Object}</li>
 * </ul>
 * </p>
 */
public final class EncryptingJsonCacheSerializer implements SpectorCacheSerializer {

    private final ObjectMapper objectMapper;
    private final DataEncryptor encryptor;

    public EncryptingJsonCacheSerializer(ObjectMapper objectMapper, DataEncryptor encryptor) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.encryptor = encryptor != null ? encryptor : DataEncryptor.NOOP;
    }

    public EncryptingJsonCacheSerializer(ObjectMapper objectMapper) {
        this(objectMapper, DataEncryptor.NOOP);
    }

    @Override
    public byte[] serialize(Object value) {
        if (value == null) {
            return new byte[0];
        }
        try {
            byte[] jsonBytes = objectMapper.writeValueAsBytes(value);
            return encryptor.isEnabled() ? encryptor.encryptPayload(jsonBytes) : jsonBytes;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize cache value to JSON", e);
        }
    }

    @Override
    public <T> T deserialize(byte[] data, Class<T> targetClass) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            byte[] jsonBytes = encryptor.isEnabled() ? encryptor.decryptPayload(data) : data;
            return objectMapper.readValue(jsonBytes, targetClass);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to deserialize cache value from JSON for " + targetClass.getName(), e);
        }
    }

    @Override
    public boolean isEncryptionEnabled() {
        return encryptor.isEnabled();
    }
}
