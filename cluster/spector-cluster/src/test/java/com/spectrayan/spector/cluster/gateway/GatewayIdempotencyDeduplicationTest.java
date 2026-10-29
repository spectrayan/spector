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
package com.spectrayan.spector.cluster.gateway;

import com.spectrayan.spector.cluster.idempotency.IdempotencyReservation;
import com.spectrayan.spector.cluster.idempotency.InMemoryIdempotencyStore;
import com.spectrayan.spector.cluster.idempotency.RequestFingerprint;
import com.spectrayan.spector.cluster.idempotency.StoredIdempotentResponse;
import com.spectrayan.spector.cluster.routing.ConsistentHashRing;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.cluster.routing.cache.WaterfallRoutingResolver;
import com.spectrayan.spector.commons.error.SpectorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GatewayForwarder Idempotency Deduplication Tests")
class GatewayIdempotencyDeduplicationTest {

    private WaterfallRoutingResolver resolver;
    private RoutingKey routingKey;

    @BeforeEach
    void setUp() {
        ConsistentHashRing ring = ConsistentHashRing.of(1, List.of("node-1", "node-2", "node-3"));
        resolver = new WaterfallRoutingResolver(
                ring, null, Duration.ofMinutes(1), 1000L, 30L, null, Runnable::run
        );
        routingKey = RoutingKey.ofTenanted("cell-1", "tenant-a", "ns-alpha");
    }

    @Test
    @DisplayName("Repeating mutating request with same namespace, Idempotency-Key, and fingerprint replays without re-executing")
    void duplicateWriteWithSameKeyAndPayloadReplaysStoredResponse() throws Exception {
        AtomicInteger executionCount = new AtomicInteger(0);
        GatewayHttpTransport transport = (targetNodeId, targetUrl, req, routingHeaders) -> {
            int call = executionCount.incrementAndGet();
            String body = "{\"id\":\"mem-" + call + "\"}";
            return new ForwardResponse(
                    201,
                    Map.of("Content-Type", List.of("application/json"), "X-Custom", List.of("v-" + call)),
                    body.getBytes(StandardCharsets.UTF_8),
                    targetNodeId,
                    1
            );
        };

        GatewayForwarder forwarder = new GatewayForwarder(resolver, 2, transport, nodeId -> "http://" + nodeId + ":7070");
        byte[] payload = "{\"content\":\"hello world\"}".getBytes(StandardCharsets.UTF_8);
        ForwardRequest req1 = new ForwardRequest("POST", "/api/v1/memory/remember", Map.of(), payload, "idem-key-001");
        ForwardRequest req2 = new ForwardRequest("POST", "/api/v1/memory/remember", Map.of(), payload.clone(), "idem-key-001");

        ForwardResponse first = forwarder.forward(routingKey, req1);
        ForwardResponse second = forwarder.forward(routingKey, req2);

        assertThat(executionCount.get()).isEqualTo(1);
        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(second.statusCode()).isEqualTo(201);
        assertThat(new String(second.body(), StandardCharsets.UTF_8)).isEqualTo("{\"id\":\"mem-1\"}");
        assertThat(second.headers().get("X-Custom")).containsExactly("v-1");
        assertThat(second.headers().get("Idempotent-Replayed")).containsExactly("true");
    }

