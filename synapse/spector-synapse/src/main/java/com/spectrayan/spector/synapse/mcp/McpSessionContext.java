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
    private static final ConcurrentHashMap<String, String> SESSION_OWNERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> SESSION_EPOCHS = new ConcurrentHashMap<>();
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
     * Binds an MCP session to an authoritative owner node and epoch for its lifetime (Req R10.1).
     *
     * @param connectionId the MCP session/connection ID
     * @param ownerNodeId  the owner node ID
     * @param epoch        the epoch at bind time
     */
    public static void bindSessionOwner(String connectionId, String ownerNodeId, long epoch) {
        if (connectionId != null && !connectionId.isBlank() && ownerNodeId != null) {
            SESSION_OWNERS.put(connectionId, ownerNodeId);
            SESSION_EPOCHS.put(connectionId, epoch);
            log.debug("[McpSessionContext] bound session {} to owner node '{}' at epoch {}", connectionId, ownerNodeId, epoch);
        }
    }

    /**
     * Verifies that the session's affinity matches the expected owner node and epoch.
     * If the owner or epoch has diverged mid-session, clears session state and returns false (Req R10.2).
     *
     * @param connectionId  the MCP session/connection ID
     * @param expectedOwner the current resolved owner
     * @param currentEpoch  the current resolved epoch
     * @return true if affinity is intact, false if mid-session owner change occurred
     */
    public static boolean checkSessionAffinity(String connectionId, String expectedOwner, long currentEpoch) {
        if (connectionId == null || connectionId.isBlank()) {
            return true;
        }
        String boundOwner = SESSION_OWNERS.get(connectionId);
        Long boundEpoch = SESSION_EPOCHS.get(connectionId);
        if (boundOwner == null) {
            bindSessionOwner(connectionId, expectedOwner, currentEpoch);
            return true;
        }
        if (!boundOwner.equals(expectedOwner) || (boundEpoch != null && boundEpoch < currentEpoch)) {
            log.warn("[McpSessionContext] Mid-session owner change detected for session {}: bound to '{}:{}' but now '{}:{}'; terminating session state (Req R10.2)",
                    connectionId, boundOwner, boundEpoch, expectedOwner, currentEpoch);
            clearSession(connectionId);
            return false;
        }
        return true;
    }

    public static Optional<String> getSessionOwner(String connectionId) {
        return Optional.ofNullable(connectionId != null ? SESSION_OWNERS.get(connectionId) : null);
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
            SESSION_OWNERS.remove(connectionId);
            SESSION_EPOCHS.remove(connectionId);
            log.debug("[McpSessionContext] cleared session: {}", connectionId);
        }
        FALLBACK_DEFAULT.remove();
    }

    /** Clears thread fallback state. */
    public static void clearFallback() {
        FALLBACK_DEFAULT.remove();
    }
}
