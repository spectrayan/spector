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

import com.spectrayan.spector.cluster.routing.ConsistentHashRing;
import com.spectrayan.spector.cluster.routing.ResolvedRoute;
import com.spectrayan.spector.cluster.routing.RouteSource;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.cluster.routing.RoutingMetricsListener;
import com.spectrayan.spector.cluster.routing.cache.RedisRoutingCache;
import com.spectrayan.spector.cluster.routing.cache.WaterfallRoutingResolver;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisPubSubInvalidationTest {

    @Test
    @DisplayName("RoutingInvalidationMessage serializes to valid JSON and deserializes accurately")
    void testMessageSerializationRoundTrip() {
        RoutingInvalidationMessage original = new RoutingInvalidationMessage("tenant-a/ns-alpha", 42L, "stale_route");
        String json = original.toJson();

        assertThat(json).contains("\"nsKey\":\"tenant-a/ns-alpha\"");
        assertThat(json).contains("\"epoch\":42");
        assertThat(json).contains("\"reason\":\"stale_route\"");

        RoutingInvalidationMessage parsed = RoutingInvalidationMessage.fromJson(json);
        assertThat(parsed).isNotNull();
        assertThat(parsed.nsKey()).isEqualTo("tenant-a/ns-alpha");
        assertThat(parsed.epoch()).isEqualTo(42L);
        assertThat(parsed.reason()).isEqualTo("stale_route");
    }

    @Test
    @DisplayName("RoutingInvalidationSubscriber drops L1 cache entry on receipt of invalidation message (Req R4.2)")
    @SuppressWarnings("unchecked")
    void testSubscriberEvictsL1CaffeineOnMessage() {
        String cellId = "cell-alpha";
        ConsistentHashRing ring = ConsistentHashRing.of(1, List.of("node-1", "node-2", "node-3"));

        WaterfallRoutingResolver resolver = new WaterfallRoutingResolver(
                ring,
                null, // degraded / no Redis
                Duration.ofMinutes(1),
                1000L,
                30L,
                null,
                Runnable::run
        );

        StatefulRedisPubSubConnection<String, String> pubSubConn = mock(StatefulRedisPubSubConnection.class);
        RedisPubSubAsyncCommands<String, String> asyncCommands = mock(RedisPubSubAsyncCommands.class);
        when(pubSubConn.async()).thenReturn(asyncCommands);

        AtomicBoolean unsubscribedStateObserved = new AtomicBoolean(false);
        RoutingMetricsListener metrics = new RoutingMetricsListener() {
            @Override
            public void recordLookup(RouteSource source) {}

            @Override
            public void recordPubSubUnsubscribedTransition(boolean unsubscribed) {
                unsubscribedStateObserved.set(unsubscribed);
            }
        };

        RoutingInvalidationSubscriber subscriber = new RoutingInvalidationSubscriber(
                cellId, pubSubConn, resolver, metrics
        );

        RoutingKey key = RoutingKey.ofTenanted(cellId, "tenant-1", "ns-target");

        // 1. First lookup triggers hash fallback and populates L1 Caffeine cache
        ResolvedRoute firstLookup = resolver.resolve(key);
        assertThat(firstLookup.source()).isEqualTo(RouteSource.HASH_FALLBACK);

        // 2. Second lookup is fulfilled by L1 Caffeine cache
        ResolvedRoute secondLookup = resolver.resolve(key);
        assertThat(secondLookup.source()).isEqualTo(RouteSource.CAFFEINE);

        // 3. Receive pub/sub invalidation message for the namespace
        RoutingInvalidationMessage invalidation = new RoutingInvalidationMessage(key.keyMaterial(), 2L, "rebalance");
        subscriber.message(RoutingKey.redisInvalidationChannel(cellId), invalidation.toJson());

        assertThat(subscriber.invalidationCount()).isEqualTo(1L);

        // 4. Third lookup must be a miss in L1, falling back to L3 Ketama ring
        ResolvedRoute thirdLookup = resolver.resolve(key);
        assertThat(thirdLookup.source()).isEqualTo(RouteSource.HASH_FALLBACK);
    }

    @Test
    @DisplayName("Subscription loss and restoration are observable with metric notifications (Req R4.5, R8.3)")
    @SuppressWarnings("unchecked")
    void testSubscriptionLifecycleTransitions() throws InterruptedException {
        String cellId = "cell-alpha";
        ConsistentHashRing ring = ConsistentHashRing.of(1, List.of("node-1", "node-2", "node-3"));

        WaterfallRoutingResolver resolver = new WaterfallRoutingResolver(
                ring, null, Duration.ofMinutes(1), 1000L, 30L, null, Runnable::run
        );

        StatefulRedisPubSubConnection<String, String> pubSubConn = mock(StatefulRedisPubSubConnection.class);
        AtomicInteger unsubTransitions = new AtomicInteger(0);
        AtomicInteger subTransitions = new AtomicInteger(0);

        RoutingMetricsListener metrics = new RoutingMetricsListener() {
            @Override
            public void recordLookup(RouteSource source) {}

            @Override
            public void recordPubSubUnsubscribedTransition(boolean unsubscribed) {
                if (unsubscribed) {
                    unsubTransitions.incrementAndGet();
                } else {
                    subTransitions.incrementAndGet();
                }
            }
        };

        RoutingInvalidationSubscriber subscriber = new RoutingInvalidationSubscriber(
                cellId, pubSubConn, resolver, metrics
        );

        String channel = subscriber.channelName();

        // Simulate subscribed event
        subscriber.subscribed(channel, 1L);
        assertThat(subscriber.isSubscribed()).isTrue();
        assertThat(subTransitions.get()).isEqualTo(1);

        // Simulate connection lost / unsubscribed event
        subscriber.unsubscribed(channel, 0L);
        assertThat(subscriber.isSubscribed()).isFalse();
        assertThat(unsubTransitions.get()).isEqualTo(1);

        Thread.sleep(10); // simulate brief unsubscription duration
        assertThat(subscriber.unsubscribedDurationMs()).isGreaterThan(0L);

        // Simulate re-subscribed event
        subscriber.subscribed(channel, 1L);
        assertThat(subscriber.isSubscribed()).isTrue();
        assertThat(subTransitions.get()).isEqualTo(2);
    }
}
