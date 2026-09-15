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

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * An adapter {@link SynapticRelay} that delegates execution to another {@link Pathway} discovered via {@link PathwayCatalog}.
 *
 * <p>Optionally wraps the nested invocation in a {@link CircuitBreaker} permit lifecycle
 * per ADR-0036 §9.3 — acquire before invoke, record success/failure after. The call-site
 * {@link OnOpen} determines whether an open circuit fails fast or bypasses gracefully.</p>
 *
 * @param <S> parent contextual signal type
 * @param <I> target pathway input type
 * @param <O> target pathway output type
 */
public final class PathwayRelay<S extends ContextualSignal, I, O> implements SynapticRelay<S> {

    private final String name;
    private final Class<? extends Pathway<I, O>> targetType;
    private final Function<S, I> toInput;
    private final BiConsumer<S, O> absorb;
    private final boolean required;
    private final BreakerRef breakerRef;

    public PathwayRelay(final String name,
                        final Class<? extends Pathway<I, O>> targetType,
                        final Function<S, I> toInput,
                        final BiConsumer<S, O> absorb,
                        final boolean required,
                        final BreakerRef breakerRef) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.targetType = Objects.requireNonNull(targetType, "targetType cannot be null");
        this.toInput = Objects.requireNonNull(toInput, "toInput cannot be null");
        this.absorb = Objects.requireNonNull(absorb, "absorb cannot be null");
        this.required = required;
        this.breakerRef = breakerRef;
    }

    /** Backwards-compatible constructor without breaker. */
    public PathwayRelay(final String name,
                        final Class<? extends Pathway<I, O>> targetType,
                        final Function<S, I> toInput,
                        final BiConsumer<S, O> absorb,
                        final boolean required) {
        this(name, targetType, toInput, absorb, required, null);
    }

    @Override
    public boolean transmit(final S signal) throws Exception {
        final PathwayContext ctx = signal.context();
        if (ctx == null) {
            throw new CognitivePathwayException(name, "<entry>", FaultKind.CONTRACT, false,
                    new IllegalStateException("PathwayRelay '" + name + "' executed without PathwayContext"));
        }
        final PathwayCatalog catalog = ctx.catalog();
        if (catalog == null) {
            if (required) {
                throw new CognitivePathwayException(
                        ctx.scope().pathwayName(),
                        name,
                        FaultKind.CONTRACT,
                        false,
                        new IllegalStateException("PathwayCatalog is null in PathwayContext"));
            }
            return true;
        }

        final Optional<Pathway<I, O>> target = catalog.find(targetType);
        if (target.isEmpty()) {
            if (required) {
                throw new CognitivePathwayException(
                        ctx.scope().pathwayName(),
                        name,
                        FaultKind.CONTRACT,
                        false,
                        new IllegalStateException(targetType.getSimpleName() + " not registered in catalog"));
            }
            return true;
        }

        // Breaker permit lifecycle (ADR-0036 §9.3)
        final CircuitBreaker breaker = resolveBreaker(ctx);
        final CircuitBreaker.Permit permit;
        if (breaker != null) {
            permit = breaker.tryAcquire(breakerRef.onOpen());
            if (permit.isBypass()) {
                final String scopeName = ctx.scope().pathwayName() + "/" + name;
                ctx.outcome().markBypassed(scopeName, "circuit_open:" + breakerRef.name());
                return true;
            }
        } else {
            permit = null;
        }

        try {
            final I nestedInput = toInput.apply(signal);
            // catalog.invoke handles child outcome isolation + importFrom
            final O output = catalog.invoke(targetType, ctx, nestedInput);
            absorb.accept(signal, output);
            if (breaker != null && permit != null) {
                breaker.onSuccess(permit);
            }
            return true;
        } catch (final Exception e) {
            if (breaker != null && permit != null) {
                breaker.onFailure(permit, Faults.kindOf(e));
            }
            throw new CognitivePathwayException(
                    ctx.scope().pathwayName(),
                    name,
                    Faults.kindOf(e),
                    true,
                    e);
        }
    }

    /**
     * Resolves the circuit breaker from the context's registry, or returns null if no breaker ref is configured.
     */
    private CircuitBreaker resolveBreaker(final PathwayContext ctx) {
        if (breakerRef == null) {
            return null;
        }
        final Optional<CircuitBreakerRegistry> registryOpt = ctx.find(CircuitBreakerRegistry.class);
        if (registryOpt.isEmpty()) {
            return null;
        }
        return registryOpt.get().get(breakerRef);
    }

    @Override
    public String relayName() {
        return name;
    }

    public Class<? extends Pathway<I, O>> targetType() {
        return targetType;
    }

    public boolean isRequired() {
        return required;
    }

    public BreakerRef breakerRef() {
        return breakerRef;
    }

    public static <S extends ContextualSignal, I, O> Builder<S, I, O> to(final Class<? extends Pathway<I, O>> targetType) {
        return new Builder<>(targetType);
    }

    public static final class Builder<S extends ContextualSignal, I, O> {
        private final Class<? extends Pathway<I, O>> targetType;
        private String name;
        private Function<S, I> toInput;
        private BiConsumer<S, O> absorb = (s, o) -> {};
        private boolean required = true;
        private BreakerRef breakerRef;

        private Builder(final Class<? extends Pathway<I, O>> targetType) {
            this.targetType = Objects.requireNonNull(targetType, "targetType cannot be null");
            this.name = targetType.getSimpleName();
        }

        public Builder<S, I, O> named(final String name) {
            this.name = name;
            return this;
        }

        public Builder<S, I, O> from(final Function<S, I> toInput) {
            this.toInput = toInput;
            return this;
        }

        public Builder<S, I, O> into(final BiConsumer<S, O> absorb) {
            this.absorb = absorb;
            return this;
        }

        public Builder<S, I, O> required(final boolean required) {
            this.required = required;
            return this;
        }

        public Builder<S, I, O> breaker(final BreakerRef breakerRef) {
            this.breakerRef = breakerRef;
            return this;
        }

        public PathwayRelay<S, I, O> build() {
            Objects.requireNonNull(name, "name cannot be null");
            Objects.requireNonNull(toInput, "toInput mapper cannot be null");
            return new PathwayRelay<>(name, targetType, toInput, absorb, required, breakerRef);
        }
    }
}
