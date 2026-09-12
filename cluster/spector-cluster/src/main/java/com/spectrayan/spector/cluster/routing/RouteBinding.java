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
package com.spectrayan.spector.cluster.routing;

import java.util.Objects;

/**
 * Immutable decision recording the authoritative owner node and generation for a {@link RoutingKey}
 * (ADR-0034 §15.2, Req R2.3).
 *
 * @param key     the routing key
 * @param ownerId the authoritative owner node identifier
 * @param epoch   monotonic epoch counter
 * @param fence   fence token string (unpopulated in Phase 1, populated in Phase 4)
 * @param hwm     WAL high-water mark (unpopulated in Phase 1, populated in Phase 3)
 * @param mode    resolution mode ({@link RouteMode#HASH} or {@link RouteMode#OVERRIDE})
 */
public record RouteBinding(
        RoutingKey key,
        String ownerId,
        long epoch,
        String fence,
        Long hwm,
        RouteMode mode) {

    public RouteBinding {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
    }

    /**
     * Creates a hash-resolved route binding.
     *
     * @param key     the routing key
     * @param ownerId the node owning this namespace on the ring
     * @param epoch   the ring epoch / version
     * @return route binding
     */
    public static RouteBinding ofHash(RoutingKey key, String ownerId, long epoch) {
        return new RouteBinding(key, ownerId, epoch, null, null, RouteMode.HASH);
    }

    /**
     * Creates a hash-resolved route binding with an active fence token (Req R2.2).
     *
     * @param key     the routing key
     * @param ownerId the node owning this namespace on the ring
     * @param epoch   the ring epoch / version
     * @param fence   the fence token string
     * @return route binding
     */
    public static RouteBinding ofHash(RoutingKey key, String ownerId, long epoch, String fence) {
        return new RouteBinding(key, ownerId, epoch, fence, null, RouteMode.HASH);
    }

    /**
     * Creates an override route binding pinned by the coordinator (Req R3.1).
     *
     * @param key     the routing key
     * @param ownerId the node designated by the override lease
     * @param epoch   the override epoch
     * @param fence   the fence token string
     * @return route binding
     */
    public static RouteBinding ofOverride(RoutingKey key, String ownerId, long epoch, String fence) {
        return new RouteBinding(key, ownerId, epoch, fence, null, RouteMode.OVERRIDE);
    }
}
