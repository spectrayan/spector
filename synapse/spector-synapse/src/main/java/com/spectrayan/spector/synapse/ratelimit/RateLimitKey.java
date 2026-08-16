/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
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
