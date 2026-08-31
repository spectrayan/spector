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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.security.SecurityUtils;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool for revoking a grant on a memory namespace (ADR-0029 §8.2).
 */
@Component
public class NamespaceRevokeTool extends McpToolHandler {

    private static final Logger log = LoggerFactory.getLogger(NamespaceRevokeTool.class);

    private final AccountCatalog catalog;
    private final ObjectMapper objectMapper;

    public NamespaceRevokeTool(AccountCatalog catalog, ObjectMapper objectMapper) {
        this.catalog = catalog;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "namespace_revoke";
    }

    @Override
    public String description() {
        return "Revokes a previously granted access permission on a memory namespace. "
                + "Requires the caller to have ADMIN or OWNER role on the namespace.";
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
                        "slug", Map.of(
                                "type", "string",
                                "description", "Namespace slug or identifier"
                        ),
                        "grantId", Map.of(
                                "type", "string",
                                "description", "Unique identifier of the grant to revoke"
                        )
                ),
                "required", List.of("slug", "grantId")
        );
    }

    @Override
    public McpSchema.CallToolResult execute(Map<String, Object> arguments) throws Exception {
        String slug = requireString(arguments, "slug");
        String grantId = requireString(arguments, "grantId");

        try {
            String callerAccountId = SecurityUtils.getUserId();
            catalog.revokeNamespaceGrant(callerAccountId, slug, grantId);
            return textResult("{\"status\":\"revoked\",\"grantId\":\"" + grantId + "\",\"namespace\":\"" + slug + "\"}");
        } catch (Exception e) {
            log.error("[NamespaceRevokeTool] Failed to revoke grant '{}' on namespace '{}': {}",
                    grantId, slug, e.getMessage());
            return errorResult("Failed to revoke namespace grant: " + e.getMessage());
        }
    }
}
