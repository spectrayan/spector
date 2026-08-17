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

import com.spectrayan.spector.commons.concurrent.MemoryScope;
import com.spectrayan.spector.config.ObservabilityConfig;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Base class for components that emit observability signals (metrics and traces) via Micrometer.
 * Uses ScopedValue for implicit parent observation context propagation.
 */
public abstract class ObservableComponent {

    private static final Logger log = LoggerFactory.getLogger(ObservableComponent.class);

    static final ScopedValue<String> PARENT_OBSERVATION = ScopedValue.newInstance();

    private final ObservationRegistry observationRegistry;
    private final ObservabilityConfig config;

    protected ObservableComponent(ObservationRegistry observationRegistry, ObservabilityConfig config) {
        this.observationRegistry = observationRegistry;
        this.config = config;
    }

    protected <T> T withObservation(SpectorObservationDocumentation doc, Map<String, String> tags, Supplier<T> work) {
        if (!config.isEnabled(doc.getName())) {
            return work.get();
        }

        MemoryObservationContext context = new MemoryObservationContext(doc.getName());
        context.setNamespace(MemoryScope.namespaceId());
        context.setSessionId(MemoryScope.sessionId());

        if (tags != null) {
            tags.forEach((k, v) -> {
                if ("tier".equalsIgnoreCase(k)) context.setTier(v);
                else if ("memory_id".equalsIgnoreCase(k)) context.setMemoryId(v);
                else if ("task_id".equalsIgnoreCase(k)) context.setTaskId(v);
                else if ("query".equalsIgnoreCase(k)) context.setQuery(v);
                else context.addCustomTag(k, v);
            });
        }

        Observation observation = Observation.createNotStarted(
                doc.getName(),
                () -> context,
                observationRegistry
        ).observationConvention(DefaultSpectorObservationConvention.INSTANCE).start();

        try (Observation.Scope scope = observation.openScope()) {
            return ScopedValue.where(PARENT_OBSERVATION, doc.getName()).call(work::get);
        } catch (Exception e) {
            observation.error(e);
            context.setStatus("ERROR");
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException(e);
        } finally {
            observation.stop();
        }
    }

    protected void withObservation(SpectorObservationDocumentation doc, Map<String, String> tags, Runnable work) {
        withObservation(doc, tags, () -> {
            work.run();
            return null;
        });
    }

    static String currentParent() {
        return PARENT_OBSERVATION.isBound() ? PARENT_OBSERVATION.get() : null;
    }
}
