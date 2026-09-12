/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.cluster.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.spectrayan.spector.cluster.node.NodeRole;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.cell.CellProperties;
import com.spectrayan.spector.synapse.memory.MemoryDto;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GatewayForwardingFilter Tests (G48, G54)")
class GatewayForwardingFilterTest {

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
    @DisplayName("G48: Namespace is extracted from URI path when header and query parameter are missing")
    void testExtractNamespaceFromPath() throws Exception {
        AtomicReference<RoutingKey> routedKey = new AtomicReference<>();
        when(forwarder.forward(any(), any())).thenAnswer(inv -> {
            routedKey.set(inv.getArgument(0));
            return new ForwardResponse(200, Map.of(), "{}".getBytes(StandardCharsets.UTF_8), "target-node", 1);
        });

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/namespaces/team-alpha/reset");
        request.setRequestURI("/api/v1/namespaces/team-alpha/reset");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(routedKey.get()).isNotNull();
        assertThat(routedKey.get().namespaceId()).isEqualTo("team-alpha");
    }

    @Test
    @DisplayName("G48: Request body exceeding 10MB limit is rejected with 413 Payload Too Large JSON envelope")
    void testLargeBodyRejectedWith413() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/memory/remember");
        request.setRequestURI("/api/v1/memory/remember");
        // Over 10 MB
        byte[] oversizedBody = new byte[GatewayForwardingFilter.MAX_BUFFERED_BODY_BYTES + 100];
        request.setContent(oversizedBody);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentType()).startsWith("application/json");

        MemoryDto.ErrorResponse error = objectMapper.readValue(response.getContentAsString(), MemoryDto.ErrorResponse.class);
        assertThat(error.status()).isEqualTo(413);
        assertThat(error.error()).isEqualTo("PAYLOAD_TOO_LARGE");
    }

    @Test
    @DisplayName("G54: Gateway forwarding failure returns 502 JSON ErrorResponse without leaking internal exception")
    void testForwardingFailureReturns502JsonWithoutLeak() throws Exception {
        when(forwarder.forward(any(), any())).thenThrow(new RuntimeException("Connection refused: redis://super-secret-host:6379"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/memory");
        request.setRequestURI("/api/v1/memory");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(502);
        assertThat(response.getContentType()).startsWith("application/json");

        String responseBody = response.getContentAsString();
        // Crucial G54 invariant: Must NOT leak internal Redis URL or hostname
        assertThat(responseBody).doesNotContain("super-secret-host");
        assertThat(responseBody).doesNotContain("6379");

        MemoryDto.ErrorResponse error = objectMapper.readValue(responseBody, MemoryDto.ErrorResponse.class);
        assertThat(error.status()).isEqualTo(502);
        assertThat(error.error()).isEqualTo("GATEWAY_ROUTING_FAILURE");
    }
}
