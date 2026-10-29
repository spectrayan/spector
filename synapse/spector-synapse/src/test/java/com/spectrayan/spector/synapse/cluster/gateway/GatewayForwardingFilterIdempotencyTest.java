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
package com.spectrayan.spector.synapse.cluster.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.spectrayan.spector.cluster.gateway.ForwardResponse;
import com.spectrayan.spector.cluster.gateway.GatewayForwarder;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.cell.CellProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

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

@DisplayName("GatewayForwardingFilter Idempotency Deduplication Tests")
class GatewayForwardingFilterIdempotencyTest {

    private SynapseProperties properties;
    private GatewayForwarder forwarder;
    private ObjectMapper objectMapper;
    private GatewayForwardingFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        properties = new SynapseProperties();
        CellProperties cell = new CellProperties();
        cell.setRole("gateway");
        cell.setId("cell-test");
        cell.setNodeId("gateway-node-1");
        properties.setCell(cell);

        forwarder = mock(GatewayForwarder.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        filter = new GatewayForwardingFilter(properties, forwarder, objectMapper);
        filterChain = mock(FilterChain.class);
    }

    @Test
    @DisplayName("Repeating mutating request with same namespace, Idempotency-Key, and fingerprint replays cached response")
    void duplicateWriteReplaysWithoutReExecuting() throws Exception {
        AtomicInteger forwardCount = new AtomicInteger(0);
        when(forwarder.forward(any(), any())).thenAnswer(inv -> {
            int n = forwardCount.incrementAndGet();
            return new ForwardResponse(
                    201,
                    Map.of("Content-Type", List.of("application/json"), "X-Seq", List.of(String.valueOf(n))),
                    ("{\"id\":\"mem-" + n + "\"}").getBytes(StandardCharsets.UTF_8),
                    "node-1",
                    1
            );
        });

        byte[] body = "{\"content\":\"hello\"}".getBytes(StandardCharsets.UTF_8);

        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/v1/memory/remember");
        req1.setRequestURI("/api/v1/memory/remember");
        req1.addHeader("X-Spector-Namespace", "ns-1");
        req1.addHeader("Idempotency-Key", "idem-syn-1");
        req1.setContent(body);
        MockHttpServletResponse resp1 = new MockHttpServletResponse();

        filter.doFilter(req1, resp1, filterChain);
        assertThat(resp1.getStatus()).isEqualTo(201);
        assertThat(resp1.getContentAsString()).isEqualTo("{\"id\":\"mem-1\"}");

        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/v1/memory/remember");
        req2.setRequestURI("/api/v1/memory/remember");
        req2.addHeader("X-Spector-Namespace", "ns-1");
        req2.addHeader("Idempotency-Key", "idem-syn-1");
        req2.setContent(body);
        MockHttpServletResponse resp2 = new MockHttpServletResponse();

        filter.doFilter(req2, resp2, filterChain);
        assertThat(resp2.getStatus()).isEqualTo(201);
        assertThat(resp2.getContentAsString()).isEqualTo("{\"id\":\"mem-1\"}");
        assertThat(resp2.getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(forwardCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Reusing Idempotency-Key with different payload, method, or path returns 422 Unprocessable Entity")
    void reusingKeyWithDifferentFingerprintReturns422() throws Exception {
        when(forwarder.forward(any(), any())).thenReturn(
                new ForwardResponse(200, Map.of("Content-Type", List.of("application/json")),
                        "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8), "node-1", 1)
        );

        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/v1/memory/remember");
        req1.setRequestURI("/api/v1/memory/remember");
        req1.addHeader("X-Spector-Namespace", "ns-1");
        req1.addHeader("Idempotency-Key", "idem-syn-422");
        req1.setContent("{\"content\":\"v1\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse resp1 = new MockHttpServletResponse();
        filter.doFilter(req1, resp1, filterChain);
        assertThat(resp1.getStatus()).isEqualTo(200);

        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/v1/memory/remember");
        req2.setRequestURI("/api/v1/memory/remember");
        req2.addHeader("X-Spector-Namespace", "ns-1");
        req2.addHeader("Idempotency-Key", "idem-syn-422");
        req2.setContent("{\"content\":\"v2\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse resp2 = new MockHttpServletResponse();
        filter.doFilter(req2, resp2, filterChain);

        assertThat(resp2.getStatus()).isEqualTo(422);
        assertThat(resp2.getContentAsString()).contains("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    @DisplayName("Invalid or overlong Idempotency-Key returns 400 Bad Request")
    void invalidOrOverlongKeyReturns400() throws Exception {
        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/v1/memory/remember");
        req1.setRequestURI("/api/v1/memory/remember");
        req1.addHeader("Idempotency-Key", "a".repeat(257));
        req1.setContent("{}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse resp1 = new MockHttpServletResponse();

        filter.doFilter(req1, resp1, filterChain);
        assertThat(resp1.getStatus()).isEqualTo(400);

        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/v1/memory/remember");
        req2.setRequestURI("/api/v1/memory/remember");
        req2.addHeader("Idempotency-Key", "bad key with spaces");
        req2.setContent("{}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse resp2 = new MockHttpServletResponse();

        filter.doFilter(req2, resp2, filterChain);
        assertThat(resp2.getStatus()).isEqualTo(400);

        verify(forwarder, never()).forward(any(), any());
    }

    @Test
    @DisplayName("Concurrent in-flight request with same Idempotency-Key returns 409 Conflict with Retry-After")
    void concurrentInFlightRequestReturns409() throws Exception {
        CountDownLatch leaderStarted = new CountDownLatch(1);
        CountDownLatch releaseLeader = new CountDownLatch(1);
        AtomicInteger forwardCount = new AtomicInteger(0);

        when(forwarder.forward(any(), any())).thenAnswer(inv -> {
            forwardCount.incrementAndGet();
            leaderStarted.countDown();
            releaseLeader.await(5, TimeUnit.SECONDS);
            return new ForwardResponse(200, Map.of(), "{\"ok\":true}".getBytes(StandardCharsets.UTF_8), "node-1", 1);
        });

        byte[] body = "{\"content\":\"inflight\"}".getBytes(StandardCharsets.UTF_8);

        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/v1/memory/remember");
        req1.setRequestURI("/api/v1/memory/remember");
        req1.addHeader("X-Spector-Namespace", "ns-1");
        req1.addHeader("Idempotency-Key", "idem-syn-409");
        req1.setContent(body);
        MockHttpServletResponse resp1 = new MockHttpServletResponse();

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Future<?> leaderFuture = pool.submit(() -> {
                filter.doFilter(req1, resp1, filterChain);
                return null;
            });
            assertThat(leaderStarted.await(5, TimeUnit.SECONDS)).isTrue();

            MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/v1/memory/remember");
            req2.setRequestURI("/api/v1/memory/remember");
            req2.addHeader("X-Spector-Namespace", "ns-1");
            req2.addHeader("Idempotency-Key", "idem-syn-409");
            req2.setContent(body);
            MockHttpServletResponse resp2 = new MockHttpServletResponse();

            filter.doFilter(req2, resp2, filterChain);
            assertThat(resp2.getStatus()).isEqualTo(409);
            assertThat(resp2.getHeader("Retry-After")).isNotBlank();

            releaseLeader.countDown();
            leaderFuture.get(5, TimeUnit.SECONDS);
        }

        assertThat(forwardCount.get()).isEqualTo(1);
    }
}
