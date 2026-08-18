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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Orchestrates a sequence of synaptic relays to process a signal.
 *
 * @param <S> the type of the signal
 */
public final class CognitivePathway<S> {

    private static final Logger log = LoggerFactory.getLogger(CognitivePathway.class);

    private final String pathwayName;
    private final List<RelayEntry<S>> entries;

    private CognitivePathway(final String pathwayName, final List<RelayEntry<S>> entries) {
        this.pathwayName = pathwayName;
        this.entries = List.copyOf(entries);
    }

    /**
     * Conducts the signal through the pathway.
     *
     * @param signal the signal to conduct
     * @return the processed signal
     */
    public S conduct(final S signal) {
        for (final RelayEntry<S> entry : entries) {
            try {
                final boolean shouldContinue = entry.relay().transmit(signal);
                if (!shouldContinue) {
                    log.debug("Pathway '{}' short-circuited at relay '{}'", pathwayName, entry.relay().relayName());
                    break;
                }
            } catch (final Exception e) {
                if (entry.errorPolicy() == ErrorPolicy.FAIL_FAST) {
                    throw new CognitivePathwayException("Failed at relay: " + entry.relay().relayName(), e);
                } else {
                    log.warn("Pathway '{}' degraded gracefully at relay '{}' due to error.", pathwayName, entry.relay().relayName(), e);
                }
            }
        }
        return signal;
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
     * Creates a new builder for a CognitivePathway.
     *
     * @param pathwayName the name of the pathway
     * @param <S>         the type of the signal
     * @return a new Builder instance
     */
    public static <S> Builder<S> pathway(final String pathwayName) {
        return new Builder<>(pathwayName);
    }

    /**
     * Builder for constructing a {@link CognitivePathway}.
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
            this.interceptor = interceptor;
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
         * Adds a divergent relay to execute multiple branches in parallel.
         * Note: Interceptor is applied to each branch and to the DivergentRelay itself.
         *
         * @param name     the name of the relay
         * @param branches the parallel branches
         * @return this builder
         */
        public Builder<S> divergent(final String name, final List<SynapticRelay<S>> branches) {
            final List<SynapticRelay<S>> interceptedBranches = new ArrayList<>();
            for (final SynapticRelay<S> branch : branches) {
                interceptedBranches.add(interceptor.apply(branch));
            }
            final DivergentRelay<S> divergentRelay = new DivergentRelay<>(name, interceptedBranches);
            return relay(name, divergentRelay, ErrorPolicy.FAIL_FAST);
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
         * Builds the immutable CognitivePathway.
         *
         * @return the constructed pathway
         */
        public CognitivePathway<S> build() {
            return new CognitivePathway<>(pathwayName, entries);
        }
    }
}
