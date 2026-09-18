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
        super("namespace_list_grants");
        this.catalog = catalog;
        this.objectMapper = objectMapper;
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
