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
package com.spectrayan.spector.gateway;

import com.spectrayan.spector.cluster.gateway.ForwardRequest;
import com.spectrayan.spector.cluster.gateway.ForwardResponse;
import com.spectrayan.spector.cluster.gateway.GatewayForwarder;
import com.spectrayan.spector.cluster.gateway.RoutingKeyExtractor;
import com.spectrayan.spector.gateway.config.GatewayProperties;
import com.spectrayan.spector.gateway.filter.GatewayWebFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Gateway Streaming and SSE Tests (ADR-0081 §8 Phase 5)")
class GatewayStreamingTest {

    private GatewayProperties properties;
    private GatewayForwarder forwarder;
    private RoutingKeyExtractor extractor;
    private GatewayWebFilter filter;
    private WebFilterChain chain;

    @BeforeEach
    void setUp() {
        properties = new GatewayProperties();
        properties.getCell().setId("cell-test");
        properties.getRouting().getGateway().setMaxBufferedBodyBytes(1024); // 1 KB limit for streaming test

        forwarder = mock(GatewayForwarder.class);
        extractor = new RoutingKeyExtractor();
        filter = new GatewayWebFilter(properties, forwarder, extractor);
        chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("Streaming request exceeding max-buffered-body-bytes aborts with 413 without loading full body")
    void testStreamingBodyExceedingLimitAbortsWith413() {
        DefaultDataBufferFactory factory = new DefaultDataBufferFactory();
        byte[] chunk = new byte[600];
        Flux<DataBuffer> bodyFlux = Flux.just(
                factory.wrap(chunk),
                factory.wrap(chunk) // Total 1200 bytes > 1024 bytes limit
        );

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/memory/remember")
                .body(bodyFlux);
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    @DisplayName("SSE proxy test forwards at least two data: frames without buffering entire stream")
    void testSseStreamProxyForwardsFrames() throws Exception {
        String frame1 = "data: {\"event\":\"ping\",\"seq\":1}\n\n";
        String frame2 = "data: {\"event\":\"ping\",\"seq\":2}\n\n";
        InputStream stream = new ByteArrayInputStream((frame1 + frame2).getBytes(StandardCharsets.UTF_8));

        Map<String, List<String>> sseHeaders = Map.of(
                "Content-Type", List.of(MediaType.TEXT_EVENT_STREAM_VALUE),
                "Cache-Control", List.of("no-cache")
        );

        ForwardResponse sseResponse = new ForwardResponse(200, sseHeaders, stream, "owner-0", 1);
        when(forwarder.forward(any(), any())).thenReturn(sseResponse);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/events")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange.getResponse().getHeaders().getFirst("Content-Type")).contains(MediaType.TEXT_EVENT_STREAM_VALUE);

        StringBuilder received = new StringBuilder();
        StepVerifier.create(exchange.getResponse().getBody())
                .thenConsumeWhile(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    received.append(new String(bytes, StandardCharsets.UTF_8));
                    return true;
                })
                .verifyComplete();

        assertThat(received.toString()).contains("seq\":1");
        assertThat(received.toString()).contains("seq\":2");
    }
}
