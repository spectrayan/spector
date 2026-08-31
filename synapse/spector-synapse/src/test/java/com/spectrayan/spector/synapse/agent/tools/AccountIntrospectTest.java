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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.memory.model.UserSoul;
import com.spectrayan.spector.synapse.catalog.Account;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.NamespaceType;
import com.spectrayan.spector.synapse.catalog.api.AccountDefaultController;
import com.spectrayan.spector.synapse.catalog.api.AccountIntrospectResponse;
import com.spectrayan.spector.synapse.catalog.file.FileAccountCatalog;
import com.spectrayan.spector.synapse.identity.IdentityCache;
import com.spectrayan.spector.synapse.identity.IdentityPlane;
import com.spectrayan.spector.synapse.security.SecurityUtils;

import io.modelcontextprotocol.spec.McpSchema;

@DisplayName("Account Introspection Specification (ADR-0029 §21)")
class AccountIntrospectTest {

    @TempDir
    Path tempDir;

    private AccountCatalog catalog;
    private IdentityPlane identityPlane;
    private ObjectMapper objectMapper;
    private AccountDefaultController controller;
    private AccountIntrospectTool mcpTool;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        catalog = new FileAccountCatalog(tempDir, objectMapper);
        IdentityCache identityCache = new IdentityCache(tempDir);
        identityPlane = new IdentityPlane(identityCache, objectMapper, catalog);

        controller = new AccountDefaultController(catalog, identityPlane);
        mcpTool = new AccountIntrospectTool(catalog, identityPlane, objectMapper);
    }

    @Test
    @DisplayName("REST and MCP account_introspect returns full metadata, slug map, and soul version")
    void accountIntrospectReturnsMetadata() throws Exception {
        String accountId = "acc-introspect-1";
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(accountId, "pw", java.util.List.of());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            Account account = catalog.getOrCreateAccount(accountId);
            catalog.createNamespace(accountId, "project-neuro", NamespaceType.PROJECT);

            UserSoul soul = new UserSoul(
                    accountId, "Alice", "Neuroscientist",
                    null, new float[]{0.1f}, (short) 3,
                    Instant.now(), Instant.now()
            );
            identityPlane.updateAccountSoul(accountId, soul);

            // Test REST controller
            ResponseEntity<AccountIntrospectResponse> restResponse = controller.introspect();
            assertThat(restResponse.getStatusCode().is2xxSuccessful()).isTrue();
            AccountIntrospectResponse body = restResponse.getBody();
            assertThat(body).isNotNull();
            assertThat(body.accountId()).isEqualTo(accountId);
            assertThat(body.slugMap()).containsKey("project-neuro");
            assertThat(body.soulVersion()).isEqualTo((short) 3);

            // Test MCP tool
            McpSchema.CallToolResult mcpResult = mcpTool.execute(Map.of());
            assertThat(mcpResult.isError()).isFalse();
            assertThat(mcpResult.content()).isNotEmpty();
            assertThat(mcpResult.content().get(0)).isInstanceOf(McpSchema.TextContent.class);
            String json = ((McpSchema.TextContent) mcpResult.content().get(0)).text();
            assertThat(json).contains("project-neuro");
            assertThat(json).contains("\"soulVersion\" : 3");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}
