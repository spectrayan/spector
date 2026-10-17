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
package com.spectrayan.spector.synapse.security;

import java.io.IOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.spectrayan.spector.commons.concurrent.MemoryScope;
import com.spectrayan.spector.synapse.catalog.exception.CrossTenantAccessException;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceAccessDeniedException;
import com.spectrayan.spector.synapse.memory.MemoryBinding;
import com.spectrayan.spector.synapse.memory.MemoryRequestBinder;
import org.springframework.http.MediaType;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet filter that resolves the active namespace for REST requests
 * and attaches the resulting {@link MemoryBinding} to {@link RequestAttributes} (ADR-0029 §16, Q19).
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class NamespaceResolutionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(NamespaceResolutionFilter.class);

    public static final String HEADER_NAMESPACE = "X-Spector-Namespace";
    public static final String PARAM_NAMESPACE = "namespace";

    private final MemoryRequestBinder binder;

    public NamespaceResolutionFilter(MemoryRequestBinder binder) {
        this.binder = binder;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/v1/")) {
            return true;
        }
        return path.startsWith("/api/v1/auth")
                || path.startsWith("/api/v1/events")
                || path.startsWith("/api/v1/admin/tenants")
                || path.startsWith("/api/v1/admin/accounts")
                || path.startsWith("/api/v1/admin/namespaces")
                || path.startsWith("/api/v1/admin/analytics")
                || path.startsWith("/api/v1/admin/audit")
                || path.startsWith("/api/v1/admin/break-glass")
                || path.contains("/health");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String headerNamespace = request.getHeader(HEADER_NAMESPACE);
        String paramNamespace = request.getParameter(PARAM_NAMESPACE);

        String selector = (headerNamespace != null && !headerNamespace.isBlank())
                ? headerNamespace.trim()
                : (paramNamespace != null && !paramNamespace.isBlank() ? paramNamespace.trim() : null);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String initialTenantId = SecurityUtils.getTenantId();
        MemoryBinding binding = null;

        try {
            binding = binder.bind(auth, Optional.ofNullable(selector));
            if (binding != null && binding.isBreakGlass()) {
                enforceBreakGlassReadOnly(request, binding);
            }
            request.setAttribute(MemoryBinding.ATTRIBUTE_KEY, binding);

            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                attrs.setAttribute(MemoryBinding.ATTRIBUTE_KEY, binding, RequestAttributes.SCOPE_REQUEST);
            }

            String effectiveTenantId = (binding.context() != null && binding.context().tenantId() != null)
                    ? binding.context().tenantId()
                    : initialTenantId;
            String sessionId = binding.context() != null ? binding.context().sessionId() : null;
            String namespaceId = binding.namespaceId();

            MemoryScope.runWithScope(effectiveTenantId, sessionId, namespaceId, () -> {
                try {
                    filterChain.doFilter(request, response);
                } catch (IOException | ServletException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (NamespaceAccessDeniedException e) {
            writeAccessDenied(response, e);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof NamespaceAccessDeniedException nade) {
                writeAccessDenied(response, nade);
                return;
            }
            if (e.getCause() instanceof ServletException servletException) {
                if (servletException.getCause() instanceof NamespaceAccessDeniedException nade) {
                    writeAccessDenied(response, nade);
                    return;
                }
                throw servletException;
            }
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        } finally {
            if (binding != null) {
                binder.unbind(binding);
            }
        }
    }

    private void enforceBreakGlassReadOnly(HttpServletRequest request, MemoryBinding binding) {
        String method = request.getMethod() != null ? request.getMethod().toUpperCase() : "GET";
        String path = request.getRequestURI() != null ? request.getRequestURI() : "";

        if (path.contains("/export")) {
            throw new com.spectrayan.spector.synapse.catalog.exception.BreakGlassAccessDeniedException(
                    binding.namespaceId(),
                    binding.accountId(),
                    "Break-glass sessions are non-exportable; export is forbidden during break-glass access"
            );
        }

        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return;
        }

        if ("POST".equals(method) && (
                path.equals("/api/v1/memory/recall")
                || path.equals("/api/v1/memory/recall/response")
                || path.equals("/api/v1/memory/search")
                || path.equals("/api/v1/memory/browse"))) {
            return;
        }

        throw new com.spectrayan.spector.synapse.catalog.exception.BreakGlassAccessDeniedException(
                binding.namespaceId(),
                binding.accountId(),
                "Break-glass sessions are strictly read-only; " + method + " " + path + " is forbidden"
        );
    }

    private void writeAccessDenied(HttpServletResponse response, NamespaceAccessDeniedException e) throws IOException {
        log.warn("[NamespaceResolutionFilter] Access denied: {}", e.getMessage());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        String errorCodeId = e.errorCode() != null ? e.errorCode().id() : "SPE-820-001";
        String alias;
        String msg;
        if (e instanceof CrossTenantAccessException) {
            alias = CrossTenantAccessException.ERROR_CODE_ALIAS;
            msg = "Cross-tenant access forbidden";
        } else if (e instanceof com.spectrayan.spector.synapse.catalog.exception.BreakGlassAccessDeniedException bgade) {
            alias = com.spectrayan.spector.synapse.catalog.exception.BreakGlassAccessDeniedException.ERROR_CODE_ALIAS;
            msg = bgade.getReason();
        } else {
            alias = "NamespaceAccessDenied";
            msg = "Namespace access denied";
        }
        String isoTimestamp = java.time.format.DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.now());
        String json = String.format(
                "{\"status\":403,\"error\":\"%s\",\"code\":\"%s\",\"alias\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\",\"details\":{\"alias\":\"%s\",\"code\":\"%s\"}}",
                errorCodeId, errorCodeId, alias, msg, isoTimestamp, alias, errorCodeId
        );
        response.getWriter().write(json);
    }
}
