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
package com.spectrayan.spector.metrics.observation;

import com.spectrayan.spector.commons.observation.MemoryObservationHook;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import java.util.Map;
import java.util.Objects;

/**
 * Micrometer Observation adapter implementing {@link MemoryObservationHook}.
 *
 * <p>Bridges low-level memory observation calls to {@link ObservationRegistry} observations,
 * automatically emitting timers, counters, and distributed trace spans.</p>
 */
public final class MicrometerMemoryObservationHook implements MemoryObservationHook {

    private final ObservationRegistry registry;
    private final SpectorObservationConvention convention;

    public MicrometerMemoryObservationHook(ObservationRegistry registry) {
        this(registry, DefaultSpectorObservationConvention.INSTANCE);
    }

    public MicrometerMemoryObservationHook(ObservationRegistry registry, SpectorObservationConvention convention) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.convention = convention != null ? convention : DefaultSpectorObservationConvention.INSTANCE;
    }

    @Override
    public AutoCloseable start(String name, Map<String, String> tags) {
        MemoryObservationContext context = new MemoryObservationContext(name);
        if (tags != null) {
            for (var entry : tags.entrySet()) {
                String k = entry.getKey();
                String v = entry.getValue();
                if ("tier".equalsIgnoreCase(k)) {
                    context.setTier(v);
                } else if ("namespace".equalsIgnoreCase(k)) {
                    context.setNamespace(v);
                } else if ("session_id".equalsIgnoreCase(k)) {
                    context.setSessionId(v);
                } else if ("memory_id".equalsIgnoreCase(k)) {
                    context.setMemoryId(v);
                } else if ("task_id".equalsIgnoreCase(k)) {
                    context.setTaskId(v);
                } else if ("query".equalsIgnoreCase(k)) {
                    context.setQuery(v);
                } else {
                    context.addCustomTag(k, v);
                }
            }
        }

        Observation observation = Observation.createNotStarted(
                name != null ? name : "spector.memory.operation",
                () -> context,
                registry
        ).observationConvention(convention).start();

        Observation.Scope scope = observation.openScope();

        return () -> {
            try {
                scope.close();
            } finally {
                observation.stop();
            }
        };
    }
}
