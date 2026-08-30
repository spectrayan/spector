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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.synapse.memory.MemoryBinding;
import com.spectrayan.spector.synapse.memory.MemoryRequestBinder;

import jakarta.servlet.ServletException;

@DisplayName("NamespaceResolutionFilter Tests")
class NamespaceResolutionFilterTest {

    private MemoryRequestBinder binder;
    private NamespaceResolutionFilter filter;
    private SpectorMemory mockMemory;

    private static final String TEST_ACCOUNT = "0195500000001";

    @BeforeEach
    void setUp() {
        binder = mock(MemoryRequestBinder.class);
        filter = new NamespaceResolutionFilter(binder);
        mockMemory = mock(SpectorMemory.class);

        var auth = new UsernamePasswordAuthenticationToken(TEST_ACCOUNT, "pw", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("Filter resolves namespace from X-Spector-Namespace header")
    void testHeaderResolution() throws ServletException, IOException {
        var request = new MockHttpServletRequest("GET", "/api/v1/memory/status");
        request.addHeader(NamespaceResolutionFilter.HEADER_NAMESPACE, "project-x");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        var binding = new MemoryBinding(mockMemory, TEST_ACCOUNT, "0195500000002", "project-x");
        when(binder.bind(any(), eq(Optional.of("project-x")))).thenReturn(binding);

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute(MemoryBinding.ATTRIBUTE_KEY)).isEqualTo(binding);
        verify(binder).unbind(binding);
    }

    @Test
    @DisplayName("Filter resolves namespace from ?namespace= query parameter when header is absent")
    void testQueryParamResolution() throws ServletException, IOException {
        var request = new MockHttpServletRequest("GET", "/api/v1/memory/status");
        request.setParameter(NamespaceResolutionFilter.PARAM_NAMESPACE, "project-y");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        var binding = new MemoryBinding(mockMemory, TEST_ACCOUNT, "0195500000003", "project-y");
        when(binder.bind(any(), eq(Optional.of("project-y")))).thenReturn(binding);

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute(MemoryBinding.ATTRIBUTE_KEY)).isEqualTo(binding);
        verify(binder).unbind(binding);
    }

    @Test
    @DisplayName("Filter prioritizes header over query parameter")
    void testHeaderPriority() throws ServletException, IOException {
        var request = new MockHttpServletRequest("GET", "/api/v1/memory/status");
        request.addHeader(NamespaceResolutionFilter.HEADER_NAMESPACE, "header-ns");
        request.setParameter(NamespaceResolutionFilter.PARAM_NAMESPACE, "param-ns");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        var binding = new MemoryBinding(mockMemory, TEST_ACCOUNT, "0195500000004", "header-ns");
        when(binder.bind(any(), eq(Optional.of("header-ns")))).thenReturn(binding);

        filter.doFilter(request, response, chain);

        verify(binder).bind(any(), eq(Optional.of("header-ns")));
        verify(binder).unbind(binding);
    }

    @Test
    @DisplayName("Filter skips non-memory paths")
    void testNonMemoryPathsSkipped() throws ServletException, IOException {
        var request = new MockHttpServletRequest("GET", "/api/v1/auth/login");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute(MemoryBinding.ATTRIBUTE_KEY)).isNull();
    }
}
