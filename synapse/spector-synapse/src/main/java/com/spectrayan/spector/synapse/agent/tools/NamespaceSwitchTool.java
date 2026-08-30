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
import com.spectrayan.spector.synapse.catalog.NamespaceStatus;
import com.spectrayan.spector.synapse.mcp.McpSessionContext;
import com.spectrayan.spector.synapse.security.SecurityUtils;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool for setting a connection-scoped default namespace (ADR-0029 §6.2, §8.2).
 *
 * <p>The switch applies to subsequent tool invocations on the active connection.
 * It is non-durable and does not persist across restarts or new connections
 * unless {@code namespace_set_default} is called.</p>
 */
@Component
public class NamespaceSwitchTool extends McpToolHandler {

    private static final Logger log = LoggerFactory.getLogger(NamespaceSwitchTool.class);

    private final AccountCatalog catalog;

    public NamespaceSwitchTool(AccountCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public String name() {
        return "namespace_switch";
    }

    @Override
    public String description() {
        return "Sets the connection-scoped active default namespace for subsequent memory tool invocations "
                + "(remember, recall, forget, etc.) in the current session. Non-durable.";
    }

    @Override
    public McpToolCategory category() {
        return McpToolCategory.MEMORY;
    }

    @Override
    public boolean isWriteTool() {
        return false;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "namespace", Map.of(
                                "type", "string",
                                "description", "Namespace slug or identifier to switch to as connection default"
                        )
                ),
                "required", List.of("namespace")
        );
    }

    @Override
    public McpSchema.CallToolResult execute(Map<String, Object> args) throws Exception {
        String target = requireString(args, "namespace");
        if ("*".equals(target.trim())) {
            return errorResult("Wildcard namespace '*' cannot be set as connection default.");
        }

        String accountId = SecurityUtils.getUserId();
        log.info("[NamespaceSwitchTool] switching session namespace: account={}, target={}", accountId, target);

        Optional<NamespaceRecord> record = catalog.resolve(accountId, target);
        if (record.isEmpty()) {
            return errorResult("Namespace not found: '" + target + "' for account " + accountId);
        }

        NamespaceRecord ns = record.get();
        if (ns.status() == NamespaceStatus.TOMBSTONED) {
            return errorResult("Cannot switch to tombstoned namespace: '" + target + "' (id: " + ns.namespaceId() + ")");
        }

        // Set on connection / thread session context
        McpSessionContext.setSessionDefault(null, ns.namespaceId());

        return textResult(String.format(
                "Switched active connection namespace to '%s' (ID: %s, type: %s). "
                + "Subsequent memory operations without an explicit namespace argument will target this namespace.",
                ns.slug(), ns.namespaceId(), ns.type()
        ));
    }
}
