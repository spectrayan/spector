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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.AuthProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * API key authentication filter.
 *
 * <p>Accepts keys via two mechanisms, preferring the {@code Authorization} header over
 * {@code X-API-Key} when both are present:</p>
 * <ul>
 *   <li>{@code Authorization: Bearer <api-key>}</li>
 *   <li>{@code X-API-Key: <api-key>}</li>
 * </ul>
 *
 * <p>Only requests whose path starts with {@code /api/} or {@code /mcp} are inspected; all other
 * paths pass straight through. The filter always continues the chain regardless of whether an
 * {@code Authentication} was bound.</p>
 *
 * <p>Behavior depends on {@code spector.auth.enabled}:</p>
 * <ul>
 *   <li><strong>disabled</strong> (legacy, backward-compatible): if the extracted key equals the
 *       configured shared key ({@code spector.api-key}), bind an {@code Authentication} carrying
 *       {@code ROLE_API}; otherwise leave the context unauthenticated.</li>
 *   <li><strong>enabled</strong>: compute {@code SHA-256} of the raw key and look up a non-revoked,
 *       non-expired row via {@link ApiKeyStore#findActiveByHash(String)}. On a match, bind an
 *       {@code Authentication} whose principal is the owning {@code userId} and whose authorities
 *       are the key's scopes mapped to {@code SCOPE_*}; otherwise leave the context
 *       unauthenticated (downstream authorization yields 401/403).</li>
 * </ul>
 *
 * <p>Raw key values are never logged, including on validation-failure and exception paths.</p>
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String SCOPE_PREFIX = "SCOPE_";

    /** Maximum accepted length of a presented API key value (inclusive). */
    private static final int MAX_KEY_LENGTH = 512;

    private final SynapseProperties props;
    private final AuthProperties auth;
    private final ApiKeyStore apiKeyStore;

    public ApiKeyAuthenticationFilter(SynapseProperties props, ApiKeyStore apiKeyStore) {
        this.props = props;
        this.auth = props.auth();
        this.apiKeyStore = apiKeyStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip non-API and non-MCP paths (unchanged skip logic).
        if (!path.startsWith("/api/") && !path.startsWith("/mcp")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String apiKey = extractApiKey(request);
            if (apiKey != null) {
                if (auth != null && auth.enabled()) {
                    // codeql[java/user-controlled-bypass] - Filter extracts presented key and delegates to authentication method
                    authenticatePerUserKey(apiKey, path);
                } else {
                    // codeql[java/user-controlled-bypass] - Filter extracts presented key and delegates to legacy authentication method
                    authenticateLegacySharedKey(apiKey, path);
                }
            }
        } catch (RuntimeException e) {
            // Never log the raw key; leave the context unauthenticated and continue the chain.
            log.warn("[Auth] API key authentication failed for {}", path, e);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Legacy backward-compatible path used when {@code spector.auth.enabled=false}: binds
     * {@code ROLE_API} only when the presented key equals the configured shared key.
     */
    private void authenticateLegacySharedKey(String apiKey, String path) {
        byte[] a = apiKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] b = props.apiKey() != null ? props.apiKey().getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0];
        if (java.security.MessageDigest.isEqual(a, b)) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    "api-client", null,
                    List.of(new SimpleGrantedAuthority("ROLE_API")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("[Auth] Legacy shared-key authenticated for {}", path);
        }
    }

    /**
     * Multi-user path used when {@code spector.auth.enabled=true}: hashes the raw key with SHA-256,
     * looks up a non-revoked, non-expired row, and on a match binds an {@code Authentication} whose
     * principal is the owning user id and whose authorities are the key's {@code SCOPE_*} scopes.
     */
    private void authenticatePerUserKey(String apiKey, String path) {
        String hash = ApiKeyStore.sha256Hex(apiKey);
        Optional<ApiKeyStore.ApiKeyRow> match = apiKeyStore.findActiveByHash(hash);
        if (match.isPresent()) {
            ApiKeyStore.ApiKeyRow row = match.get();
            var authentication = new UsernamePasswordAuthenticationToken(
                    row.userId(), null, scopeAuthorities(row.scopes()));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("[Auth] API key {} authenticated user {} for {}", row.keyId(), row.userId(), path);
        }
    }

    /**
     * Maps a set of scope strings to {@code SCOPE_}-prefixed {@link GrantedAuthority} instances.
     */
    private static List<GrantedAuthority> scopeAuthorities(Set<String> scopes) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (scopes != null) {
            for (String scope : scopes) {
                if (scope != null && !scope.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority(SCOPE_PREFIX + scope.trim()));
                }
            }
        }
        return authorities;
    }

    /**
     * Extracts the presented API key, preferring {@code Authorization: Bearer <key>} over
     * {@code X-API-Key}. Only values whose length is between 1 and {@value #MAX_KEY_LENGTH}
     * characters (inclusive) are accepted; anything else yields {@code null}.
     */
    private String extractApiKey(HttpServletRequest request) {
        // Prefer Authorization: Bearer <key>.
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String candidate = authHeader.substring(BEARER_PREFIX.length()).trim();
            return acceptable(candidate) ? candidate : null;
        }

        // Fall back to X-API-Key: <key>.
        String xApiKey = request.getHeader(API_KEY_HEADER);
        if (xApiKey != null) {
            String candidate = xApiKey.trim();
            return acceptable(candidate) ? candidate : null;
        }

        return null;
    }

    /**
     * Accepts only key values with length in the range 1..{@value #MAX_KEY_LENGTH}.
     */
    private static boolean acceptable(String candidate) {
        return !candidate.isEmpty() && candidate.length() <= MAX_KEY_LENGTH;
    }
}
