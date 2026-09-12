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

import com.spectrayan.spector.cluster.routing.RouteBinding;
import com.spectrayan.spector.cluster.routing.RoutingKey;

import java.util.Optional;

/**
 * Interface for the distributed routing cache and pub/sub invalidation bus (ADR-0034 §8, Req R1, R3, R4).
 *
 * <p><b>Core Invariant:</b> Redis is an accelerator and invalidation bus only.
 * It is never the authoritative control store (Invariant K1, K2).</p>
 */
public interface RedisRoutingCache extends AutoCloseable {

    /**
     * Retrieves the cached route binding for the specified routing key.
     *
     * @param key the routing key
     * @return optional containing the cached binding, or empty if absent/unreachable
     */
    Optional<RouteBinding> get(RoutingKey key);

    /**
     * Conditionally caches a route binding with set-if-absent semantics and TTL.
     *
     * <p><b>Invariant K3:</b> Miss-and-fill writes {@code mode=HASH}. It MUST NEVER
     * overwrite an existing entry or fabricate {@code mode=OVERRIDE}.</p>
     *
     * @param key        the routing key
     * @param binding    the route binding to cache
     * @param ttlSeconds TTL in seconds (load-bearing staleness bound, Req R3.2)
     * @return true if successfully inserted, false if already present or failed
     */
    boolean putIfAbsent(RoutingKey key, RouteBinding binding, long ttlSeconds);

    /**
     * Drops the specified key from the distributed cache.
     *
     * @param key the routing key to invalidate
     */
    void invalidate(RoutingKey key);

    /**
     * Publishes a route invalidation message across the cell's pub/sub channel.
     *
     * @param cellId  the cell identifier
     * @param nsKey   the canonical namespace key material
     * @param epoch   the epoch associated with the invalidation
     * @param reason  human-readable reason for invalidation
     */
    void publishInvalidation(String cellId, String nsKey, long epoch, String reason);

    /**
     * Indicates whether the underlying Redis connection is currently active and healthy.
     *
     * @return true if healthy, false if degraded
     */
    boolean isAvailable();

    @Override
    default void close() {}
}
