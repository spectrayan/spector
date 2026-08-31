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
package com.spectrayan.spector.synapse.agent.tools;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.NamespaceStatus;
import com.spectrayan.spector.synapse.catalog.NamespaceType;
import com.spectrayan.spector.synapse.catalog.exception.DefaultNamespaceProtectedException;
import com.spectrayan.spector.synapse.mcp.McpSessionContext;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

@DisplayName("Namespace MCP Tools Tests")
class NamespaceToolsTest {

    private AccountCatalog catalog;
    private ObjectMapper objectMapper;

    private static final String TEST_ACCOUNT = "0195500000001";

    @BeforeEach
    void setUp() {
        catalog = mock(AccountCatalog.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        var auth = new UsernamePasswordAuthenticationToken(TEST_ACCOUNT, "pw", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        McpSessionContext.clearFallback();
    }

    @Test
    @DisplayName("namespace_list lists accessible namespaces")
    void testNamespaceList() throws Exception {
        var record = new NamespaceRecord(
                TEST_ACCOUNT, "default", TEST_ACCOUNT, NamespaceType.DEFAULT,
                NamespaceStatus.ACTIVE, "Default", null, null, Instant.now(), Instant.now()
        );
        when(catalog.listAccessible(TEST_ACCOUNT)).thenReturn(List.of(record));

        var tool = new NamespaceListTool(catalog, objectMapper);
        CallToolResult result = tool.execute(Map.of());

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).isNotEmpty();
    }

    @Test
    @DisplayName("namespace_create creates new namespace")
    void testNamespaceCreate() throws Exception {
        var record = new NamespaceRecord(
                "0195500000002", "research", TEST_ACCOUNT, NamespaceType.PROJECT,
                NamespaceStatus.ACTIVE, "Research", "Notes", null, Instant.now(), Instant.now()
        );
        when(catalog.createNamespace(eq(TEST_ACCOUNT), eq("research"), eq(NamespaceType.PROJECT),
                eq("Research"), eq("Notes"), any())).thenReturn(record);

        var tool = new NamespaceCreateTool(catalog, objectMapper);
        CallToolResult result = tool.execute(Map.of(
                "slug", "research",
                "type", "PROJECT",
                "displayName", "Research",
                "description", "Notes"
        ));

        assertThat(result.isError()).isFalse();
        verify(catalog).createNamespace(eq(TEST_ACCOUNT), eq("research"), eq(NamespaceType.PROJECT),
                eq("Research"), eq("Notes"), any());
    }

    @Test
    @DisplayName("namespace_info returns namespace details")
    void testNamespaceInfo() throws Exception {
        var record = new NamespaceRecord(
                "0195500000002", "research", TEST_ACCOUNT, NamespaceType.PROJECT,
                NamespaceStatus.ACTIVE, "Research", "Notes", null, Instant.now(), Instant.now()
        );
        when(catalog.resolve(TEST_ACCOUNT, "research")).thenReturn(Optional.of(record));

        var tool = new NamespaceInfoTool(catalog, objectMapper);
        CallToolResult result = tool.execute(Map.of("namespace", "research"));

        assertThat(result.isError()).isFalse();
    }

    @Test
    @DisplayName("namespace_info returns error when namespace not found")
    void testNamespaceInfoNotFound() throws Exception {
        when(catalog.resolve(TEST_ACCOUNT, "missing")).thenReturn(Optional.empty());

        var tool = new NamespaceInfoTool(catalog, objectMapper);
        CallToolResult result = tool.execute(Map.of("namespace", "missing"));

        assertThat(result.isError()).isTrue();
    }

    @Test
    @DisplayName("namespace_switch sets connection-scoped default")
    void testNamespaceSwitch() throws Exception {
        var record = new NamespaceRecord(
                "0195500000002", "research", TEST_ACCOUNT, NamespaceType.PROJECT,
                NamespaceStatus.ACTIVE, "Research", null, null, Instant.now(), Instant.now()
        );
        when(catalog.resolve(TEST_ACCOUNT, "research")).thenReturn(Optional.of(record));

        var tool = new NamespaceSwitchTool(catalog);
        CallToolResult result = tool.execute(Map.of("namespace", "research"));

        assertThat(result.isError()).isFalse();
        assertThat(McpSessionContext.getSessionDefault(null)).contains("0195500000002");
    }

    @Test
    @DisplayName("namespace_switch rejects wildcard '*'")
    void testNamespaceSwitchWildcard() throws Exception {
        var tool = new NamespaceSwitchTool(catalog);
        CallToolResult result = tool.execute(Map.of("namespace", "*"));

        assertThat(result.isError()).isTrue();
    }

    @Test
    @DisplayName("namespace_switch rejects tombstoned namespace")
    void testNamespaceSwitchTombstoned() throws Exception {
        var tombstoned = new NamespaceRecord(
                "0195500000002", "old-project", TEST_ACCOUNT, NamespaceType.PROJECT,
                NamespaceStatus.TOMBSTONED, null, null, null, Instant.now(), Instant.now()
        );
        when(catalog.resolve(TEST_ACCOUNT, "old-project")).thenReturn(Optional.of(tombstoned));

        var tool = new NamespaceSwitchTool(catalog);
        CallToolResult result = tool.execute(Map.of("namespace", "old-project"));

        assertThat(result.isError()).isTrue();
    }

    @Test
    @DisplayName("namespace_set_default sets persistent default namespace")
    void testNamespaceSetDefault() throws Exception {
        var record = new NamespaceRecord(
                "0195500000002", "research", TEST_ACCOUNT, NamespaceType.PROJECT,
                NamespaceStatus.ACTIVE, "Research", null, null, Instant.now(), Instant.now()
        );
        when(catalog.resolve(TEST_ACCOUNT, "research")).thenReturn(Optional.of(record));

        var tool = new NamespaceSetDefaultTool(catalog);
        CallToolResult result = tool.execute(Map.of("namespace", "research"));

        assertThat(result.isError()).isFalse();
        verify(catalog).setDefaultNamespace(TEST_ACCOUNT, "0195500000002");
    }

    @Test
    @DisplayName("namespace_delete deletes namespace")
    void testNamespaceDelete() throws Exception {
        var tool = new NamespaceDeleteTool(catalog);
        CallToolResult result = tool.execute(Map.of("namespace", "research"));

        assertThat(result.isError()).isFalse();
        verify(catalog).tombstone(TEST_ACCOUNT, "research");
    }

    @Test
    @DisplayName("namespace_delete on default namespace returns error")
    void testNamespaceDeleteDefaultProtected() throws Exception {
        doThrow(new DefaultNamespaceProtectedException(TEST_ACCOUNT))
                .when(catalog).tombstone(TEST_ACCOUNT, "default");

        var tool = new NamespaceDeleteTool(catalog);
        CallToolResult result = tool.execute(Map.of("namespace", "default"));

        assertThat(result.isError()).isTrue();
    }

    @Test
    @DisplayName("namespace_grant creates grant via MCP tool")
    void testNamespaceGrantTool() throws Exception {
        var grant = new com.spectrayan.spector.synapse.catalog.Grant(
                "grant-mcp-1",
                com.spectrayan.spector.synapse.catalog.GrantObjectType.NAMESPACE,
                "0195500000002",
                "0195500000099",
                com.spectrayan.spector.synapse.catalog.PrincipalType.ACCOUNT,
                com.spectrayan.spector.synapse.catalog.GrantRole.READER,
                null,
                TEST_ACCOUNT,
                Instant.now(),
                null,
                null
        );
        when(catalog.grantNamespace(eq(TEST_ACCOUNT), eq("research"), eq("0195500000099"),
                eq(com.spectrayan.spector.synapse.catalog.GrantRole.READER), any(), any())).thenReturn(grant);

        var tool = new NamespaceGrantTool(catalog, objectMapper);
        CallToolResult result = tool.execute(Map.of(
                "slug", "research",
                "granteeAccountId", "0195500000099",
                "role", "READER"
        ));

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).isNotEmpty();
    }

