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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.config.SynapseProperties.AuthProperties;
import com.spectrayan.spector.synapse.security.ApiKeyStore.ApiKeyRow;

/**
 * Unit tests for {@link ApiKeyAuthenticationFilter}.
 *
 * <p>Exercises both the legacy shared-key path ({@code spector.auth.enabled=false}) and the
 * multi-user hashed-key path ({@code spector.auth.enabled=true}) using Spring's servlet mocks and
 * a mocked {@link ApiKeyStore}. Each test clears the {@link SecurityContextHolder} afterwards so
 * that the per-request binding never leaks across tests.</p>
 *
 * <p>Covers Requirements 1.2, 1.3, 5.1, 5.2, 5.4, 5.5, 5.6, 5.7, 5.8.</p>
 */
class ApiKeyAuthenticationFilterTest {

    private static final String SHARED_KEY = "spector-shared-secret";
    private static final String BEARER = "Bearer ";
    private static final String X_API_KEY = "X-API-Key";
    private static final String AUTHORIZATION = "Authorization";

    private final ApiKeyStore apiKeyStore = mock(ApiKeyStore.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────────

    private static SynapseProperties props(boolean authEnabled) {
        AuthProperties auth = new AuthProperties(authEnabled, null, null, null, null, null, null, null);
        return new SynapseProperties(0, SHARED_KEY, null, null, null, null, auth);
    }

    private ApiKeyAuthenticationFilter filter(boolean authEnabled) {
        return new ApiKeyAuthenticationFilter(props(authEnabled), apiKeyStore);
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
    }

    private static ApiKeyRow activeRow(String userId, Set<String> scopes) {
        return new ApiKeyRow("key_0000000001", userId, "unused-hash", scopes,
                null, false, Instant.now());
    }

    private static Authentication currentAuth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private static List<String> currentAuthorities() {
        return currentAuth().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    /** Runs the filter and asserts the chain was continued (Req 5.8). */
    private MockFilterChain run(ApiKeyAuthenticationFilter filter, MockHttpServletRequest request)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(chain.getRequest())
                .as("filter chain must always continue")
                .isSameAs(request);
        return chain;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Legacy shared-key path (auth disabled) — Reqs 1.2, 1.3, 5.6
    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("legacy shared-key path (auth disabled)")
    class LegacySharedKey {

        @Test
        @DisplayName("matching shared key binds ROLE_API and continues the chain (Req 1.2)")
        void matchingSharedKeyBindsRoleApi() throws Exception {
            MockHttpServletRequest request = request("/api/v1/memory");
            request.addHeader(AUTHORIZATION, BEARER + SHARED_KEY);

            run(filter(false), request);

            assertThat(currentAuth()).isNotNull();
            assertThat(currentAuth().getName()).isEqualTo("api-client");
            assertThat(currentAuthorities()).containsExactly("ROLE_API");
            // Legacy path never consults the per-user store (Req 5.6).
            verify(apiKeyStore, never()).findActiveByHash(anyString());
        }

        @Test
        @DisplayName("non-matching key leaves context unauthenticated but continues (Req 1.3)")
        void nonMatchingKeyLeavesUnauthenticated() throws Exception {
            MockHttpServletRequest request = request("/api/v1/memory");
            request.addHeader(AUTHORIZATION, BEARER + "not-the-shared-key");

            run(filter(false), request);

            assertThat(currentAuth()).isNull();
            verify(apiKeyStore, never()).findActiveByHash(anyString());
        }

        @Test
        @DisplayName("X-API-Key carrying the shared key authenticates (Req 5.2)")
        void xApiKeyWithSharedKeyAuthenticates() throws Exception {
            MockHttpServletRequest request = request("/api/v1/memory");
            request.addHeader(X_API_KEY, SHARED_KEY);

            run(filter(false), request);

            assertThat(currentAuth()).isNotNull();
            assertThat(currentAuthorities()).containsExactly("ROLE_API");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Header precedence — Reqs 5.1, 5.2, 5.5
    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("header precedence")
    class HeaderPrecedence {

        @Test
        @DisplayName("Authorization: Bearer is preferred over X-API-Key when both present (Req 5.1)")
        void bearerPreferredOverXApiKey() throws Exception {
            // Bearer carries the valid shared key; X-API-Key is garbage. Bearer must win.
            MockHttpServletRequest request = request("/api/v1/memory");
            request.addHeader(AUTHORIZATION, BEARER + SHARED_KEY);
            request.addHeader(X_API_KEY, "garbage-value");

            run(filter(false), request);

            assertThat(currentAuth()).isNotNull();
            assertThat(currentAuthorities()).containsExactly("ROLE_API");
        }

        @Test
        @DisplayName("X-API-Key is ignored when Authorization: Bearer is present (Req 5.1)")
        void xApiKeyIgnoredWhenBearerPresent() throws Exception {
            // Bearer carries garbage; X-API-Key carries the valid shared key. Bearer still wins,
            // so the request must remain unauthenticated (X-API-Key is ignored).
            MockHttpServletRequest request = request("/api/v1/memory");
            request.addHeader(AUTHORIZATION, BEARER + "garbage-value");
            request.addHeader(X_API_KEY, SHARED_KEY);

            run(filter(false), request);

            assertThat(currentAuth()).isNull();
        }

        @Test
        @DisplayName("neither header present leaves context unauthenticated (Req 5.5)")
        void neitherHeaderLeavesUnauthenticated() throws Exception {
            MockHttpServletRequest request = request("/api/v1/memory");

            run(filter(false), request);

            assertThat(currentAuth()).isNull();
            verify(apiKeyStore, never()).findActiveByHash(anyString());
        }

        @Test
        @DisplayName("neither header present leaves context unauthenticated when enabled (Req 5.5)")
        void neitherHeaderUnauthenticatedWhenEnabled() throws Exception {
            MockHttpServletRequest request = request("/api/v1/memory");

            run(filter(true), request);

            assertThat(currentAuth()).isNull();
            verify(apiKeyStore, never()).findActiveByHash(anyString());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Multi-user hashed-key path (auth enabled) — Reqs 5.3, 5.4
    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("per-user hashed-key path (auth enabled)")
    class PerUserKey {

        @Test
        @DisplayName("SHA-256 match binds userId principal with SCOPE_* authorities (Req 5.3)")
        void matchBindsUserIdAndScopes() throws Exception {
            String rawKey = "raw-user-key-abc";
            String hash = ApiKeyStore.sha256Hex(rawKey);
            when(apiKeyStore.findActiveByHash(hash))
                    .thenReturn(Optional.of(activeRow("user_00000001", Set.of("memory:read", "memory:write"))));

            MockHttpServletRequest request = request("/api/v1/memory");
            request.addHeader(AUTHORIZATION, BEARER + rawKey);

            run(filter(true), request);

            assertThat(currentAuth()).isNotNull();
            assertThat(currentAuth().getName()).isEqualTo("user_00000001");
            assertThat(currentAuthorities())
                    .containsExactlyInAnyOrder("SCOPE_memory:read", "SCOPE_memory:write");
            verify(apiKeyStore).findActiveByHash(hash);
        }

        @Test
        @DisplayName("match on the /mcp path also authenticates (Req 5.3)")
        void matchOnMcpPathAuthenticates() throws Exception {
            String rawKey = "raw-mcp-key";
            String hash = ApiKeyStore.sha256Hex(rawKey);
            when(apiKeyStore.findActiveByHash(hash))
                    .thenReturn(Optional.of(activeRow("user_mcp0001", Set.of("memory:read"))));

            MockHttpServletRequest request = request("/mcp");
            request.addHeader(X_API_KEY, rawKey);

            run(filter(true), request);

            assertThat(currentAuth()).isNotNull();
            assertThat(currentAuth().getName()).isEqualTo("user_mcp0001");
            assertThat(currentAuthorities()).containsExactly("SCOPE_memory:read");
        }

        @Test
        @DisplayName("unknown/revoked/expired key (no active row) leaves unauthenticated (Req 5.4)")
        void noActiveRowLeavesUnauthenticated() throws Exception {
            when(apiKeyStore.findActiveByHash(anyString())).thenReturn(Optional.empty());

            MockHttpServletRequest request = request("/api/v1/memory");
            request.addHeader(AUTHORIZATION, BEARER + "unknown-key");

            run(filter(true), request);

            assertThat(currentAuth()).isNull();
            verify(apiKeyStore).findActiveByHash(ApiKeyStore.sha256Hex("unknown-key"));
        }

        @Test
        @DisplayName("store failure is swallowed; context stays unauthenticated and chain continues (Reqs 5.7, 5.8)")
        void storeFailureLeavesUnauthenticatedAndContinues() throws Exception {
            when(apiKeyStore.findActiveByHash(anyString()))
                    .thenThrow(new RuntimeException("db down"));

            MockHttpServletRequest request = request("/api/v1/memory");
            request.addHeader(AUTHORIZATION, BEARER + "some-key");

            // run(...) asserts the chain continued despite the exception.
            run(filter(true), request);

            assertThat(currentAuth()).isNull();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Value-length bounds — Req 5.2
    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("value-length bounds (1..512)")
    class ValueLength {

        @Test
        @DisplayName("over-length key (>512) is rejected without a store lookup (Req 5.2)")
        void overLengthKeyRejected() throws Exception {
            String overLength = "a".repeat(513);
            MockHttpServletRequest request = request("/api/v1/memory");
            request.addHeader(AUTHORIZATION, BEARER + overLength);

            run(filter(true), request);

            assertThat(currentAuth()).isNull();
            verify(apiKeyStore, never()).findActiveByHash(anyString());
        }

        @Test
        @DisplayName("exactly 512-char key is accepted and looked up (Req 5.2)")
        void maxLengthKeyAccepted() throws Exception {
            String maxLength = "a".repeat(512);
            String hash = ApiKeyStore.sha256Hex(maxLength);
            when(apiKeyStore.findActiveByHash(hash))
                    .thenReturn(Optional.of(activeRow("user_maxlen01", Set.of("memory:read"))));

            MockHttpServletRequest request = request("/api/v1/memory");
            request.addHeader(AUTHORIZATION, BEARER + maxLength);

            run(filter(true), request);

            assertThat(currentAuth()).isNotNull();
            assertThat(currentAuth().getName()).isEqualTo("user_maxlen01");
            verify(apiKeyStore).findActiveByHash(hash);
        }

        @Test
        @DisplayName("empty key value is rejected without a store lookup (Req 5.2)")
        void emptyKeyRejected() throws Exception {
            MockHttpServletRequest request = request("/api/v1/memory");
            request.addHeader(X_API_KEY, "");

            run(filter(true), request);

            assertThat(currentAuth()).isNull();
            verify(apiKeyStore, never()).findActiveByHash(anyString());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Path scoping — only /api/ and /mcp are inspected (Req 5.8 skip behavior)
    // ─────────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("path scoping")
    class PathScoping {

        @Test
        @DisplayName("non /api,/mcp path is skipped entirely (no lookup, no binding)")
        void unrelatedPathSkipped() throws Exception {
            String rawKey = "raw-user-key-abc";
            MockHttpServletRequest request = request("/actuator/health");
            request.addHeader(AUTHORIZATION, BEARER + rawKey);

            run(filter(true), request);

            assertThat(currentAuth()).isNull();
            verify(apiKeyStore, never()).findActiveByHash(anyString());
        }

        @Test
        @DisplayName("non /api,/mcp path is skipped in legacy mode too")
        void unrelatedPathSkippedLegacy() throws Exception {
            MockHttpServletRequest request = request("/actuator/health");
            request.addHeader(AUTHORIZATION, BEARER + SHARED_KEY);

            run(filter(false), request);

            assertThat(currentAuth()).isNull();
        }
    }
}
