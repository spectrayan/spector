package com.spectrayan.spector.node.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.commons.concurrent.ConcurrentTasks;

/**
 * Thread-safe publish/subscribe event bus for Spector node events.
 *
 * <p>Implements the Observer pattern. Any component can publish events,
 * and any number of subscribers can listen.</p>
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
 *   // Subscribe
 *   SpectorEventBus.Subscription sub = eventBus.subscribe(event -> {
 *       if (event instanceof SpectorSearchCompletedEvent e) {
 *           log.info("Search completed: {} results", e.resultCount());
 *       }
 *   });
 *
 *   // Publish
 *   eventBus.publish(new SpectorSearchCompletedEvent("node-1", Instant.now(), 5, 12L, "HYBRID"));
 *
 *   // Unsubscribe
 *   sub.cancel();
 * }</pre>
 *
 * <h3>Thread Safety</h3>
 * <p>Uses {@link CopyOnWriteArrayList} for lock-free reads during event
 * dispatch. Suitable for high-throughput event publishing with infrequent
 * subscribe/unsubscribe operations.</p>
 */
public class SpectorEventBus {

    private static final Logger log = LoggerFactory.getLogger(SpectorEventBus.class);

    /** System property to enable async event dispatch on virtual threads. */
    private static final String ASYNC_PROP = "spector.events.async";

    private final List<Consumer<SpectorEvent>> subscribers = new CopyOnWriteArrayList<>();
    private final boolean asyncMode;

    public SpectorEventBus() {
        this.asyncMode = Boolean.getBoolean(ASYNC_PROP);
        if (asyncMode) {
            log.info("Event bus initialized in ASYNC mode (virtual threads via ConcurrentTasks)");
        }
    }

    /**
     * Publishes an event to all subscribers.
     *
     * <p>In synchronous mode, exceptions thrown by individual subscribers are caught
     * and logged to prevent one failing subscriber from blocking others.</p>
     *
     * <p>In async mode, each subscriber is dispatched via
     * {@link ConcurrentTasks#fireAndForget(Runnable)} on its own virtual thread.
     * This prevents slow SSE serialization from blocking the search pipeline.</p>
     *
     * @param event the event to publish
     */
    public void publish(SpectorEvent event) {
        if (asyncMode) {
            publishAsync(event);
        } else {
            publishSync(event);
        }
    }

    private void publishSync(SpectorEvent event) {
        for (Consumer<SpectorEvent> subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (Exception e) {
                log.warn("Event subscriber threw exception for {}: {}",
                        event.eventType(), e.getMessage(), e);
            }
        }
    }

    private void publishAsync(SpectorEvent event) {
        for (Consumer<SpectorEvent> subscriber : subscribers) {
            ConcurrentTasks.fireAndForget(() -> subscriber.accept(event));
        }
    }

    /**
     * Subscribes to all events.
     *
     * @param subscriber the event handler
     * @return a subscription handle that can be cancelled
     */
    public Subscription subscribe(Consumer<SpectorEvent> subscriber) {
        subscribers.add(subscriber);
        log.debug("Event subscriber added (total: {})", subscribers.size());
        return () -> {
            subscribers.remove(subscriber);
            log.debug("Event subscriber removed (total: {})", subscribers.size());
        };
    }

    /**
     * Subscribes to events of a specific type only.
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
        subscribers.add(wrapped);
        return () -> subscribers.remove(wrapped);
    }

    /** Returns the current subscriber count (for monitoring). */
    public int subscriberCount() {
        return subscribers.size();
    }

    /** Returns whether async dispatch mode is enabled. */
    public boolean isAsyncMode() {
        return asyncMode;
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
