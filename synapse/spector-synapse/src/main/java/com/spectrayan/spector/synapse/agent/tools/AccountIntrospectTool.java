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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.synapse.catalog.Account;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.api.AccountIntrospectResponse;
import com.spectrayan.spector.synapse.catalog.api.NamespaceResponse;
import com.spectrayan.spector.synapse.identity.IdentityPlane;
import com.spectrayan.spector.synapse.security.SecurityUtils;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool for account-level introspection (ADR-0029 §21).
 */
@Component
public class AccountIntrospectTool extends McpToolHandler {

    private static final Logger log = LoggerFactory.getLogger(AccountIntrospectTool.class);

    private final AccountCatalog catalog;
    private final IdentityPlane identityPlane;
    private final ObjectMapper objectMapper;

    public AccountIntrospectTool(
            AccountCatalog catalog,
            @Autowired(required = false) IdentityPlane identityPlane,
            ObjectMapper objectMapper) {
        super("account_introspect");
        this.catalog = catalog;
        this.identityPlane = identityPlane;
        this.objectMapper = objectMapper;
    }

    @Override
    public McpSchema.CallToolResult execute(Map<String, Object> arguments) throws Exception {
        String accountId = SecurityUtils.getUserId();
        log.debug("[AccountIntrospectTool] introspecting account={}", accountId);

        Account account = catalog.getAccount(accountId);
        List<NamespaceRecord> records = catalog.listAccessible(accountId);

        Map<String, String> slugMap = new HashMap<>();
        List<NamespaceResponse> nsResponses = new ArrayList<>();
        for (NamespaceRecord r : records) {
            slugMap.put(r.slug(), r.namespaceId());
            nsResponses.add(NamespaceResponse.from(r));
        }

        Short soulVersion = null;
        if (identityPlane != null) {
            soulVersion = identityPlane.primarySoulFor(accountId)
                    .map(SoulContext::soulVersion)
                    .orElse(null);
        }

        AccountIntrospectResponse response = new AccountIntrospectResponse(
                account.id(),
                account.displayName(),
                account.kind() != null ? account.kind().name() : null,
                account.profile() != null ? account.profile().name() : null,
                account.defaultNamespaceId(),
                account.quotas(),
                account.flags(),
                account.tenantId(),
                account.legalHold(),
                slugMap,
                nsResponses,
                List.of(),
                soulVersion
        );

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
        return textResult(json);
    }
}
