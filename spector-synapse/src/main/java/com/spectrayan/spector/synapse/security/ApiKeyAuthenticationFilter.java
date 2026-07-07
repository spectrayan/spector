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
package com.spectrayan.spector.synapse.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.spectrayan.spector.synapse.config.SynapseProperties;

import java.io.IOException;
import java.util.List;

/**
 * API key authentication filter.
 *
 * <p>Accepts keys via two mechanisms:</p>
 * <ul>
 *   <li>{@code Authorization: Bearer <api-key>}</li>
 *   <li>{@code X-API-Key: <api-key>}</li>
 * </ul>
 *
 * <p>Requests to non-API paths are passed through without authentication.</p>
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String API_KEY_HEADER = "X-API-Key";

    private final SynapseProperties props;

    public ApiKeyAuthenticationFilter(SynapseProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip non-API paths
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String apiKey = extractApiKey(request);
        if (apiKey != null && apiKey.equals(props.apiKey())) {
            var auth = new UsernamePasswordAuthenticationToken(
                    "api-client", null,
                    List.of(new SimpleGrantedAuthority("ROLE_API")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            log.debug("[Auth] API key authenticated for {}", path);
        }

        filterChain.doFilter(request, response);
    }

    private String extractApiKey(HttpServletRequest request) {
        // Try Authorization: Bearer <key>
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length()).trim();
        }

        // Try X-API-Key: <key>
        String xApiKey = request.getHeader(API_KEY_HEADER);
        if (xApiKey != null && !xApiKey.isBlank()) {
            return xApiKey.trim();
        }

        return null;
    }
}
