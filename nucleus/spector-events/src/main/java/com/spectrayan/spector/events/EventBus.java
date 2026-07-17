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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.commons.concurrent.ConcurrentTasks;

/**
 * Generic, type-safe event bus for Spector — the central pub/sub hub.
 *
 * <p>Provides both in-process subscriber delivery and scope-aware delivery
 * through pluggable {@link NotificationTransport} instances. Supports
 * multiple simultaneous transports (e.g., local + Kafka + MQTT) for
 * fan-out to external systems.</p>
 *
 * <h3>Architecture</h3>
 * <pre>
 *   eventBus.publish(event)
 *       │
 *       ├── In-process subscribers  (subscribe(Consumer))  — all events, no scope filtering
 *       │                                                    (analytics, metrics, audit)
 *       │
 *       └── Transport subscribers   (addTransport(T))       — scope-filtered delivery
 *           ├── LocalNotificationTransport   (SSE clients)
 *           ├── KafkaTransport               (analytics pipeline)
 *           └── MqttTransport                (remote UIs)
 * </pre>
 *
 * <h3>Dispatch Modes</h3>
 * <ul>
 *   <li><b>Synchronous</b> (default): Subscribers receive events on the publisher's thread.
 *       Keep handlers fast.</li>
 *   <li><b>Async</b> (opt-in): Subscribers receive events on virtual threads via
 *       {@link ConcurrentTasks#fireAndForget(Runnable)}. Enabled via
 *       {@code -Dspector.events.async=true}. Prevents slow subscribers
 *       from blocking the hot path.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   // Broadcast bus (no scope filtering)
 *   EventBus<SpectorLifecycleEvent> lifecycleBus = EventBus.broadcast();
 *
 *   // Scoped bus (scope-aware delivery)
 *   EventBus<SpectorNodeEvent> nodeBus = EventBus.scoped(SpectorNodeEvent::scope);
 *
 *   // Subscribe to specific event types
 *   lifecycleBus.subscribe(CheckpointCompletedEvent.class, event -> { ... });
 *
 *   // Add external transports
 *   nodeBus.addTransport(new KafkaTransport<>(...));
 * }</pre>
 *
 * @param <E> the event type bound — must extend {@link SpectorEvent}
 * @see SpectorEvent
 * @see NotificationTransport
 */
