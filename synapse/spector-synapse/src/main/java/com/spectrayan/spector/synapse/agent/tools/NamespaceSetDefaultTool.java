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
        super("namespace_set_default");
        this.catalog = catalog;
    }

    @Override
    public McpSchema.CallToolResult execute(Map<String, Object> args) throws Exception {
        String target = optionalString(args, "namespace", null);
        if (target == null || target.isBlank()) {
            target = requireString(args, "slug");
        }
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
