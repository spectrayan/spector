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
import com.spectrayan.spector.connector.core.CamelConnectorEngine;
import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.runtime.SpectorRuntime;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agent tool to invoke Apache Camel connector routes dynamically.
 *
 * <p>Allows LLM agents to execute connected external services, pipelines,
 * and messaging endpoints (Slack, databases, webhooks, REST, S3) by route ID
 * with parameterized payloads and headers.</p>
 */
@Component
public class CamelRouteInvokerTool extends McpToolHandler {

    private static final Logger log = LoggerFactory.getLogger(CamelRouteInvokerTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CamelConnectorEngine connectorEngine;

    public CamelRouteInvokerTool(CamelConnectorEngine connectorEngine) {
        this.connectorEngine = connectorEngine;
    }

    @Override
    public String name() {
        return "invoke_connector_route";
    }

    @Override
    public String description() {
        return "Invoke a Spector Camel connector route or direct endpoint by route ID or endpoint URI with dynamic payload and headers. Returns the processed output or response.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "routeId", Map.of(
                                "type", "string",
                                "description", "The ID of the connector route or endpoint name (e.g. 'slack-notify', 'my-route', or 'direct:custom')"
                        ),
                        "payload", Map.of(
                                "type", "string",
                                "description", "Message payload/body to pass to the route"
                        ),
                        "headers", Map.of(
                                "type", "object",
                                "description", "Optional map of header key-value pairs (e.g. channel, recipient, subject)"
                        )
                ),
                "required", List.of("routeId")
        );
    }

    @Override
    public McpToolCategory category() {
        return McpToolCategory.DATA;
    }

    @Override
    public Set<String> requiredScopes() {
        return Set.of("connector:write");
    }

    @Override
    @SuppressWarnings("unchecked")
    public McpSchema.CallToolResult execute(SpectorRuntime runtime, Map<String, Object> args) throws Exception {
        String routeId = (String) args.get("routeId");
        if (routeId == null || routeId.isBlank()) {
            return errorResult("Parameter 'routeId' is required.");
        }

        String payload = (String) args.getOrDefault("payload", "");
        Map<String, Object> headers = (Map<String, Object>) args.getOrDefault("headers", Map.of());

        try {
            if (!connectorEngine.isStarted()) {
                connectorEngine.start();
            }

            String endpointUri = routeId.contains(":") ? routeId : "direct:" + routeId;
            ProducerTemplate producer = connectorEngine.camelContext().createProducerTemplate();

            Map<String, Object> camelHeaders = new HashMap<>(headers);
            camelHeaders.put("spector-invoker", "agent-tool");

            Object result = producer.requestBodyAndHeaders(endpointUri, payload, camelHeaders);
            producer.stop();

            String responseString = result != null ? result.toString() : "Route executed successfully with no output body.";
            log.info("[CamelRouteInvokerTool] Successfully invoked endpoint '{}' for routeId '{}'", endpointUri, routeId);
            return textResult(responseString);
        } catch (Exception e) {
            log.error("[CamelRouteInvokerTool] Failed to invoke route '{}': {}", routeId, e.getMessage(), e);
            return errorResult("Failed to invoke route '" + routeId + "': " + e.getMessage());
        }
    }
}
