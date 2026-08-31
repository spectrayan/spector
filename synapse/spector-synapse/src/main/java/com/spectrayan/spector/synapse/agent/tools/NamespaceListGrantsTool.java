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
import com.spectrayan.spector.synapse.catalog.Grant;
import com.spectrayan.spector.synapse.catalog.api.GrantResponse;
import com.spectrayan.spector.synapse.security.SecurityUtils;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool for listing active grants on a memory namespace (ADR-0029 §8.2).
 */
@Component
public class NamespaceListGrantsTool extends McpToolHandler {

    private static final Logger log = LoggerFactory.getLogger(NamespaceListGrantsTool.class);

    private final AccountCatalog catalog;
    private final ObjectMapper objectMapper;

    public NamespaceListGrantsTool(AccountCatalog catalog, ObjectMapper objectMapper) {
        this.catalog = catalog;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "namespace_list_grants";
    }

    @Override
    public String description() {
        return "Lists all active grants on a memory namespace. "
                + "Requires the caller to have ADMIN or OWNER role on the namespace.";
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
                        "slug", Map.of(
                                "type", "string",
                                "description", "Namespace slug or identifier"
                        )
                ),
                "required", List.of("slug")
        );
    }

    @Override
    public McpSchema.CallToolResult execute(Map<String, Object> arguments) throws Exception {
        String slug = requireString(arguments, "slug");

        try {
            String callerAccountId = SecurityUtils.getUserId();
            List<Grant> grants = catalog.listGrants(callerAccountId, slug);
            List<GrantResponse> responses = grants.stream().map(GrantResponse::from).toList();
            String json = objectMapper.writeValueAsString(responses);
            return textResult(json);
        } catch (Exception e) {
            log.error("[NamespaceListGrantsTool] Failed to list grants for namespace '{}': {}",
                    slug, e.getMessage());
            return errorResult("Failed to list namespace grants: " + e.getMessage());
        }
    }
}
