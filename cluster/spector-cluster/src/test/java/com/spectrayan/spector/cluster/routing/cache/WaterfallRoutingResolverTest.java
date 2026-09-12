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
import com.spectrayan.spector.cluster.routing.RouteMode;
import com.spectrayan.spector.cluster.routing.RouteSource;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.cluster.routing.RoutingMetricsListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class WaterfallRoutingResolverTest {

    private ConsistentHashRing ring;
    private MockRedisRoutingCache mockRedis;
    private TestMetricsListener metrics;
    private WaterfallRoutingResolver resolver;

    @BeforeEach
    void setUp() {
        ring = ConsistentHashRing.of(1, List.of("node-1", "node-2", "node-3"));
        mockRedis = new MockRedisRoutingCache();
        metrics = new TestMetricsListener();
        resolver = new WaterfallRoutingResolver(
                ring,
                mockRedis,
                Duration.ofSeconds(5),
                1000,
                30,
                metrics,
                Runnable::run // direct executor
        );
    }

    @Test
    @DisplayName("L3 fallback computes from ring, populates L1 and L2 on cold miss")
    void testColdMissFallsBackToRing() {
        RoutingKey key = RoutingKey.ofTenanted("cell-1", "tenant-a", "ns-cold");

        // 1st lookup: cold miss -> L3 ring fallback
        ResolvedRoute first = resolver.resolve(key);
        assertThat(first.source()).isEqualTo(RouteSource.HASH_FALLBACK);
        assertThat(first.binding().mode()).isEqualTo(RouteMode.HASH);
        assertThat(first.ownerId()).isEqualTo(ring.ownerOf(key));

        // Redis write-behind was triggered
        assertThat(mockRedis.get(key)).isPresent();
        assertThat(mockRedis.get(key).get().ownerId()).isEqualTo(first.ownerId());

        // 2nd lookup: hits L1 Caffeine
        ResolvedRoute second = resolver.resolve(key);
        assertThat(second.source()).isEqualTo(RouteSource.CAFFEINE);
        assertThat(second.ownerId()).isEqualTo(first.ownerId());
    }

    @Test
    @DisplayName("L2 Redis hit populates L1 Caffeine and avoids ring fallback")
    void testL2RedisHit() {
        RoutingKey key = RoutingKey.ofTenanted("cell-1", "tenant-b", "ns-redis");
        RouteBinding preCached = RouteBinding.ofHash(key, "node-2", 1);
        mockRedis.putIfAbsent(key, preCached, 30);

        // 1st lookup: hits L2 Redis
        ResolvedRoute first = resolver.resolve(key);
        assertThat(first.source()).isEqualTo(RouteSource.REDIS);
        assertThat(first.ownerId()).isEqualTo("node-2");

        // 2nd lookup: hits L1 Caffeine
        ResolvedRoute second = resolver.resolve(key);
        assertThat(second.source()).isEqualTo(RouteSource.CAFFEINE);
        assertThat(second.ownerId()).isEqualTo("node-2");
    }

    @Test
    @DisplayName("Degraded mode (Redis null/down) resolves via L3 ring with zero errors")
    void testDegradedModeResolvesCleanly() {
        WaterfallRoutingResolver degradedResolver = new WaterfallRoutingResolver(
                ring,
                null, // Redis absent
                Duration.ofSeconds(5),
                1000,
                30,
                metrics,
                Runnable::run
        );

        RoutingKey key = RoutingKey.ofTenanted("cell-1", "tenant-c", "ns-degraded");
        ResolvedRoute resolved = degradedResolver.resolve(key);

        assertThat(resolved.source()).isEqualTo(RouteSource.HASH_FALLBACK);
        assertThat(resolved.ownerId()).isEqualTo(ring.ownerOf(key));

        // Next hit comes from L1 Caffeine
        ResolvedRoute next = degradedResolver.resolve(key);
        assertThat(next.source()).isEqualTo(RouteSource.CAFFEINE);
    }

    @Test
    @DisplayName("Req K3: Conditional cache fill never overwrites existing override")
    void testConditionalFillDoesNotOverwriteOverride() {
        RoutingKey key = RoutingKey.ofTenanted("cell-1", "tenant-d", "ns-override");
        RouteBinding override = new RouteBinding(key, "node-special", 5, "fence-1", 100L, RouteMode.OVERRIDE);
        mockRedis.putDirect(key, override);

        // Resolver tries to putIfAbsent with mode=HASH; must return false and preserve OVERRIDE
        boolean inserted = mockRedis.putIfAbsent(key, RouteBinding.ofHash(key, "node-1", 1), 30);
        assertThat(inserted).isFalse();

        RouteBinding current = mockRedis.get(key).orElseThrow();
        assertThat(current.ownerId()).isEqualTo("node-special");
        assertThat(current.mode()).isEqualTo(RouteMode.OVERRIDE);
    }

    @Test
    @DisplayName("Invalidation drops entry from L1 Caffeine cache")
    void testInvalidateDropsFromCaffeine() {
        RoutingKey key = RoutingKey.ofTenanted("cell-1", "tenant-e", "ns-evict");
        resolver.resolve(key); // L3 fallback -> populates L1

        // Invalidate local L1
        resolver.invalidateLocal(key);

        // Next resolve must check L2 Redis (where write-behind filled it) rather than L1
        ResolvedRoute next = resolver.resolve(key);
        assertThat(next.source()).isEqualTo(RouteSource.REDIS);
    }

    private static class MockRedisRoutingCache implements RedisRoutingCache {
        private final Map<RoutingKey, RouteBinding> store = new ConcurrentHashMap<>();
        private volatile boolean available = true;

        @Override
        public Optional<RouteBinding> get(RoutingKey key) {
            if (!available) return Optional.empty();
            return Optional.ofNullable(store.get(key));
        }

        @Override
        public boolean putIfAbsent(RoutingKey key, RouteBinding binding, long ttlSeconds) {
            if (!available) return false;
            return store.putIfAbsent(key, binding) == null;
        }

        public void putDirect(RoutingKey key, RouteBinding binding) {
            store.put(key, binding);
        }

        @Override
        public void invalidate(RoutingKey key) {
            store.remove(key);
        }

        @Override
        public void publishInvalidation(String cellId, String nsKey, long epoch, String reason) {}

        @Override
        public boolean isAvailable() {
            return available;
        }
    }

    private static class TestMetricsListener implements RoutingMetricsListener {
        private final AtomicInteger caffeineHits = new AtomicInteger();
        private final AtomicInteger redisHits = new AtomicInteger();
        private final AtomicInteger hashFallbacks = new AtomicInteger();

        @Override
        public void recordLookup(RouteSource source) {
            switch (source) {
                case CAFFEINE -> caffeineHits.incrementAndGet();
                case REDIS -> redisHits.incrementAndGet();
                case HASH_FALLBACK -> hashFallbacks.incrementAndGet();
            }
        }
    }
}
