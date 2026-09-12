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
 * Immutable outcome of a routing resolution, pairing the resolved {@link RouteBinding}
 * with the {@link RouteSource} tier that supplied it (ADR-0034 §8, Req R8.1).
 *
 * @param binding the resolved route binding
 * @param source  the tier from which the binding was resolved (caffeine, redis, or hash-fallback)
 */
public record ResolvedRoute(RouteBinding binding, RouteSource source) {

    public ResolvedRoute {
        Objects.requireNonNull(binding, "binding must not be null");
        Objects.requireNonNull(source, "source must not be null");
    }

    /**
     * Convenience accessor for the owner node ID.
     *
     * @return owner node ID
     */
    public String ownerId() {
        return binding.ownerId();
    }

    /**
     * Convenience accessor for the routing epoch.
     *
     * @return epoch
     */
    public long epoch() {
        return binding.epoch();
    }
}
