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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceAccessDeniedException;
import com.spectrayan.spector.synapse.catalog.exception.TokenNamespaceLockedException;
import com.spectrayan.spector.synapse.memory.MemoryBinding;
import com.spectrayan.spector.synapse.memory.MemoryRequestBinder;
import com.spectrayan.spector.synapse.memory.RequestMemoryContext;

@DisplayName("McpRequestMemory MemoryRequestBinder Integration")
class McpRequestMemoryBinderTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        McpRequestMemory.clear();
    }

    @Test
    @DisplayName("bindForCurrentRequest binds MemoryBinding and closes lease on clear")
    void testBindAndClearWithLease() throws Exception {
        MemoryRequestBinder binder = mock(MemoryRequestBinder.class);
        SpectorMemory memory = mock(SpectorMemory.class);
        AutoCloseable lease = mock(AutoCloseable.class);

        Authentication auth = new UsernamePasswordAuthenticationToken("user-1", "pass", AuthorityUtils.NO_AUTHORITIES);
        SecurityContextHolder.getContext().setAuthentication(auth);

        RequestMemoryContext context = new RequestMemoryContext("tenant-1", java.util.List.of(), "user-1", "user-1", "default",
                com.spectrayan.spector.synapse.catalog.GrantRole.OWNER, java.util.Set.of(), "conn-1", java.util.List.of(), null);
        MemoryBinding binding = new MemoryBinding(memory, "user-1", "user-1", "default", lease, context);

        when(binder.bind(eq(auth), eq(Optional.of("default")), eq("conn-1"))).thenReturn(binding);

        Optional<McpRequestMemory.DenyReason> deny = McpRequestMemory.bindForCurrentRequest(binder, true, "default", "conn-1");
        assertThat(deny).isEmpty();
        assertThat(McpRequestMemory.current()).isSameAs(memory);
        assertThat(McpRequestMemory.currentBinding()).isSameAs(binding);

        McpRequestMemory.clear();
        verify(binder).unbind(binding);
        assertThat(McpRequestMemory.current()).isNull();
        assertThat(McpRequestMemory.currentBinding()).isNull();
    }

    @Test
    @DisplayName("bindForCurrentRequest returns AUTH_REQUIRED when unauthenticated and auth enabled")
    void testAuthRequired() {
        MemoryRequestBinder binder = mock(MemoryRequestBinder.class);
        Optional<McpRequestMemory.DenyReason> deny = McpRequestMemory.bindForCurrentRequest(binder, true, "default", "conn-1");
        assertThat(deny).contains(McpRequestMemory.DenyReason.AUTH_REQUIRED);
        assertThat(McpRequestMemory.current()).isNull();
    }

    @Test
    @DisplayName("bindForCurrentRequest returns WILDCARD_REJECTED when namespace selector is *")
    void testWildcardRejected() {
        MemoryRequestBinder binder = mock(MemoryRequestBinder.class);
        Authentication auth = new UsernamePasswordAuthenticationToken("user-1", "pass", AuthorityUtils.NO_AUTHORITIES);
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<McpRequestMemory.DenyReason> deny = McpRequestMemory.bindForCurrentRequest(binder, true, "*", "conn-1");
        assertThat(deny).contains(McpRequestMemory.DenyReason.WILDCARD_REJECTED);
        assertThat(McpRequestMemory.current()).isNull();
    }

    @Test
    @DisplayName("bindForCurrentRequest returns ACCESS_DENIED on NamespaceAccessDeniedException")
    void testAccessDenied() {
        MemoryRequestBinder binder = mock(MemoryRequestBinder.class);
        Authentication auth = new UsernamePasswordAuthenticationToken("user-1", "pass", AuthorityUtils.NO_AUTHORITIES);
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(binder.bind(any(), any(), any())).thenThrow(new NamespaceAccessDeniedException("ns-secret", "user-1"));

        Optional<McpRequestMemory.DenyReason> deny = McpRequestMemory.bindForCurrentRequest(binder, true, "ns-secret", "conn-1");
        assertThat(deny).contains(McpRequestMemory.DenyReason.ACCESS_DENIED);
        assertThat(McpRequestMemory.current()).isNull();
    }

    @Test
    @DisplayName("bindForCurrentRequest returns TOKEN_LOCKED on TokenNamespaceLockedException")
    void testTokenLocked() {
        MemoryRequestBinder binder = mock(MemoryRequestBinder.class);
        Authentication auth = new UsernamePasswordAuthenticationToken("user-1", "pass", AuthorityUtils.NO_AUTHORITIES);
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(binder.bind(any(), any(), any())).thenThrow(new TokenNamespaceLockedException("ns-allowed", "ns-locked"));

        Optional<McpRequestMemory.DenyReason> deny = McpRequestMemory.bindForCurrentRequest(binder, true, "ns-locked", "conn-1");
        assertThat(deny).contains(McpRequestMemory.DenyReason.TOKEN_LOCKED);
        assertThat(McpRequestMemory.current()).isNull();
    }
}
