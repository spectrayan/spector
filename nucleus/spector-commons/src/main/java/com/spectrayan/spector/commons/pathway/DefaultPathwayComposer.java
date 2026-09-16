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

import com.spectrayan.spector.commons.error.ErrorCode;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Default implementation of {@link PathwayComposer} backed by {@link PathwayEngine.Builder}.
 *
 * @param <S> signal type
 */
public final class DefaultPathwayComposer<S> implements PathwayComposer<S> {

    private static final Logger log = LoggerFactory.getLogger(DefaultPathwayComposer.class);

    private final PathwayEngine.Builder<S> builder;
    private final RelayFactory relayFactory;
    /** Retained for build-time misconfiguration diagnostics (ADR-0036 §7.2, §8). */
    private final String pathwayName;

    public DefaultPathwayComposer(final String pathwayName) {
        this(pathwayName, null);
    }

    public DefaultPathwayComposer(final String pathwayName, final RelayFactory relayFactory) {
        this.pathwayName = pathwayName;
        this.builder = PathwayEngine.builder(pathwayName);
        this.relayFactory = relayFactory;
    }

    @Override
    public PathwayComposer<S> withInterceptor(final Function<SynapticRelay<S>, SynapticRelay<S>> interceptor) {
        builder.withInterceptor(interceptor);
        return this;
    }

    @Override
    public PathwayComposer<S> relay(final String name, final SynapticRelay<S> relay, final ErrorPolicy policy) {
        builder.relay(name, relay, policy);
        return this;
    }

    @Override
    public PathwayComposer<S> gated(final String name, final Predicate<S> gate, final SynapticRelay<S> relay, final ErrorPolicy policy) {
        builder.gated(name, gate, relay, policy);
        return this;
    }

    @Override
    public PathwayComposer<S> divergent(final String name, final List<SynapticRelay<S>> branches, final List<ErrorPolicy> policies) {
        if (policies != null && policies.contains(ErrorPolicy.ABORT)) {
            throw new CognitivePathwayException(ErrorCode.PATHWAY_MISCONFIGURED, pathwayName, name,
                    FaultKind.CONTRACT, false, new IllegalStateException("Divergent branch cannot use ErrorPolicy.ABORT"));
        }
        if (branches != null) {
            for (final SynapticRelay<S> branch : branches) {
                if (isOrContainsPathwayRelay(branch)) {
                    throw new CognitivePathwayException(ErrorCode.PATHWAY_MISCONFIGURED, pathwayName, name,
                    FaultKind.CONTRACT, false, new IllegalStateException("Divergent branch cannot contain PathwayRelay: " + branch.relayName()));
                }
                final String scopeToucher = findScopeTouchingWrapper(branch);
                if (scopeToucher != null) {
                    throw new CognitivePathwayException(ErrorCode.PATHWAY_MISCONFIGURED, pathwayName, name,
                            FaultKind.CONTRACT, false, new IllegalStateException(
                            "Divergent branch '" + branch.relayName() + "' contains a " + scopeToucher
                            + ", which reads the thread-confined ConductionScope. Divergent branches run on "
                            + "separate virtual threads, so this would throw at runtime. Compose the "
                            + scopeToucher + " OUTSIDE the divergent relay instead."));
                }
            }
        }
        builder.divergent(name, branches, policies);
        return this;
    }

    /**
     * Detects branch wrappers that read {@link ConductionScope} during {@code transmit}.
     *
     * <p>{@link ConductionScope} is thread-confined (ADR-0035 §6.5.1) and
     * {@link DivergentRelay} dispatches branches onto separate virtual threads.
     * Any wrapper that calls {@code scope().pathwayName()} — {@link GatedRelay}
     * (to record a BYPASSED trace) and {@link CircuitBreakerRelay} (to mark the
     * outcome on an open circuit) — therefore fails with a confinement violation
     * when used inside a branch. Reject at build time rather than at 3am.</p>
     *
     * @param relay branch relay to inspect, possibly wrapped
     * @return simple name of the offending wrapper, or {@code null} when safe
     */
    private String findScopeTouchingWrapper(SynapticRelay<S> relay) {
        SynapticRelay<?> r = relay;
        while (r != null) {
            if (r instanceof GatedRelay<?>) {
                return "GatedRelay";
            }
            if (r instanceof CircuitBreakerRelay<?>) {
                return "CircuitBreakerRelay";
            }
            if (r instanceof TimeoutRelay<?>) {
                return "TimeoutRelay";
            }
            if (r instanceof BulkheadRelay<?>) {
                return "BulkheadRelay";
            }
            if (r instanceof RetryRelay<?>) {
                return "RetryRelay";
            }
            if (r instanceof NamedRelay<?> nr) {
                r = nr.delegate();
            } else {
                break;
            }
        }
        return null;
    }

