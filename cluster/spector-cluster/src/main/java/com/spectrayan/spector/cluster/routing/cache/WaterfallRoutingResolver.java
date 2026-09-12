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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.spectrayan.spector.cluster.routing.ConsistentHashRing;
import com.spectrayan.spector.cluster.routing.ResolvedRoute;
import com.spectrayan.spector.cluster.routing.RouteBinding;
import com.spectrayan.spector.cluster.routing.RouteSource;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.cluster.routing.RoutingMetricsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Three-tier routing waterfall: L1 Caffeine → L2 Redis → L3 Ketama ConsistentHashRing
 * (ADR-0034 §8, Req R5, R6).
 *
 * <p><b>Core Architectural Principle:</b> Redis is an accelerator in front of a function that
 * already works. Tier 3 is pure computation over the static ring and cannot fail. Losing Redis degrades
 * performance and override visibility, never correctness (Invariant K1, K4, K5).</p>
 */
public class WaterfallRoutingResolver {

    private static final Logger log = LoggerFactory.getLogger(WaterfallRoutingResolver.class);

    private final Cache<RoutingKey, RouteBinding> caffeineCache;
    private final RedisRoutingCache redisCache;
    private final ConsistentHashRing ring;
    private final long redisTtlSeconds;
    private final RoutingMetricsListener metricsListener;
    private final Executor writeBehindExecutor;

    public WaterfallRoutingResolver(
            ConsistentHashRing ring,
            RedisRoutingCache redisCache,
            Duration caffeineTtl,
            long caffeineMaxSize,
            long redisTtlSeconds,
            RoutingMetricsListener metricsListener,
            Executor writeBehindExecutor
    ) {
        this.ring = Objects.requireNonNull(ring, "ring must not be null (K4)");
        this.redisCache = redisCache;
        this.redisTtlSeconds = Math.max(1L, redisTtlSeconds);
        this.metricsListener = metricsListener != null ? metricsListener : RoutingMetricsListener.NOOP;
        this.writeBehindExecutor = writeBehindExecutor != null ? writeBehindExecutor : ForkJoinPool.commonPool();

        Duration l1Ttl = caffeineTtl != null ? caffeineTtl : Duration.ofSeconds(5);
        long maxEntries = caffeineMaxSize > 0 ? caffeineMaxSize : 200_000L;

        this.caffeineCache = Caffeine.newBuilder()
                .expireAfterWrite(l1Ttl)
                .maximumSize(maxEntries)
                .build();
    }

    /**
     * Resolves the authoritative {@link RouteBinding} for the specified key through the three-tier waterfall.
     *
     * <ol>
     *   <li>Tier 1: Local Caffeine L1 cache (~5s TTL). Hit → return immediately with zero I/O.</li>
     *   <li>Tier 2: Distributed Redis L2 cache (50–100ms timeout). Hit → populate L1, return.</li>
     *   <li>Tier 3: Pure Ketama Hash Ring fallback. Pure computation, always succeeds. Populates L1 and
     *       schedules conditional write-behind fill (mode=HASH, Invariant K3).</li>
     * </ol>
     *
     * @param key the routing key
     * @return the resolved route with tier source tag
     */
    public ResolvedRoute resolve(RoutingKey key) {
        Objects.requireNonNull(key, "key must not be null");

        // ── Tier 1: Local L1 Caffeine ──
        RouteBinding l1Hit = caffeineCache.getIfPresent(key);
        if (l1Hit != null) {
            metricsListener.recordLookup(RouteSource.CAFFEINE);
            return new ResolvedRoute(l1Hit, RouteSource.CAFFEINE);
        }

        // ── Tier 2: Distributed L2 Redis ──
        if (redisCache != null && redisCache.isAvailable()) {
            Optional<RouteBinding> l2Hit = redisCache.get(key);
            if (l2Hit.isPresent()) {
                RouteBinding binding = l2Hit.get();
                caffeineCache.put(key, binding);
                metricsListener.recordLookup(RouteSource.REDIS);
                return new ResolvedRoute(binding, RouteSource.REDIS);
            }
        }

        // ── Tier 3: Ketama Ring Fallback (Authoritative, Pure Computation, Invariant K4) ──
        String ownerNodeId = ring.ownerOf(key);
        RouteBinding ringBinding = RouteBinding.ofHash(key, ownerNodeId, ring.ringVersion());

        caffeineCache.put(key, ringBinding);
        metricsListener.recordLookup(RouteSource.HASH_FALLBACK);

        // Conditional write-behind fill to Redis (mode=HASH, Invariant K3)
        if (redisCache != null && redisCache.isAvailable()) {
            writeBehindExecutor.execute(() -> {
                try {
                    redisCache.putIfAbsent(key, ringBinding, redisTtlSeconds);
                } catch (Exception e) {
                    log.debug("Write-behind cache fill failed for {}: {}", key.keyMaterial(), e.getMessage());
                }
            });
        }

        return new ResolvedRoute(ringBinding, RouteSource.HASH_FALLBACK);
    }

    /**
     * Invalidates the specified key from the local Caffeine cache.
     * Called upon receipt of a pub/sub invalidation message (Req R4.2).
     *
     * @param key the routing key
     */
    public void invalidateLocal(RoutingKey key) {
        if (key != null) {
            caffeineCache.invalidate(key);
        }
    }

    /**
     * Invalidates the key from both local Caffeine and distributed Redis caches.
     *
     * @param key the routing key
     */
    public void invalidateAll(RoutingKey key) {
        if (key != null) {
            caffeineCache.invalidate(key);
            if (redisCache != null && redisCache.isAvailable()) {
                redisCache.invalidate(key);
            }
        }
    }

    /**
     * @return the underlying consistent hash ring
     */
    public ConsistentHashRing ring() {
        return ring;
    }

    /**
     * @return true if Redis L2 cache is present and active, false if operating in degraded fallback mode
     */
    public boolean isRedisActive() {
        return redisCache != null && redisCache.isAvailable();
    }
}
