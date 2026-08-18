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
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.config.ObservabilityConfig;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * Wraps a SynapticRelay with Micrometer observation telemetry.
 * 
 * @param <S> the signal type
 */
public final class ObservableRelay<S> extends ObservableComponent implements SynapticRelay<S> {

    private static final Logger log = LoggerFactory.getLogger(ObservableRelay.class);

    private final SynapticRelay<S> delegate;
    private final ObservationRegistry registry;
    private final ObservabilityConfig config;
    private final String relayName;

    /**
     * Constructs an ObservableRelay.
     * 
     * @param delegate the relay to wrap
     * @param registry the Micrometer observation registry
     * @param config the observability configuration
     */
    public ObservableRelay(SynapticRelay<S> delegate, ObservationRegistry registry, ObservabilityConfig config) {
        super(registry, config);
        this.delegate = delegate;
        this.registry = registry;
        this.config = config;
        this.relayName = delegate.relayName();
    }

    @Override
    public boolean transmit(S signal) throws Exception {
        if (!config.isEnabled(relayName)) {
            return delegate.transmit(signal);
        }

        MemoryObservationContext context = new MemoryObservationContext(relayName);
        context.setNamespace(MemoryScope.namespaceId());
        context.setSessionId(MemoryScope.sessionId());

        Observation observation = Observation.createNotStarted(
                relayName,
                () -> context,
                registry
        ).observationConvention(DefaultSpectorObservationConvention.INSTANCE).start();

        try (Observation.Scope scope = observation.openScope()) {
            return ScopedValue.where(PARENT_OBSERVATION, relayName).call(() -> delegate.transmit(signal));
        } catch (Exception e) {
            observation.error(e);
            context.setStatus("ERROR");
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException(e);
        } finally {
            observation.stop();
        }
    }

    @Override
    public String relayName() {
        return relayName;
    }

    /**
     * Creates an interceptor function that wraps relays with ObservableRelay.
     * 
     * @param registry the observation registry
     * @param config the observability config
     * @param <S> the signal type
     * @return a function that wraps a relay in an ObservableRelay
     */
    public static <S> Function<SynapticRelay<S>, SynapticRelay<S>> interceptor(ObservationRegistry registry, ObservabilityConfig config) {
        return relay -> new ObservableRelay<>(relay, registry, config);
    }
}
