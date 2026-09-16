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

import com.spectrayan.spector.commons.error.SpectorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Orchestrates a sequence of synaptic relays to process a signal with failure boundaries
 * and execution diagnostics.
 *
 * @param <S> the type of the signal
 */
public final class PathwayEngine<S> {

    private static final Logger log = LoggerFactory.getLogger(PathwayEngine.class);

    private final String pathwayName;
    private final List<RelayEntry<S>> entries;

    private PathwayEngine(final String pathwayName, final List<RelayEntry<S>> entries) {
        this.pathwayName = Objects.requireNonNull(pathwayName, "pathwayName cannot be null");
        this.entries = List.copyOf(entries);
    }

    /**
     * Conducts the signal through the configured synaptic relays.
     *
     * @param signal the signal to conduct
     * @return the processed signal
     */
    public S conduct(final S signal) {
        final boolean isTraceable = (signal instanceof TraceableSignal ts) && ts.isTraceEnabled();
        final ConductionOutcome outcome = isTraceable ? outcomeOf(signal) : null;

        for (final RelayEntry<S> entry : entries) {
            final long startNanos = isTraceable ? System.nanoTime() : 0L;
            final int bypassedBefore = outcome != null ? outcome.bypassedMarks().size() : -1;
            try {
                final boolean shouldContinue = entry.relay().transmit(signal);
                if (isTraceable) {
                    final long elapsed = System.nanoTime() - startNanos;
                    // A relay that bypassed itself (closed gate, OPEN circuit, full bulkhead)
                    // returns true so the chain continues, but must not be traced as EXECUTED.
                    final String bypassReason = shouldContinue
                            ? bypassReasonSince(outcome, bypassedBefore, entry.relay().relayName())
                            : null;
                    final RelayTrace.TraceStatus status;
                    if (!shouldContinue) {
                        status = RelayTrace.TraceStatus.SHORT_CIRCUITED;
                    } else if (bypassReason != null) {
                        status = RelayTrace.TraceStatus.BYPASSED;
                    } else {
                        status = RelayTrace.TraceStatus.EXECUTED;
                    }
                    ((TraceableSignal) signal).recordTrace(
                            new RelayTrace(entry.relay().relayName(), elapsed, status, bypassReason));
                }

                if (!shouldContinue) {
                    if (signal instanceof ContextualSignal cs && cs.context() != null && cs.context().scope() != null) {
                        cs.context().scope().markShortCircuited(pathwayName);
                    }
                    log.debug("Pathway '{}' short-circuited at relay '{}'", pathwayName, entry.relay().relayName());
                    break;
                }
            } catch (final Exception e) {
                final FaultKind kind = Faults.kindOf(e);
                if (kind == FaultKind.INTERRUPTED) {
                    Thread.currentThread().interrupt();
                    if (isTraceable) {
                        final long elapsed = System.nanoTime() - startNanos;
                        ((TraceableSignal) signal).recordTrace(new RelayTrace(entry.relay().relayName(), elapsed, RelayTrace.TraceStatus.FAILED, detailOf(e)));
                    }
                    if (e instanceof SpectorException se) throw se;
                    if (e.getCause() instanceof SpectorException se) throw se;
                    throw new CognitivePathwayException(pathwayName, entry.relay().relayName(), kind, false, e);
                }

                switch (entry.errorPolicy()) {
                    case FAIL_FAST -> {
                        if (isTraceable) {
                            final long elapsed = System.nanoTime() - startNanos;
                            ((TraceableSignal) signal).recordTrace(new RelayTrace(entry.relay().relayName(), elapsed, RelayTrace.TraceStatus.FAILED, detailOf(e)));
                        }
                        if (e instanceof SpectorException se) throw se;
                        if (e.getCause() instanceof SpectorException se) throw se;
                        throw new CognitivePathwayException(pathwayName, entry.relay().relayName(), kind, false, e);
                    }
                    case DEGRADE_GRACEFULLY -> {
                        if (isTraceable) {
                            final long elapsed = System.nanoTime() - startNanos;
                            ((TraceableSignal) signal).recordTrace(new RelayTrace(entry.relay().relayName(), elapsed, RelayTrace.TraceStatus.DEGRADED, detailOf(e)));
                        }
                        if (signal instanceof ContextualSignal cs && cs.context() != null) {
                            cs.context().outcome().markDegraded(entry.relay().relayName(), kind, e);
                            com.spectrayan.spector.commons.observation.PathwayObservationHooks.get(cs.context())
                                    .onDegraded(pathwayName, entry.relay().relayName(), kind);
                        } else {
                            com.spectrayan.spector.commons.observation.PathwayObservationHooks.get(null)
                                    .onDegraded(pathwayName, entry.relay().relayName(), kind);
                        }
                        log.warn("Pathway '{}' degraded gracefully at relay '{}' due to error.",
                                pathwayName, entry.relay().relayName(), e);
                    }
                    case ABORT -> {
                        if (isTraceable) {
                            final long elapsed = System.nanoTime() - startNanos;
                            ((TraceableSignal) signal).recordTrace(new RelayTrace(entry.relay().relayName(), elapsed, RelayTrace.TraceStatus.SHORT_CIRCUITED, "aborted:" + kind));
                        }
                        if (signal instanceof ContextualSignal cs && cs.context() != null && cs.context().scope() != null) {
                            cs.context().scope().markShortCircuited(pathwayName);
                        }
                        log.debug("Pathway '{}' aborted at relay '{}' due to error: {}",
                                pathwayName, entry.relay().relayName(), e.getMessage());
                        return signal;
                    }
                }
            }
        }
        return signal;
    }

