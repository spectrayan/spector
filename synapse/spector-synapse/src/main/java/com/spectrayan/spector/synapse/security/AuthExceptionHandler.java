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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.spectrayan.spector.synapse.memory.MemoryDto.ErrorResponse;

/**
 * Fail-closed exception handling for the authentication and per-user memory-resolution surfaces
 * (Requirement 19).
 *
 * <p>This advice is deliberately <strong>narrowly scoped</strong> to the packages where credential
 * validation and per-user namespace resolution occur — the {@code security}, {@code memory}, and
 * {@code mcp} packages — so it never broadly intercepts unrelated {@link IllegalArgumentException}s
 * raised by other controllers. It is ordered ahead of the application-wide
 * {@code GlobalExceptionHandler} ({@link Ordered#HIGHEST_PRECEDENCE}) so that, for controllers in
 * those packages, the two mappings below take effect while every other exception continues to fall
 * through to the global handler.</p>
 *
 * <ul>
 *   <li><strong>Unsafe namespace identifier → {@code 400}.</strong> When a resolved {@code User_Id}
 *       yields an unsafe namespace identifier, {@code StorageLayout.namespaceDirSharded(...)} throws
 *       an {@link IllegalArgumentException} whose message begins with
 *       {@value #UNSAFE_NAMESPACE_PREFIX} <em>before</em> any path is resolved or any filesystem
 *       mutation occurs. Such failures are mapped to HTTP {@code 400}, logged at <strong>DEBUG</strong>
 *       with the exception's own (value-free) diagnostic message but <strong>never</strong> the raw
 *       identifier, and answered with a generic body (Requirement 19.4). Any other
 *       {@code IllegalArgumentException} from these packages preserves the pre-existing {@code 400}
 *       "Bad Request" behavior so no unrelated contract changes.</li>
 *   <li><strong>Auth data store unavailable → {@code 401}.</strong> A
 *       {@link DataAccessException} surfacing on a request thread (the authentication data store is
 *       unreachable) fails the request closed with HTTP {@code 401}, logged via SLF4J at
 *       <strong>error</strong> level, and never mutates the requesting user's stored data
 *       (Requirement 19.5). The message excludes the SQL statement, connection string, and every
 *       other secret (Requirement 19.6).</li>
 * </ul>
 *
 * <p>Neither mapping ever returns another user's data or routes to another user's namespace, and no
 * response carries a secret or raw API key (Requirements 19.1, 19.6).</p>
 */
@RestControllerAdvice(basePackages = {
        "com.spectrayan.spector.synapse.security",
        "com.spectrayan.spector.synapse.memory",
        "com.spectrayan.spector.synapse.mcp"
})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthExceptionHandler.class);

    /**
     * Message prefix produced by {@code StorageLayout.validateNamespaceId(...)} for every unsafe
     * identifier (null/blank, over-length, or containing an illegal character). The identifier's raw
     * value is intentionally absent from that message, so matching on this prefix lets us map the
     * failure without ever reading the raw value.
     */
    static final String UNSAFE_NAMESPACE_PREFIX = "Invalid namespace identifier";

    /** Generic, value-free body returned for an unsafe namespace identifier. */
    static final String MSG_INVALID_NAMESPACE = "Invalid namespace identifier";

    /** Uniform body returned when authentication cannot be completed (store unavailable). */
    static final String MSG_AUTH_FAILED = "Authentication failed";

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            org.springframework.web.bind.MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getDefaultMessage())
                .findFirst()
                .orElse("Validation failure");
        log.warn("[Auth] Validation failed for request: {}", detail);
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(400, "Bad Request", detail));
    }

    /**
     * Maps namespace-safety violations to a fail-closed {@code 400} without echoing the raw
     * identifier (Requirement 19.4); preserves the legacy {@code 400} behavior for any other
     * {@link IllegalArgumentException} raised within the scoped packages.
     *
     * @param ex the raised argument exception
     * @return a {@code 400} response with a value-free body
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        String message = ex.getMessage();
        if (message != null && message.startsWith(UNSAFE_NAMESPACE_PREFIX)) {
            // DEBUG only; ex.getMessage() carries index/code-point/length diagnostics but NOT the
            // raw identifier value, so it is safe to log while the raw value is withheld (Req 19.4).
            log.debug("[Auth] Rejecting unsafe namespace identifier (raw value withheld): {}", message);
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ErrorResponse(400, "Bad Request", MSG_INVALID_NAMESPACE));
        }
        // Unrelated bad request within the scoped packages: preserve prior contract.
        log.warn("[Auth] Bad request: {}", message);
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(400, "Bad Request", message));
    }

    /**
     * Fails closed with {@code 401} when the authentication data store is unavailable, logging via
     * SLF4J without leaking the SQL statement, connection details, or any other secret
     * (Requirements 19.5, 19.6).
     *
     * @param ex the data-access failure
     * @return a {@code 401} response with a uniform, secret-free body
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccess(DataAccessException ex) {
        // Log the exception type only; DataAccessException messages can embed SQL/connection detail,
        // so we must not forward getMessage() to the response and log defensively (Req 19.5, 19.6).
        log.error("[Auth] Authentication data store unavailable: {} -> failing closed with 401",
                ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(401, "Unauthorized", MSG_AUTH_FAILED));
    }
}
