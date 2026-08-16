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

import com.spectrayan.spector.synapse.actuator.RateLimitActuatorEndpoint;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Refill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitActuatorEndpointTest {

    private SynapseProperties properties;
    private RateLimitStateStore stateStore;
    private RateLimitActuatorEndpoint endpoint;

    @BeforeEach
    void setUp() {
        properties = new SynapseProperties();
        properties.getRateLimit().setEnabled(true);
        stateStore = new CaffeineRateLimitStateStore(Duration.ofMinutes(5), 1000);
        endpoint = new RateLimitActuatorEndpoint(properties, stateStore);
    }

    @Test
    @DisplayName("Should return comprehensive rate limit configuration and runtime state")
    void testReadOperationSummary() {
        Map<String, Object> summary = endpoint.rateLimitsSummary();

        assertThat(summary).containsEntry("enabled", true);
        assertThat(summary).containsEntry("backend", "in-memory-caffeine");
        assertThat(summary).containsEntry("defaultTier", "standard");
        assertThat(summary).containsKey("activeBuckets");
        assertThat(summary).containsKey("storeStats");
    }

    @Test
    @DisplayName("Should successfully reset specific bucket and clear all buckets")
    void testResetOperations() {
        // Populate a bucket
        stateStore.resolveBucket("ip:192.168.1.1", Bandwidth.classic(10, Refill.greedy(10, Duration.ofSeconds(1))));
        assertThat(stateStore.activeBucketCount()).isGreaterThan(0);

        // Reset specific key
        Map<String, Object> resetResp = endpoint.resetBucket("ip:192.168.1.1");
        assertThat(resetResp).containsEntry("foundAndReset", true);

        // Reset unknown key
        Map<String, Object> resetUnknown = endpoint.resetBucket("ip:10.0.0.99");
        assertThat(resetUnknown).containsEntry("foundAndReset", false);

        // Clear all
        Map<String, Object> clearAll = endpoint.resetBucket("ALL");
        assertThat(clearAll).containsEntry("success", true);
        assertThat(stateStore.activeBucketCount()).isEqualTo(0);
    }
}