    /**
     * Returns the {@link ConductionOutcome} carried by the signal's context, or {@code null}
     * when the signal is not contextual.
     */
    private static ConductionOutcome outcomeOf(final Object signal) {
        if (signal instanceof ContextualSignal cs && cs.context() != null) {
            return cs.context().outcome();
        }
        return null;
    }

    /**
     * Returns the bypass reason a relay recorded on the outcome during its own {@code transmit},
     * or {@code null} if it recorded none.
     *
     * <p>Relays that self-bypass ({@link GatedRelay}, {@link CircuitBreakerRelay},
     * {@link BulkheadRelay}) return {@code true} so the chain continues, which is
     * indistinguishable from a real execution at the conductor. Marks appended to the
     * outcome since {@code before} disambiguate it. Scope is matched against both
     * conventions in use: a bare relay name and {@code pathway/relay}.</p>
     */
    private static String bypassReasonSince(final ConductionOutcome outcome,
                                           final int before,
                                           final String relayName) {
        if (outcome == null || before < 0) {
            return null;
        }
        final List<ConductionOutcome.Mark> marks = outcome.bypassedMarks();
        for (int i = marks.size() - 1; i >= before; i--) {
            final ConductionOutcome.Mark mark = marks.get(i);
            final String scope = mark.scope();
            if (scope != null && (scope.equals(relayName) || scope.endsWith("/" + relayName))) {
                return mark.message();
            }
        }
        return null;
    }

    /**
     * Returns the trace detail for a failure. Timeouts report {@code timeout:<budget>}
     * per ADR-0036 §13; everything else reports the exception message.
     */
    private static String detailOf(final Throwable e) {
        Throwable cursor = e;
        for (int depth = 0; cursor != null && depth < 16; depth++) {
            if (cursor instanceof java.util.concurrent.TimeoutException) {
                final String message = cursor.getMessage();
                return (message != null && message.startsWith("timeout:")) ? message : "timeout";
            }
            if (cursor.getCause() == cursor) {
                break;
            }
            cursor = cursor.getCause();
        }
        return e != null ? e.getMessage() : null;
    }

    /**
     * Returns the configured name of this pathway.
     *
     * @return pathway name
     */
    public String pathwayName() {
        return pathwayName;
    }

    /**
     * Returns an unmodifiable list of relay names in their configured execution order.
     *
     * @return ordered relay names
     */
    public List<String> relayNames() {
        return entries.stream().map(e -> e.relay().relayName()).toList();
    }

    /**
     * Returns an unmodifiable list of the configured relay entries.
     *
     * @return relay entries
     */
    public List<RelayEntry<S>> entries() {
        return entries;
    }

    /**
     * Represents a configured relay within the pathway.
     *
     * @param <S>         the type of the signal
     * @param relay       the relay instance
     * @param errorPolicy the error handling policy for this relay
     */
    public record RelayEntry<S>(SynapticRelay<S> relay, ErrorPolicy errorPolicy) {}

    /**
     * Creates a new builder for a PathwayEngine.
     *
     * @param pathwayName the name of the pathway
     * @param <S>         the type of the signal
     * @return a new Builder instance
     */
    public static <S> Builder<S> builder(final String pathwayName) {
        return new Builder<>(pathwayName);
    }

    /**
     * Builder for constructing a {@link PathwayEngine}.
     *
     * @param <S> the type of the signal
     */
    public static final class Builder<S> {

        private final String pathwayName;
        private final List<RelayEntry<S>> entries = new ArrayList<>();
        private Function<SynapticRelay<S>, SynapticRelay<S>> interceptor = Function.identity();

        private Builder(final String pathwayName) {
            this.pathwayName = pathwayName;
        }

