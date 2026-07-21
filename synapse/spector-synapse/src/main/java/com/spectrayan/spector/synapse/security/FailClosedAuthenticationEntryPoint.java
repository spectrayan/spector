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
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.AuthenticationEntryPoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Fail-closed {@link AuthenticationEntryPoint} for the Spring Security filter chain (Requirement
 * 19).
 *
 * <p>Invoked whenever a request to a protected path lacks a usable {@code Authentication} — either
 * because no credentials were presented (the {@code ExceptionTranslationFilter} path) or because a
 * presented bearer token failed validation (the OAuth2 Resource Server path). In every case the
 * response is a uniform HTTP {@code 401} carrying a minimal JSON body of the shape
 * {@code {"error":"..."}} that discloses no secret, raw API key, token value, or account-existence
 * signal.</p>
 *
 * <p>The response message is one of three fixed, non-enumerating constants derived solely from the
 * <em>class/kind</em> of {@link AuthenticationException} (never from user-supplied content):</p>
 * <ul>
 *   <li>{@value #MSG_TOKEN_EXPIRED} — the bearer token was rejected as expired
 *       (Requirements 3.4, 4.7);</li>
 *   <li>{@value #MSG_INVALID_CREDENTIALS} — a bearer token or API key was presented but is invalid
 *       (bad signature, wrong issuer, malformed, missing {@code sub}) (Requirements 3.3, 3.5, 3.6,
 *       4.4, 4.6);</li>
 *   <li>{@value #MSG_AUTH_REQUIRED} — no usable credential was presented on a protected path, or an
 *       {@link OAuth2Error} description could not be classified more precisely (Requirement 6.3).
 *       This constant is also the fail-closed outcome when the authentication data store is
 *       unavailable during filter-chain authentication (Requirement 19.5): the DAO provider surfaces
 *       that as an {@code AuthenticationException}, which lands here as a {@code 401} and mutates no
 *       user data.</li>
 * </ul>
 *
 * <p>All logging is at DEBUG and never includes credentials, tokens, or raw API keys
 * (Requirement 19.6).</p>
 */
public final class FailClosedAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(FailClosedAuthenticationEntryPoint.class);

    /** Uniform message for a missing/unclassifiable credential on a protected path. */
    static final String MSG_AUTH_REQUIRED = "Authentication required";

    /** Uniform message for an invalid bearer token or API key (no enumeration). */
    static final String MSG_INVALID_CREDENTIALS = "Invalid credentials";

    /** Uniform message for an expired bearer token. */
    static final String MSG_TOKEN_EXPIRED = "Token expired";

    /** OAuth2 error code emitted by the resource server for any token-validation failure. */
    private static final String INVALID_TOKEN = "invalid_token";

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String message = classify(authException);
        // DEBUG only; never echo the Authorization header, token, or API key value (Req 19.6).
        log.debug("[Auth] Denying unauthenticated request to {} -> 401 ({})",
                request.getRequestURI(), message);
        writeUnauthorized(response, message);
    }

    /**
     * Maps an {@link AuthenticationException} to one of the three fixed messages using only the
     * exception kind and (for OAuth2 token failures) the standard error code/description — never any
     * user-supplied or secret content.
     */
    private static String classify(AuthenticationException authException) {
        if (authException instanceof OAuth2AuthenticationException oauth2) {
            OAuth2Error error = oauth2.getError();
            if (error != null && INVALID_TOKEN.equals(error.getErrorCode())) {
                String description = error.getDescription();
                if (description != null && description.toLowerCase().contains("expired")) {
                    return MSG_TOKEN_EXPIRED;
                }
                return MSG_INVALID_CREDENTIALS;
            }
            return MSG_INVALID_CREDENTIALS;
        }
        return MSG_AUTH_REQUIRED;
    }

    /**
     * Writes a uniform {@code 401} JSON body {@code {"error":"<message>"}}. The message is always one
     * of the fixed constants above, so no escaping of dynamic content is required.
     */
    static void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
