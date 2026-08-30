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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.security.SecurityUtils;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool for persisting an account's default namespace (ADR-0029 §8.2).
 */
@Component
public class NamespaceSetDefaultTool extends McpToolHandler {

    private static final Logger log = LoggerFactory.getLogger(NamespaceSetDefaultTool.class);

    private final AccountCatalog catalog;

    public NamespaceSetDefaultTool(AccountCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public String name() {
        return "namespace_set_default";
    }

    @Override
    public String description() {
        return "Persistently sets the default namespace for the authenticated account in the catalog. "
                + "Durable across restarts and new sessions.";
    }

    @Override
    public McpToolCategory category() {
        return McpToolCategory.MEMORY;
    }

    @Override
    public boolean isWriteTool() {
        return true;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "namespace", Map.of(
                                "type", "string",
                                "description", "Namespace slug or identifier to set as the durable account default"
                        )
                ),
                "required", List.of("namespace")
        );
    }

    @Override
    public McpSchema.CallToolResult execute(Map<String, Object> args) throws Exception {
        String target = requireString(args, "namespace");
        String accountId = SecurityUtils.getUserId();
        log.info("[NamespaceSetDefaultTool] set default namespace: account={}, target={}", accountId, target);

        Optional<NamespaceRecord> record = catalog.resolve(accountId, target);
        if (record.isEmpty()) {
            return errorResult("Namespace not found: '" + target + "' for account " + accountId);
        }

        NamespaceRecord ns = record.get();
        catalog.setDefaultNamespace(accountId, ns.namespaceId());

        return textResult(String.format(
                "Successfully set default namespace to '%s' (ID: %s) for account %s.",
                ns.slug(), ns.namespaceId(), accountId
        ));
    }
}
