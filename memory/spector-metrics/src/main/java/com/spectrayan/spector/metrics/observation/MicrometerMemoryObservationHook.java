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

import com.spectrayan.spector.commons.concurrent.MemoryScope;
import com.spectrayan.spector.config.ObservabilityConfig;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Micrometer Observation adapter implementing {@link MemoryObservationHook}.
 *
 * <p>Bridges low-level memory observation calls to {@link ObservationRegistry} observations,
 * automatically emitting timers, counters, and distributed trace spans.</p>
 */
public final class MicrometerMemoryObservationHook implements MemoryObservationHook {

    private final ObservationRegistry registry;
    private final SpectorObservationConvention convention;
    private final ObservabilityConfig config;
    private final Set<String> enabledObservations;

    public MicrometerMemoryObservationHook(ObservationRegistry registry, ObservabilityConfig config) {
        this(registry, DefaultSpectorObservationConvention.INSTANCE, config);
    }

    public MicrometerMemoryObservationHook(ObservationRegistry registry, SpectorObservationConvention convention, ObservabilityConfig config) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.convention = convention != null ? convention : DefaultSpectorObservationConvention.INSTANCE;
        this.config = config != null ? config : ObservabilityConfig.DEFAULT;
        this.enabledObservations = this.config.computeEnabledObservationSet();
    }

    @Override
    public AutoCloseable start(String name, Map<String, String> tags) {
        String fullName = name;
        String currentParent = ObservableComponent.currentParent();
        if (currentParent != null) {
            fullName = currentParent + "." + name;
        }

        if (!enabledObservations.contains(name)) {
            return () -> {}; // No-op if disabled
        }

        MemoryObservationContext context = new MemoryObservationContext(fullName);
        context.setNamespace(MemoryScope.namespaceId());
        context.setSessionId(MemoryScope.sessionId());

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
                fullName != null ? fullName : "spector.memory.operation",
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
    
    @Override
    public <T> T observe(String name, Map<String, String> tags, java.util.function.Supplier<T> action) {
        String fullName = name;
        String currentParent = ObservableComponent.currentParent();
        if (currentParent != null) {
            fullName = currentParent + "." + name;
        }

        if (!enabledObservations.contains(name)) {
            return action.get();
        }

        try (AutoCloseable c = start(name, tags)) {
            final String fName = fullName;
            return ScopedValue.where(ObservableComponent.PARENT_OBSERVATION, fName).call(action::get);
        } catch (Exception e) {
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public void observe(String name, Map<String, String> tags, Runnable action) {
        observe(name, tags, () -> {
            action.run();
            return null;
        });
    }
}
