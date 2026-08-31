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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceAccessDeniedException;
import com.spectrayan.spector.synapse.catalog.exception.TokenNamespaceLockedException;
import com.spectrayan.spector.synapse.memory.MemoryBinding;
import com.spectrayan.spector.synapse.memory.MemoryRegistry;
import com.spectrayan.spector.synapse.memory.MemoryRequestBinder;
import com.spectrayan.spector.synapse.security.SecurityUtils;

/**
 * Request-thread holder that routes an MCP tool invocation to the caller's per-user
 * {@link MemoryBinding} and {@link SpectorMemory} namespace (ADR-0029 §16, §24).
 *
 * <p>MCP tools are executed synchronously on the servlet request thread
 * ({@code transport → SyncToolSpecification lambda → mcpTool.execute(args)}) and resolve
 * their {@link MemoryBinding} via {@link MemoryRequestBinder}. This holder lets the invocation site
 * bind the memory, lease, and identity context resolved for the authenticated caller on that same
 * thread — so a memory-aware tool operates exclusively on that user's authorized namespace,
 * with full soul stack, bias overlay, and org intersection.</p>
 */
public final class McpRequestMemory {

    private static final Logger log = LoggerFactory.getLogger(McpRequestMemory.class);

    private static final ThreadLocal<MemoryBinding> CURRENT_BINDING = new ThreadLocal<>();
    private static final ThreadLocal<MemoryRequestBinder> CURRENT_BINDER = new ThreadLocal<>();

    private McpRequestMemory() {
    }

    /** Why an MCP invocation was denied before the tool ran. */
    public enum DenyReason {
        /** Auth is enabled but the request carries no resolvable authenticated security context. */
        AUTH_REQUIRED,
        /** Caller does not have sufficient grant permissions to access the requested namespace. */
        ACCESS_DENIED,
        /** Per-user memory resolution or lazy construction failed; the call fails closed. */
        RESOLUTION_FAILED,
        /** Wildcard '*' was supplied as a namespace selector (ADR-0029 Invariant 7). */
        WILDCARD_REJECTED,
        /** Token namespace allow-set locked and request is outside allowed namespaces. */
        TOKEN_LOCKED
    }

    /**
     * Resolves the caller's memory on the current (request/servlet) thread and binds it for the
     * duration of the invocation using {@link MemoryRequestBinder}.
     */
    public static Optional<DenyReason> bindForCurrentRequest(MemoryRequestBinder binder, boolean authEnabled) {
        return bindForCurrentRequest(binder, authEnabled, null, null);
    }

