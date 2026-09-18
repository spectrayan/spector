/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
        super("namespace_grant");
        this.catalog = catalog;
        this.objectMapper = objectMapper;
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
