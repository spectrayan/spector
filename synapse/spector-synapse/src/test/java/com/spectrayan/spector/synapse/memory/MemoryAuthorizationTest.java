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
package com.spectrayan.spector.synapse.memory;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.id.TsidGenerator;
import com.spectrayan.spector.synapse.catalog.GrantRole;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceAccessDeniedException;
import com.spectrayan.spector.synapse.platform.events.EventPublisher;

@DisplayName("Memory Authorization PEP Tests")
class MemoryAuthorizationTest {

    private MemoryService memoryService;
    private MemoryAccessObject mao;
    private EventPublisher eventPublisher;
    private TsidGenerator tsid;
    private SpectorMemory spectorMemory;

    private static final String ACCOUNT_ID = "0195500000001";
    private static final String NAMESPACE_ID = "0195500000002";

    @BeforeEach
    void setUp() {
        mao = mock(MemoryAccessObject.class);
        eventPublisher = mock(EventPublisher.class);
        tsid = new TsidGenerator();
        spectorMemory = mock(SpectorMemory.class);

        memoryService = new MemoryService(mao, eventPublisher, tsid);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void bindRole(GrantRole role) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ServletRequestAttributes attrs = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attrs);

        RequestMemoryContext reqCtx = new RequestMemoryContext(
                null, List.of(), ACCOUNT_ID, NAMESPACE_ID, "proj",
                role, Set.of(), "session-1", List.of(), null);
        MemoryBinding binding = new MemoryBinding(spectorMemory, ACCOUNT_ID, NAMESPACE_ID, "proj", null, reqCtx);
        attrs.setAttribute(MemoryBinding.ATTRIBUTE_KEY, binding, ServletRequestAttributes.SCOPE_REQUEST);
    }

    @Test
    @DisplayName("READER role is denied on store")
    void testReaderDeniedOnStore() {
        bindRole(GrantRole.READER);

        var request = new MemoryDto.StoreRequest("some content", List.of("tag1"), 1.0, Map.of());
        assertThatThrownBy(() -> memoryService.store(request))
                .isInstanceOf(NamespaceAccessDeniedException.class);
    }

    @Test
    @DisplayName("READER role is denied on remember")
    void testReaderDeniedOnRemember() {
        bindRole(GrantRole.READER);

        var request = new MemoryDto.RememberRequest("some text", "SEMANTIC", "USER_STATED", null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> memoryService.remember(request))
                .isInstanceOf(NamespaceAccessDeniedException.class);
    }

    @Test
    @DisplayName("READER role is denied on forget")
    void testReaderDeniedOnForget() {
        bindRole(GrantRole.READER);

        assertThatThrownBy(() -> memoryService.forget("mem-123"))
                .isInstanceOf(NamespaceAccessDeniedException.class);
    }

    @Test
    @DisplayName("READER role is denied on reinforce")
    void testReaderDeniedOnReinforce() {
        bindRole(GrantRole.READER);

        assertThatThrownBy(() -> memoryService.reinforce("mem-123", 1))
                .isInstanceOf(NamespaceAccessDeniedException.class);
    }

    @Test
    @DisplayName("READER role is denied on suppress")
    void testReaderDeniedOnSuppress() {
        bindRole(GrantRole.READER);

        var request = new MemoryDto.SuppressRequest("SUPPRESS", "Outdated");
        assertThatThrownBy(() -> memoryService.suppress("mem-123", request))
                .isInstanceOf(NamespaceAccessDeniedException.class);
    }
}
