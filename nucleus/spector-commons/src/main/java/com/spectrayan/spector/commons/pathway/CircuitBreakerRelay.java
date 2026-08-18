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

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A decorating {@link SynapticRelay} that provides non-blocking adaptive circuit breaking.
 *
 * <p>Protects external or high-latency downstream dependencies (e.g. remote rerankers,
 * neural sparse encoders, or LLM entity extractors) from cascading failures. When consecutive
 * errors cross {@code failureThreshold}, the circuit trips to {@link State#OPEN} for
 * {@code cooldownMs}, skipping delegate invocation and degrading gracefully without thread starvation.</p>
 *
 * @param <S> the type of signal processed by the relay
 */
public final class CircuitBreakerRelay<S> implements SynapticRelay<S> {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerRelay.class);

    public static final int DEFAULT_FAILURE_THRESHOLD = 5;
    public static final long DEFAULT_COOLDOWN_MS = 30_000L;

    /**
     * Circuit breaker operating states.
     */
    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final SynapticRelay<S> delegate;
    private final int failureThreshold;
    private final long cooldownMs;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong lastStateChangeMs = new AtomicLong(0L);

    /**
     * Constructs a CircuitBreakerRelay with default thresholds (5 failures, 30s cooldown).
     *
     * @param delegate the underlying relay to protect
     */
    public CircuitBreakerRelay(final SynapticRelay<S> delegate) {
        this(delegate, DEFAULT_FAILURE_THRESHOLD, DEFAULT_COOLDOWN_MS);
    }

    /**
     * Constructs a CircuitBreakerRelay with custom thresholds.
     *
     * @param delegate         the underlying relay to protect
     * @param failureThreshold number of consecutive failures before tripping open
     * @param cooldownMs       duration in milliseconds to stay open before half-open probe
     */
    public CircuitBreakerRelay(final SynapticRelay<S> delegate, final int failureThreshold, final long cooldownMs) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("failureThreshold must be positive");
        }
        if (cooldownMs <= 0) {
            throw new IllegalArgumentException("cooldownMs must be positive");
        }
        this.failureThreshold = failureThreshold;
        this.cooldownMs = cooldownMs;
    }

    @Override
    public boolean transmit(final S signal) throws Exception {
        final long now = System.currentTimeMillis();
        final State currentState = state.get();

        if (currentState == State.OPEN) {
            if (now - lastStateChangeMs.get() > cooldownMs) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    log.info("Circuit breaker for relay '{}' entering HALF_OPEN trial state.", relayName());
                }
            } else {
                log.debug("Circuit breaker for relay '{}' is OPEN. Bypassing execution.", relayName());
                return true; // Bypass gracefully
            }
        }

        try {
            final boolean result = delegate.transmit(signal);
            onSuccess();
            return result;
        } catch (final Exception e) {
            onFailure(e);
            throw e;
        }
    }

    private void onSuccess() {
        if (state.get() != State.CLOSED) {
            log.info("Circuit breaker for relay '{}' closed successfully after recovery.", relayName());
            state.set(State.CLOSED);
        }
        consecutiveFailures.set(0);
    }

    private void onFailure(final Exception cause) {
        final int failures = consecutiveFailures.incrementAndGet();
        final long now = System.currentTimeMillis();

        if (state.get() == State.HALF_OPEN) {
            log.warn("Probe trial failed for relay '{}'. Tripping circuit back to OPEN.", relayName(), cause);
            state.set(State.OPEN);
            lastStateChangeMs.set(now);
        } else if (failures >= failureThreshold) {
            if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                lastStateChangeMs.set(now);
                log.warn("Circuit breaker for relay '{}' TRIPPED OPEN after {} consecutive failures.",
                        relayName(), failures, cause);
            }
        }
    }

    /**
     * Returns the current state of the circuit breaker.
     *
     * @return current state
     */
    public State state() {
        return state.get();
    }

    /**
     * Returns the current consecutive failure count.
     *
     * @return failure count
     */
    public int failureCount() {
        return consecutiveFailures.get();
    }

    /**
     * Returns the failure threshold.
     *
     * @return threshold count
     */
    public int failureThreshold() {
        return failureThreshold;
    }

    /**
     * Returns the cooldown duration in milliseconds.
     *
     * @return cooldown in ms
     */
    public long cooldownMs() {
        return cooldownMs;
    }

    /**
     * Resets the circuit breaker back to CLOSED state with 0 failures.
     */
    public void reset() {
        state.set(State.CLOSED);
        consecutiveFailures.set(0);
        lastStateChangeMs.set(0L);
    }

    @Override
    public String relayName() {
        return delegate.relayName();
    }
}
