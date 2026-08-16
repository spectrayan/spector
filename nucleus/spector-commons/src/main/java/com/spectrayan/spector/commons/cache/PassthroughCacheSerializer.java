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
 * No-op passthrough {@link SpectorCacheSerializer} for in-memory caches where objects are
 * stored directly by reference without serialization or encryption overhead.
 */
public final class PassthroughCacheSerializer implements SpectorCacheSerializer {

    public static final PassthroughCacheSerializer INSTANCE = new PassthroughCacheSerializer();

    private PassthroughCacheSerializer() {}

    @Override
    public byte[] serialize(Object value) {
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        throw new UnsupportedOperationException(
                "PassthroughCacheSerializer does not serialize non-byte objects. Use a JSON or binary serializer for remote caches.");
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T deserialize(byte[] data, Class<T> targetClass) {
        if (targetClass.isInstance(data)) {
            return (T) data;
        }
        throw new UnsupportedOperationException(
                "PassthroughCacheSerializer does not deserialize raw bytes to " + targetClass.getName());
    }

    @Override
    public boolean isEncryptionEnabled() {
        return false;
    }
}