    @Test
    @DisplayName("Reusing Idempotency-Key with different payload, path, or method is rejected with IDEMPOTENCY_KEY_REUSED")
    void reusingKeyWithDifferentFingerprintIsRejected() throws Exception {
        AtomicInteger executionCount = new AtomicInteger(0);
        GatewayHttpTransport transport = (targetNodeId, targetUrl, req, routingHeaders) -> {
            executionCount.incrementAndGet();
            return new ForwardResponse(200, Map.of(), "{\"ok\":true}".getBytes(StandardCharsets.UTF_8), targetNodeId, 1);
        };

        GatewayForwarder forwarder = new GatewayForwarder(resolver, 2, transport, nodeId -> "http://" + nodeId + ":7070");
        byte[] payload1 = "{\"content\":\"first payload\"}".getBytes(StandardCharsets.UTF_8);
        byte[] payload2 = "{\"content\":\"second payload\"}".getBytes(StandardCharsets.UTF_8);

        ForwardRequest req1 = new ForwardRequest("POST", "/api/v1/memory/remember", Map.of(), payload1, "idem-reuse-1");
        ForwardResponse first = forwarder.forward(routingKey, req1);
        assertThat(first.statusCode()).isEqualTo(200);

        ForwardRequest differentPayload = new ForwardRequest("POST", "/api/v1/memory/remember", Map.of(), payload2, "idem-reuse-1");
        assertThatThrownBy(() -> forwarder.forward(routingKey, differentPayload))
                .isInstanceOf(SpectorException.class)
                .hasMessageContaining("SPE-100-022");

        ForwardRequest differentPath = new ForwardRequest("POST", "/api/v1/memory/store", Map.of(), payload1, "idem-reuse-1");
        assertThatThrownBy(() -> forwarder.forward(routingKey, differentPath))
                .isInstanceOf(SpectorException.class)
                .hasMessageContaining("SPE-100-022");

        ForwardRequest differentMethod = new ForwardRequest("DELETE", "/api/v1/memory/remember", Map.of(), payload1, "idem-reuse-1");
        assertThatThrownBy(() -> forwarder.forward(routingKey, differentMethod))
                .isInstanceOf(SpectorException.class)
                .hasMessageContaining("SPE-100-022");

        assertThat(executionCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Overlong (>256 bytes) or non-printable Idempotency-Key is rejected")
    void invalidOrOverlongKeyIsRejected() {
        AtomicInteger executionCount = new AtomicInteger(0);
        GatewayHttpTransport transport = (targetNodeId, targetUrl, req, routingHeaders) -> {
            executionCount.incrementAndGet();
            return new ForwardResponse(200, Map.of(), "{}".getBytes(StandardCharsets.UTF_8), targetNodeId, 1);
        };

        GatewayForwarder forwarder = new GatewayForwarder(resolver, 2, transport, nodeId -> "http://" + nodeId + ":7070");
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);

        String overlongKey = "k".repeat(257);
        ForwardRequest overlongReq = new ForwardRequest("POST", "/api/v1/memory/remember", Map.of(), payload, overlongKey);
        assertThatThrownBy(() -> forwarder.forward(routingKey, overlongReq))
                .isInstanceOf(SpectorException.class)
                .hasMessageContaining("SPE-100-021");

        ForwardRequest nonPrintableReq = new ForwardRequest("POST", "/api/v1/memory/remember", Map.of(), payload, "bad\u0007key");
        assertThatThrownBy(() -> forwarder.forward(routingKey, nonPrintableReq))
                .isInstanceOf(SpectorException.class)
                .hasMessageContaining("SPE-100-020");

        assertThat(executionCount.get()).isZero();
    }

    @Test
    @DisplayName("Concurrent in-flight requests with same (namespace, Idempotency-Key) execute transport at most once")
    void concurrentInFlightRequestsExecuteAtMostOnce() throws Exception {
        CountDownLatch leaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLeader = new CountDownLatch(1);
        AtomicInteger executionCount = new AtomicInteger(0);

        GatewayHttpTransport transport = (targetNodeId, targetUrl, req, routingHeaders) -> {
            executionCount.incrementAndGet();
            leaderEntered.countDown();
            try {
                releaseLeader.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new ForwardResponse(200, Map.of(), "{\"ok\":true}".getBytes(StandardCharsets.UTF_8), targetNodeId, 1);
        };

        GatewayForwarder forwarder = new GatewayForwarder(resolver, 2, transport, nodeId -> "http://" + nodeId + ":7070");
        byte[] payload = "{\"content\":\"concurrent\"}".getBytes(StandardCharsets.UTF_8);
        ForwardRequest req = new ForwardRequest("POST", "/api/v1/memory/remember", Map.of(), payload, "idem-concurrent-1");

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<ForwardResponse> leaderFuture = pool.submit(() -> forwarder.forward(routingKey, req));
            assertThat(leaderEntered.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> forwarder.forward(routingKey, req))
                    .isInstanceOf(SpectorException.class)
                    .hasMessageContaining("SPE-500-015");

            releaseLeader.countDown();
            ForwardResponse leaderResp = leaderFuture.get(5, TimeUnit.SECONDS);
            assertThat(leaderResp.statusCode()).isEqualTo(200);
        }

        assertThat(executionCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("InMemoryIdempotencyStore enforces TTL expiration and maximum entry count bounds")
    void storeEnforcesTtlAndMaxEntryCap() {
        AtomicLong nowMs = new AtomicLong(1_700_000_000_000L);
        Clock mutableClock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(nowMs.get());
            }

            @Override
            public long millis() {
                return nowMs.get();
            }
        };

        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(Duration.ofSeconds(10), 2, mutableClock, null);
        RequestFingerprint fp1 = RequestFingerprint.of("POST", "/api/v1/memory/remember", "{\"a\":1}".getBytes(StandardCharsets.UTF_8));
        RequestFingerprint fp2 = RequestFingerprint.of("POST", "/api/v1/memory/remember", "{\"a\":2}".getBytes(StandardCharsets.UTF_8));
        RequestFingerprint fp3 = RequestFingerprint.of("POST", "/api/v1/memory/remember", "{\"a\":3}".getBytes(StandardCharsets.UTF_8));

        assertThat(store.reserve("tenant:ns-1", "k1", fp1).status()).isEqualTo(IdempotencyReservation.Status.ACQUIRED);
        store.commit("tenant:ns-1", "k1", fp1, new StoredIdempotentResponse(201, Map.of(), "1".getBytes(StandardCharsets.UTF_8), nowMs.get()));

        assertThat(store.reserve("tenant:ns-1", "k2", fp2).status()).isEqualTo(IdempotencyReservation.Status.ACQUIRED);
        store.commit("tenant:ns-1", "k2", fp2, new StoredIdempotentResponse(201, Map.of(), "2".getBytes(StandardCharsets.UTF_8), nowMs.get()));

        // Adding k3 exceeds maxEntries=2 -> evicts oldest committed k1
        assertThat(store.reserve("tenant:ns-1", "k3", fp3).status()).isEqualTo(IdempotencyReservation.Status.ACQUIRED);
        store.commit("tenant:ns-1", "k3", fp3, new StoredIdempotentResponse(201, Map.of(), "3".getBytes(StandardCharsets.UTF_8), nowMs.get()));

        assertThat(store.size()).isEqualTo(2);
        assertThat(store.reserve("tenant:ns-1", "k2", fp2).status()).isEqualTo(IdempotencyReservation.Status.REPLAYED);
        assertThat(store.reserve("tenant:ns-1", "k1", fp1).status()).isEqualTo(IdempotencyReservation.Status.ACQUIRED);
        store.release("tenant:ns-1", "k1");

        // Advance clock past 10s TTL -> all entries expire
        nowMs.addAndGet(11_000L);
        assertThat(store.size()).isZero();
        assertThat(store.reserve("tenant:ns-1", "k2", fp2).status()).isEqualTo(IdempotencyReservation.Status.ACQUIRED);
    }
}
