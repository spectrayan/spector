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
package com.spectrayan.spector.commons.observation;

import java.util.Map;

/**
 * Lightweight, zero-dependency observation SPI for cognitive memory operations.
 *
 * <p>Enables low-level core and memory components to fire observation spans and lifecycle
 * timings without taking a compile-time dependency on Micrometer or OpenTelemetry.</p>
 */
@FunctionalInterface
public interface MemoryObservationHook {

    /**
     * No-op implementation that does nothing and returns an empty AutoCloseable.
     */
    MemoryObservationHook NOOP = (name, tags) -> () -> {};

    /**
     * Starts an observation span for the given operation name and contextual tags.
     *
     * @param name operation name (e.g. "spector.memory.recall", "spector.taskqueue.process")
     * @param tags contextual low-cardinality and high-cardinality tags
     * @return an AutoCloseable handle whose {@link AutoCloseable#close()} method stops the observation
     */
    AutoCloseable start(String name, Map<String, String> tags);
}