public class EventBus<E extends SpectorEvent> implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    /** System property to enable async event dispatch on virtual threads. */
    private static final String ASYNC_PROP = "spector.events.async";

    // In-process subscribers — receive ALL events (no scope filtering)
    private final List<Consumer<E>> subscribers = new CopyOnWriteArrayList<>();

    // External transports — 0..N (local, Kafka, MQTT, Redis, etc.)
    private final List<NotificationTransport<E>> transports = new CopyOnWriteArrayList<>();

    private final boolean asyncMode;

    // ── Constructors ────────────────────────────────────────────────

    /**
     * Creates an event bus with no transports (in-process subscribers only).
     */
    public EventBus() {
        this.asyncMode = Boolean.getBoolean(ASYNC_PROP);
    }

    /**
     * Creates an event bus with an initial transport.
     *
     * @param transport the initial notification transport
     */
    public EventBus(NotificationTransport<E> transport) {
        this();
        if (transport != null) {
            this.transports.add(transport);
        }
    }

    // ── Factory Methods ─────────────────────────────────────────────

    /**
     * Creates a broadcast event bus (no scope filtering).
     *
     * <p>All subscribers receive all events. Use for lifecycle events,
     * telemetry, and other events that don't need audience targeting.</p>
     */
    public static <E extends SpectorEvent> EventBus<E> broadcast() {
        return new EventBus<>(new LocalNotificationTransport<>(
                e -> NotificationScope.BROADCAST));
    }

    /**
     * Creates a scoped event bus with scope-aware delivery.
     *
     * <p>The scope extractor function extracts a {@link NotificationScope}
     * from each event to determine which subscribers receive it.</p>
     *
     * @param scopeExtractor function to extract scope from events
     *                       (e.g., {@code SpectorNodeEvent::scope})
     */
    public static <E extends SpectorEvent> EventBus<E> scoped(
            Function<E, NotificationScope> scopeExtractor) {
        return new EventBus<>(new LocalNotificationTransport<>(scopeExtractor));
    }

    // ── Publishing ──────────────────────────────────────────────────

    /**
     * Publishes an event to all in-process subscribers and all transports.
     *
     * @param event the event to publish
     */
    public void publish(E event) {
        if (asyncMode) {
            publishAsync(event);
        } else {
            publishSync(event);
        }
    }

    private void publishSync(E event) {
        // Deliver to in-process subscribers (all events, no filtering)
        for (Consumer<E> subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (Exception e) {
                log.warn("Event subscriber threw exception for {}: {}",
                        event.eventType(), e.getMessage(), e);
            }
        }
        // Deliver to all transports (scope-filtered per transport)
        for (NotificationTransport<E> transport : transports) {
            try {
                transport.publish(event);
            } catch (Exception e) {
                log.warn("Transport publish failed for {}: {}",
                        event.eventType(), e.getMessage(), e);
            }
        }
    }

    private void publishAsync(E event) {
        for (Consumer<E> subscriber : subscribers) {
            ConcurrentTasks.fireAndForget(() -> subscriber.accept(event));
        }
        for (NotificationTransport<E> transport : transports) {
            ConcurrentTasks.fireAndForget(() -> transport.publish(event));
        }
    }

    // ── Subscribe (in-process, no scope filtering) ──────────────────

    /**
     * Subscribes to all events without scope filtering.
     *
     * <p>Used by internal components (analytics CDC, metrics publishers,
     * audit loggers) that need to observe every event regardless of scope.</p>
     *
     * @param listener the event handler
     * @return a subscription handle that can be cancelled
     */
    public Subscription subscribe(Consumer<E> listener) {
        subscribers.add(listener);
        log.debug("Event subscriber added (total: {})", subscribers.size());
        return () -> {
            subscribers.remove(listener);
            log.debug("Event subscriber removed (total: {})", subscribers.size());
        };
    }

    /**
     * Subscribes to events of a specific type only (type-filtered).
     *
     * <p>Uses {@code instanceof} to filter events by concrete class. Only
     * matching events are delivered to the subscriber.</p>
     *
     * @param eventType  the event class to filter for
     * @param listener   the typed event handler
     * @param <T>        the event type
     * @return a subscription handle that can be cancelled
     */
    @SuppressWarnings("unchecked")
    public <T extends E> Subscription subscribe(Class<T> eventType, Consumer<T> listener) {
        Consumer<E> wrapped = event -> {
            if (eventType.isInstance(event)) {
                listener.accept((T) event);
            }
        };
        subscribers.add(wrapped);
        return () -> subscribers.remove(wrapped);
    }

    // ── Scoped Subscribe (delegates to transports) ──────────────────

    /**
     * Subscribes with scope-aware filtering via the notification transports.
     *
     * <p>The subscriber only receives events whose {@link NotificationScope}
     * matches the provided {@link SubscriberIdentity}. Delegates to the
     * first transport in the transport list.</p>
     *
     * @param identity the subscriber's identity for scope matching
     * @param listener the event handler
     * @return a subscription handle that can be cancelled
     * @throws IllegalStateException if no transports are configured
     */
    public NotificationTransport.Subscription subscribe(SubscriberIdentity identity,
                                                         Consumer<E> listener) {
        if (transports.isEmpty()) {
            throw new IllegalStateException(
                    "No transports configured — use addTransport() or factory methods");
        }
        return transports.getFirst().subscribe(identity, listener);
    }

    /**
     * Subscribes to all events via the transport (broadcast listener).
     *
     * @param listener the event handler
     * @return a subscription handle that can be cancelled
     */
    public NotificationTransport.Subscription subscribeAll(Consumer<E> listener) {
        if (transports.isEmpty()) {
            throw new IllegalStateException(
                    "No transports configured — use addTransport() or factory methods");
        }
        return transports.getFirst().subscribeAll(listener);
    }

    // ── Transport Management ────────────────────────────────────────

    /**
     * Adds a notification transport for external event delivery.
     *
     * <p>Multiple transports can be active simultaneously — each receives
     * a copy of every published event. Use this for fan-out to multiple
     * external systems (e.g., Kafka + MQTT + Redis Streams).</p>
     *
     * @param transport the transport to add
     */
    public void addTransport(NotificationTransport<E> transport) {
        transports.add(transport);
        log.info("Transport added: {} (total: {})",
                transport.getClass().getSimpleName(), transports.size());
    }

    // ── Introspection ───────────────────────────────────────────────

    /** Returns the total in-process subscriber count. */
    public int subscriberCount() {
        int transportSubs = 0;
        for (NotificationTransport<E> t : transports) {
            transportSubs += t.subscriberCount();
        }
        return subscribers.size() + transportSubs;
    }

    /** Returns the in-process subscriber count only. */
    public int directSubscriberCount() {
        return subscribers.size();
    }

    /** Returns whether async dispatch mode is enabled. */
    public boolean isAsyncMode() {
        return asyncMode;
    }

    /** Returns the number of configured transports. */
    public int transportCount() {
        return transports.size();
    }

    // ── Lifecycle ───────────────────────────────────────────────────

    @Override
    public void close() {
        subscribers.clear();
        for (NotificationTransport<E> transport : transports) {
            try {
                transport.close();
            } catch (Exception e) {
                log.warn("Error closing transport: {}", e.getMessage(), e);
            }
        }
        transports.clear();
    }

    /**
     * Handle for cancelling an in-process event subscription.
     */
    @FunctionalInterface
    public interface Subscription {
        /** Cancels the subscription — the subscriber will no longer receive events. */
        void cancel();
    }
}
