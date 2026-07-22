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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.synapse.memory.UserMemoryRegistry;
import com.spectrayan.spector.synapse.security.SecurityUtils;

/**
 * Request-thread holder that routes an MCP tool invocation to the caller's per-user
 * {@link SpectorMemory} namespace.
 *
 * <p>MCP tools are executed synchronously on the servlet request thread
 * ({@code transport → SyncToolSpecification lambda → mcpTool.execute(null, args)}) and resolve
 * their {@link SpectorMemory} independently. This holder lets the invocation site bind the memory
 * resolved for the authenticated caller — via {@link UserMemoryRegistry#resolveForCurrentRequest()}
 * on that same thread — so a memory-aware tool operates exclusively on that user's namespace,
 * regardless of any client-supplied {@code namespace}/{@code workspace_id}/{@code agent_id} hints
 * (those never widen scope to another user; the effective root is always the caller's namespace).</p>
 *
 * <h3>Resolution and deny semantics</h3>
 * <ul>
 *   <li>When {@code spector.auth.enabled=false} or the principal is anonymous, the registry returns
 *       the single shared instance — byte-for-byte the legacy single-user behavior (also the stdio
 *       path, which never binds through this holder).</li>
 *   <li>When auth is enabled and no resolvable authenticated context is present, the call is
 *       denied with {@link DenyReason#AUTH_REQUIRED} and nothing is bound.</li>
 *   <li>When per-user memory resolution fails, the call is denied with
 *       {@link DenyReason#RESOLUTION_FAILED}, nothing is bound, and no memory is modified — the
 *       registry never falls back to the shared or another user's instance.</li>
 * </ul>
 *
 * <p>The bound reference is confined to the current thread. Callers <strong>must</strong>
 * {@link #clear()} in a {@code finally} block after the tool completes so the thread-local never
 * leaks across pooled request threads.</p>
 */
public final class McpRequestMemory {

    private static final Logger log = LoggerFactory.getLogger(McpRequestMemory.class);

    private static final ThreadLocal<SpectorMemory> CURRENT = new ThreadLocal<>();

    private McpRequestMemory() {
    }

    /** Why an MCP invocation was denied before the tool ran. */
    public enum DenyReason {
        /** Auth is enabled but the request carries no resolvable authenticated security context. */
        AUTH_REQUIRED,
        /** Per-user memory resolution or lazy construction failed; the call fails closed. */
        RESOLUTION_FAILED
    }

    /**
     * Resolves the caller's memory on the current (request/servlet) thread and binds it for the
     * duration of the invocation. Reads {@link SecurityContextHolder} via {@link SecurityUtils} and
     * {@link UserMemoryRegistry}; never call this from an asynchronous task.
     *
     * @param registry    the per-user memory registry
     * @param authEnabled whether {@code spector.auth.enabled} is {@code true}
     * @return {@link Optional#empty()} when a memory instance was resolved and bound; otherwise the
     *         {@link DenyReason} describing why the call must be denied (nothing is bound)
     */
    public static Optional<DenyReason> bindForCurrentRequest(UserMemoryRegistry registry, boolean authEnabled) {
        if (authEnabled && !SecurityUtils.isAuthenticated()) {
            return Optional.of(DenyReason.AUTH_REQUIRED);
        }
        try {
            CURRENT.set(registry.resolveForCurrentRequest());
            return Optional.empty();
        } catch (RuntimeException e) {
            clear();
            // Never echo the resolved namespace/identifier; message stays generic.
            log.warn("[McpRequestMemory] per-user memory resolution failed; denying MCP call: {}", e.getMessage());
            log.debug("[McpRequestMemory] resolution failure detail (id withheld)", e);
            return Optional.of(DenyReason.RESOLUTION_FAILED);
        }
    }

    /**
     * The memory bound for the current request thread, or {@code null} when none is bound (e.g. the
     * stdio transport or any non-servlet caller). Memory-aware tools consult this first and fall
     * back to their own shared provider when it is {@code null}.
     *
     * @return the request-scoped {@link SpectorMemory}, or {@code null}
     */
    public static SpectorMemory current() {
        return CURRENT.get();
    }

    /** Removes any memory bound to the current thread. Must be invoked in a {@code finally} block. */
    public static void clear() {
        CURRENT.remove();
    }

    /**
     * Human-readable tool-error content for a deny reason.
     *
     * @param reason the deny reason
     * @return a message suitable for an MCP tool error result
     */
    public static String message(DenyReason reason) {
        return switch (reason) {
            case AUTH_REQUIRED ->
                    "Authentication is required to invoke MCP tools over /mcp.";
            case RESOLUTION_FAILED ->
                    "Memory resolution failed; the MCP call was denied and no memory was modified.";
        };
    }
}
