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
package com.spectrayan.spector.commons.pathway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Objects;

/**
 * A decorating {@link SynapticRelay} that provides non-blocking adaptive circuit breaking
 * as a façade over {@link CircuitBreaker}.
 *
 * <p>Protects external or high-latency downstream dependencies from cascading failures.
 * Supports both standalone anonymous breakers with backwards-compatible defaults
 * and shared named circuit breakers across pathways.</p>
 *
 * @param <S> the type of signal processed by the relay
 */
public final class CircuitBreakerRelay<S> implements SynapticRelay<S> {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerRelay.class);

    public static final int DEFAULT_FAILURE_THRESHOLD = 5;
    public static final long DEFAULT_COOLDOWN_MS = 30_000L;

    /**
     * Circuit breaker operating states matching {@link CircuitBreaker.State}.
     */
    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final SynapticRelay<S> delegate;
    private final CircuitBreaker breaker;
    private final BreakerRef ref;
    private final OnOpen onOpen;
    private volatile CircuitBreaker resolvedBreaker;

    /**
     * Constructs a CircuitBreakerRelay with default thresholds (5 failures, 30s cooldown, BYPASS on open).
     *
     * @param delegate the underlying relay to protect
     * @deprecated Use {@link #CircuitBreakerRelay(SynapticRelay, BreakerRef, CircuitBreakerRegistry)} or {@link PathwayComposer.StageBuilder#breaker(BreakerRef)} instead.
     */
    @Deprecated(forRemoval = true, since = "1.5.0")
    public CircuitBreakerRelay(final SynapticRelay<S> delegate) {
        this(delegate, DEFAULT_FAILURE_THRESHOLD, DEFAULT_COOLDOWN_MS);
    }

    /**
     * Constructs a CircuitBreakerRelay with custom thresholds and BYPASS on open.
     *
     * @param delegate         the underlying relay to protect
     * @param failureThreshold number of consecutive failures before tripping open
     * @param cooldownMs       duration in milliseconds to stay open before half-open probe
     * @deprecated Use {@link #CircuitBreakerRelay(SynapticRelay, BreakerRef, CircuitBreakerRegistry)} or {@link PathwayComposer.StageBuilder#breaker(BreakerRef)} instead.
     */
    @Deprecated(forRemoval = true, since = "1.5.0")
    public CircuitBreakerRelay(final SynapticRelay<S> delegate, final int failureThreshold, final long cooldownMs) {
        this(delegate, new CircuitBreaker(
                "anon-" + delegate.relayName(),
                CircuitBreakerConfig.builder()
                        .failureThreshold(failureThreshold)
                        .cooldown(Duration.ofMillis(cooldownMs))
                        .tripOn(EnumSet.of(FaultKind.TRANSIENT, FaultKind.DOWNSTREAM, FaultKind.INTERNAL))
                        .build()
        ), OnOpen.BYPASS);
    }

    /**
     * Constructs a CircuitBreakerRelay with a specified {@link CircuitBreaker} and call-site {@link OnOpen} action.
     *
     * @param delegate the underlying relay to protect
     * @param breaker  the circuit breaker instance (may be shared)
     * @param onOpen   the call-site action on open circuit (FAIL vs BYPASS)
     */
    public CircuitBreakerRelay(final SynapticRelay<S> delegate, final CircuitBreaker breaker, final OnOpen onOpen) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        this.breaker = Objects.requireNonNull(breaker, "breaker cannot be null");
        this.ref = null;
        this.onOpen = Objects.requireNonNull(onOpen, "onOpen cannot be null");
    }

    /**
     * Constructs a CircuitBreakerRelay from a {@link BreakerRef} and registry.
     *
     * @param delegate the underlying relay to protect
     * @param ref      breaker reference defining name, config, and call-site onOpen
     * @param registry circuit breaker registry
     */
    public CircuitBreakerRelay(final SynapticRelay<S> delegate, final BreakerRef ref, final CircuitBreakerRegistry registry) {
        this(delegate, Objects.requireNonNull(registry, "registry cannot be null").get(ref), ref.onOpen());
    }

    /**
     * Constructs a CircuitBreakerRelay from a {@link BreakerRef}, resolving the breaker
     * dynamically from {@link PathwayContext} or falling back to a local breaker.
     *
     * @param delegate the underlying relay to protect
     * @param ref      breaker reference defining name, config, and call-site onOpen
     */
    public CircuitBreakerRelay(final SynapticRelay<S> delegate, final BreakerRef ref) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        this.ref = Objects.requireNonNull(ref, "ref cannot be null");
        this.breaker = null;
        this.onOpen = ref.onOpen();
    }

    private CircuitBreaker effectiveBreaker(final S signal) {
        if (breaker != null) {
            return breaker;
        }
        if (signal instanceof ContextualSignal cs && cs.context() != null) {
            final CircuitBreaker fromCtx = cs.context().find(CircuitBreakerRegistry.class)
                    .map(r -> r.get(ref))
                    .orElse(null);
            if (fromCtx != null) {
                return fromCtx;
            }
        }
        if (resolvedBreaker == null) {
            synchronized (this) {
                if (resolvedBreaker == null) {
                    resolvedBreaker = new CircuitBreaker(ref.name(), ref.config());
                }
            }
        }
        return resolvedBreaker;
    }

    @Override
    public boolean transmit(final S signal) throws Exception {
        final CircuitBreaker targetBreaker = effectiveBreaker(signal);
        final CircuitBreaker.Permit permit = targetBreaker.tryAcquire(onOpen);

        if (permit.isBypass()) {
            log.debug("Circuit breaker for relay '{}' ({}) is OPEN. Bypassing execution.", relayName(), targetBreaker.name());
            if (signal instanceof ContextualSignal cs && cs.context() != null) {
                final String scopeName = (cs.context().scope() != null && cs.context().scope().pathwayName() != null)
                        ? cs.context().scope().pathwayName() + "/" + relayName()
                        : relayName();
                cs.context().outcome().markBypassed(scopeName, "circuit_open:" + targetBreaker.name());
            }
            return true;
        }

        try {
            final boolean result = delegate.transmit(signal);
            targetBreaker.onSuccess(permit);
            return result;
        } catch (final Exception e) {
            targetBreaker.onFailure(permit, Faults.kindOf(e));
            throw e;
        }
    }

    /**
     * Returns the current state of the circuit breaker.
     *
     * @return current state
     */
    public State state() {
        return State.valueOf(effectiveBreaker(null).state().name());
    }

    /**
     * Returns the underlying {@link CircuitBreaker.State}.
     *
     * @return breaker state
     */
    public CircuitBreaker.State breakerState() {
        return effectiveBreaker(null).state();
    }

    /**
     * Returns the current consecutive failure count.
     *
     * @return failure count
     */
    public int failureCount() {
        return effectiveBreaker(null).consecutiveFailures();
    }

    /**
     * Returns the failure threshold.
     *
     * @return threshold count
     */
    public int failureThreshold() {
        return effectiveBreaker(null).config().failureThreshold();
    }

    /**
     * Returns the cooldown duration in milliseconds.
     *
     * @return cooldown in ms
     */
    public long cooldownMs() {
        return effectiveBreaker(null).config().cooldown().toMillis();
    }

    /**
     * Resets the circuit breaker back to CLOSED state with 0 failures.
     */
    public void reset() {
        effectiveBreaker(null).reset();
    }

    /**
     * Returns the underlying delegate relay.
     *
     * @return delegate relay
     */
    public SynapticRelay<S> delegate() {
        return delegate;
    }

    /**
     * Returns the underlying {@link CircuitBreaker} instance.
     *
     * @return circuit breaker
     */
    public CircuitBreaker circuitBreaker() {
        return effectiveBreaker(null);
    }

    /**
     * Returns the call-site {@link OnOpen} action.
     *
     * @return onOpen action
     */
    public OnOpen onOpen() {
        return onOpen;
    }

    @Override
    public String relayName() {
        return delegate.relayName();
    }
}
