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
package com.spectrayan.spector.synapse.cluster;

import com.spectrayan.spector.cluster.routing.ConsistentHashRing;
import com.spectrayan.spector.cluster.routing.RouteBinding;
import com.spectrayan.spector.cluster.routing.RouteMode;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.cluster.routing.cache.RedisRoutingCache;
import com.spectrayan.spector.cluster.routing.cache.WaterfallRoutingResolver;
import com.spectrayan.spector.synapse.cluster.gateway.ForwardRequest;
import com.spectrayan.spector.synapse.cluster.gateway.ForwardResponse;
import com.spectrayan.spector.synapse.cluster.gateway.GatewayForwarder;
import com.spectrayan.spector.synapse.cluster.gateway.GatewayHttpTransport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedRetryGuardTest {

    @Test
    @DisplayName("Req R7.4: Forwarding retry is strictly bounded by retryMax (prevents retry storms)")
    void testForwardingRetryStrictlyBounded() throws Exception {
        String cellId = "cell-1";
        ConsistentHashRing ring = ConsistentHashRing.of(1, List.of("node-1", "node-2", "node-3"));
        RoutingKey key = RoutingKey.ofTenanted(cellId, "tenant-a", "ns-stubborn");

        // Cache always returns a stale entry so every attempt gets 421
        RedisRoutingCache persistentStaleCache = new RedisRoutingCache() {
            @Override
            public Optional<RouteBinding> get(RoutingKey k) {
                return Optional.of(new RouteBinding(k, "node-1", 1L, null, null, RouteMode.HASH));
            }

            @Override
            public boolean putIfAbsent(RoutingKey k, RouteBinding b, long ttl) {
                return true;
            }

            @Override
            public void invalidate(RoutingKey k) {}

            @Override
            public void publishInvalidation(String cell, String nsKey, long epoch, String reason) {}

            @Override
            public boolean isAvailable() {
                return true;
            }
        };

        WaterfallRoutingResolver resolver = new WaterfallRoutingResolver(
                ring, persistentStaleCache, Duration.ofMinutes(1), 1000L, 30L, null, Runnable::run
        );

        AtomicInteger callCount = new AtomicInteger(0);
        GatewayHttpTransport transport = (targetNodeId, targetUrl, req, routingHeaders) -> {
            callCount.incrementAndGet();
            return new ForwardResponse(421, Map.of(), "refused".getBytes(StandardCharsets.UTF_8), targetNodeId, 1);
        };

        int configuredRetryMax = 2;
        GatewayForwarder forwarder = new GatewayForwarder(resolver, configuredRetryMax, transport, null);

        ForwardRequest request = new ForwardRequest("GET", "/api/v1/memory/profile", Map.of(), new byte[0], "idem-key");
        ForwardResponse response = forwarder.forward(key, request);

        // Initial attempt + configuredRetryMax retries = 3 total attempts
        assertThat(response.statusCode()).isEqualTo(421);
        assertThat(callCount.get()).isEqualTo(configuredRetryMax + 1);
        assertThat(response.attempts()).isEqualTo(configuredRetryMax + 1);
    }

    @Test
    @DisplayName("Req R7.6: Do not forward when route came from degraded HASH_FALLBACK path and target refuses")
    void testDegradedFallbackRefusalSurfacedImmediately() throws Exception {
        String cellId = "cell-1";
        ConsistentHashRing ring = ConsistentHashRing.of(1, List.of("node-1", "node-2", "node-3"));
        RoutingKey key = RoutingKey.ofTenanted(cellId, "tenant-a", "ns-degraded");

        // Operating in degraded mode (no Redis, null cache)
        WaterfallRoutingResolver resolver = new WaterfallRoutingResolver(
                ring, null, Duration.ofMinutes(1), 1000L, 30L, null, Runnable::run
        );

        AtomicInteger callCount = new AtomicInteger(0);
        GatewayHttpTransport transport = (targetNodeId, targetUrl, req, routingHeaders) -> {
            callCount.incrementAndGet();
            // Downstream refuses (421)
            return new ForwardResponse(421, Map.of(), "not_owner".getBytes(StandardCharsets.UTF_8), targetNodeId, 1);
        };

        // Even with retryMax = 3, degraded refusal must terminate on attempt 1
        GatewayForwarder forwarder = new GatewayForwarder(resolver, 3, transport, null);

        ForwardRequest request = new ForwardRequest("GET", "/api/v1/memory/profile", Map.of(), new byte[0], "idem-key");
        ForwardResponse response = forwarder.forward(key, request);

        assertThat(response.statusCode()).isEqualTo(421);
        assertThat(callCount.get()).isEqualTo(1);
        assertThat(response.attempts()).isEqualTo(1);
    }
}
