package com.spectrayan.spector.node.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.commons.concurrent.ConcurrentTasks;
import com.spectrayan.spector.events.LocalNotificationTransport;
import com.spectrayan.spector.events.NotificationScope;
import com.spectrayan.spector.events.NotificationTransport;
import com.spectrayan.spector.events.SubscriberIdentity;

/**
 * Unified event bus for Spector — implements {@link NotificationTransport}.
 *
 * <p>Provides both the legacy {@code subscribe(Consumer)} API for internal domain
 * event listeners and the new scope-aware {@code subscribe(SubscriberIdentity, Consumer)}
 * API for client-facing notification delivery.</p>
 *
 * <h3>Architecture</h3>
 * <pre>
 *   eventBus.publish(event)
 *       │
 *       ├── Legacy subscribers  (subscribe(Consumer))  — receive ALL events, no scope filtering
 *       │                                                (analytics CDC, metrics, audit)
 *       │
 *       └── Scoped subscribers  (subscribe(Identity, Consumer)) — receive only matching events
 *                                                                  (SSE clients, per-user delivery)
 * </pre>
 *
 * <h3>Transport Delegation</h3>
 * <p>Scope-aware delivery is delegated to a pluggable {@link NotificationTransport}.
 * By default, uses {@link LocalNotificationTransport} (in-memory, single-pod).
 * In multi-pod deployments, inject a {@code RedisStreamTransport} for cross-pod
 * fan-out via Redis Streams.</p>
 *
 * <h3>Dispatch Modes</h3>
 * <ul>
 *   <li><b>Synchronous</b> (default): Subscribers receive events on the publisher's thread.
 *       Keep handlers fast.</li>
 *   <li><b>Async</b> (opt-in): Subscribers receive events on virtual threads via
 *       {@link ConcurrentTasks#fireAndForget(Runnable)}. Enabled via
 *       {@code -Dspector.events.async=true}. Prevents slow subscribers
 *       from blocking the search hot path.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   SpectorEventBus eventBus = new SpectorEventBus();
 *
 *   // Legacy subscribe (all events, no filtering)
 *   eventBus.subscribe(event -> { ... });
 *
 *   // Scoped subscribe (only matching events)
 *   var identity = SubscriberIdentity.ofUser("user-1", "acme");
 *   eventBus.subscribe(identity, event -> { ... });
 *
 *   // Publish
 *   eventBus.publish(new SpectorSearchCompletedEvent(...));
 *
 *   sub.cancel();
 * }</pre>
 *
 * @see NotificationTransport
 * @see NotificationScope
 */
public class SpectorEventBus implements NotificationTransport<SpectorEvent> {

    private static final Logger log = LoggerFactory.getLogger(SpectorEventBus.class);

    /** System property to enable async event dispatch on virtual threads. */
    private static final String ASYNC_PROP = "spector.events.async";

    // Legacy subscribers — receive ALL events (no scope filtering)
    private final List<Consumer<SpectorEvent>> legacySubscribers = new CopyOnWriteArrayList<>();

    // Scope-aware transport for filtered delivery (SSE clients)
    private final NotificationTransport<SpectorEvent> transport;
    private final boolean asyncMode;

    /**
     * Creates an event bus with the default {@link LocalNotificationTransport}.
     */
    public SpectorEventBus() {
        this(new LocalNotificationTransport<>(SpectorEvent::scope));
    }

    /**
     * Creates an event bus with a custom notification transport.
     *
     * <p>Use this constructor to inject a distributed transport (Redis Streams,
     * NATS) for multi-pod deployments.</p>
     *
     * @param transport the notification transport for scope-aware delivery
     */
    public SpectorEventBus(NotificationTransport<SpectorEvent> transport) {
        this.transport = transport;
        this.asyncMode = Boolean.getBoolean(ASYNC_PROP);
        if (asyncMode) {
            log.info("Event bus initialized in ASYNC mode (virtual threads via ConcurrentTasks)");
        }
    }

    // ── Publishing ──────────────────────────────────────────────────

    /**
     * Publishes an event to all subscribers (both legacy and scoped).
     *
     * <p>Legacy subscribers receive every event unconditionally. Scoped subscribers
     * receive events only if their {@link SubscriberIdentity} matches the event's
     * {@link NotificationScope}.</p>
     */
    @Override
    public void publish(SpectorEvent event) {
        if (asyncMode) {
            publishAsync(event);
        } else {
            publishSync(event);
        }
    }

