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

import com.spectrayan.spector.cluster.node.NodeRole;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servlet filter that intercepts requests on {@code role=gateway} nodes and forwards them
 * to the authoritative owner node (ADR-0034 §8, Req R7.1).
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class GatewayForwardingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayForwardingFilter.class);

    private final SynapseProperties properties;
    private final GatewayForwarder forwarder;

    public GatewayForwardingFilter(SynapseProperties properties, GatewayForwarder forwarder) {
        this.properties = properties;
        this.forwarder = forwarder;
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

        String cellId = properties.getCell().getId();
        String headerNs = request.getHeader(GatewayForwarder.HEADER_NAMESPACE);
        String paramNs = request.getParameter("namespace");
        String namespaceId = (headerNs != null && !headerNs.isBlank())
                ? headerNs.trim()
                : (paramNs != null && !paramNs.isBlank() ? paramNs.trim() : "default");

        String headerTenant = request.getHeader(GatewayForwarder.HEADER_TENANT);
        String paramTenant = request.getParameter("tenant");
        String tenantId = (headerTenant != null && !headerTenant.isBlank())
                ? headerTenant.trim()
                : (paramTenant != null && !paramTenant.isBlank() ? paramTenant.trim() : null);

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

        byte[] body = request.getInputStream().readAllBytes();
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
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Gateway routing failure: " + e.getMessage());
        }
    }
}
