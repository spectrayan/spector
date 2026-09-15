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
package com.spectrayan.spector.metrics.observation;

import com.spectrayan.spector.commons.observation.PathwayObservationHooks;
import com.spectrayan.spector.commons.pathway.ContextualSignal;
import com.spectrayan.spector.commons.pathway.PathwayContext;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;

/**
 * Decorating relay interceptor that measures relay execution duration and status (ADR-0036 §15).
 *
 * <p>Emits the {@code spector.pathway.relay} timer with tags {@code pathway}, {@code relay},
 * and {@code status}.</p>
 *
 * @param <S> signal type
 */
public final class PathwayRelayMetricsInterceptor<S> implements SynapticRelay<S> {

    private final SynapticRelay<S> delegate;
    private final MeterRegistry registry;

    public PathwayRelayMetricsInterceptor(SynapticRelay<S> delegate, MeterRegistry registry) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
    }

    @Override
    public boolean transmit(S signal) throws Exception {
        final long startNanos = System.nanoTime();
        String pathway = "unknown";
        PathwayContext ctx = null;
        if (signal instanceof ContextualSignal cs) {
            ctx = cs.context();
            if (ctx != null && ctx.scope() != null) {
                pathway = ctx.scope().pathwayName();
            }
        }
        final String relay = relayName();

        boolean shortCircuited = false;
        Exception error = null;

        try {
            final boolean shouldContinue = delegate.transmit(signal);
            if (!shouldContinue) {
                shortCircuited = true;
            }
            return shouldContinue;
        } catch (Exception e) {
            error = e;
            throw e;
        } finally {
            final long durationNanos = System.nanoTime() - startNanos;
            final String status;
            if (ctx != null && ctx.outcome().isBypassed(relay)) {
                status = "bypassed";
            } else if (ctx != null && ctx.outcome().isDegraded(relay)) {
                status = "degraded";
            } else if (error != null) {
                status = "failed";
            } else if (shortCircuited) {
                status = "short_circuited";
            } else {
                status = "success";
            }

            final Duration duration = Duration.ofNanos(durationNanos);
            PathwayObservationHooks.get(ctx).onRelay(pathway, relay, status, duration);
        }
    }

    @Override
    public String relayName() {
        return delegate.relayName();
    }

    /**
     * Creates an interceptor function wrapping relays with metrics timing.
     *
     * @param registry meter registry
     * @param <S>      signal type
     * @return interceptor function
     */
    public static <S> Function<SynapticRelay<S>, SynapticRelay<S>> interceptor(MeterRegistry registry) {
        Objects.requireNonNull(registry, "registry cannot be null");
        return relay -> new PathwayRelayMetricsInterceptor<>(relay, registry);
    }
}
