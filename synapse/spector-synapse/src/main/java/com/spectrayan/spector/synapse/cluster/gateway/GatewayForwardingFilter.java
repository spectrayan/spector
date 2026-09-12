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
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servlet filter that intercepts requests on {@code role=gateway} nodes and forwards them
 * to the authoritative owner node (ADR-0034 §8, Req R7.1).
 */
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class GatewayForwardingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayForwardingFilter.class);

    public static final int MAX_BUFFERED_BODY_BYTES = 10 * 1024 * 1024; // 10 MB limit (G48)
    private static final Pattern NAMESPACE_PATH_PATTERN =
            Pattern.compile("^/api/v1/namespaces/([^/?]+)(?:/.*)?$");

    private final SynapseProperties properties;
    private final GatewayForwarder forwarder;
    private final ObjectMapper objectMapper;

    public GatewayForwardingFilter(SynapseProperties properties, GatewayForwarder forwarder) {
        this(properties, forwarder, defaultObjectMapper());
    }

    public GatewayForwardingFilter(SynapseProperties properties, GatewayForwarder forwarder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.forwarder = forwarder;
        this.objectMapper = objectMapper != null ? objectMapper : defaultObjectMapper();
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
        // Exclude health, auth, events from forwarding
        return path.startsWith("/api/v1/auth")
                || path.startsWith("/api/v1/events")
                || path.contains("/health");
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
        String headerNs = request.getHeader(GatewayForwarder.HEADER_NAMESPACE);
        String paramNs = request.getParameter("namespace");
        String pathNs = null;
        String reqUri = request.getRequestURI();
        if (reqUri != null) {
            Matcher m = NAMESPACE_PATH_PATTERN.matcher(reqUri);
            if (m.matches()) {
                pathNs = m.group(1);
            }
        }

        String namespaceId = (headerNs != null && !headerNs.isBlank())
                ? headerNs.trim()
                : (paramNs != null && !paramNs.isBlank()
                        ? paramNs.trim()
                        : (pathNs != null && !pathNs.isBlank() ? pathNs.trim() : null));

        String headerTenant = request.getHeader(GatewayForwarder.HEADER_TENANT);
        String paramTenant = request.getParameter("tenant");
        String tenantId = (headerTenant != null && !headerTenant.isBlank())
                ? headerTenant.trim()
                : (paramTenant != null && !paramTenant.isBlank() ? paramTenant.trim() : null);

        // Fallback: extract namespace (sub) and tenant from JWT Bearer token if not explicitly provided
        if (namespaceId == null || tenantId == null) {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();
                if (namespaceId == null) {
                    namespaceId = extractSubFromJwt(token);
                }
                if (tenantId == null) {
                    tenantId = extractTenantFromJwt(token);
                }
            }
        }

        if (namespaceId == null || namespaceId.isBlank()) {
            namespaceId = "default";
        }

        RoutingKey routingKey = (tenantId != null && !tenantId.isBlank())
                ? RoutingKey.ofTenanted(cellId, tenantId, namespaceId)
                : RoutingKey.ofUntenanted(cellId, namespaceId);

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

        try {
            ForwardResponse forwardResponse = forwarder.forward(routingKey, forwardRequest);
            response.setStatus(forwardResponse.statusCode());
            for (Map.Entry<String, List<String>> entry : forwardResponse.headers().entrySet()) {
                String name = entry.getKey();
                if (!"transfer-encoding".equalsIgnoreCase(name) && !"content-length".equalsIgnoreCase(name)) {
                    for (String val : entry.getValue()) {
                        response.addHeader(name, val);
                    }
                }
            }
            if (forwardResponse.body().length > 0) {
                response.getOutputStream().write(forwardResponse.body());
                response.getOutputStream().flush();
            }
        } catch (Exception e) {
            log.error("Gateway forward failed for namespace '{}': {}", namespaceId, e.getMessage(), e);
            writeError(response, HttpServletResponse.SC_BAD_GATEWAY,
                    "GATEWAY_ROUTING_FAILURE", "Gateway routing failure for namespace: " + namespaceId);
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

    private String extractSubFromJwt(String token) {
        if (token == null || token.isBlank()) return null;
        String[] parts = token.split("\\.");
        if (parts.length < 2) return null;
        try {
            byte[] decoded = java.util.Base64.getUrlDecoder().decode(parts[1]);
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(decoded);
            if (node.has("sub")) {
                String sub = node.get("sub").asText();
                return (sub != null && !sub.isBlank()) ? sub.trim() : null;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String extractTenantFromJwt(String token) {
        if (token == null || token.isBlank()) return null;
        String[] parts = token.split("\\.");
        if (parts.length < 2) return null;
        try {
            byte[] decoded = java.util.Base64.getUrlDecoder().decode(parts[1]);
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(decoded);
            if (node.has("tenant_id")) {
                String tid = node.get("tenant_id").asText();
                return (tid != null && !tid.isBlank()) ? tid.trim() : null;
            }
            if (node.has("tenantId")) {
                String tid = node.get("tenantId").asText();
                return (tid != null && !tid.isBlank()) ? tid.trim() : null;
            }
        } catch (Exception ignored) {}
        return null;
    }
}
