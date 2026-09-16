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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Shared registry for named {@link Semaphore bulkheads}, same pattern as
 * {@link CircuitBreakerRegistry}.
 *
 * <p>Bulkheads are identified by name. The first call to {@link #get(String, BulkheadConfig)}
 * with a given name creates the semaphore; subsequent calls return the same instance.
 * A config mismatch on an existing name logs a warning and returns the existing semaphore
 * (same semantics as breaker registry).</p>
 *
 * @see BulkheadConfig
 * @see BulkheadRelay
 */
public final class BulkheadRegistry {

    private final ConcurrentHashMap<String, BulkheadEntry> entries = new ConcurrentHashMap<>();

    /**
     * Creates a new empty bulkhead registry.
     */
    public static BulkheadRegistry create() {
        return new BulkheadRegistry();
    }

    /**
     * Returns (or creates) the bulkhead semaphore for the given name.
     *
     * @param name   bulkhead name
     * @param config bulkhead configuration
     * @return the semaphore with {@code config.maxInFlight()} permits
     */
    public Semaphore get(String name, BulkheadConfig config) {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(config, "config cannot be null");
        return entries.computeIfAbsent(name, n -> new BulkheadEntry(config,
                new Semaphore(config.maxInFlight(), true))).semaphore;
    }

    /**
     * Returns the configuration for the given bulkhead name, or null if not registered.
     *
     * @param name bulkhead name
     * @return config or null
     */
    public BulkheadConfig configFor(String name) {
        BulkheadEntry entry = entries.get(name);
        return entry != null ? entry.config : null;
    }

    /**
     * Returns the number of registered bulkheads.
     */
    public int size() {
        return entries.size();
    }

    private record BulkheadEntry(BulkheadConfig config, Semaphore semaphore) {}
}
