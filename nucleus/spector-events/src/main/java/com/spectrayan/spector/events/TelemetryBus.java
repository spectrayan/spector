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
package com.spectrayan.spector.events;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Instance-scoped, thread-safe telemetry event bus.
 *
 * <p>Unlike static-holder patterns, each {@code TelemetryBus} instance is
 * independent — safe for HA deployments where multiple {@code SpectorNode}
 * instances run in the same JVM.</p>
 *
 * @deprecated Since 2.0.0. Use {@link EventBus EventBus&lt;SpectorTelemetryEvent&gt;}
 *             directly. This class is retained for backward compatibility and will
 *             delegate to the generic {@link EventBus} in a future release. The
 *             generic EventBus supports scope-aware delivery, multiple transports,
 *             and type-filtered subscriptions — features that this class lacks.
 *
 * @see EventBus
 * @see TelemetryScope
 * @see SpectorTelemetryEvent
 */
@Deprecated(since = "2.0.0", forRemoval = false)
public final class TelemetryBus implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TelemetryBus.class);

    private final CopyOnWriteArrayList<Consumer<SpectorTelemetryEvent>> subscribers =
            new CopyOnWriteArrayList<>();

    /**
     * Publishes a telemetry event to all subscribers.
     *
     * <p>Subscriber exceptions are caught and logged — a failing subscriber
     * does not prevent delivery to other subscribers.</p>
     *
     * @param event the telemetry event to publish
     */
    public void publish(SpectorTelemetryEvent event) {
        for (Consumer<SpectorTelemetryEvent> subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (Exception e) {
                log.debug("Telemetry subscriber failed: {}", e.getMessage());
            }
        }
    }

    /**
     * Subscribes a consumer to receive telemetry events.
     *
     * @param subscriber the event consumer
     * @return a subscription handle for cancellation
     */
    public Subscription subscribe(Consumer<SpectorTelemetryEvent> subscriber) {
        subscribers.add(subscriber);
        return () -> subscribers.remove(subscriber);
    }

    /**
     * Removes all subscribers and releases resources.
     */
    @Override
    public void close() {
        int count = subscribers.size();
        subscribers.clear();
        log.debug("TelemetryBus closed ({} subscribers removed)", count);
    }

    /** Returns the current subscriber count (for diagnostics). */
    public int subscriberCount() {
        return subscribers.size();
    }

    /** Cancellable subscription handle. */
    @FunctionalInterface
    public interface Subscription {
        void cancel();
    }
}
