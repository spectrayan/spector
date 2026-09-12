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
package com.spectrayan.spector.cluster.routing.invalidation;

import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.cluster.routing.RoutingMetricsListener;
import com.spectrayan.spector.cluster.routing.cache.WaterfallRoutingResolver;
import io.lettuce.core.RedisClient;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pub/Sub invalidation subscriber that evicts entries from the local Caffeine L1 cache
 * upon receiving invalidation events over the cell channel (ADR-0034 §8.3, Req R4, R8.3).
 *
 * <p><b>Resilience & Malformed Payload Handling (Req R4.3, R4.4):</b>
 * Delivery is best-effort. Malformed JSON or unparseable keys are logged at WARN and safely dropped;
 * they never crash or throw from the listener.</p>
 */
public class RoutingInvalidationSubscriber extends RedisPubSubAdapter<String, String> implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RoutingInvalidationSubscriber.class);

    private final String cellId;
    private final String channelName;
    private final WaterfallRoutingResolver resolver;
    private final RoutingMetricsListener metricsListener;

    private final RedisClient client;
    private volatile StatefulRedisPubSubConnection<String, String> connection;
    private final ScheduledExecutorService retryScheduler;

    private final AtomicBoolean subscribed = new AtomicBoolean(false);
    private final AtomicLong unsubscribedSince = new AtomicLong(0L);
    private final AtomicLong unsubscribedDurationMs = new AtomicLong(0L);
    private final AtomicLong invalidationCount = new AtomicLong(0L);
    private final AtomicLong malformedCount = new AtomicLong(0L);

    /**
     * Creates a subscriber with an existing pub/sub connection (useful for testing or shared connections).
     *
     * @param cellId          the cell identifier
     * @param connection      the Lettuce pub/sub connection
     * @param resolver        the waterfall resolver whose L1 cache is evicted
     * @param metricsListener the metrics listener
     */
    public RoutingInvalidationSubscriber(
            String cellId,
            StatefulRedisPubSubConnection<String, String> connection,
            WaterfallRoutingResolver resolver,
            RoutingMetricsListener metricsListener
    ) {
        this.cellId = (cellId != null && !cellId.isBlank()) ? cellId : "default";
        this.channelName = RoutingKey.redisInvalidationChannel(this.cellId);
        this.connection = Objects.requireNonNull(connection, "connection must not be null");
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
        this.metricsListener = metricsListener != null ? metricsListener : RoutingMetricsListener.NOOP;
        this.client = null;
        this.retryScheduler = null;

        this.connection.addListener(this);
    }

    /**
     * Creates a subscriber that manages its own pub/sub connection via the provided RedisClient.
     *
     * @param cellId          the cell identifier
     * @param client          the RedisClient
     * @param resolver        the waterfall resolver whose L1 cache is evicted
     * @param metricsListener the metrics listener
     */
    public RoutingInvalidationSubscriber(
            String cellId,
            RedisClient client,
            WaterfallRoutingResolver resolver,
            RoutingMetricsListener metricsListener
    ) {
        this.cellId = (cellId != null && !cellId.isBlank()) ? cellId : "default";
        this.channelName = RoutingKey.redisInvalidationChannel(this.cellId);
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
        this.metricsListener = metricsListener != null ? metricsListener : RoutingMetricsListener.NOOP;

        StatefulRedisPubSubConnection<String, String> conn = null;
        try {
            conn = client.connectPubSub();
            conn.addListener(this);
        } catch (Exception e) {
            log.warn("Failed to establish initial pub/sub connection for cell invalidation: {}", e.getMessage());
            this.subscribed.set(false);
            this.unsubscribedSince.set(System.currentTimeMillis());
            this.metricsListener.recordPubSubUnsubscribedTransition(true);
        }
        this.connection = conn;

        this.retryScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "routing-invalidation-subscriber-reconnect");
            t.setDaemon(true);
            return t;
        });
        this.retryScheduler.scheduleWithFixedDelay(this::ensureSubscribed, 5, 5, TimeUnit.SECONDS);
    }

    /**
     * Attempts to connect and subscribe to the cell invalidation channel if currently unsubscribed.
     */
    public synchronized void ensureSubscribed() {
        if (subscribed.get()) {
            return;
        }
        try {
            if (connection == null || !connection.isOpen()) {
                if (client != null) {
                    connection = client.connectPubSub();
                    connection.addListener(this);
                }
            }
            if (connection != null && connection.isOpen()) {
                connection.async().subscribe(channelName);
            }
        } catch (Exception e) {
            log.debug("Periodic reconnect/subscribe to channel {} failed: {}", channelName, e.getMessage());
        }
    }

    /**
     * Subscribes to the cell invalidation channel.
     */
    public void start() {
        ensureSubscribed();
    }

    @Override
    public void message(String channel, String message) {
        if (message == null || message.isBlank()) {
            malformedCount.incrementAndGet();
            log.warn("Received empty or blank invalidation payload on channel: {}", channel);
            return;
        }

        RoutingInvalidationMessage invalidation = RoutingInvalidationMessage.fromJson(message);
        if (invalidation == null) {
            malformedCount.incrementAndGet();
            log.warn("Dropping malformed invalidation payload on channel {}: {}", channel, message);
            return;
        }

        try {
            RoutingKey routingKey = RoutingKey.fromKeyMaterial(cellId, invalidation.nsKey());
            resolver.invalidateLocal(routingKey);
            invalidationCount.incrementAndGet();
            log.debug("Invalidated local route for key {} at epoch {} (reason: {})",
                    routingKey.keyMaterial(), invalidation.epoch(), invalidation.reason());
        } catch (Exception e) {
            malformedCount.incrementAndGet();
            log.warn("Dropping invalidation message with unparseable key material: {} (error: {})",
                    invalidation.nsKey(), e.getMessage());
        }
    }

    @Override
    public void subscribed(String channel, long count) {
        boolean wasUnsubscribed = subscribed.compareAndSet(false, true);
        long duration = 0L;
        long since = unsubscribedSince.get();
        if (since > 0L) {
            duration = Math.max(0L, System.currentTimeMillis() - since);
            unsubscribedDurationMs.addAndGet(duration);
            unsubscribedSince.set(0L);
        }
        if (wasUnsubscribed) {
            log.info("Subscribed to routing invalidation channel: {} (was unsubscribed for {} ms)", channel, duration);
            metricsListener.recordPubSubUnsubscribedTransition(false);
        }
    }

    @Override
    public void unsubscribed(String channel, long count) {
        boolean wasSubscribed = subscribed.compareAndSet(true, false);
        unsubscribedSince.set(System.currentTimeMillis());
        if (wasSubscribed) {
            log.warn("Unsubscribed from routing invalidation channel: {} (Req R4.5)", channel);
            metricsListener.recordPubSubUnsubscribedTransition(true);
        }
    }

    public boolean isSubscribed() {
        return subscribed.get();
    }

    public long invalidationCount() {
        return invalidationCount.get();
    }

    public long malformedCount() {
        return malformedCount.get();
    }

    public long unsubscribedDurationMs() {
        long currentPeriod = 0L;
        long since = unsubscribedSince.get();
        if (!subscribed.get() && since > 0L) {
            currentPeriod = Math.max(0L, System.currentTimeMillis() - since);
        }
        return unsubscribedDurationMs.get() + currentPeriod;
    }

    public String channelName() {
        return channelName;
    }

    @Override
    public void close() {
        if (retryScheduler != null) {
            retryScheduler.shutdownNow();
        }
        try {
            if (connection != null && connection.isOpen()) {
                connection.async().unsubscribe(channelName);
                connection.close();
            }
        } catch (Exception ignored) {}
    }
}
