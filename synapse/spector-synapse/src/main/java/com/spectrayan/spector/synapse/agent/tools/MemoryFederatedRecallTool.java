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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.synapse.memory.FederatedRecallRequest;
import com.spectrayan.spector.synapse.memory.FederatedRecallResponse;
import com.spectrayan.spector.synapse.memory.FederatedRecallService;
import com.spectrayan.spector.synapse.security.SecurityUtils;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Declarative MCP tool handler for cross-rememberer federated recall (ADR-0029 §7).
 * Only loaded when federation is explicitly enabled.
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "spector.federation.enabled", havingValue = "true", matchIfMissing = false)
public class MemoryFederatedRecallTool extends McpToolHandler {

    private static final Logger log = LoggerFactory.getLogger(MemoryFederatedRecallTool.class);

    private final FederatedRecallService federatedRecallService;
    private final ObjectMapper objectMapper;

    public MemoryFederatedRecallTool(FederatedRecallService federatedRecallService, ObjectMapper objectMapper) {
        super("memory_federated_recall");
        this.federatedRecallService = federatedRecallService;
        this.objectMapper = objectMapper;
    }

    @Override
    public McpSchema.CallToolResult execute(Map<String, Object> arguments) throws Exception {
        String accountId = SecurityUtils.getUserId();
        String query = arguments.get("query") != null ? arguments.get("query").toString() : null;
        if (query == null || query.isBlank()) {
            query = arguments.get("queryText") != null ? arguments.get("queryText").toString() : null;
        }

        @SuppressWarnings("unchecked")
        List<String> namespaces = arguments.get("namespaces") instanceof List<?> list
                ? list.stream().map(Object::toString).toList()
                : null;

        Integer topK = arguments.get("topK") instanceof Number n ? n.intValue() : null;
        Integer perNamespaceTopK = arguments.get("perNamespaceTopK") instanceof Number n ? n.intValue() : null;
        Integer timeoutMs = arguments.get("timeoutMs") instanceof Number n ? n.intValue() : null;
        Integer maxColdOpens = arguments.get("maxColdOpens") instanceof Number n ? n.intValue() : null;

        FederatedRecallRequest request = new FederatedRecallRequest(
                query,
                namespaces,
                topK,
                perNamespaceTopK,
                timeoutMs,
                maxColdOpens,
                null,
                null
        );

        log.debug("[MemoryFederatedRecallTool] executing for account={}, query='{}'", accountId, query);
        FederatedRecallResponse response = federatedRecallService.federatedRecall(accountId, request);

        String json = objectMapper.writeValueAsString(response);
        return textResult(json);
    }
}
