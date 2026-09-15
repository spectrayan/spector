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

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Default implementation of {@link PathwayComposer} backed by {@link CognitivePathway.Builder}.
 *
 * @param <S> signal type
 */
public final class DefaultPathwayComposer<S> implements PathwayComposer<S> {

    private final CognitivePathway.Builder<S> builder;
    private final RelayFactory relayFactory;

    public DefaultPathwayComposer(final String pathwayName) {
        this(pathwayName, null);
    }

    public DefaultPathwayComposer(final String pathwayName, final RelayFactory relayFactory) {
        this.builder = CognitivePathway.pathway(pathwayName);
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
            throw new IllegalArgumentException("Divergent branch cannot use ErrorPolicy.ABORT");
        }
        if (branches != null) {
            for (final SynapticRelay<S> branch : branches) {
                if (isOrContainsPathwayRelay(branch)) {
                    throw new IllegalArgumentException("Divergent branch cannot contain PathwayRelay: " + branch.relayName());
                }
            }
        }
        builder.divergent(name, branches, policies);
        return this;
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
    public CognitivePathway<S> build() {
        return builder.build();
    }
}
