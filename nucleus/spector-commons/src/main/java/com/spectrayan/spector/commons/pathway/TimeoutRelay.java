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

import com.spectrayan.spector.commons.concurrent.ConcurrentTasks;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

/**
 * Decorator relay that wraps a delegate with a timeout budget (ADR-0036 §7).
 *
 * <p>Executes the delegate on a virtual thread via
 * {@link ConcurrentTasks#callWithTimeout(java.util.concurrent.Callable, Duration)}.
 * On expiry, the virtual thread is interrupted and a
 * {@link CognitivePathwayException} with {@link FaultKind#TRANSIENT} is thrown.</p>
 *
 * <p>Only relays implementing {@link InterruptibleRelay} should be wrapped — the
 * {@link PathwayComposer} enforces this at build time.</p>
 *
 * @param <S> signal type
 */
public final class TimeoutRelay<S> implements SynapticRelay<S> {

    private final SynapticRelay<S> delegate;
    private final Duration budget;
    private final String relayName;

    /**
     * Creates a timeout decorator.
     *
     * @param delegate  the inner relay to wrap
     * @param budget    maximum execution time
     * @param relayName name for diagnostics
     */
    public TimeoutRelay(SynapticRelay<S> delegate, Duration budget, String relayName) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        this.budget = Objects.requireNonNull(budget, "budget cannot be null");
        this.relayName = Objects.requireNonNull(relayName, "relayName cannot be null");
    }

    @Override
    public boolean transmit(S signal) throws Exception {
        try {
            return ConcurrentTasks.callWithTimeout(() -> delegate.transmit(signal), budget);
        } catch (TimeoutException e) {
            String pathwayName = "unknown";
            if (signal instanceof ContextualSignal cs && cs.context() != null
                    && cs.context().scope() != null) {
                pathwayName = cs.context().scope().pathwayName();
            }
            com.spectrayan.spector.commons.observation.PathwayObservationHooks.get(
                    signal instanceof ContextualSignal cs ? cs.context() : null)
                    .onTimeout(pathwayName, relayName);
            throw new CognitivePathwayException(
                    pathwayName, relayName, FaultKind.TRANSIENT, false, e);
        }
    }

    /** Returns the wrapped delegate relay. */
    public SynapticRelay<S> delegate() { return delegate; }

    /** Returns the timeout budget. */
    public Duration budget() { return budget; }
}
