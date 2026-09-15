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

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Fluent builder for composing {@link CognitivePathway} instances from recipes, stages, and relays.
 *
 * @param <S> signal type
 */
public interface PathwayComposer<S> {

    /**
     * Fluent builder for a pathway stage configured with resilience decorators (ADR-0036 §6).
     *
     * <p>Decorators wrap the inner relay in order:
     * {@code bulkhead -> timeout -> retry -> circuit breaker -> named relay -> user relay}.</p>
     *
     * @param <S> signal type
     */
    interface StageBuilder<S> {
        /** Sets the user relay for this stage. */
        StageBuilder<S> relay(SynapticRelay<S> relay);

        /** Sets the error disposition policy. Defaults to {@link ErrorPolicy#FAIL_FAST}. */
        StageBuilder<S> policy(ErrorPolicy policy);

        /**
         * Sets a timeout execution budget for each attempt.
         *
         * @throws IllegalArgumentException if the relay does not implement {@link InterruptibleRelay}
         */
        StageBuilder<S> timeout(Duration budget);

        /**
         * Sets a retry policy for transient failures.
         *
         * @throws IllegalArgumentException if the relay does not implement {@link IdempotentRelay}
         */
        StageBuilder<S> retry(RetryPolicy retryPolicy);

        /** Sets a circuit breaker permit reference for this stage. */
        StageBuilder<S> breaker(BreakerRef breakerRef);

        /** Sets a bulkhead configuration using the stage name as the bulkhead key. */
        StageBuilder<S> bulkhead(BulkheadConfig bulkheadConfig);

        /** Sets a bulkhead configuration with an explicit shared bulkhead name. */
        StageBuilder<S> bulkhead(String bulkheadName, BulkheadConfig bulkheadConfig);

        /** Builds the decorator chain and adds the stage to the composer. */
        PathwayComposer<S> add();
    }

    /**
     * Begins defining a stage configured with resilience decorators.
     *
     * @param name stage name
     * @return stage builder
     */
    StageBuilder<S> stage(String name);

    /**
     * Sets an interceptor decorating each relay.
     *
     * @param interceptor interceptor function
     * @return this composer
     */
    PathwayComposer<S> withInterceptor(Function<SynapticRelay<S>, SynapticRelay<S>> interceptor);

    /**
     * Adds a relay with an explicit error policy.
     *
     * @param name   relay name
     * @param relay  relay instance
     * @param policy error policy
     * @return this composer
     */
    PathwayComposer<S> relay(String name, SynapticRelay<S> relay, ErrorPolicy policy);

    /**
     * Adds a relay with default {@link ErrorPolicy#FAIL_FAST}.
     *
     * @param name  relay name
     * @param relay relay instance
     * @return this composer
     */
    default PathwayComposer<S> relay(String name, SynapticRelay<S> relay) {
        return relay(name, relay, ErrorPolicy.FAIL_FAST);
    }

    /**
     * Adds a gated relay evaluated only when the gate predicate matches.
     *
     * @param name   relay name
     * @param gate   predicate condition
     * @param relay  relay instance
     * @param policy error policy
     * @return this composer
     */
    PathwayComposer<S> gated(String name, Predicate<S> gate, SynapticRelay<S> relay, ErrorPolicy policy);

    /**
     * Adds a gated relay with default {@link ErrorPolicy#FAIL_FAST}.
     *
     * @param name  relay name
     * @param gate  predicate condition
     * @param relay relay instance
     * @return this composer
     */
    default PathwayComposer<S> gated(String name, Predicate<S> gate, SynapticRelay<S> relay) {
        return gated(name, gate, relay, ErrorPolicy.FAIL_FAST);
    }

    /**
     * Adds divergent parallel execution branches with individual error policies.
     *
     * @param name     relay name
     * @param branches parallel branch relays
     * @param policies corresponding error policies
     * @return this composer
     * @throws IllegalArgumentException if any policy is {@link ErrorPolicy#ABORT} or any branch contains {@link PathwayRelay}
     */
    PathwayComposer<S> divergent(String name, List<SynapticRelay<S>> branches, List<ErrorPolicy> policies);

    /**
     * Adds divergent parallel execution branches with default {@link ErrorPolicy#FAIL_FAST}.
     *
     * @param name     relay name
     * @param branches parallel branch relays
     * @return this composer
     */
    default PathwayComposer<S> divergent(String name, List<SynapticRelay<S>> branches) {
        return divergent(name, branches, branches != null
                ? branches.stream().map(b -> ErrorPolicy.FAIL_FAST).toList()
                : List.of());
    }

    /**
     * Adds an asynchronous consolidation relay.
     *
     * @param name        relay name
     * @param asyncAction action to execute asynchronously
     * @return this composer
     */
    PathwayComposer<S> consolidate(String name, Consumer<S> asyncAction);

    /**
     * Adds a circuit-breaker protected relay.
     *
     * @param name             relay name
     * @param relay            relay instance
     * @param failureThreshold consecutive failure threshold
     * @param cooldownMs       cooldown in milliseconds
     * @param policy           error policy
     * @return this composer
     * @deprecated Use {@link #stage(String)} with {@link StageBuilder#breaker(BreakerRef)} instead.
     */
    @Deprecated(forRemoval = true, since = "1.5.0")
    PathwayComposer<S> circuitBreaker(String name, SynapticRelay<S> relay, int failureThreshold, long cooldownMs, ErrorPolicy policy);

    /**
     * Adds a nested pathway relay.
     *
     * @param name   relay name
     * @param nested pathway relay instance
     * @param policy error policy
     * @return this composer
     */
    PathwayComposer<S> pathway(String name, PathwayRelay<?, ?, ?> nested, ErrorPolicy policy);

    /**
     * Adds a nested pathway relay with default {@link ErrorPolicy#FAIL_FAST}.
     *
     * @param name   relay name
     * @param nested pathway relay instance
     * @return this composer
     */
    default PathwayComposer<S> pathway(String name, PathwayRelay<?, ?, ?> nested) {
        return pathway(name, nested, ErrorPolicy.FAIL_FAST);
    }

    /**
     * Adds an optional relay resolved via {@link RelayFactory}. If unresolved, the stage is skipped.
     *
     * @param name   relay name
     * @param type   relay class type
     * @param policy error policy
     * @return this composer
     */
    PathwayComposer<S> optional(String name, Class<? extends SynapticRelay<S>> type, ErrorPolicy policy);

    /**
     * Builds and returns the configured {@link CognitivePathway}.
     *
     * @return cognitive pathway instance
     */
    CognitivePathway<S> build();

    /**
     * Creates a new composer instance for the given pathway name.
     *
     * @param pathwayName pathway name
     * @param <S>         signal type
     * @return pathway composer
     */
    static <S> PathwayComposer<S> of(String pathwayName) {
        return new DefaultPathwayComposer<>(pathwayName);
    }

    /**
     * Creates a new composer instance with a relay factory.
     *
     * @param pathwayName  pathway name
     * @param relayFactory relay factory for optional relays
     * @param <S>          signal type
     * @return pathway composer
     */
    static <S> PathwayComposer<S> of(String pathwayName, RelayFactory relayFactory) {
        return new DefaultPathwayComposer<>(pathwayName, relayFactory);
    }
}
