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

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Named, thread-safe, non-blocking circuit breaker with trip classification and probe limiting.
 *
 * <p>State transitions:
 * <ul>
 *   <li>{@link State#CLOSED}: normal operation. Failures classified under {@link CircuitBreakerConfig#tripOn()}
 *       increment consecutive failures. At {@link CircuitBreakerConfig#failureThreshold()}, trips to {@link State#OPEN}.</li>
 *   <li>{@link State#OPEN}: executions fail fast with {@link CircuitOpenException} or bypass gracefully
 *       depending on call-site {@link OnOpen}. After {@link CircuitBreakerConfig#cooldown()}, transitions to {@link State#HALF_OPEN}.</li>
 *   <li>{@link State#HALF_OPEN}: permits a bounded number of trial probes ({@link CircuitBreakerConfig#halfOpenProbes()}).
 *       Any single probe failure of a trip kind trips back to OPEN. Reaching {@link CircuitBreakerConfig#halfOpenSuccesses()}
 *       consecutive probe successes closes the breaker.</li>
 * </ul>
 * </p>
 */
public final class CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    /**
     * Circuit breaker operating states.
     */
    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    /**
     * Execution permit issued by {@link #tryAcquire(OnOpen)}.
     */
    public static final class Permit {
        public enum Type { CLOSED, PROBE, BYPASS }

        private final Type type;

        private Permit(final Type type) {
            this.type = type;
        }

        public static Permit closed() {
            return new Permit(Type.CLOSED);
        }

        public static Permit probe() {
            return new Permit(Type.PROBE);
        }

        public static Permit bypass() {
            return new Permit(Type.BYPASS);
        }

        public boolean isBypass() {
            return type == Type.BYPASS;
        }

        public boolean isProbe() {
            return type == Type.PROBE;
        }

        public boolean isClosed() {
            return type == Type.CLOSED;
        }
    }

    /**
     * Callback for state transition notifications.
     */
    @FunctionalInterface
    public interface StateChangeListener {
        void onStateChange(String breakerName, State from, State to);
    }

    private final String name;
    private final CircuitBreakerConfig config;
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger halfOpenInFlight = new AtomicInteger(0);
    private final AtomicInteger halfOpenSuccesses = new AtomicInteger(0);
    private final AtomicLong lastStateChangeMs = new AtomicLong(0L);
    private final List<StateChangeListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Constructs a new CircuitBreaker.
     *
     * @param name   unique breaker name
     * @param config configuration governing trip criteria and recovery
     */
    public CircuitBreaker(final String name, final CircuitBreakerConfig config) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    /**
     * Attempts to acquire an execution permit.
     *
     * @param onOpen call-site disposition when circuit is open or probe capacity is saturated
     * @return an execution permit (closed, probe, or bypass)
     * @throws CircuitOpenException if the circuit is open and {@code onOpen} is {@link OnOpen#FAIL}
     */
    public Permit tryAcquire(final OnOpen onOpen) {
        final long now = System.currentTimeMillis();

        while (true) {
            final State current = state.get();
            if (current == State.OPEN) {
                if (now - lastStateChangeMs.get() > config.cooldown().toMillis()) {
                    if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                        lastStateChangeMs.set(now);
                        halfOpenInFlight.set(1);
                        halfOpenSuccesses.set(0);
                        notifyStateChange(State.OPEN, State.HALF_OPEN);
                        return Permit.probe();
                    }
                    continue;
                } else {
                    if (onOpen == OnOpen.FAIL) {
                        throw new CircuitOpenException(name);
                    }
                    return Permit.bypass();
                }
            } else if (current == State.HALF_OPEN) {
                int inFlight = halfOpenInFlight.get();
                while (inFlight < config.halfOpenProbes()) {
                    if (halfOpenInFlight.compareAndSet(inFlight, inFlight + 1)) {
                        return Permit.probe();
                    }
                    inFlight = halfOpenInFlight.get();
                }
                if (onOpen == OnOpen.FAIL) {
                    throw new CircuitOpenException(name);
                }
                return Permit.bypass();
            } else {
                // CLOSED
                return Permit.closed();
            }
        }
    }

    /**
     * Records a successful execution.
     *
     * @param permit the permit returned by {@link #tryAcquire(OnOpen)}
     */
    public void onSuccess(final Permit permit) {
        if (permit == null) {
            return;
        }
        if (permit.isProbe()) {
            halfOpenInFlight.decrementAndGet();
            final int successes = halfOpenSuccesses.incrementAndGet();
            if (successes >= config.halfOpenSuccesses()) {
                if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                    lastStateChangeMs.set(System.currentTimeMillis());
                    consecutiveFailures.set(0);
                    halfOpenSuccesses.set(0);
                    notifyStateChange(State.HALF_OPEN, State.CLOSED);
                }
            }
        } else if (permit.isClosed()) {
            consecutiveFailures.set(0);
        }
    }

    /**
     * Records a failed execution.
     *
     * @param permit the permit returned by {@link #tryAcquire(OnOpen)}
     * @param kind   the classified fault kind
     */
    public void onFailure(final Permit permit, final FaultKind kind) {
        if (permit == null) {
            return;
        }
        if (!config.tripOn().contains(kind)) {
            if (permit.isProbe()) {
                halfOpenInFlight.decrementAndGet();
            }
            return;
        }

        final long now = System.currentTimeMillis();
        if (permit.isProbe()) {
            halfOpenInFlight.decrementAndGet();
            state.set(State.OPEN);
            lastStateChangeMs.set(now);
            halfOpenSuccesses.set(0);
            notifyStateChange(State.HALF_OPEN, State.OPEN);
        } else if (permit.isClosed()) {
            final int failures = consecutiveFailures.incrementAndGet();
            if (failures >= config.failureThreshold()) {
                if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                    lastStateChangeMs.set(now);
                    notifyStateChange(State.CLOSED, State.OPEN);
                }
            }
        }
    }

    /**
     * Resets the circuit breaker back to CLOSED state with zero failure count.
     */
    public void reset() {
        state.set(State.CLOSED);
        consecutiveFailures.set(0);
        halfOpenInFlight.set(0);
        halfOpenSuccesses.set(0);
        lastStateChangeMs.set(0L);
    }

    private void notifyStateChange(final State from, final State to) {
        if (to == State.HALF_OPEN) {
            log.debug("Circuit breaker '{}' state transitioned from {} to {}", name, from, to);
        } else {
            log.info("Circuit breaker '{}' state transitioned from {} to {}", name, from, to);
        }
        for (final StateChangeListener listener : listeners) {
            try {
                listener.onStateChange(name, from, to);
            } catch (final Throwable t) {
                log.warn("Circuit breaker '{}' state change listener threw exception", name, t);
            }
        }
    }

    public void addListener(final StateChangeListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener cannot be null"));
    }

    public void removeListener(final StateChangeListener listener) {
        listeners.remove(listener);
    }

    public String name() {
        return name;
    }

    public CircuitBreakerConfig config() {
        return config;
    }

    public State state() {
        return state.get();
    }

    public int consecutiveFailures() {
        return consecutiveFailures.get();
    }

    public long lastStateChangeMs() {
        return lastStateChangeMs.get();
    }
}