    @Override
    public StageBuilder<S> stage(final String name) {
        return new DefaultStageBuilder(name);
    }

    private final class DefaultStageBuilder implements StageBuilder<S> {
        private final String stageName;
        private SynapticRelay<S> relay;
        private ErrorPolicy policy = ErrorPolicy.FAIL_FAST;
        private java.time.Duration timeoutBudget;
        private RetryPolicy retryPolicy;
        private BreakerRef breakerRef;
        private String bulkheadName;
        private BulkheadConfig bulkheadConfig;
        private Predicate<S> gate;
        /** true when the budget came from timeoutIfInterruptible() and may be skipped. */
        private boolean timeoutOptional;
        /** true when the policy came from retryIfIdempotent() and may be skipped. */
        private boolean retryOptional;

        DefaultStageBuilder(final String stageName) {
            this.stageName = Objects.requireNonNull(stageName, "stageName cannot be null");
        }

        @Override
        public StageBuilder<S> relay(final SynapticRelay<S> relay) {
            this.relay = Objects.requireNonNull(relay, "relay cannot be null");
            return this;
        }

        @Override
        public StageBuilder<S> policy(final ErrorPolicy policy) {
            this.policy = Objects.requireNonNull(policy, "policy cannot be null");
            return this;
        }

        @Override
        public StageBuilder<S> timeout(final java.time.Duration budget) {
            this.timeoutBudget = Objects.requireNonNull(budget, "budget cannot be null");
            return this;
        }

        @Override
        public StageBuilder<S> retry(final RetryPolicy retryPolicy) {
            this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy cannot be null");
            return this;
        }

        @Override
        public StageBuilder<S> breaker(final BreakerRef breakerRef) {
            this.breakerRef = Objects.requireNonNull(breakerRef, "breakerRef cannot be null");
            return this;
        }

        @Override
        public StageBuilder<S> bulkhead(final BulkheadConfig bulkheadConfig) {
            return bulkhead(this.stageName, bulkheadConfig);
        }

        @Override
        public StageBuilder<S> bulkhead(final String bulkheadName, final BulkheadConfig bulkheadConfig) {
            this.bulkheadName = Objects.requireNonNull(bulkheadName, "bulkheadName cannot be null");
            this.bulkheadConfig = Objects.requireNonNull(bulkheadConfig, "bulkheadConfig cannot be null");
            return this;
        }

        @Override
        public StageBuilder<S> timeoutIfInterruptible(final java.time.Duration budget) {
            this.timeoutOptional = true;
            this.timeoutBudget = budget;
            return this;
        }

        @Override
        public StageBuilder<S> retryIfIdempotent(final RetryPolicy retryPolicy) {
            this.retryOptional = true;
            this.retryPolicy = retryPolicy;
            return this;
        }

        @Override
        public StageBuilder<S> gate(final Predicate<S> gate) {
            this.gate = gate;
            return this;
        }

        @Override
        public PathwayComposer<S> add() {
            if (relay == null) {
                throw new CognitivePathwayException(ErrorCode.PATHWAY_MISCONFIGURED, pathwayName, stageName,
                    FaultKind.CONTRACT, false, new IllegalStateException("relay must be set on stage '" + stageName + "'"));
            }

            // Build-time safety validations (ADR-0036 §7.2, §8).
            // Either way a non-interruptible relay never gets a budget; timeoutOptional
            // only decides whether that is a build failure or a logged skip.
            java.time.Duration effectiveTimeout = timeoutBudget;
            if (timeoutBudget != null && !isInterruptible(relay)) {
                if (timeoutOptional) {
                    effectiveTimeout = null;
                    log.debug("Stage '{}' in pathway '{}': relay {} does not declare "
                                    + "InterruptibleRelay, skipping the {} timeout budget",
                            stageName, pathwayName, relay.getClass().getSimpleName(), timeoutBudget);
                } else {
                    throw new CognitivePathwayException(ErrorCode.PATHWAY_MISCONFIGURED, pathwayName, stageName,
                            FaultKind.CONTRACT, false, new IllegalArgumentException("Relay '" + stageName + "' ("
                            + relay.getClass().getSimpleName()
                            + ") does not implement InterruptibleRelay; cannot wrap with a timeout budget"));
                }
            }

            RetryPolicy effectiveRetry = retryPolicy;
            if (retryPolicy != null && retryPolicy.maxAttempts() > 1 && !isIdempotent(relay)) {
                if (retryOptional) {
                    effectiveRetry = null;
                    log.debug("Stage '{}' in pathway '{}': relay {} does not declare "
                                    + "IdempotentRelay, skipping the retry policy",
                            stageName, pathwayName, relay.getClass().getSimpleName());
                } else {
                    throw new CognitivePathwayException(ErrorCode.PATHWAY_MISCONFIGURED, pathwayName, stageName,
                            FaultKind.CONTRACT, false, new IllegalArgumentException("Relay '" + stageName + "' ("
                            + relay.getClass().getSimpleName()
                            + ") does not implement IdempotentRelay; cannot wrap with a retry policy"));
                }
            }

            // Decorator composition order (ADR-0036 §6, §8.6):
            // Outer to inner: bulkhead -> circuit breaker -> retry -> timeout -> relay
            SynapticRelay<S> current = relay;

            if (effectiveTimeout != null) {
                current = new TimeoutRelay<>(current, effectiveTimeout, stageName);
            }
            if (effectiveRetry != null && effectiveRetry.maxAttempts() > 1) {
                current = new RetryRelay<>(current, effectiveRetry, stageName);
            }
            if (breakerRef != null) {
                current = new CircuitBreakerRelay<>(current, breakerRef);
            }
            if (bulkheadConfig != null) {
                current = new BulkheadRelay<>(current, bulkheadConfig, stageName, bulkheadName);
            }
            // Gate goes OUTSIDE every decorator (ADR-0036 §6): a closed gate must not
            // consume a bulkhead permit, count as a breaker success, or start a timeout.
            if (gate != null) {
                current = new GatedRelay<>(stageName, gate, current);
            }

            return DefaultPathwayComposer.this.relay(stageName, current, policy);
        }

