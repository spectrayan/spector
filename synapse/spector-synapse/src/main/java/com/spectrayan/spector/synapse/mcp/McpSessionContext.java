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
package com.spectrayan.spector.synapse.mcp;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages connection-scoped session state for MCP transports (ADR-0029 §6.2, §16).
 *
 * <p>{@code namespace_switch} sets the connection-scoped default on this context.
 * It is tied to the MCP connection/session lifecycle and dies with the session.
 * For stateless transports without a connection ID, a request-scoped fallback is used.</p>
 */
public final class McpSessionContext {

    private static final Logger log = LoggerFactory.getLogger(McpSessionContext.class);

    private static final ConcurrentHashMap<String, String> SESSION_DEFAULTS = new ConcurrentHashMap<>();
    private static final ThreadLocal<String> FALLBACK_DEFAULT = new ThreadLocal<>();

    private McpSessionContext() {
    }

    /**
     * Sets the default namespace slug or ID for the specified MCP connection/session.
     *
     * @param connectionId the MCP connection or session identifier, or null for fallback
     * @param slugOrId     the namespace slug or identifier
     */
    public static void setSessionDefault(String connectionId, String slugOrId) {
        if (connectionId != null && !connectionId.isBlank()) {
            SESSION_DEFAULTS.put(connectionId, slugOrId);
            log.debug("[McpSessionContext] set default namespace for session {}: {}", connectionId, slugOrId);
        } else {
            FALLBACK_DEFAULT.set(slugOrId);
            log.debug("[McpSessionContext] set default namespace on thread fallback: {}", slugOrId);
        }
    }

    /**
     * Gets the connection-scoped default namespace for the given connection ID, if any.
     *
     * @param connectionId the MCP connection or session identifier
     * @return optional containing the active default namespace slug or identifier
     */
    public static Optional<String> getSessionDefault(String connectionId) {
        if (connectionId != null && !connectionId.isBlank()) {
            String value = SESSION_DEFAULTS.get(connectionId);
            if (value != null) {
                return Optional.of(value);
            }
        }
        String fallback = FALLBACK_DEFAULT.get();
        return Optional.ofNullable(fallback);
    }

    /**
     * Clears session state when an MCP connection terminates.
     *
     * @param connectionId the MCP connection or session identifier
     */
    public static void clearSession(String connectionId) {
        if (connectionId != null) {
            SESSION_DEFAULTS.remove(connectionId);
            log.debug("[McpSessionContext] cleared session: {}", connectionId);
        }
        FALLBACK_DEFAULT.remove();
    }

    /** Clears thread fallback state. */
    public static void clearFallback() {
        FALLBACK_DEFAULT.remove();
    }
}
