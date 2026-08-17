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

import java.util.function.Predicate;

/**
 * A relay that conditionally executes its delegate based on a predicate gate.
 *
 * @param <S> the type of the signal
 */
public final class GatedRelay<S> implements SynapticRelay<S> {

    private final String name;
    private final Predicate<S> gate;
    private final SynapticRelay<S> delegate;

    /**
     * Constructs a new GatedRelay.
     *
     * @param name     the name of the relay
     * @param gate     the predicate condition to evaluate
     * @param delegate the relay to execute if the gate evaluates to true
     */
    public GatedRelay(final String name, final Predicate<S> gate, final SynapticRelay<S> delegate) {
        this.name = name;
        this.gate = gate;
        this.delegate = delegate;
    }

    @Override
    public boolean transmit(final S signal) throws Exception {
        if (gate.test(signal)) {
            return delegate.transmit(signal);
        }
        return true;
    }

    @Override
    public String relayName() {
        return name;
    }
}
