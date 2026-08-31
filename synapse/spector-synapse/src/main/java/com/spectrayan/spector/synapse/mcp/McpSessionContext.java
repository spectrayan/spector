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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages connection-scoped session state and the volatile active working set for MCP transports (ADR-0029 §6.2, §16).
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Connection-scoped default namespace (set by {@code namespace_switch})</li>
 *   <li>Active working set (FIFO bounded to 100 items, persists across {@code namespace_switch})</li>
 *   <li>Thread fallback for stateless requests</li>
 * </ul>
 */
public final class McpSessionContext {

    private static final Logger log = LoggerFactory.getLogger(McpSessionContext.class);

    public static final int MAX_ACTIVE_WORKING_ITEMS = 100;

    /** Working memory record in the volatile session working set. */
    public record ActiveWorkingItem(String id, String text, Map<String, Object> metadata, long timestampMs) {}

    private static final ConcurrentHashMap<String, String> SESSION_DEFAULTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Deque<ActiveWorkingItem>> SESSION_WORKING_SETS = new ConcurrentHashMap<>();
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
     * Appends an item to the session's active volatile working set (FIFO bounded to 100).
     *
     * @param connectionId the connection or session identifier
     * @param item         the working memory item
     */
    public static void addWorkingItem(String connectionId, ActiveWorkingItem item) {
        if (connectionId == null || connectionId.isBlank()) {
            return;
        }
        SESSION_WORKING_SETS.compute(connectionId, (key, queue) -> {
            Deque<ActiveWorkingItem> q = queue != null ? queue : new ArrayDeque<>();
            synchronized (q) {
                if (q.size() >= MAX_ACTIVE_WORKING_ITEMS) {
                    q.pollFirst(); // FIFO eviction
                }
                q.addLast(item);
            }
            return q;
        });
    }

    /**
     * Gets a snapshot of the active working set for the given connection ID.
     *
     * @param connectionId the connection or session identifier
     * @return ordered list of active working items
     */
    public static List<ActiveWorkingItem> getWorkingItems(String connectionId) {
        if (connectionId == null || connectionId.isBlank()) {
            return List.of();
        }
        Deque<ActiveWorkingItem> q = SESSION_WORKING_SETS.get(connectionId);
        if (q == null) {
            return List.of();
        }
        synchronized (q) {
            return List.copyOf(q);
        }
    }

    /**
     * Clears the active working set for the specified connection ID.
     */
    public static void clearWorkingItems(String connectionId) {
        if (connectionId != null) {
            SESSION_WORKING_SETS.remove(connectionId);
        }
    }

    /**
     * Clears session state when an MCP connection terminates.
     *
     * @param connectionId the MCP connection or session identifier
     */
    public static void clearSession(String connectionId) {
        if (connectionId != null) {
            SESSION_DEFAULTS.remove(connectionId);
            SESSION_WORKING_SETS.remove(connectionId);
            log.debug("[McpSessionContext] cleared session: {}", connectionId);
        }
        FALLBACK_DEFAULT.remove();
    }

    /** Clears thread fallback state. */
    public static void clearFallback() {
        FALLBACK_DEFAULT.remove();
    }
}
