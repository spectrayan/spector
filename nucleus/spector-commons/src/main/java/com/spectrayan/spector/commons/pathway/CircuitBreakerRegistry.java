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

import java.util.Map;

/**
 * Registry for named, process-wide {@link CircuitBreaker} instances.
 */
public interface CircuitBreakerRegistry {

    /**
     * Retrieves or registers a named circuit breaker.
     *
     * <p>First registration wins: if a breaker with {@code name} already exists, it is returned.
     * If the supplied {@code config} does not match the existing configuration, a warning is logged.</p>
     *
     * @param name   unique breaker name
     * @param config configuration used for initial registration
     * @return the circuit breaker instance
     */
    CircuitBreaker get(String name, CircuitBreakerConfig config);

    /**
     * Retrieves or registers a named circuit breaker with default remote config.
     *
     * @param name unique breaker name
     * @return the circuit breaker instance
     */
    default CircuitBreaker get(String name) {
        return get(name, CircuitBreakerConfig.remote());
    }

    /**
     * Retrieves or registers a circuit breaker for the given {@link BreakerRef}.
     *
     * @param ref breaker reference
     * @return the circuit breaker instance
     */
    default CircuitBreaker get(BreakerRef ref) {
        return get(ref.name(), ref.config());
    }

    /**
     * Returns an unmodifiable view of all registered circuit breakers.
     *
     * @return map of breaker name to breaker instance
     */
    Map<String, CircuitBreaker> all();

    /**
     * Resets all registered circuit breakers to CLOSED state.
     */
    void resetAll();

    /**
     * Creates a new in-memory {@link CircuitBreakerRegistry}.
     *
     * @return registry instance
     */
    static CircuitBreakerRegistry create() {
        return new DefaultCircuitBreakerRegistry();
    }
}
