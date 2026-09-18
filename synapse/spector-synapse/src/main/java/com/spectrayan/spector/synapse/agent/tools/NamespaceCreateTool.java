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

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.NamespaceType;
import com.spectrayan.spector.synapse.catalog.api.NamespaceResponse;
import com.spectrayan.spector.synapse.security.SecurityUtils;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool for creating a new memory namespace (ADR-0029 §8.2).
 */
@Component
public class NamespaceCreateTool extends McpToolHandler {

    private static final Logger log = LoggerFactory.getLogger(NamespaceCreateTool.class);

    private final AccountCatalog catalog;
    private final ObjectMapper objectMapper;

    public NamespaceCreateTool(AccountCatalog catalog, ObjectMapper objectMapper) {
        super("namespace_create");
        this.catalog = catalog;
        this.objectMapper = objectMapper;
    }

    @Override
    public McpSchema.CallToolResult execute(Map<String, Object> args) throws Exception {
        String slug = requireString(args, "slug");
        String typeStr = optionalString(args, "type", "PROJECT");
        NamespaceType type;
        try {
            type = NamespaceType.valueOf(typeStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return errorResult("Invalid namespace type: '" + typeStr + "'. Must be PROJECT, AGENT, SHARED, or ARCHIVE.");
        }

        String displayName = optionalString(args, "displayName", null);
        String description = optionalString(args, "description", null);

        String accountId = SecurityUtils.getUserId();
        log.info("[NamespaceCreateTool] creating namespace: account={}, slug={}, type={}", accountId, slug, type);

        try {
            NamespaceRecord created = catalog.createNamespace(accountId, slug, type, displayName, description, null);
            return textResult("Namespace created successfully:\n"
                    + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(NamespaceResponse.from(created)));
        } catch (IllegalArgumentException e) {
            return errorResult(e.getMessage());
        }
    }
}
