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
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.cluster.routing.cache.WaterfallRoutingResolver;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class MalformedPayloadToleranceTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "not-json-at-all",
            "{\"broken\":",
            "{\"epoch\": 42}", // missing nsKey
            "{\"nsKey\":\"\"}", // empty nsKey
            "{\"nsKey\":\"illegal/with/multiple/slashes/inside\"}", // illegal slashes violating validation
            "{\"nsKey\":\"TENANT_UPPER/ns-1\"}", // uppercase tenant violating lowercase validation
            "{\"nsKey\":\"tenant-1/../traversal\"}", // illegal dots violating validation
            "null",
            "{}"
    })
    @DisplayName("Malformed or unknown invalidation payloads are logged and dropped without throwing (Req R4.4, R12.6)")
    @SuppressWarnings("unchecked")
    void testMalformedPayloadDroppedSafely(String badPayload) {
        String cellId = "cell-alpha";
        ConsistentHashRing ring = ConsistentHashRing.of(1, List.of("node-1", "node-2", "node-3"));

        WaterfallRoutingResolver resolver = new WaterfallRoutingResolver(
                ring, null, Duration.ofMinutes(1), 1000L, 30L, null, Runnable::run
        );

        StatefulRedisPubSubConnection<String, String> pubSubConn = mock(StatefulRedisPubSubConnection.class);
        RoutingInvalidationSubscriber subscriber = new RoutingInvalidationSubscriber(
                cellId, pubSubConn, resolver, null
        );

        // Must not throw any exception
        assertThatCode(() -> subscriber.message("rt:cell:{cell-alpha}:invalidation", badPayload))
                .doesNotThrowAnyException();

        assertThat(subscriber.malformedCount()).isEqualTo(1L);
        assertThat(subscriber.invalidationCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Subscriber processes valid message immediately after surviving malformed payload")
    @SuppressWarnings("unchecked")
    void testSubscriberRecoversFromMalformedPayload() {
        String cellId = "cell-alpha";
        ConsistentHashRing ring = ConsistentHashRing.of(1, List.of("node-1", "node-2", "node-3"));

        WaterfallRoutingResolver resolver = new WaterfallRoutingResolver(
                ring, null, Duration.ofMinutes(1), 1000L, 30L, null, Runnable::run
        );

        StatefulRedisPubSubConnection<String, String> pubSubConn = mock(StatefulRedisPubSubConnection.class);
        RoutingInvalidationSubscriber subscriber = new RoutingInvalidationSubscriber(
                cellId, pubSubConn, resolver, null
        );

        // 1. Send malformed message
        subscriber.message("rt:cell:{cell-alpha}:invalidation", "{corrupted_data!!!");
        assertThat(subscriber.malformedCount()).isEqualTo(1L);

        // 2. Populate cache with valid entry
        RoutingKey validKey = RoutingKey.ofTenanted(cellId, "tenant-1", "ns-healthy");
        resolver.resolve(validKey);

        // 3. Send valid invalidation message
        RoutingInvalidationMessage validMsg = new RoutingInvalidationMessage(validKey.keyMaterial(), 10L, "rebalance");
        subscriber.message("rt:cell:{cell-alpha}:invalidation", validMsg.toJson());

        assertThat(subscriber.invalidationCount()).isEqualTo(1L);
        assertThat(subscriber.malformedCount()).isEqualTo(1L);
    }
}
