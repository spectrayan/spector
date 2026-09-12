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

/**
 * SPI listener for routing metrics, allowing decoupled instrumentation across frameworks
 * (ADR-0034 §15.7, Req R8.1–R8.4).
 */
public interface RoutingMetricsListener {

    /**
     * Records a route lookup attempt and the tier that fulfilled it.
     *
     * @param source the resolution tier
     */
    void recordLookup(RouteSource source);

    /**
     * Records a stale route rejection.
     *
     * @param key         the routing key
     * @param staleEpoch  the stale epoch from the client request
     * @param activeEpoch the active epoch on the owner node
     */
    default void recordStaleRoute(RoutingKey key, long staleEpoch, long activeEpoch) {}

    /**
     * Records a transition into or out of degraded routing mode.
     *
     * @param degraded true if degraded (Redis unavailable), false if recovered
     */
    default void recordDegradationTransition(boolean degraded) {}

    /**
     * No-op implementation for standalone or unmetered deployments.
     */
    RoutingMetricsListener NOOP = source -> {};
}
