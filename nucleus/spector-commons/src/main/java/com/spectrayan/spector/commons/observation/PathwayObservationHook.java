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
package com.spectrayan.spector.commons.observation;

import com.spectrayan.spector.commons.pathway.FaultKind;

import java.time.Duration;

/**
 * Lightweight, zero-dependency SPI for cognitive pathway observation and telemetry (ADR-0036 §15).
 *
 * <p>Enables core pathway execution components in {@code spector-commons} to emit telemetry
 * events without taking a compile-time dependency on Micrometer or OpenTelemetry.</p>
 */
public interface PathwayObservationHook {

    /**
     * No-op implementation that silently discards all observations.
     */
    PathwayObservationHook NOOP = new PathwayObservationHook() {};

    /**
     * Called when a pathway conduction completes, short-circuits, or fails.
     * Maps to {@code spector.pathway.conduct}.
     *
     * @param pathway  pathway name
     * @param finish   outcome status ("completed", "short_circuited", "failed")
     * @param duration conduction execution duration
     */
    default void onConduct(String pathway, String finish, Duration duration) {}

    /**
     * Called by relay interceptor when a synaptic relay finishes transmitting.
     * Maps to {@code spector.pathway.relay}.
     *
     * @param pathway  pathway name
     * @param relay    relay name
     * @param status   execution status ("success", "short_circuited", "failed", "degraded", "bypassed")
     * @param duration relay execution duration
     */
    default void onRelay(String pathway, String relay, String status, Duration duration) {}

    /**
     * Called when a relay execution degrades gracefully.
     * Maps to {@code spector.pathway.degraded}.
     *
     * @param pathway pathway name
     * @param relay   relay name
     * @param kind    classified fault kind
     */
    default void onDegraded(String pathway, String relay, FaultKind kind) {}

    /**
     * Called when a circuit breaker transitions state or rejects an execution.
     * Maps to {@code spector.pathway.circuit}.
     *
     * @param breaker breaker name
     * @param event   circuit event ("trip", "probe", "close", "reject")
     */
    default void onCircuitEvent(String breaker, String event) {}

    /**
     * Called when a bulkhead rejects an execution due to capacity exhaustion.
     * Maps to {@code spector.pathway.bulkhead.reject}.
     *
     * @param bulkhead bulkhead name
     */
    default void onBulkheadReject(String bulkhead) {}

    /**
     * Called when a relay execution times out.
     * Maps to {@code spector.pathway.timeout}.
     *
     * @param pathway pathway name
     * @param relay   relay name
     */
    default void onTimeout(String pathway, String relay) {}

    /**
     * Called on each retry attempt after the initial failure.
     * Maps to {@code spector.pathway.retry}.
     *
     * @param pathway pathway name
     * @param relay   relay name
     */
    default void onRetry(String pathway, String relay) {}

    /**
     * Called when a nested pathway invocation via {@code PathwayRelay} finishes.
     * Maps to {@code spector.pathway.nested}.
     *
     * @param from     calling pathway name
     * @param to       nested pathway name
     * @param duration nested pathway execution duration
     */
    default void onNested(String from, String to, Duration duration) {}

    /**
     * Invoked when an asynchronous consolidation action fails. Consolidation failures never
     * fail the parent conduction and never trip {@code pathway:*} breakers (ADR-0036 §12).
     *
     * @param relay the consolidation relay name
     * @param kind  the classified fault kind
     */
    default void onConsolidationFailure(String relay, FaultKind kind) {}

    /**
     * Called when partition recall completes pruning and budgeting.
     * Maps to {@code spector.recall.partitions_*}.
     *
     * @param namespace the target memory namespace (may be null)
     * @param visited   number of candidate partitions actually scanned
     * @param skipped   number of partitions pruned by pruner
     * @param budgeted  number of surviving partitions dropped by visit budget
     */
    default void onRecallPartitionStats(String namespace, int visited, int skipped, int budgeted) {}
}
