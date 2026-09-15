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

/**
 * Call-site reference to a named, shareable {@link CircuitBreaker}.
 *
 * <p>Trip state is shared across call sites using the same {@code name}, while {@link #onOpen()}
 * defines the local disposition (fail vs. bypass) for this specific invocation site.</p>
 *
 * @param name   the unique name of the breaker downstream (e.g. "embed-provider")
 * @param config the configuration used if the breaker has not yet been registered
 * @param onOpen the reaction when the circuit is open or half-open capacity is saturated
 */
public record BreakerRef(String name, CircuitBreakerConfig config, OnOpen onOpen) {

    public BreakerRef {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(config, "config cannot be null");
        Objects.requireNonNull(onOpen, "onOpen cannot be null");
    }

    public static BreakerRef of(final String name, final CircuitBreakerConfig config, final OnOpen onOpen) {
        return new BreakerRef(name, config, onOpen);
    }

    public static BreakerRef of(final String name, final OnOpen onOpen) {
        return new BreakerRef(name, CircuitBreakerConfig.remote(), onOpen);
    }

    public static BreakerRef of(final String name, final CircuitBreakerConfig config) {
        return new BreakerRef(name, config, OnOpen.FAIL);
    }

    public static BreakerRef of(final String name) {
        return new BreakerRef(name, CircuitBreakerConfig.remote(), OnOpen.FAIL);
    }
}
