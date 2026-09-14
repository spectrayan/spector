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
package com.spectrayan.spector.synapse.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.synapse.security.toolaccess.ToolAccessPolicy;
import io.modelcontextprotocol.spec.McpSchema;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for all available agent tools — bridges Spector's {@link McpToolHandler}
 * model with LangChain4j's tool calling framework.
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ConcurrentHashMap<String, McpToolHandler> tools = new ConcurrentHashMap<>();
    private final ToolAccessPolicy toolAccessPolicy;

    /**
     * Helper constructor for testing and manual registry creation.
     */
    public ToolRegistry(List<? extends McpToolHandler> individualBeans) {
        this(individualBeans, null);
    }

    /**
     * Helper constructor for testing with an explicit tool-access policy.
     */
    public ToolRegistry(List<? extends McpToolHandler> individualBeans, ToolAccessPolicy toolAccessPolicy) {
        this.toolAccessPolicy = toolAccessPolicy;
        if (individualBeans != null) {
            for (McpToolHandler tool : individualBeans) {
                tools.put(tool.name(), tool);
            }
        }
    }

    /**
     * Auto-register all individual and bulk McpToolHandler beans found by Spring.
     */
    @org.springframework.beans.factory.annotation.Autowired
    public ToolRegistry(
            org.springframework.beans.factory.ObjectProvider<McpToolHandler> individualBeansProvider,
            org.springframework.beans.factory.ObjectProvider<List<McpToolHandler>> bulkBeansProvider,
            org.springframework.beans.factory.ObjectProvider<ToolAccessPolicy> toolAccessPolicyProvider) {
        this.toolAccessPolicy = toolAccessPolicyProvider != null
                ? toolAccessPolicyProvider.getIfAvailable()
                : null;

        individualBeansProvider.orderedStream().forEach(tool -> {
            tools.put(tool.name(), tool);
            log.info("[ToolRegistry] Registered individual tool: {} [{}] — {}",
                    tool.name(), tool.category(), tool.description());
        });

        bulkBeansProvider.orderedStream().forEach(list -> {
            if (list != null) {
                for (McpToolHandler tool : list) {
                    tools.put(tool.name(), tool);
                    log.info("[ToolRegistry] Registered bulk tool: {} [{}] — {}",
                            tool.name(), tool.category(), tool.description());
                }
            }
        });
        log.info("[ToolRegistry] {} tools successfully registered", tools.size());
    }

    /** Get a tool by name. */
    public Optional<McpToolHandler> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /** Get all registered tool names. */
    public Set<String> names() {
        return Collections.unmodifiableSet(tools.keySet());
    }

    /** Get all registered tools. */
    public Map<String, McpToolHandler> all() {
        return Collections.unmodifiableMap(tools);
    }

    /** Register a tool dynamically at runtime. */
    public void register(McpToolHandler tool) {
        tools.put(tool.name(), tool);
        log.info("[ToolRegistry] Dynamically registered tool: {} [{}]", tool.name(), tool.category());
    }

    /** Check if a tool is registered. */
    public boolean has(String name) {
        return tools.containsKey(name);
    }

    /** Get tools matching a list of names (for agent soul filtering). */
    public List<McpToolHandler> forNames(List<String> names) {
        return names.stream()
                .map(tools::get)
                .filter(t -> t != null)
                .toList();
    }

    /** Get tools by category. */
    public List<McpToolHandler> byCategory(McpToolHandler.McpToolCategory category) {
        return tools.values().stream()
                .filter(t -> t.category() == category)
                .toList();
    }

    /** Get only write tools (requiring supervised approval). */
    public List<McpToolHandler> writeTools() {
        return tools.values().stream()
                .filter(McpToolHandler::isWriteTool)
                .toList();
    }

    /** Builds LangChain4j ToolSpecification list for all registered tools. */
    public List<ToolSpecification> toolSpecifications() {
        return tools.values().stream()
                .map(ToolRegistry::toToolSpecification)
                .toList();
    }

    /**
     * Resolves tool specifications for an agent soul (list path).
     *
     * <p>Effective set = Synapse {@link ToolAccessPolicy} ∩ {@code soul.tools}
     * when {@code soul.tools} is non-empty; otherwise policy alone. When no
     * policy bean is present, falls back to the legacy soul.tools / all-tools
     * behavior.</p>
     */
    public List<ToolSpecification> resolveToolSpecs(AgentSoul soul) {
        String agentId = soul != null ? soul.id() : null;
        List<String> hint = soul != null && soul.tools() != null ? soul.tools() : List.of();
        Set<String> allowed = effectiveToolNames(agentId, hint);
        return tools.values().stream()
                .filter(t -> allowed.contains(t.name()))
                .map(ToolRegistry::toToolSpecification)
                .toList();
    }

    /**
     * Returns whether the agent may invoke {@code toolName} (execute path).
     * Must use the same effective set as {@link #resolveToolSpecs(AgentSoul)}.
     */
    public boolean isToolAllowed(String agentId, String toolName, List<String> soulToolsHint) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        return effectiveToolNames(agentId, soulToolsHint).contains(toolName);
    }

    /**
     * Executes a tool after enforcing Synapse tool-access policy for the agent.
     *
     * @param request        LangChain4j tool request
     * @param agentId        soul/agent id (may be null)
     * @param soulToolsHint  {@code AgentSoul.tools} capability hint
     * @return tool output or a clear permission error (SPE-500-013)
     */
    public String executeTool(ToolExecutionRequest request, String agentId, List<String> soulToolsHint) {
        if (request == null || request.name() == null) {
            return "Error: Invalid tool request";
        }
        if (!isToolAllowed(agentId, request.name(), soulToolsHint)) {
            log.warn("[ToolRegistry] Permission denied for tool '{}' agentId={}",
                    request.name(), agentId != null ? agentId : "");
            return ToolAccessPolicy.permissionDeniedMessage(request.name());
        }
        return executeTool(request);
    }

    private Set<String> effectiveToolNames(String agentId, List<String> soulToolsHint) {
        List<String> hint = soulToolsHint != null ? soulToolsHint : List.of();
        if (toolAccessPolicy != null) {
            return toolAccessPolicy.effectiveTools(agentId, hint, tools.keySet());
        }
        // Legacy: no policy bean — soul.tools allowlist or all tools
        if (!hint.isEmpty()) {
            java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
            for (String name : hint) {
                if (tools.containsKey(name)) {
                    names.add(name);
                }
            }
            return names;
        }
        return Set.copyOf(tools.keySet());
    }

    /** Executes a tool by name from a ToolExecutionRequest. */
    public String executeTool(ToolExecutionRequest request) {
        McpToolHandler tool = tools.get(request.name());
        if (tool == null) {
            return "Error: Unknown tool '" + request.name() + "'";
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> args = request.arguments() != null && !request.arguments().isBlank()
                    ? MAPPER.readValue(request.arguments(), Map.class)
                    : Map.of();
            
            // Execute the MCP tool
            McpSchema.CallToolResult result = tool.execute(args);
            if (result == null || result.content() == null) {
                return "";
            }
            
            StringBuilder sb = new StringBuilder();
            for (var content : result.content()) {
                if (content instanceof McpSchema.TextContent textContent) {
                    sb.append(textContent.text());
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("[ToolRegistry] Tool '{}' execution failed: {}", tool.name(), e.getMessage());
            return "Error executing tool '" + tool.name() + "': " + e.getMessage();
        }
    }

    /** Builds OpenAI-compatible function definitions for REST API responses. */
    public List<Map<String, Object>> toFunctionDefinitions() {
        return tools.values().stream()
                .map(tool -> Map.<String, Object>of(
                        "name", tool.name(),
                        "description", tool.description(),
                        "parameters", tool.inputSchema(),
                        "category", tool.category().name(),
                        "isWriteTool", tool.isWriteTool()
                ))
                .toList();
    }

    // ── Internal helpers ──────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static ToolSpecification toToolSpecification(McpToolHandler tool) {
        var builder = ToolSpecification.builder()
                .name(tool.name())
                .description(tool.description());

        Map<String, Object> schema = tool.inputSchema();
        if (schema != null && !schema.isEmpty()) {
            var properties = (Map<String, Object>) schema.getOrDefault("properties", Map.of());
            var required = (List<String>) schema.getOrDefault("required", List.of());

            if (!properties.isEmpty()) {
                var objBuilder = JsonObjectSchema.builder();
                objBuilder.addProperties(buildJsonProperties(properties));
                objBuilder.required(required);
                builder.parameters(objBuilder.build());
            }
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, JsonSchemaElement> buildJsonProperties(
            Map<String, Object> properties) {
        Map<String, JsonSchemaElement> result = new LinkedHashMap<>();
        for (var entry : properties.entrySet()) {
            var propDef = (Map<String, Object>) entry.getValue();
            String type = (String) propDef.getOrDefault("type", "string");
            String description = (String) propDef.getOrDefault("description", "");

            result.put(entry.getKey(), switch (type) {
                case "integer" -> JsonIntegerSchema.builder()
                        .description(description).build();
                case "number" -> JsonNumberSchema.builder()
                        .description(description).build();
                case "boolean" -> JsonBooleanSchema.builder()
                        .description(description).build();
                case "array" -> JsonArraySchema.builder()
                        .description(description).build();
                default -> JsonStringSchema.builder()
                        .description(description).build();
            });
        }
        return result;
    }
}
