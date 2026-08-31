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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.Grant;
import com.spectrayan.spector.synapse.catalog.GrantConstraints;
import com.spectrayan.spector.synapse.catalog.GrantRole;
import com.spectrayan.spector.synapse.catalog.api.GrantResponse;
import com.spectrayan.spector.synapse.security.SecurityUtils;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool for granting namespace access to another account (ADR-0029 §8.2).
 */
@Component
public class NamespaceGrantTool extends McpToolHandler {

    private static final Logger log = LoggerFactory.getLogger(NamespaceGrantTool.class);

    private final AccountCatalog catalog;
    private final ObjectMapper objectMapper;

    public NamespaceGrantTool(AccountCatalog catalog, ObjectMapper objectMapper) {
        this.catalog = catalog;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "namespace_grant";
    }

    @Override
    public String description() {
        return "Grants access permissions (READER, WRITER, ADMIN) for a memory namespace to another account TSID. "
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
                                "description", "Namespace slug or namespace identifier"
                        ),
                        "granteeAccountId", Map.of(
                                "type", "string",
                                "description", "The TSID account identifier receiving the grant"
                        ),
                        "role", Map.of(
                                "type", "string",
                                "description", "Grant role to assign: READER, WRITER, ADMIN",
                                "enum", List.of("READER", "WRITER", "ADMIN")
                        ),
                        "expiresInSeconds", Map.of(
                                "type", "integer",
                                "description", "Optional expiration duration in seconds from now"
                        ),
                        "tagPrefix", Map.of(
                                "type", "string",
                                "description", "Optional tag prefix filter constraint"
                        )
                ),
                "required", List.of("slug", "granteeAccountId", "role")
        );
    }

    @Override
    public McpSchema.CallToolResult execute(Map<String, Object> arguments) throws Exception {
        String slug = requireString(arguments, "slug");
        String granteeAccountId = requireString(arguments, "granteeAccountId");
        String roleStr = requireString(arguments, "role");

        GrantRole role;
        try {
            role = GrantRole.valueOf(roleStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return errorResult("Invalid role: " + roleStr + ". Allowed values: READER, WRITER, ADMIN");
        }

        Instant expiresAt = null;
        if (arguments.containsKey("expiresInSeconds") && arguments.get("expiresInSeconds") instanceof Number num) {
            expiresAt = Instant.now().plusSeconds(num.longValue());
        }

        GrantConstraints constraints = null;
        if (arguments.containsKey("tagPrefix") && arguments.get("tagPrefix") instanceof String tp && !tp.isBlank()) {
            constraints = new GrantConstraints(null, tp.trim(), null, null);
        }

        try {
            String callerAccountId = SecurityUtils.getUserId();
            Grant created = catalog.grantNamespace(callerAccountId, slug, granteeAccountId, role, expiresAt, constraints);
            GrantResponse response = GrantResponse.from(created);
            String json = objectMapper.writeValueAsString(response);
            return textResult(json);
        } catch (Exception e) {
            log.error("[NamespaceGrantTool] Failed to grant access on namespace '{}' to '{}': {}",
                    slug, granteeAccountId, e.getMessage());
            return errorResult("Failed to grant namespace access: " + e.getMessage());
        }
    }
}
