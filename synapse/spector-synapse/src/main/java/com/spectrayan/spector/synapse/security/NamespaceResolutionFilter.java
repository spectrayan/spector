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

import com.spectrayan.spector.synapse.memory.MemoryBinding;
import com.spectrayan.spector.synapse.memory.MemoryRequestBinder;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet filter that resolves the active namespace for REST memory requests
 * and attaches the resulting {@link MemoryBinding} to {@link RequestAttributes} (ADR-0029 §16, Q19).
 *
 * <p>Selection order per ADR §6.1:</p>
 * <pre>
 * HTTP: X-Spector-Namespace > ?namespace= > account.defaultNamespaceId
 * </pre>
 *
 * <p>Cleans up the bound context in a {@code finally} block upon filter completion.</p>
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
        return path == null || !path.startsWith("/api/v1/memory");
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
        MemoryBinding binding = null;

        try {
            binding = binder.bind(auth, Optional.ofNullable(selector));
            request.setAttribute(MemoryBinding.ATTRIBUTE_KEY, binding);

            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                attrs.setAttribute(MemoryBinding.ATTRIBUTE_KEY, binding, RequestAttributes.SCOPE_REQUEST);
            }

            filterChain.doFilter(request, response);
        } finally {
            if (binding != null) {
                binder.unbind(binding);
            }
        }
    }
}
