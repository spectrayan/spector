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
package com.spectrayan.spector.cluster.routing.cache;

import com.spectrayan.spector.cluster.routing.ConsistentHashRing;
import com.spectrayan.spector.cluster.routing.ResolvedRoute;
import com.spectrayan.spector.cluster.routing.RouteBinding;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.cluster.routing.RoutingMetricsListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chaos test verifying that an abrupt Redis outage under load gracefully degrades to ring resolution
 * without failing requests or creating multi-writer divergence (ADR-0034 §15.8, Req R6.7, R12.3, Invariant K1).
 */
class ChaosRedisOutageTest {

    @Test
    @DisplayName("Req R6.7 & K1: Abrupt Redis outage under concurrent load preserves single-writer determinism")
    void redisOutageUnderLoadDegradesCleanly() throws Exception {
        List<String> members = List.of("node-1", "node-2", "node-3");
        ConsistentHashRing ring = ConsistentHashRing.of(1, members);

        ChaosRedisCache chaosRedis = new ChaosRedisCache();

        WaterfallRoutingResolver resolver = new WaterfallRoutingResolver(
                ring,
                chaosRedis,
                Duration.ofMillis(50), // short L1 TTL so it exercises L2/L3
                10_000,
                30,
                RoutingMetricsListener.NOOP,
                Runnable::run
        );

        int concurrentRequests = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(16);
        List<Callable<ResolvedRoute>> tasks = new ArrayList<>();

        for (int i = 0; i < concurrentRequests; i++) {
            final int index = i;
            tasks.add(() -> {
                // Mid-flight: kill Redis when reaching the middle requests
                if (index == concurrentRequests / 2) {
                    chaosRedis.killRedis();
                }
                RoutingKey key = RoutingKey.ofTenanted("cell-1", "tenant-chaos", "ns-" + (index % 50));
                return resolver.resolve(key);
            });
        }

        List<Future<ResolvedRoute>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        // Verify every single request resolved successfully with zero exceptions
        for (int i = 0; i < concurrentRequests; i++) {
            ResolvedRoute route = futures.get(i).get();
            RoutingKey key = RoutingKey.ofTenanted("cell-1", "tenant-chaos", "ns-" + (i % 50));
            String expectedOwner = ring.ownerOf(key);

            // Invariant K1: Regardless of whether Redis was alive or dead, the resolved owner matches the ring
            assertThat(route.ownerId())
                    .as("Route for key %s must match ring owner %s even under Redis failure", key.keyMaterial(), expectedOwner)
                    .isEqualTo(expectedOwner);
        }

        // Now revive Redis and verify lookups continue smoothly
        chaosRedis.reviveRedis();
        RoutingKey postReviveKey = RoutingKey.ofTenanted("cell-1", "tenant-chaos", "ns-post-revive");
        ResolvedRoute postRevive = resolver.resolve(postReviveKey);
        assertThat(postRevive.ownerId()).isEqualTo(ring.ownerOf(postReviveKey));
    }

    private static class ChaosRedisCache implements RedisRoutingCache {
        private final ConcurrentHashMap<RoutingKey, RouteBinding> memory = new ConcurrentHashMap<>();
        private final AtomicBoolean isAlive = new AtomicBoolean(true);

        public void killRedis() {
            isAlive.set(false);
        }

        public void reviveRedis() {
            isAlive.set(true);
        }

        @Override
        public Optional<RouteBinding> get(RoutingKey key) {
            if (!isAlive.get()) {
                throw new RuntimeException("Redis connection refused / timed out (chaos injected)");
            }
            return Optional.ofNullable(memory.get(key));
        }

        @Override
        public boolean putIfAbsent(RoutingKey key, RouteBinding binding, long ttlSeconds) {
            if (!isAlive.get()) {
                throw new RuntimeException("Redis connection refused / timed out (chaos injected)");
            }
            return memory.putIfAbsent(key, binding) == null;
        }

        @Override
        public void invalidate(RoutingKey key) {
            if (!isAlive.get()) {
                throw new RuntimeException("Redis connection refused / timed out (chaos injected)");
            }
            memory.remove(key);
        }

        @Override
        public void publishInvalidation(String cellId, String nsKey, long epoch, String reason) {
            if (!isAlive.get()) {
                throw new RuntimeException("Redis connection refused / timed out (chaos injected)");
            }
        }

        @Override
        public boolean isAvailable() {
            // G41: Return true even when killed to simulate mid-flight failure during get()
            return true;
        }
    }
}
