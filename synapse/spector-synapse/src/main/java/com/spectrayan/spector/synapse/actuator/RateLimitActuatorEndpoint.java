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
package com.spectrayan.spector.synapse.actuator;

import com.spectrayan.spector.config.properties.RateLimitProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.ratelimit.RateLimitStateStore;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Spring Boot Actuator endpoint ({@code /actuator/ratelimits}) for monitoring,
 * querying, and managing runtime rate limiter state and token buckets.
 */
@Endpoint(id = "ratelimits")
public class RateLimitActuatorEndpoint {

    private final SynapseProperties properties;
    private final RateLimitStateStore stateStore;

    public RateLimitActuatorEndpoint(SynapseProperties properties, RateLimitStateStore stateStore) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore must not be null");
    }

    @ReadOperation
    public Map<String, Object> rateLimitsSummary() {
        RateLimitProperties config = properties.getRateLimit();
        Map<String, Object> result = new HashMap<>();

        result.put("enabled", config != null && config.isEnabled());
        result.put("backend", stateStore.backendType());
        result.put("defaultTier", config != null ? config.getDefaultTier() : "standard");
        result.put("activeBuckets", stateStore.activeBucketCount());
        result.put("storeStats", stateStore.stats());

        if (config != null) {
            result.put("tiers", config.getTiers());
            result.put("endpoints", config.getEndpoints());
            result.put("llmEnabled", config.getLlm().isEnabled());
            result.put("channelsEnabled", config.getChannels().isEnabled());
            result.put("connectorsEnabled", config.getConnectors().isEnabled());
        }

        return result;
    }

    @WriteOperation
    public Map<String, Object> resetBucket(@Nullable String key) {
        Map<String, Object> response = new HashMap<>();
        if (key == null || key.isBlank() || "ALL".equalsIgnoreCase(key)) {
            stateStore.clear();
            response.put("action", "clear_all");
            response.put("success", true);
            response.put("message", "All rate limit buckets have been reset.");
        } else {
            boolean reset = stateStore.reset(key);
            response.put("action", "reset_key");
            response.put("key", key);
            response.put("foundAndReset", reset);
            response.put("message", reset ? "Bucket successfully reset." : "Bucket was not present.");
        }
        return response;
    }
}
