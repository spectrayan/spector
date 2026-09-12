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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayForwardingTest {

    @Test
    @DisplayName("Req R7.1, R7.2: Gateway resolves owner and propagates routing headers")
    void testGatewayResolvesAndPropagatesRoutingHeaders() throws Exception {
        String cellId = "cell-1";
        ConsistentHashRing ring = ConsistentHashRing.of(1, List.of("node-1", "node-2", "node-3"));
        WaterfallRoutingResolver resolver = new WaterfallRoutingResolver(
                ring, null, Duration.ofMinutes(1), 1000L, 30L, null, Runnable::run
        );

        List<Map<String, String>> capturedHeaders = new ArrayList<>();
        GatewayHttpTransport transport = (targetNodeId, targetUrl, req, routingHeaders) -> {
            capturedHeaders.add(routingHeaders);
            return new ForwardResponse(200, Map.of(), "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8), targetNodeId, 1);
        };

        GatewayForwarder forwarder = new GatewayForwarder(resolver, 2, transport, nodeId -> "http://" + nodeId + ":7070");
        RoutingKey key = RoutingKey.ofTenanted(cellId, "tenant-a", "ns-test");

        ForwardRequest request = new ForwardRequest("POST", "/api/v1/memory/remember", Map.of(), new byte[0], "idem-key-123");
        ForwardResponse response = forwarder.forward(key, request);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(capturedHeaders).hasSize(1);
        Map<String, String> headers = capturedHeaders.get(0);
        assertThat(headers.get(GatewayForwarder.HEADER_NAMESPACE)).isEqualTo("ns-test");
        assertThat(headers.get(GatewayForwarder.HEADER_TENANT)).isEqualTo("tenant-a");
        assertThat(headers.get(GatewayForwarder.HEADER_EPOCH)).isEqualTo("1");
    }

    @Test
    @DisplayName("Req R12.4, R7.4, R7.5: Stale epoch -> 421 -> invalidation -> retry -> success, preserving idempotency key")
    void testStaleEpochInvalidationRetryAndIdempotencyPreservation() throws Exception {
        String cellId = "cell-1";
        ConsistentHashRing ring = ConsistentHashRing.of(2, List.of("node-1", "node-2", "node-3"));

        // Mock Redis cache holding stale route at epoch 1 pointing to old node
        RoutingKey key = RoutingKey.ofTenanted(cellId, "tenant-a", "ns-migrated");
        RouteBinding staleBinding = new RouteBinding(key, "node-old", 1L, null, null, RouteMode.HASH);

        Map<RoutingKey, RouteBinding> fakeRedisStorage = new ConcurrentHashMap<>();
        fakeRedisStorage.put(key, staleBinding);

        RedisRoutingCache mockRedis = new RedisRoutingCache() {
            @Override
            public Optional<RouteBinding> get(RoutingKey k) {
                return Optional.ofNullable(fakeRedisStorage.get(k));
            }

            @Override
            public boolean putIfAbsent(RoutingKey k, RouteBinding b, long ttl) {
                return fakeRedisStorage.putIfAbsent(k, b) == null;
            }

            @Override
            public void invalidate(RoutingKey k) {
                fakeRedisStorage.remove(k);
            }

            @Override
            public void publishInvalidation(String cell, String nsKey, long epoch, String reason) {}

            @Override
            public boolean isAvailable() {
                return true;
            }
        };

        WaterfallRoutingResolver resolver = new WaterfallRoutingResolver(
                ring, mockRedis, Duration.ofMinutes(1), 1000L, 30L, null, Runnable::run
        );

        AtomicInteger dispatchCount = new AtomicInteger(0);
        List<String> seenTargets = new ArrayList<>();
        List<String> seenIdempotencyKeys = new ArrayList<>();

        GatewayHttpTransport transport = (targetNodeId, targetUrl, req, routingHeaders) -> {
            int attempt = dispatchCount.incrementAndGet();
            seenTargets.add(targetNodeId);
            seenIdempotencyKeys.add(req.idempotencyKey());

            if (attempt == 1) {
                // First attempt sent to stale node-old with epoch 1 -> returns 421 STALE_ROUTE
                return new ForwardResponse(421, Map.of(), "stale_route".getBytes(StandardCharsets.UTF_8), targetNodeId, 1);
            } else {
                // Second attempt after invalidation re-resolves to ring owner at epoch 2 -> success 200
                return new ForwardResponse(200, Map.of(), "success".getBytes(StandardCharsets.UTF_8), targetNodeId, 1);
            }
        };

        GatewayForwarder forwarder = new GatewayForwarder(resolver, 2, transport, nodeId -> "http://" + nodeId + ":7070");

        String clientUniqueIdempotencyKey = "client-uuid-999-never-mint-new";
        ForwardRequest request = new ForwardRequest("POST", "/api/v1/memory/remember", Map.of(), "test".getBytes(StandardCharsets.UTF_8), clientUniqueIdempotencyKey);

        ForwardResponse finalResponse = forwarder.forward(key, request);

        // Verification of R12.4 (one invalidation, one retry, success)
        assertThat(finalResponse.statusCode()).isEqualTo(200);
        assertThat(finalResponse.attempts()).isEqualTo(2);
        assertThat(dispatchCount.get()).isEqualTo(2);

        // First attempt was to stale target from Redis cache
        assertThat(seenTargets.get(0)).isEqualTo("node-old");
        // Second attempt was to re-resolved owner from Ketama ring after cache eviction
        assertThat(seenTargets.get(1)).isEqualTo(ring.ownerOf(key));

        // Verification of R7.5 and Invariant K7: exact same idempotency key was reused across retries!
        assertThat(seenIdempotencyKeys).containsExactly(clientUniqueIdempotencyKey, clientUniqueIdempotencyKey);
    }
}
