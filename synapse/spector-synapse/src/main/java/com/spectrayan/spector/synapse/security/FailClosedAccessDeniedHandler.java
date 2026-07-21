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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Fail-closed {@link AccessDeniedHandler} for the Spring Security filter chain (Requirement 6.4,
 * 19.1).
 *
 * <p>Invoked when a request carries a valid {@code Authentication} but the principal lacks a scope
 * or role authority required by the target endpoint. The response is a uniform HTTP {@code 403}
 * carrying a minimal JSON body {@code {"error":"Forbidden"}} that discloses no secret, no required
 * authority, and no other detail that could aid privilege probing (Requirement 19.6).</p>
 *
 * <p>Logging is at DEBUG only and never includes credentials, tokens, or raw API keys.</p>
 */
public final class FailClosedAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(FailClosedAccessDeniedHandler.class);

    /** Uniform message for an authenticated-but-insufficient request. */
    static final String MSG_FORBIDDEN = "Forbidden";

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        // DEBUG only; never echo the required authority or any credential (Req 19.6).
        log.debug("[Auth] Denying request to {} for insufficient authority -> 403",
                request.getRequestURI());
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"" + MSG_FORBIDDEN + "\"}");
    }
}
