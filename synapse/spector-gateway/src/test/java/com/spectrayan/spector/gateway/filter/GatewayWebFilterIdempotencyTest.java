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
package com.spectrayan.spector.gateway.filter;

import com.spectrayan.spector.cluster.gateway.ForwardResponse;
import com.spectrayan.spector.cluster.gateway.GatewayForwarder;
import com.spectrayan.spector.cluster.gateway.RoutingKeyExtractor;
import com.spectrayan.spector.gateway.config.GatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GatewayWebFilter Idempotency Deduplication Tests")
class GatewayWebFilterIdempotencyTest {

    private GatewayProperties properties;
    private GatewayForwarder forwarder;
    private RoutingKeyExtractor extractor;
    private GatewayWebFilter filter;
    private WebFilterChain chain;

    @BeforeEach
    void setUp() {
        properties = new GatewayProperties();
        properties.getCell().setId("cell-test");
        properties.getCell().setNodeId("gateway-node-1");

        forwarder = mock(GatewayForwarder.class);
        extractor = new RoutingKeyExtractor();
        filter = new GatewayWebFilter(properties, forwarder, extractor);
        chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Duplicate POST with same Idempotency-Key and payload replays cached response without re-forwarding")
    void duplicateWriteReplaysResponseWithoutReforwarding() throws Exception {
        AtomicInteger forwardCalls = new AtomicInteger(0);
        when(forwarder.forward(any(), any())).thenAnswer(inv -> {
            int c = forwardCalls.incrementAndGet();
            return new ForwardResponse(
                    201,
                    Map.of("Content-Type", List.of("application/json"), "X-Write-Seq", List.of(String.valueOf(c))),
                    ("{\"id\":\"engram-" + c + "\"}").getBytes(StandardCharsets.UTF_8),
                    "node-1",
                    1
            );
        });

        MockServerHttpRequest req1 = MockServerHttpRequest.post("/api/v1/memory/remember")
                .header("X-Spector-Namespace", "ns-alpha")
                .header("Idempotency-Key", "idem-web-1")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"content\":\"alpha fact\"}");
        MockServerWebExchange ex1 = MockServerWebExchange.from(req1);

        StepVerifier.create(filter.filter(ex1, chain)).verifyComplete();
        assertThat(ex1.getResponse().getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(ex1.getResponse().getHeaders().getFirst("X-Write-Seq")).isEqualTo("1");

        MockServerHttpRequest req2 = MockServerHttpRequest.post("/api/v1/memory/remember")
                .header("X-Spector-Namespace", "ns-alpha")
                .header("Idempotency-Key", "idem-web-1")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"content\":\"alpha fact\"}");
        MockServerWebExchange ex2 = MockServerWebExchange.from(req2);

        StepVerifier.create(filter.filter(ex2, chain)).verifyComplete();
        assertThat(ex2.getResponse().getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(ex2.getResponse().getHeaders().getFirst("X-Write-Seq")).isEqualTo("1");
        assertThat(ex2.getResponse().getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(ex2.getResponse().getBodyAsString().block()).isEqualTo("{\"id\":\"engram-1\"}");
        assertThat(forwardCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Reusing Idempotency-Key with different payload returns 422 Unprocessable Entity")
    void reusingIdempotencyKeyWithDifferentPayloadReturns422() throws Exception {
        when(forwarder.forward(any(), any())).thenReturn(
                new ForwardResponse(200, Map.of("Content-Type", List.of("application/json")),
                        "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8), "node-1", 1)
        );

        MockServerHttpRequest req1 = MockServerHttpRequest.post("/api/v1/memory/remember")
                .header("X-Spector-Namespace", "ns-alpha")
                .header("Idempotency-Key", "idem-web-422")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"content\":\"original\"}");
        MockServerWebExchange ex1 = MockServerWebExchange.from(req1);
        StepVerifier.create(filter.filter(ex1, chain)).verifyComplete();
        assertThat(ex1.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);

        MockServerHttpRequest req2 = MockServerHttpRequest.post("/api/v1/memory/remember")
                .header("X-Spector-Namespace", "ns-alpha")
                .header("Idempotency-Key", "idem-web-422")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"content\":\"tampered\"}");
        MockServerWebExchange ex2 = MockServerWebExchange.from(req2);
        StepVerifier.create(filter.filter(ex2, chain)).verifyComplete();

        assertThat(ex2.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ex2.getResponse().getBodyAsString().block()).contains("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    @DisplayName("Invalid or overlong Idempotency-Key returns 400 Bad Request")
    void invalidOrOverlongKeyReturns400() throws Exception {
        String overlong = "x".repeat(257);
        MockServerHttpRequest req1 = MockServerHttpRequest.post("/api/v1/memory/remember")
                .header("Idempotency-Key", overlong)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"content\":\"test\"}");
        MockServerWebExchange ex1 = MockServerWebExchange.from(req1);

        StepVerifier.create(filter.filter(ex1, chain)).verifyComplete();
        assertThat(ex1.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        MockServerHttpRequest req2 = MockServerHttpRequest.post("/api/v1/memory/remember")
                .header("Idempotency-Key", "invalid\u0001key")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"content\":\"test\"}");
        MockServerWebExchange ex2 = MockServerWebExchange.from(req2);

        StepVerifier.create(filter.filter(ex2, chain)).verifyComplete();
        assertThat(ex2.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        verify(forwarder, never()).forward(any(), any());
    }

    @Test
    @DisplayName("Concurrent in-flight request with same Idempotency-Key returns 409 Conflict with Retry-After")
    void concurrentInFlightRequestReturns409WithRetryAfter() throws Exception {
        CountDownLatch leaderInFlight = new CountDownLatch(1);
        CountDownLatch releaseLeader = new CountDownLatch(1);
        AtomicInteger forwardCalls = new AtomicInteger(0);

        when(forwarder.forward(any(), any())).thenAnswer(inv -> {
            forwardCalls.incrementAndGet();
            leaderInFlight.countDown();
            releaseLeader.await(5, TimeUnit.SECONDS);
            return new ForwardResponse(200, Map.of(), "{\"ok\":true}".getBytes(StandardCharsets.UTF_8), "node-1", 1);
        });

        MockServerHttpRequest req1 = MockServerHttpRequest.post("/api/v1/memory/remember")
                .header("X-Spector-Namespace", "ns-alpha")
                .header("Idempotency-Key", "idem-inflight-1")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"content\":\"concurrent\"}");
        MockServerWebExchange ex1 = MockServerWebExchange.from(req1);

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Future<?> leaderFuture = pool.submit(() -> filter.filter(ex1, chain).block());
            assertThat(leaderInFlight.await(5, TimeUnit.SECONDS)).isTrue();

            MockServerHttpRequest req2 = MockServerHttpRequest.post("/api/v1/memory/remember")
                    .header("X-Spector-Namespace", "ns-alpha")
                    .header("Idempotency-Key", "idem-inflight-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"content\":\"concurrent\"}");
            MockServerWebExchange ex2 = MockServerWebExchange.from(req2);

            StepVerifier.create(filter.filter(ex2, chain)).verifyComplete();
            assertThat(ex2.getResponse().getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(ex2.getResponse().getHeaders().getFirst("Retry-After")).isNotBlank();

            releaseLeader.countDown();
            leaderFuture.get(5, TimeUnit.SECONDS);
        }

        assertThat(forwardCalls.get()).isEqualTo(1);
    }
}
