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
package com.spectrayan.spector.synapse.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Registry for all available agent tools — bridges Spector's {@link AgentTool}
 * interface with LangChain4j's tool calling framework.
 *
 * <p>Tools are auto-discovered via Spring component scan. Any bean implementing
 * {@link AgentTool} is automatically registered at startup and made available
 * as {@link ToolSpecification} for LangGraph4j agentic graphs.</p>
 *
 * <h3>LangGraph4j-Spring AI Integration</h3>
 * <p>This registry provides {@link #toolSpecifications()} which can be directly
 * passed to LangGraph4j-Spring AI's agent executor nodes, and
 * {@link #executeTool(ToolExecutionRequest)} for dispatching tool calls.</p>
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ConcurrentHashMap<String, AgentTool> tools = new ConcurrentHashMap<>();

    /**
     * Auto-register all AgentTool beans found by Spring.
     */
    public ToolRegistry(List<AgentTool> toolBeans) {
        for (AgentTool tool : toolBeans) {
            tools.put(tool.name(), tool);
            log.info("[ToolRegistry] Registered tool: {} [{}] — {}",
                    tool.name(), tool.category(), tool.description());
        }
        log.info("[ToolRegistry] {} tools registered", tools.size());
    }

    /** Get a tool by name. */
    public Optional<AgentTool> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /** Get all registered tool names. */
    public Set<String> names() {
        return Collections.unmodifiableSet(tools.keySet());
    }

    /** Get all registered tools. */
    public Map<String, AgentTool> all() {
        return Collections.unmodifiableMap(tools);
    }

    /** Register a tool dynamically at runtime. */
    public void register(AgentTool tool) {
        tools.put(tool.name(), tool);
        log.info("[ToolRegistry] Dynamically registered tool: {} [{}]", tool.name(), tool.category());
    }

    /** Check if a tool is registered. */
    public boolean has(String name) {
        return tools.containsKey(name);
    }

    /** Get tools matching a list of names (for agent soul filtering). */
    public List<AgentTool> forNames(List<String> names) {
        return names.stream()
                .map(tools::get)
                .filter(t -> t != null)
                .toList();
    }

    /** Get tools by category. */
    public List<AgentTool> byCategory(AgentTool.ToolCategory category) {
        return tools.values().stream()
                .filter(t -> t.category() == category)
                .toList();
    }

    /** Get only write tools (requiring supervised approval). */
    public List<AgentTool> writeTools() {
        return tools.values().stream()
                .filter(AgentTool::isWriteTool)
                .toList();
    }

    // ═══════════════════════════════════════════════════════════════
    // LangChain4j / LangGraph4j Integration
    // ═══════════════════════════════════════════════════════════════

    /**
     * Builds LangChain4j {@link ToolSpecification} list for all registered tools.
     *
     * <p>These specifications are used by LangGraph4j-Spring AI's agent nodes
     * to present available tools to the LLM for function calling.</p>
     */
    public List<ToolSpecification> toolSpecifications() {
        return tools.values().stream()
                .map(ToolRegistry::toToolSpecification)
                .toList();
    }

    /**
     * Executes a tool by name from a {@link ToolExecutionRequest}.
     *
     * <p>LangGraph4j dispatches tool calls through this method after the LLM
     * selects a tool via function calling.</p>
     *
     * @param request the tool execution request from the LLM
     * @return the tool execution result as a string
     */
    public String executeTool(ToolExecutionRequest request) {
        AgentTool tool = tools.get(request.name());
        if (tool == null) {
            return "Error: Unknown tool '" + request.name() + "'";
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> args = request.arguments() != null && !request.arguments().isBlank()
                    ? MAPPER.readValue(request.arguments(), Map.class)
                    : Map.of();
            return tool.execute(args);
        } catch (Exception e) {
            log.warn("[ToolRegistry] Tool '{}' execution failed: {}", tool.name(), e.getMessage());
            return "Error executing tool '" + tool.name() + "': " + e.getMessage();
        }
    }

    /**
     * Builds OpenAI-compatible function definitions for REST API responses.
     *
     * <p>Used by the agent tools listing endpoint to present tool schemas
     * to the UI/API clients.</p>
     */
    public List<Map<String, Object>> toFunctionDefinitions() {
        return tools.values().stream()
                .map(tool -> Map.<String, Object>of(
                        "name", tool.name(),
                        "description", tool.description(),
                        "parameters", tool.parameterSchema(),
                        "category", tool.category().name(),
                        "isWriteTool", tool.isWriteTool()
                ))
                .toList();
    }

    // ── Internal helpers ──────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static ToolSpecification toToolSpecification(AgentTool tool) {
        var builder = ToolSpecification.builder()
                .name(tool.name())
                .description(tool.description());

        Map<String, Object> schema = tool.parameterSchema();
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