    @Test
    @DisplayName("namespace_revoke revokes grant via MCP tool")
    void testNamespaceRevokeTool() throws Exception {
        var tool = new NamespaceRevokeTool(catalog, objectMapper);
        CallToolResult result = tool.execute(Map.of(
                "slug", "research",
                "grantId", "grant-mcp-1"
        ));

        assertThat(result.isError()).isFalse();
        verify(catalog).revokeNamespaceGrant(TEST_ACCOUNT, "research", "grant-mcp-1");
    }

    @Test
    @DisplayName("namespace_list_grants lists grants via MCP tool")
    void testNamespaceListGrantsTool() throws Exception {
        var grant = new com.spectrayan.spector.synapse.catalog.Grant(
                "grant-mcp-1",
                com.spectrayan.spector.synapse.catalog.GrantObjectType.NAMESPACE,
                "0195500000002",
                "0195500000099",
                com.spectrayan.spector.synapse.catalog.PrincipalType.ACCOUNT,
                com.spectrayan.spector.synapse.catalog.GrantRole.READER,
                null,
                TEST_ACCOUNT,
                Instant.now(),
                null,
                null
        );
        when(catalog.listGrants(TEST_ACCOUNT, "research")).thenReturn(List.of(grant));

        var tool = new NamespaceListGrantsTool(catalog, objectMapper);
        CallToolResult result = tool.execute(Map.of("slug", "research"));

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).isNotEmpty();
    }
}