    private void publishSync(SpectorEvent event) {
        // Deliver to legacy subscribers (all events, no filtering)
        for (Consumer<SpectorEvent> subscriber : legacySubscribers) {
            try {
                subscriber.accept(event);
            } catch (Exception e) {
                log.warn("Event subscriber threw exception for {}: {}",
                        event.eventType(), e.getMessage(), e);
            }
        }
        // Deliver to scoped subscribers via transport
        transport.publish(event);
    }

    private void publishAsync(SpectorEvent event) {
        for (Consumer<SpectorEvent> subscriber : legacySubscribers) {
            ConcurrentTasks.fireAndForget(() -> subscriber.accept(event));
        }
        ConcurrentTasks.fireAndForget(() -> transport.publish(event));
    }

    // ── Scoped Subscribe (NotificationTransport SPI) ────────────────

    /**
     * Subscribes with scope-aware filtering via the notification transport.
     *
     * <p>The subscriber only receives events whose {@link NotificationScope}
     * matches the provided {@link SubscriberIdentity}.</p>
     */
    @Override
    public NotificationTransport.Subscription subscribe(SubscriberIdentity identity,
                                                         Consumer<SpectorEvent> listener) {
        return transport.subscribe(identity, listener);
    }

    /**
     * Subscribes to all events regardless of scope (broadcast listener).
     *
     * <p>Delegates to the transport's {@code subscribeAll} which bypasses
     * scope matching.</p>
     */
    @Override
    public NotificationTransport.Subscription subscribeAll(Consumer<SpectorEvent> listener) {
        return transport.subscribeAll(listener);
    }

    // ── Legacy Subscribe (backward compatibility) ───────────────────

    /**
     * Subscribes to all events without scope filtering (legacy API).
     *
     * <p>Used by internal components (analytics CDC, metrics publishers, audit loggers)
     * that need to observe every event regardless of scope. For client-facing
     * subscriptions (SSE), use {@link #subscribe(SubscriberIdentity, Consumer)}.</p>
     *
     * @param subscriber the event handler
     * @return a subscription handle that can be cancelled
     */
    public Subscription subscribe(Consumer<SpectorEvent> subscriber) {
        legacySubscribers.add(subscriber);
        log.debug("Legacy event subscriber added (total: {})", legacySubscribers.size());
        return () -> {
            legacySubscribers.remove(subscriber);
            log.debug("Legacy event subscriber removed (total: {})", legacySubscribers.size());
        };
    }

    /**
     * Subscribes to events of a specific type only (legacy API).
     *
     * @param eventType  the event class to filter for
     * @param subscriber the typed event handler
     * @param <T>        the event type
     * @return a subscription handle that can be cancelled
     */
    @SuppressWarnings("unchecked")
    public <T extends SpectorEvent> Subscription subscribe(Class<T> eventType, Consumer<T> subscriber) {
        Consumer<SpectorEvent> wrapped = event -> {
            if (eventType.isInstance(event)) {
                subscriber.accept((T) event);
            }
        };
        legacySubscribers.add(wrapped);
        return () -> legacySubscribers.remove(wrapped);
    }

    // ── Introspection ───────────────────────────────────────────────

    /** Returns the total subscriber count (legacy + scoped). */
    @Override
    public int subscriberCount() {
        return legacySubscribers.size() + transport.subscriberCount();
    }

    /** Returns the legacy subscriber count only. */
    public int legacySubscriberCount() {
        return legacySubscribers.size();
    }

    /** Returns whether async dispatch mode is enabled. */
    public boolean isAsyncMode() {
        return asyncMode;
    }

    /** Returns the underlying notification transport. */
    public NotificationTransport<SpectorEvent> transport() {
        return transport;
    }

    @Override
    public void close() {
        legacySubscribers.clear();
        try {
            transport.close();
        } catch (Exception e) {
            log.warn("Error closing notification transport: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle for cancelling an event subscription.
     */
    @FunctionalInterface
    public interface Subscription {
        /** Cancels the subscription — the subscriber will no longer receive events. */
        void cancel();
    }
}
