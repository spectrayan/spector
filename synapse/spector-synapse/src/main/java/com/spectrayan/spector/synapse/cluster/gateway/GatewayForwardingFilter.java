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
import com.spectrayan.spector.cluster.gateway.ForwardRequest;
import com.spectrayan.spector.cluster.gateway.ForwardResponse;
import com.spectrayan.spector.cluster.gateway.GatewayForwarder;
import com.spectrayan.spector.cluster.gateway.RoutingKeyExtractor;
import com.spectrayan.spector.cluster.node.NodeRole;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.memory.MemoryDto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servlet filter adapter that intercepts requests on {@code role=gateway} nodes and forwards them
 * to the authoritative owner node (ADR-0034 §8, ADR-0081 §8, Req R7.1).
 *
 * <p><strong>Deprecated (ADR-0081):</strong> Maintained as a backwards-compatibility shim for single-process
 * or mixed-version deployments. In split cell topology, the dedicated reactive {@code spector-gateway}
 * service should be used instead.</p>
 */
@Deprecated
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class GatewayForwardingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayForwardingFilter.class);

    public static final int MAX_BUFFERED_BODY_BYTES = 10 * 1024 * 1024; // 10 MB limit (G48)

    private final SynapseProperties properties;
    private final GatewayForwarder forwarder;
    private final ObjectMapper objectMapper;
    private final RoutingKeyExtractor routingKeyExtractor;

    public GatewayForwardingFilter(SynapseProperties properties, GatewayForwarder forwarder) {
        this(properties, forwarder, defaultObjectMapper());
    }

    public GatewayForwardingFilter(SynapseProperties properties, GatewayForwarder forwarder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.forwarder = forwarder;
        this.objectMapper = objectMapper != null ? objectMapper : defaultObjectMapper();
        this.routingKeyExtractor = new RoutingKeyExtractor();
    }

    private static ObjectMapper defaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (properties == null || properties.getCell() == null) {
            return true;
        }
        if (properties.getCell().resolvedRole() != NodeRole.GATEWAY) {
            return true;
        }
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/v1/")) {
            return true;
        }
        // Exclude only health and actuator paths from forwarding (ADR-0081 §9).
        // Auth and events are forwarded to owner (aligned with dedicated spector-gateway).
        return path.startsWith("/api/v1/health")
                || path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (forwarder == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String cellId = properties.getCell() != null ? properties.getCell().getId() : "default";

        RoutingKey routingKey = routingKeyExtractor.extract(
                cellId,
                request::getHeader,
                request::getParameter,
                request.getRequestURI()
        );

        String uriPath = request.getRequestURI();
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            uriPath = uriPath + "?" + request.getQueryString();
        }

        Map<String, List<String>> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                headers.put(name, Collections.list(request.getHeaders(name)));
            }
        }

        long contentLength = request.getContentLengthLong();
        if (contentLength > MAX_BUFFERED_BODY_BYTES) {
            log.warn("Gateway rejected request body: Content-Length {} exceeds max limit of {} bytes",
                    contentLength, MAX_BUFFERED_BODY_BYTES);
            writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "PAYLOAD_TOO_LARGE", "Request body exceeds maximum size of " + MAX_BUFFERED_BODY_BYTES + " bytes");
            return;
        }

        byte[] body = request.getInputStream().readNBytes(MAX_BUFFERED_BODY_BYTES + 1);
        if (body.length > MAX_BUFFERED_BODY_BYTES) {
            log.warn("Gateway rejected request body: read bytes exceeded max limit of {} bytes", MAX_BUFFERED_BODY_BYTES);
            writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "PAYLOAD_TOO_LARGE", "Request body exceeds maximum size of " + MAX_BUFFERED_BODY_BYTES + " bytes");
            return;
        }

        String idempotencyKey = request.getHeader("Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = request.getHeader("X-Idempotency-Key");
        }

        ForwardRequest forwardRequest = new ForwardRequest(
                request.getMethod(),
                uriPath,
                headers,
                body,
                idempotencyKey
        );

        try (ForwardResponse forwardResponse = forwarder.forward(routingKey, forwardRequest)) {
            response.setStatus(forwardResponse.statusCode());
            for (Map.Entry<String, List<String>> entry : forwardResponse.headers().entrySet()) {
                String name = entry.getKey();
                if (!"transfer-encoding".equalsIgnoreCase(name) && !"content-length".equalsIgnoreCase(name)) {
                    for (String val : entry.getValue()) {
                        response.addHeader(name, val);
                    }
                }
            }
            try (InputStream in = forwardResponse.bodyStream()) {
                in.transferTo(response.getOutputStream());
            }
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("Gateway forward failed for namespace '{}': {}", routingKey.namespaceId(), e.getMessage(), e);
            writeError(response, HttpServletResponse.SC_BAD_GATEWAY,
                    "GATEWAY_ROUTING_FAILURE", "Gateway routing failure for namespace: " + routingKey.namespaceId());
        }
    }

    private void writeError(HttpServletResponse response, int status, String errorCode, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        MemoryDto.ErrorResponse errorResponse = new MemoryDto.ErrorResponse(status, errorCode, message);
        byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }
}
