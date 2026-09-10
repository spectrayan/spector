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
package com.spectrayan.spector.kernel.id;

import com.spectrayan.spector.kernel.id.MemoryId;

import java.util.UUID;

/**
 * UUID v4 (random) generator.
 *
 * <p>Uses {@link UUID#randomUUID()} which is backed by {@code SecureRandom}.
 * Produces 36-character strings in the standard {@code 8-4-4-4-12} format.</p>
 *
 * <p>Slowest generation (~200ns) and longest string (worst ConcurrentHashMap
 * performance). Use only when UUID format compatibility is required.</p>
 *
 * @see IdStrategy#UUID
 */
public final class UuidGenerator implements MemoryIdGenerator {

    @Override
    public String generate() {
        return UUID.randomUUID().toString();
    }
}
