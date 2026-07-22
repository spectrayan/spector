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

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import com.spectrayan.spector.memory.StorageLayout;
import com.spectrayan.spector.synapse.memory.MemoryDto.ErrorResponse;

/**
 * Unit tests for the fail-closed error handling surface (Requirement 19):
 * {@link FailClosedAuthenticationEntryPoint}, {@link FailClosedAccessDeniedHandler}, and
 * {@link AuthExceptionHandler}.
 */
class FailClosedErrorHandlingTest {

    // ── FailClosedAuthenticationEntryPoint (401) ──────────────────────────────

    @Test
    void missingCredentialsYieldUniform401AuthenticationRequired() throws Exception {
        MockHttpServletResponse response = commence(
                new InsufficientAuthenticationException("Full authentication is required"));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"Authentication required\"}");
    }

    @Test
    void invalidBearerTokenYields401InvalidCredentials() throws Exception {
        OAuth2Error error = new OAuth2Error(
                "invalid_token", "An error occurred while attempting to decode the Jwt: bad signature", null);
        MockHttpServletResponse response = commence(new OAuth2AuthenticationException(error));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"Invalid credentials\"}");
    }

    @Test
    void expiredBearerTokenYields401TokenExpired() throws Exception {
        OAuth2Error error = new OAuth2Error(
                "invalid_token", "An error occurred while attempting to decode the Jwt: Jwt expired at 2020-01-01", null);
        MockHttpServletResponse response = commence(new OAuth2AuthenticationException(error));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"Token expired\"}");
    }

    private static MockHttpServletResponse commence(
            org.springframework.security.core.AuthenticationException ex) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/memory");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new FailClosedAuthenticationEntryPoint().commence(request, response, ex);
        return response;
    }

    // ── FailClosedAccessDeniedHandler (403) ───────────────────────────────────

    @Test
    void insufficientAuthorityYields403Forbidden() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new FailClosedAccessDeniedHandler().handle(request, response,
                new AccessDeniedException("Access is denied"));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getContentType()).contains(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"Forbidden\"}");
    }

    // ── AuthExceptionHandler (400 / 401) ──────────────────────────────────────

    @Test
    void unsafeNamespaceIdentifierYields400WithoutEchoingRawValue() {
        // A real StorageLayout rejection for an identifier containing a path separator.
        String rawUnsafeId = "evil/../../secret";
        IllegalArgumentException ex = catchThrowableOfType(IllegalArgumentException.class,
                () -> StorageLayout.namespaceDirSharded(Path.of("base"), rawUnsafeId));
        assertThat(ex).isNotNull();

        ResponseEntity<ErrorResponse> result = new AuthExceptionHandler().handleIllegalArgument(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = result.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(400);
        // The raw identifier value must never appear in the response body (Req 19.4, 19.6).
        assertThat(body.message()).doesNotContain(rawUnsafeId);
        assertThat(body.message()).isEqualTo("Invalid namespace identifier");
    }

    @Test
    void unrelatedIllegalArgumentPreservesBadRequestMessage() {
        IllegalArgumentException ex = new IllegalArgumentException("limit must be positive");

        ResponseEntity<ErrorResponse> result = new AuthExceptionHandler().handleIllegalArgument(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().message()).isEqualTo("limit must be positive");
    }

    @Test
    void dataStoreUnavailableFailsClosedWith401AndNoSecretLeak() {
        DataAccessResourceFailureException ex = new DataAccessResourceFailureException(
                "could not connect to jdbc:h2:mem:secret with password hunter2");

        ResponseEntity<ErrorResponse> result = new AuthExceptionHandler().handleDataAccess(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ErrorResponse body = result.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(401);
        // The uniform body must not carry the underlying SQL/connection detail (Req 19.5, 19.6).
        assertThat(body.message()).isEqualTo("Authentication failed");
        assertThat(body.message()).doesNotContain("hunter2");
    }
}
