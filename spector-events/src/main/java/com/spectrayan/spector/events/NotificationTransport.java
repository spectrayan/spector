/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.spectrayan.spector.events;

import java.util.function.Consumer;

/**
 * SPI for notification/event delivery transports.
 *
 * <p>Implementations handle the mechanics of delivering events from publishers
 * to subscribers, including scope-based filtering and optional cross-pod
 * fan-out in distributed deployments.</p>
 *
 * <h3>Implementations</h3>
 * <ul>
 *   <li>{@link LocalNotificationTransport} — in-memory, single-pod (default)</li>
 *   <li>{@code RedisStreamTransport} — Redis Streams, multi-pod (enterprise)</li>
 *   <li>{@code NatsNotificationTransport} — NATS, high-throughput (future)</li>
 * </ul>
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>{@link #start()} — initialize connections (Redis, NATS, etc.)</li>
 *   <li>{@link #publish(Object)} / {@link #subscribe(SubscriberIdentity, Consumer)} — runtime usage</li>
 *   <li>{@link #close()} — shutdown, release resources</li>
 * </ol>
 *
 * <h3>Scope Filtering</h3>
 * <p>Each event carries a {@link NotificationScope} (via the event's {@code scope()} method).
 * The transport matches this scope against each subscriber's {@link SubscriberIdentity}
 * to determine delivery. See {@link SubscriberIdentity#matches(NotificationScope)}.</p>
 *
 * <h3>Multi-Pod Delivery</h3>
 * <p>In distributed deployments, a single-pod {@link LocalNotificationTransport}
 * misses events published on other pods. Distributed transports (Redis Streams, NATS)
 * fan-out events across all pods so that every subscriber receives matching events
 * regardless of which pod generated them.</p>
 *
 * <h3>Apache Camel Integration (Future)</h3>
 * <p>If Apache Camel is adopted for connector/ETL integrations, a
 * {@code CamelNotificationTransport} adapter can delegate to a Camel route,
 * enabling URI-based transport swapping (e.g., {@code redis-streams:},
 * {@code nats:}, {@code kafka:}) without changing application code.</p>
 *
 * @param <E> the event type (typically {@code SpectorEvent})
 * @see NotificationScope
 * @see SubscriberIdentity
 * @see LocalNotificationTransport
 */
public interface NotificationTransport<E> extends AutoCloseable {

    /**
     * Publishes an event to all matching subscribers based on the event's scope.
     *
     * <p>In single-pod mode ({@link LocalNotificationTransport}), this is direct
     * in-memory delivery with scope filtering. In multi-pod mode (Redis Streams),
     * this serializes and publishes to a shared channel; each pod's local subscriber
     * then receives and delivers the event.</p>
     *
     * @param event the event to publish
     */
    void publish(E event);

    /**
     * Registers a subscriber with identity context for scope-based filtering.
     *
     * <p>The transport matches each published event's {@link NotificationScope}
     * against the subscriber's {@link SubscriberIdentity} and only invokes the
     * listener if the scope matches.</p>
     *
     * @param identity the subscriber's identity (userId, tenantId, roles, topics)
     * @param listener the callback invoked when a matching event arrives
     * @return a handle to cancel the subscription
     */
    Subscription subscribe(SubscriberIdentity identity, Consumer<E> listener);

    /**
     * Subscribes to all events without scope filtering (broadcast listener).
     *
     * <p>Useful for analytics, audit logging, and metrics collection where
     * every event must be observed regardless of scope.</p>
     *
     * @param listener the callback invoked for every published event
     * @return a handle to cancel the subscription
     */
    default Subscription subscribeAll(Consumer<E> listener) {
        return subscribe(SubscriberIdentity.anonymous(), listener);
    }

    /**
     * Initializes the transport — connects to external systems (Redis, NATS, etc.).
     *
     * <p>For {@link LocalNotificationTransport}, this is a no-op.
     * For distributed transports, this establishes connections and
     * starts consumer threads.</p>
     */
    default void start() {}

    /**
     * Shuts down the transport — disconnects, releases resources.
     */
    @Override
    default void close() {}

    /** Returns the current number of active subscribers. */
    int subscriberCount();

    /**
     * Handle for cancelling an event subscription.
     *
     * <p>Calling {@link #cancel()} removes the subscriber from the transport.
     * Subsequent events will not be delivered to the cancelled subscriber.</p>
     */
    @FunctionalInterface
    interface Subscription {
        /** Cancels the subscription — the subscriber will no longer receive events. */
        void cancel();
    }
}
