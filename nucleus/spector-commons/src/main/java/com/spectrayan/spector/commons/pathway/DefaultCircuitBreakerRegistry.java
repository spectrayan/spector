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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default thread-safe implementation of {@link CircuitBreakerRegistry}.
 */
public final class DefaultCircuitBreakerRegistry implements CircuitBreakerRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultCircuitBreakerRegistry.class);

    private final ConcurrentHashMap<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();

    @Override
    public CircuitBreaker get(final String name, final CircuitBreakerConfig config) {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(config, "config cannot be null");

        return breakers.compute(name, (k, existing) -> {
            if (existing != null) {
                if (!existing.config().equals(config)) {
                    log.warn("Circuit breaker '{}' requested with config {} which differs from existing registration {}. First registration wins.",
                            name, config, existing.config());
                }
                return existing;
            }
            return new CircuitBreaker(k, config);
        });
    }

    @Override
    public Map<String, CircuitBreaker> all() {
        return Collections.unmodifiableMap(breakers);
    }

    @Override
    public void resetAll() {
        breakers.values().forEach(CircuitBreaker::reset);
    }
}