        /**
         * Sets an interceptor to be applied to all added relays.
         *
         * @param interceptor the interceptor function
         * @return this builder
         */
        public Builder<S> withInterceptor(final Function<SynapticRelay<S>, SynapticRelay<S>> interceptor) {
            this.interceptor = interceptor != null ? interceptor : Function.identity();
            return this;
        }

        /**
         * Adds a relay to the pathway with default FAIL_FAST policy.
         *
         * @param name  the name of the relay
         * @param relay the relay instance
         * @return this builder
         */
        public Builder<S> relay(final String name, final SynapticRelay<S> relay) {
            return relay(name, relay, ErrorPolicy.FAIL_FAST);
        }

        /**
         * Adds a relay to the pathway with the specified error policy.
         *
         * @param name        the name of the relay
         * @param relay       the relay instance
         * @param errorPolicy the error policy
         * @return this builder
         */
        public Builder<S> relay(final String name, final SynapticRelay<S> relay, final ErrorPolicy errorPolicy) {
            final SynapticRelay<S> namedRelay = new NamedRelay<>(name, relay);
            final SynapticRelay<S> interceptedRelay = interceptor.apply(namedRelay);
            this.entries.add(new RelayEntry<>(interceptedRelay, errorPolicy));
            return this;
        }

        /**
         * Adds a conditionally executed gated relay to the pathway.
         *
         * @param name        the name of the relay
         * @param gate        the predicate condition
         * @param relay       the relay instance
         * @param errorPolicy the error policy
         * @return this builder
         */
        public Builder<S> gated(final String name, final Predicate<S> gate, final SynapticRelay<S> relay, final ErrorPolicy errorPolicy) {
            final SynapticRelay<S> gatedRelay = new GatedRelay<>(name, gate, relay);
            final SynapticRelay<S> interceptedRelay = interceptor.apply(gatedRelay);
            this.entries.add(new RelayEntry<>(interceptedRelay, errorPolicy));
            return this;
        }

        /**
         * Adds a divergent relay to execute multiple branches in parallel with uniform FAIL_FAST policy.
         *
         * @param name     the name of the relay
         * @param branches the parallel branches
         * @return this builder
         */
        public Builder<S> divergent(final String name, final List<SynapticRelay<S>> branches) {
            return divergent(name, branches, branches.stream().map(b -> ErrorPolicy.FAIL_FAST).toList());
        }

        /**
         * Adds a divergent relay to execute multiple branches in parallel with individual error policies.
         *
         * @param name            the name of the relay
         * @param branches        the parallel branches
         * @param branchPolicies  error policies corresponding to each branch
         * @return this builder
         */
        public Builder<S> divergent(final String name, final List<SynapticRelay<S>> branches, final List<ErrorPolicy> branchPolicies) {
            final List<RelayEntry<S>> branchEntries = new ArrayList<>();
            for (int i = 0; i < branches.size(); i++) {
                final SynapticRelay<S> interceptedBranch = interceptor.apply(branches.get(i));
                final ErrorPolicy policy = (branchPolicies != null && i < branchPolicies.size())
                        ? branchPolicies.get(i) : ErrorPolicy.FAIL_FAST;
                branchEntries.add(new RelayEntry<>(interceptedBranch, policy));
            }
            final DivergentRelay<S> divergentRelay = new DivergentRelay<>(name, branchEntries, true);
            return relay(name, divergentRelay, ErrorPolicy.FAIL_FAST);
        }

        /**
         * Adds a circuit-breaker-protected relay to the pathway.
         *
         * @param name             the name of the relay
         * @param relay            the relay instance
         * @param failureThreshold failure count threshold
         * @param cooldownMs        cooldown duration in ms
         * @param errorPolicy      error policy
         * @return this builder
         */
        public Builder<S> circuitBreaker(
                final String name,
                final SynapticRelay<S> relay,
                final int failureThreshold,
                final long cooldownMs,
                final ErrorPolicy errorPolicy) {
            final CircuitBreakerRelay<S> cbRelay = new CircuitBreakerRelay<>(relay, failureThreshold, cooldownMs);
            return relay(name, cbRelay, errorPolicy);
        }

        /**
         * Adds a consolidation relay to dispatch asynchronous work. Uses DEGRADE_GRACEFULLY policy by default.
         *
         * @param name        the name of the relay
         * @param asyncAction the consumer action
         * @return this builder
         */
        public Builder<S> consolidate(final String name, final Consumer<S> asyncAction) {
            final ConsolidationRelay<S> consolidationRelay = new ConsolidationRelay<>(name, asyncAction);
            return relay(name, consolidationRelay, ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        /**
         * Builds the immutable PathwayEngine.
         *
         * @return the constructed pathway
         */
        public PathwayEngine<S> build() {
            return new PathwayEngine<>(pathwayName, entries);
        }
    }
}
