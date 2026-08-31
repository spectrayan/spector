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

import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.exception.DefaultNamespaceProtectedException;
import com.spectrayan.spector.synapse.security.SecurityUtils;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool for soft-deleting (tombstoning) a memory namespace (ADR-0029 §8.2).
 */
@Component
public class NamespaceDeleteTool extends McpToolHandler {

    private static final Logger log = LoggerFactory.getLogger(NamespaceDeleteTool.class);

    private final AccountCatalog catalog;

    public NamespaceDeleteTool(AccountCatalog catalog) {
        super("namespace_delete");
        this.catalog = catalog;
    }

    @Override
    public McpSchema.CallToolResult execute(Map<String, Object> args) throws Exception {
        String target = requireString(args, "namespace");
        String accountId = SecurityUtils.getUserId();
        log.info("[NamespaceDeleteTool] deleting namespace: account={}, target={}", accountId, target);

        try {
            catalog.tombstone(accountId, target);
            return textResult(String.format("Successfully deleted/tombstoned namespace '%s' for account %s.",
                    target, accountId));
        } catch (DefaultNamespaceProtectedException e) {
            return errorResult("Default namespace cannot be deleted. Use reset instead.");
        }
    }
}
