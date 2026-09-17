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

import com.spectrayan.spector.cluster.gateway.ForwardRequest;
import com.spectrayan.spector.cluster.gateway.ForwardResponse;
import com.spectrayan.spector.cluster.gateway.GatewayForwarder;
import com.spectrayan.spector.cluster.gateway.RoutingKeyExtractor;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.gateway.config.GatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GatewayWebFilter Tests (ADR-0081 §8 Phase 2, G48, G54)")
class GatewayWebFilterTest {

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
    @DisplayName("Extracts namespace from URI path and forwards request successfully")
    void testNamespaceExtractedFromPath() throws Exception {
        AtomicReference<RoutingKey> capturedKey = new AtomicReference<>();
        when(forwarder.forward(any(), any())).thenAnswer(inv -> {
            capturedKey.set(inv.getArgument(0));
            return new ForwardResponse(200, Map.of(), "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8), "node-1", 1);
        });

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/namespaces/team-beta/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}");
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(capturedKey.get()).isNotNull();
        assertThat(capturedKey.get().namespaceId()).isEqualTo("team-beta");
    }

    @Test
    @DisplayName("G48: Request body exceeding 10MB limit is rejected with 413 Payload Too Large")
    void testLargeBodyRejectedWith413() throws Exception {
        int maxBytes = properties.getRouting().getGateway().getMaxBufferedBodyBytes();
        byte[] oversized = new byte[maxBytes + 1024];

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/memory/remember")
                .header("Content-Length", String.valueOf(oversized.length))
                .body(reactor.core.publisher.Flux.just(new org.springframework.core.io.buffer.DefaultDataBufferFactory().wrap(oversized)));
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        verify(forwarder, never()).forward(any(), any());
    }

    @Test
    @DisplayName("G54: Gateway forwarding failure returns 502 without leaking internal connection details")
    void testForwardingFailureReturns502JsonWithoutLeak() throws Exception {
        when(forwarder.forward(any(), any())).thenThrow(new RuntimeException("Connection refused: redis://super-secret-cluster:6379"));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/memory").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(exchange.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);

        // Verify response body
        StepVerifier.create(exchange.getResponse().getBody())
                .consumeNextWith(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    String body = new String(bytes, StandardCharsets.UTF_8);
                    assertThat(body).contains("GATEWAY_ROUTING_FAILURE");
                    assertThat(body).doesNotContain("super-secret-cluster");
                    assertThat(body).doesNotContain("6379");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Bypasses actuator and health endpoints without routing")
    void testSkipActuatorAndHealth() throws Exception {
        MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
        verify(forwarder, never()).forward(any(), any());
    }
}
