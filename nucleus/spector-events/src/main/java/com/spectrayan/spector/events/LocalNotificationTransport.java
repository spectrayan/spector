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

/**
 * In-memory notification transport — default for single-pod deployments.
 *
 * <p>Delivers events directly from publisher to subscribers within the same
 * JVM process. Scope filtering is applied at delivery time using
 * {@link SubscriberIdentity#matches(NotificationScope)}.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>Uses {@link CopyOnWriteArrayList} for lock-free reads during event
 * dispatch. Suitable for high-throughput event publishing with infrequent
 * subscribe/unsubscribe operations.</p>
 *
 * <h3>Limitations</h3>
 * <ul>
 *   <li>Single-pod only — events do not cross process boundaries</li>
 *   <li>No persistence — missed events during subscriber downtime are lost</li>
 *   <li>No backpressure — slow subscribers can accumulate work</li>
 * </ul>
 *
 * <p>For multi-pod deployments, use {@code RedisStreamTransport} which
 * fans out events across pods via Redis Streams.</p>
 *
 * @param <E> the event type
 * @see NotificationTransport
 */
public class LocalNotificationTransport<E> implements NotificationTransport<E> {

    private static final Logger log = LoggerFactory.getLogger(LocalNotificationTransport.class);

    private final List<SubscriberEntry<E>> subscribers = new CopyOnWriteArrayList<>();
    private final Function<E, NotificationScope> scopeExtractor;

    /**
     * Creates a local transport with a custom scope extractor.
     *
     * @param scopeExtractor function that extracts the {@link NotificationScope}
     *                       from an event (e.g., {@code SpectorEvent::scope})
     */
    public LocalNotificationTransport(Function<E, NotificationScope> scopeExtractor) {
        this.scopeExtractor = scopeExtractor;
    }

    @Override
    public void publish(E event) {
        NotificationScope scope = scopeExtractor.apply(event);
        for (SubscriberEntry<E> entry : subscribers) {
            if (entry.shouldDeliver(scope)) {
                try {
                    entry.listener().accept(event);
                } catch (Exception e) {
                    log.warn("Notification subscriber threw exception: {}", e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public Subscription subscribe(SubscriberIdentity identity, Consumer<E> listener) {
        var entry = new ScopedEntry<>(identity, listener);
        subscribers.add(entry);
        log.debug("Subscriber added (identity={}, total={})", identity.userId(), subscribers.size());
        return () -> {
            subscribers.remove(entry);
            log.debug("Subscriber removed (total={})", subscribers.size());
        };
    }

    @Override
    public Subscription subscribeAll(Consumer<E> listener) {
        var entry = new BroadcastEntry<>(listener);
        subscribers.add(entry);
        log.debug("Broadcast subscriber added (total={})", subscribers.size());
        return () -> {
            subscribers.remove(entry);
            log.debug("Broadcast subscriber removed (total={})", subscribers.size());
        };
    }

    @Override
    public int subscriberCount() {
        return subscribers.size();
    }

    @Override
    public void close() {
        int count = subscribers.size();
        subscribers.clear();
        log.debug("LocalNotificationTransport closed ({} subscribers removed)", count);
    }

    // ── Internal subscriber entries ─────────────────────────────────

    /** Base type for subscriber entries in the dispatch list. */
    private sealed interface SubscriberEntry<E> permits ScopedEntry, BroadcastEntry {
        Consumer<E> listener();
        boolean shouldDeliver(NotificationScope scope);
    }

    /** Scoped entry — delivers only if identity matches the event scope. */
    private record ScopedEntry<E>(SubscriberIdentity identity, Consumer<E> listener)
            implements SubscriberEntry<E> {
        @Override
        public boolean shouldDeliver(NotificationScope scope) {
            return identity.matches(scope);
        }
    }

    /** Broadcast entry — delivers all events regardless of scope. */
    private record BroadcastEntry<E>(Consumer<E> listener) implements SubscriberEntry<E> {
        @Override
        public boolean shouldDeliver(NotificationScope scope) {
            return true;
        }
    }
}
