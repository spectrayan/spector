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
package com.spectrayan.spector.synapse.ratelimit;

import java.util.Objects;

/**
 * Resolved rate limit identifier and tier classification for an incoming request.
 */
public record RateLimitKey(KeyType type, String value, String tier) {

    public RateLimitKey {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
        tier = (tier == null || tier.isBlank()) ? "standard" : tier;
    }

    public enum KeyType {
        API_KEY,
        USER,
        TENANT,
        IP,
        GLOBAL
    }

    public String cacheKey() {
        return type.name().toLowerCase() + ":" + value;
    }
}