    /**
     * Resolves the caller's memory using {@link MemoryRequestBinder}.
     */
    public static Optional<DenyReason> bindForCurrentRequest(
            MemoryRequestBinder binder,
            boolean authEnabled,
            String namespaceSelector,
            String connectionId) {
        if (authEnabled && !SecurityUtils.isAuthenticated()) {
            return Optional.of(DenyReason.AUTH_REQUIRED);
        }

        if (namespaceSelector != null && "*".equals(namespaceSelector.trim())) {
            return Optional.of(DenyReason.WILDCARD_REJECTED);
        }

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String selector = (namespaceSelector != null && !namespaceSelector.isBlank())
                    ? namespaceSelector.trim()
                    : McpSessionContext.getSessionDefault(connectionId).orElse(null);

            MemoryBinding binding;
            if (binder != null) {
                binding = binder.bind(auth, Optional.ofNullable(selector), connectionId);
                CURRENT_BINDER.set(binder);
            } else {
                return Optional.of(DenyReason.RESOLUTION_FAILED);
            }

            CURRENT_BINDING.set(binding);
            return Optional.empty();
        } catch (NamespaceAccessDeniedException e) {
            clear();
            log.warn("[McpRequestMemory] access denied: {}", e.getMessage());
            return Optional.of(DenyReason.ACCESS_DENIED);
        } catch (TokenNamespaceLockedException e) {
            clear();
            log.warn("[McpRequestMemory] token namespace locked: {}", e.getMessage());
            return Optional.of(DenyReason.TOKEN_LOCKED);
        } catch (com.spectrayan.spector.commons.error.SpectorValidationException e) {
            clear();
            log.warn("[McpRequestMemory] validation error: {}", e.getMessage());
            return Optional.of(DenyReason.WILDCARD_REJECTED);
        } catch (RuntimeException e) {
            clear();
            log.warn("[McpRequestMemory] memory resolution failed; denying MCP call: {}", e.getMessage());
            log.debug("[McpRequestMemory] resolution failure detail", e);
            return Optional.of(DenyReason.RESOLUTION_FAILED);
        }
    }

    /**
     * Adapter method for callers that provide {@link MemoryRegistry}.
     */
    public static Optional<DenyReason> bindForCurrentRequest(MemoryRegistry registry, boolean authEnabled) {
        return bindForCurrentRequest(registry, authEnabled, null, null);
    }

    /**
     * Adapter method for callers that provide {@link MemoryRegistry}.
     */
    public static Optional<DenyReason> bindForCurrentRequest(
            MemoryRegistry registry,
            boolean authEnabled,
            String namespaceSelector,
            String connectionId) {
        if (registry != null && registry.binder() != null) {
            return bindForCurrentRequest(registry.binder(), authEnabled, namespaceSelector, connectionId);
        }
        if (authEnabled && !SecurityUtils.isAuthenticated()) {
            return Optional.of(DenyReason.AUTH_REQUIRED);
        }
        if (namespaceSelector != null && "*".equals(namespaceSelector.trim())) {
            return Optional.of(DenyReason.WILDCARD_REJECTED);
        }
        try {
            String userId = SecurityUtils.getUserId();
            String selector = (namespaceSelector != null && !namespaceSelector.isBlank())
                    ? namespaceSelector.trim()
                    : McpSessionContext.getSessionDefault(connectionId).orElse(null);

            var catalog = registry != null ? registry.catalog() : null;
            String targetNamespaceId = userId;
            if (selector != null && !selector.isBlank()) {
                if (catalog != null && userId != null) {
                    targetNamespaceId = catalog.resolve(userId, selector)
                            .map(com.spectrayan.spector.synapse.catalog.NamespaceRecord::namespaceId)
                            .orElse(selector);
                } else {
                    targetNamespaceId = selector;
                }
            }

            if (authEnabled && userId != null && catalog != null) {
                var authGrant = catalog.authorize(userId, targetNamespaceId, com.spectrayan.spector.synapse.catalog.GrantRole.READER);
                if (authGrant.isEmpty()) {
                    log.warn("[McpRequestMemory] Access denied: account={} has no grant on namespace={}", userId, targetNamespaceId);
                    return Optional.of(DenyReason.ACCESS_DENIED);
                }
            }

            SpectorMemory memory = (selector != null && !selector.isBlank())
                    ? registry.namespaceResolver().resolve(userId, targetNamespaceId)
                    : registry.resolveForCurrentRequest();

            AutoCloseable lease = memory != null ? memory.acquireLease() : null;
            MemoryBinding binding = new MemoryBinding(memory, userId, targetNamespaceId, selector != null ? selector : "default", lease, null);
            CURRENT_BINDING.set(binding);
            return Optional.empty();
        } catch (NamespaceAccessDeniedException e) {
            clear();
            log.warn("[McpRequestMemory] access denied: {}", e.getMessage());
            return Optional.of(DenyReason.ACCESS_DENIED);
        } catch (TokenNamespaceLockedException e) {
            clear();
            log.warn("[McpRequestMemory] token namespace locked: {}", e.getMessage());
            return Optional.of(DenyReason.TOKEN_LOCKED);
        } catch (RuntimeException e) {
            clear();
            log.warn("[McpRequestMemory] memory resolution failed; denying MCP call: {}", e.getMessage());
            return Optional.of(DenyReason.RESOLUTION_FAILED);
        }
    }

    /**
     * The memory bound for the current request thread, or {@code null} when none is bound.
     *
     * @return the request-scoped {@link SpectorMemory}, or {@code null}
     */
    public static SpectorMemory current() {
        MemoryBinding binding = CURRENT_BINDING.get();
        return binding != null ? binding.memory() : null;
    }

    /**
     * The full memory binding for the current request thread, or {@code null} when none is bound.
     */
    public static MemoryBinding currentBinding() {
        return CURRENT_BINDING.get();
    }

    /** Removes any memory bound to the current thread and closes its lease. */
    public static void clear() {
        MemoryBinding binding = CURRENT_BINDING.get();
        MemoryRequestBinder binder = CURRENT_BINDER.get();
        if (binding != null) {
            if (binder != null) {
                binder.unbind(binding);
            } else if (binding.lease() != null) {
                try {
                    binding.lease().close();
                } catch (Exception ignored) {
                }
            }
        }
        CURRENT_BINDING.remove();
        CURRENT_BINDER.remove();
        McpSessionContext.clearFallback();
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
            case ACCESS_DENIED ->
                    "Access denied: caller does not have sufficient permissions to access the requested namespace.";
            case RESOLUTION_FAILED ->
                    "Memory resolution failed; the MCP call was denied and no memory was modified.";
            case WILDCARD_REJECTED ->
                    "Wildcard namespace '*' is not supported. Please specify a single namespace or omit to use the default.";
            case TOKEN_LOCKED ->
                    "Token is locked to specific namespaces and access to the requested namespace was denied.";
        };
    }
}
