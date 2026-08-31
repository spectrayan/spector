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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.api.NamespaceResponse;
import com.spectrayan.spector.synapse.security.SecurityUtils;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool for retrieving details of a memory namespace (ADR-0029 §8.2).
 */
@Component
public class NamespaceInfoTool extends McpToolHandler {

    private static final Logger log = LoggerFactory.getLogger(NamespaceInfoTool.class);

    private final AccountCatalog catalog;
    private final ObjectMapper objectMapper;

    public NamespaceInfoTool(AccountCatalog catalog, ObjectMapper objectMapper) {
        super("namespace_info");
        this.catalog = catalog;
        this.objectMapper = objectMapper;
    }

    @Override
    public McpSchema.CallToolResult execute(Map<String, Object> args) throws Exception {
        String target = requireString(args, "namespace");
        String accountId = SecurityUtils.getUserId();
        log.debug("[NamespaceInfoTool] inspect namespace: account={}, target={}", accountId, target);

        Optional<NamespaceRecord> record = catalog.resolve(accountId, target);
        if (record.isEmpty()) {
            return errorResult("Namespace not found: '" + target + "' for account " + accountId);
        }

        return textResult(objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(NamespaceResponse.from(record.get())));
    }
}