        private static boolean isInterruptible(final SynapticRelay<?> r) {
            if (r instanceof InterruptibleRelay ir) {
                return ir.interruptible();
            }
            final SynapticRelay<?> inner = unwrap(r);
            return inner instanceof InterruptibleRelay ir && ir.interruptible();
        }

        private static boolean isIdempotent(final SynapticRelay<?> r) {
            if (r instanceof IdempotentRelay idr) {
                return idr.idempotent();
            }
            final SynapticRelay<?> inner = unwrap(r);
            return inner instanceof IdempotentRelay idr && idr.idempotent();
        }

        private static SynapticRelay<?> unwrap(SynapticRelay<?> r) {
            while (r != null) {
                if (r instanceof NamedRelay<?> nr) {
                    r = nr.delegate();
                } else if (r instanceof GatedRelay<?> gr) {
                    r = gr.delegate();
                } else if (r instanceof CircuitBreakerRelay<?> cbr) {
                    r = cbr.delegate();
                } else if (r instanceof RetryRelay<?> rr) {
                    r = rr.delegate();
                } else if (r instanceof TimeoutRelay<?> tr) {
                    r = tr.delegate();
                } else if (r instanceof BulkheadRelay<?> br) {
                    r = br.delegate();
                } else {
                    break;
                }
            }
            return r;
        }
    }

    private static boolean isOrContainsPathwayRelay(final SynapticRelay<?> relay) {
        if (relay == null) {
            return false;
        }
        if (relay instanceof PathwayRelay) {
            return true;
        }
        if (relay instanceof NamedRelay<?> nr) {
            return isOrContainsPathwayRelay(nr.delegate());
        }
        if (relay instanceof GatedRelay<?> gr) {
            return isOrContainsPathwayRelay(gr.delegate());
        }
        if (relay instanceof CircuitBreakerRelay<?> cbr) {
            return isOrContainsPathwayRelay(cbr.delegate());
        }
        if (relay instanceof RetryRelay<?> rr) {
            return isOrContainsPathwayRelay(rr.delegate());
        }
        if (relay instanceof TimeoutRelay<?> tr) {
            return isOrContainsPathwayRelay(tr.delegate());
        }
        if (relay instanceof BulkheadRelay<?> br) {
            return isOrContainsPathwayRelay(br.delegate());
        }
        return false;
    }

    @Override
    public PathwayComposer<S> consolidate(final String name, final Consumer<S> asyncAction) {
        builder.consolidate(name, asyncAction);
        return this;
    }

    @Override
    public PathwayComposer<S> circuitBreaker(
            final String name,
            final SynapticRelay<S> relay,
            final int failureThreshold,
            final long cooldownMs,
            final ErrorPolicy policy) {
        builder.circuitBreaker(name, relay, failureThreshold, cooldownMs, policy);
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public PathwayComposer<S> pathway(final String name, final PathwayRelay<?, ?, ?> nested, final ErrorPolicy policy) {
        Objects.requireNonNull(nested, "nested PathwayRelay cannot be null");
        return relay(name, (SynapticRelay<S>) nested, policy);
    }

    @Override
    public PathwayComposer<S> optional(final String name, final Class<? extends SynapticRelay<S>> type, final ErrorPolicy policy) {
        if (relayFactory != null && type != null) {
            final SynapticRelay<S> relay = relayFactory.create(type);
            if (relay != null) {
                return relay(name, relay, policy);
            }
        }
        return this;
    }

    @Override
    public PathwayEngine<S> build() {
        return builder.build();
    }
}
