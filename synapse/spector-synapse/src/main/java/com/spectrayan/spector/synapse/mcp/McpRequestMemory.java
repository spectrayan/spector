/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    private static final ThreadLocal<String> LAST_DENY_MESSAGE = new ThreadLocal<>();

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
        return bindForCurrentRequest(binder, authEnabled, null, null, false);
    }

    /**
     * Resolves the caller's memory using {@link MemoryRequestBinder}.
     */
    public static Optional<DenyReason> bindForCurrentRequest(
            MemoryRequestBinder binder,
            boolean authEnabled,
            String namespaceSelector,
            String connectionId) {
        return bindForCurrentRequest(binder, authEnabled, namespaceSelector, connectionId, false);
    }

    /**
     * Resolves the caller's memory using {@link MemoryRequestBinder} and optionally enforces
     * {@link com.spectrayan.spector.synapse.catalog.GrantAction#WRITE} permission for mutating tools.
     */
    public static Optional<DenyReason> bindForCurrentRequest(
            MemoryRequestBinder binder,
            boolean authEnabled,
            String namespaceSelector,
            String connectionId,
            boolean requireWrite) {
        LAST_DENY_MESSAGE.remove();

        if (authEnabled && !SecurityUtils.isAuthenticated()) {
            LAST_DENY_MESSAGE.set("[SPE-300-001] Authentication is required to invoke MCP tools over /mcp.");
            return Optional.of(DenyReason.AUTH_REQUIRED);
        }

        if (namespaceSelector != null && "*".equals(namespaceSelector.trim())) {
            LAST_DENY_MESSAGE.set("[SPE-100-001] Wildcard namespace '*' is not supported. Please specify a single namespace or omit to use the default.");
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
                if (binding != null && binding.isBreakGlass()) {
                    binder.unbind(binding);
                    log.warn("[McpRequestMemory] Break-glass sessions cannot be used for MCP tool invocations: namespace={}, account={}",
                            binding.namespaceId(), binding.accountId());
                    LAST_DENY_MESSAGE.set("[SPE-820-005 / SPE-SEC-005 / SPE-800-002] Access denied: break-glass sessions cannot be used for MCP or agentic tool invocations.");
                    return Optional.of(DenyReason.ACCESS_DENIED);
                }
                if (requireWrite && binding != null && binding.context() != null
                        && binding.context().role() != null
                        && !binding.context().role().isAtLeast(com.spectrayan.spector.synapse.catalog.GrantRole.WRITER)) {
                    binder.unbind(binding);
                    log.warn("[McpRequestMemory] Write tool denied for read-only grant role {}: namespace={}, account={}",
                            binding.context().role(), binding.namespaceId(), binding.accountId());
                    LAST_DENY_MESSAGE.set("[SPE-800-002] Access denied: caller grant role ("
                            + binding.context().role() + ") does not permit write operations on namespace '"
                            + binding.namespaceId() + "'.");
                    return Optional.of(DenyReason.ACCESS_DENIED);
                }
                CURRENT_BINDER.set(binder);
            } else {
                LAST_DENY_MESSAGE.set("[SPE-700-001] Memory resolution failed; the MCP call was denied and no memory was modified.");
                return Optional.of(DenyReason.RESOLUTION_FAILED);
            }

            CURRENT_BINDING.set(binding);
            return Optional.empty();
        } catch (NamespaceAccessDeniedException e) {
            unbindCurrent();
            log.warn("[McpRequestMemory] access denied: {}", e.getMessage());
            if (e instanceof com.spectrayan.spector.synapse.catalog.exception.CrossTenantAccessException) {
                LAST_DENY_MESSAGE.set("[SPE-820-001 / SPE-SEC-001] Cross-tenant access forbidden: " + e.getMessage());
            } else if (e instanceof com.spectrayan.spector.synapse.catalog.exception.BreakGlassAccessDeniedException) {
                LAST_DENY_MESSAGE.set("[SPE-820-005 / SPE-SEC-005 / SPE-800-002] Access denied (break-glass): " + e.getMessage());
            } else {
                LAST_DENY_MESSAGE.set("[SPE-800-002] Access denied: caller does not have sufficient permissions to access the requested namespace (" + e.getMessage() + ").");
            }
            return Optional.of(DenyReason.ACCESS_DENIED);
        } catch (com.spectrayan.spector.synapse.catalog.exception.NamespaceNotFoundException e) {
            unbindCurrent();
            log.warn("[McpRequestMemory] ungranted or unknown namespace denied: {}", e.getMessage());
            LAST_DENY_MESSAGE.set("[SPE-800-002] Access denied: caller does not own or have a grant for the requested namespace (" + e.getMessage() + ").");
            return Optional.of(DenyReason.ACCESS_DENIED);
        } catch (TokenNamespaceLockedException e) {
            unbindCurrent();
            log.warn("[McpRequestMemory] token namespace locked: {}", e.getMessage());
            LAST_DENY_MESSAGE.set("[SPE-800-003] Token is locked to specific namespaces and access to the requested namespace was denied.");
            return Optional.of(DenyReason.TOKEN_LOCKED);
        } catch (com.spectrayan.spector.commons.error.SpectorValidationException e) {
            unbindCurrent();
            log.warn("[McpRequestMemory] validation error: {}", e.getMessage());
            LAST_DENY_MESSAGE.set("[SPE-100-001] Wildcard namespace '*' is not supported. Please specify a single namespace or omit to use the default.");
            return Optional.of(DenyReason.WILDCARD_REJECTED);
        } catch (RuntimeException e) {
            unbindCurrent();
            log.warn("[McpRequestMemory] memory resolution failed; denying MCP call: {}", e.getMessage());
            log.debug("[McpRequestMemory] resolution failure detail", e);
            LAST_DENY_MESSAGE.set("[SPE-700-001] Memory resolution failed; the MCP call was denied and no memory was modified.");
            return Optional.of(DenyReason.RESOLUTION_FAILED);
        }
    }

    /**
     * Adapter method for callers that provide {@link MemoryRegistry}.
     */
    public static Optional<DenyReason> bindForCurrentRequest(MemoryRegistry registry, boolean authEnabled) {
        return bindForCurrentRequest(registry, authEnabled, null, null, false);
    }

    /**
     * Adapter method for callers that provide {@link MemoryRegistry}.
     */
    public static Optional<DenyReason> bindForCurrentRequest(
            MemoryRegistry registry,
            boolean authEnabled,
            String namespaceSelector,
            String connectionId) {
        return bindForCurrentRequest(registry, authEnabled, namespaceSelector, connectionId, false);
    }

    /**
     * Adapter method for callers that provide {@link MemoryRegistry} and specify {@code requireWrite}.
     */
    public static Optional<DenyReason> bindForCurrentRequest(
            MemoryRegistry registry,
            boolean authEnabled,
            String namespaceSelector,
            String connectionId,
            boolean requireWrite) {
        if (registry != null && registry.binder() != null) {
            return bindForCurrentRequest(registry.binder(), authEnabled, namespaceSelector, connectionId, requireWrite);
        }
        LAST_DENY_MESSAGE.remove();
        if (authEnabled) {
            log.warn("[McpRequestMemory] Denying MCP call: MemoryRequestBinder unavailable under enabled auth");
            if (!SecurityUtils.isAuthenticated()) {
                LAST_DENY_MESSAGE.set("[SPE-300-001] Authentication is required to invoke MCP tools over /mcp.");
                return Optional.of(DenyReason.AUTH_REQUIRED);
            }
            LAST_DENY_MESSAGE.set("[SPE-700-001] Memory resolution failed; the MCP call was denied and no memory was modified.");
            return Optional.of(DenyReason.RESOLUTION_FAILED);
        }
        if (namespaceSelector != null && "*".equals(namespaceSelector.trim())) {
            LAST_DENY_MESSAGE.set("[SPE-100-001] Wildcard namespace '*' is not supported. Please specify a single namespace or omit to use the default.");
            return Optional.of(DenyReason.WILDCARD_REJECTED);
        }
        try {
            String userId = SecurityUtils.getUserId();
            String selector = (namespaceSelector != null && !namespaceSelector.isBlank())
                    ? namespaceSelector.trim()
                    : McpSessionContext.getSessionDefault(connectionId).orElse(null);

            SpectorMemory memory = (selector != null && !selector.isBlank()) && registry != null
                    ? registry.namespaceResolver().resolve(userId, selector)
                    : (registry != null ? registry.resolveForCurrentRequest() : null);

            AutoCloseable lease = memory != null ? memory.acquireLease() : null;
            MemoryBinding binding = new MemoryBinding(memory, userId, userId, selector != null ? selector : "default", lease, null);
            CURRENT_BINDING.set(binding);
            return Optional.empty();
        } catch (RuntimeException e) {
            unbindCurrent();
            log.warn("[McpRequestMemory] memory resolution failed; denying MCP call: {}", e.getMessage());
            LAST_DENY_MESSAGE.set("[SPE-700-001] Memory resolution failed; the MCP call was denied and no memory was modified.");
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

    private static void unbindCurrent() {
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

    /** Removes any memory bound to the current thread and closes its lease. */
    public static void clear() {
        unbindCurrent();
        LAST_DENY_MESSAGE.remove();
    }

    /**
     * Human-readable tool-error content for a deny reason, including ADR-0070 error taxonomy codes.
     *
     * @param reason the deny reason
     * @return a message suitable for an MCP tool error result
     */
    public static String message(DenyReason reason) {
        String detailed = LAST_DENY_MESSAGE.get();
        if (detailed != null && !detailed.isBlank()) {
            return detailed;
        }
        return switch (reason) {
            case AUTH_REQUIRED ->
                    "[SPE-300-001] Authentication is required to invoke MCP tools over /mcp.";
            case ACCESS_DENIED ->
                    "[SPE-800-002] Access denied: caller does not have sufficient permissions to access the requested namespace.";
            case RESOLUTION_FAILED ->
                    "[SPE-700-001] Memory resolution failed; the MCP call was denied and no memory was modified.";
            case WILDCARD_REJECTED ->
                    "[SPE-100-001] Wildcard namespace '*' is not supported. Please specify a single namespace or omit to use the default.";
            case TOKEN_LOCKED ->
                    "[SPE-800-003] Token is locked to specific namespaces and access to the requested namespace was denied.";
        };
    }
}
