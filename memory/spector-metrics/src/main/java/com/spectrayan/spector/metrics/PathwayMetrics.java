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
package com.spectrayan.spector.metrics;

import com.spectrayan.spector.commons.observation.PathwayObservationHook;
import com.spectrayan.spector.commons.observation.PathwayObservationHooks;
import com.spectrayan.spector.commons.pathway.FaultKind;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.metrics.observation.PathwayRelayMetricsInterceptor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;

/**
 * Micrometer {@link MeterBinder} and {@link PathwayObservationHook} that records telemetry
 * from cognitive pathway executions (ADR-0036 §15).
 *
 * <h3>Exported Meters</h3>
 * <ul>
 *   <li>{@code spector.pathway.conduct} (Timer) — labels: {@code pathway}, {@code finish}</li>
 *   <li>{@code spector.pathway.relay} (Timer) — labels: {@code pathway}, {@code relay}, {@code status}</li>
 *   <li>{@code spector.pathway.degraded} (Counter) — labels: {@code pathway}, {@code relay}, {@code kind}</li>
 *   <li>{@code spector.pathway.circuit} (Counter) — labels: {@code breaker}, {@code event=trip|probe|close|reject}</li>
 *   <li>{@code spector.pathway.bulkhead.reject} (Counter) — labels: {@code bulkhead}</li>
 *   <li>{@code spector.pathway.timeout} (Counter) — labels: {@code pathway}, {@code relay}</li>
 *   <li>{@code spector.pathway.retry} (Counter) — labels: {@code pathway}, {@code relay}</li>
 *   <li>{@code spector.pathway.nested} (Timer) — labels: {@code from}, {@code to}</li>
 * </ul>
 */
public final class PathwayMetrics implements MeterBinder, PathwayObservationHook {

    private final MeterRegistry registry;

    public PathwayMetrics(final MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
    }

    /**
     * Binds pathway telemetry to the global {@link SpectorMetrics#registry()}.
     *
     * @return initialized PathwayMetrics binder
     */
    public static PathwayMetrics bind() {
        return bind(SpectorMetrics.registry());
    }

    /**
     * Binds pathway telemetry to the given {@link MeterRegistry} and sets this as global hook.
     *
     * @param registry meter registry
     * @return initialized PathwayMetrics binder
     */
    public static PathwayMetrics bind(final MeterRegistry registry) {
        final PathwayMetrics metrics = new PathwayMetrics(registry);
        metrics.bindTo(registry);
        PathwayObservationHooks.setGlobal(metrics);
        return metrics;
    }

    @Override
    public void bindTo(final MeterRegistry registry) {
        PathwayObservationHooks.setGlobal(this);
    }

    /**
     * Returns an interceptor function for decorating pathway relays with timing metrics.
     *
     * @param <S> signal type
     * @return relay interceptor function
     */
    public <S> Function<SynapticRelay<S>, SynapticRelay<S>> interceptor() {
        return PathwayRelayMetricsInterceptor.interceptor(registry);
    }

    @Override
    public void onConduct(final String pathway, final String finish, final Duration duration) {
        Timer.builder("spector.pathway.conduct")
                .tag("pathway", pathway != null ? pathway : "unknown")
                .tag("finish", finish != null ? finish : "unknown")
                .description("Total latency of pathway conduct")
                .register(registry)
                .record(duration != null ? duration : Duration.ZERO);
    }

    @Override
    public void onRelay(final String pathway, final String relay, final String status, final Duration duration) {
        Timer.builder("spector.pathway.relay")
                .tag("pathway", pathway != null ? pathway : "unknown")
                .tag("relay", relay != null ? relay : "unknown")
                .tag("status", status != null ? status : "unknown")
                .description("Relay execution latency")
                .register(registry)
                .record(duration != null ? duration : Duration.ZERO);
    }

    @Override
    public void onDegraded(final String pathway, final String relay, final FaultKind kind) {
        Counter.builder("spector.pathway.degraded")
                .tag("pathway", pathway != null ? pathway : "unknown")
                .tag("relay", relay != null ? relay : "unknown")
                .tag("kind", kind != null ? kind.name().toLowerCase() : "unknown")
                .description("Relay graceful degradation count")
                .register(registry)
                .increment();
    }

    @Override
    public void onCircuitEvent(final String breaker, final String event) {
        Counter.builder("spector.pathway.circuit")
                .tag("breaker", breaker != null ? breaker : "unknown")
                .tag("event", event != null ? event : "unknown")
                .description("Circuit breaker trip, probe, close, or reject event count")
                .register(registry)
                .increment();
    }

    @Override
    public void onBulkheadReject(final String bulkhead) {
        Counter.builder("spector.pathway.bulkhead.reject")
                .tag("bulkhead", bulkhead != null ? bulkhead : "unknown")
                .description("Bulkhead rejection count")
                .register(registry)
                .increment();
    }

    @Override
    public void onTimeout(final String pathway, final String relay) {
        Counter.builder("spector.pathway.timeout")
                .tag("pathway", pathway != null ? pathway : "unknown")
                .tag("relay", relay != null ? relay : "unknown")
                .description("Relay execution timeout count")
                .register(registry)
                .increment();
    }

    @Override
    public void onRetry(final String pathway, final String relay) {
        Counter.builder("spector.pathway.retry")
                .tag("pathway", pathway != null ? pathway : "unknown")
                .tag("relay", relay != null ? relay : "unknown")
                .description("Relay retry attempt count")
                .register(registry)
                .increment();
    }

    @Override
    public void onNested(final String from, final String to, final Duration duration) {
        Timer.builder("spector.pathway.nested")
                .tag("from", from != null ? from : "unknown")
                .tag("to", to != null ? to : "unknown")
                .description("Nested pathway invocation latency")
                .register(registry)
                .record(duration != null ? duration : Duration.ZERO);
    }

    @Override
    public void onRecallPartitionStats(final String namespace, final int visited, final int skipped, final int budgeted) {
        com.spectrayan.spector.metrics.observation.RecallBudgetMetrics.record(registry, namespace, visited, skipped, budgeted);
    }

    public MeterRegistry registry() {
        return registry;
    }
}
