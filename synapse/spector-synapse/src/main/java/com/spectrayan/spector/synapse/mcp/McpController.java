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
package com.spectrayan.spector.synapse.mcp;

import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.synapse.agent.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP-compatible REST endpoint for Synapse tools.
 *
 * <p>Exposes the Synapse tool registry as an MCP-lite HTTP API, enabling
 * external agents (Claude Desktop, Cursor, etc.) to discover and invoke
 * tools over HTTP. This complements the native {@code spector-mcp} stdio
 * transport with an HTTP-based alternative.</p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET /api/v1/mcp/tools} — List available tools with schemas</li>
 *   <li>{@code POST /api/v1/mcp/tools/{name}/call} — Invoke a tool by name</li>
 *   <li>{@code GET /api/v1/mcp/info} — Server info</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/mcp")
public class McpController {

    private static final Logger log = LoggerFactory.getLogger(McpController.class);

    private final ToolRegistry toolRegistry;

    public McpController(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * List all available tools with their parameter schemas.
     */
    @GetMapping("/tools")
    public List<Map<String, Object>> listTools() {
        return toolRegistry.all().values().stream()
                .map(tool -> {
                    Map<String, Object> descriptor = new LinkedHashMap<>();
                    descriptor.put("name", tool.name());
                    descriptor.put("description", tool.description());
                    descriptor.put("inputSchema", tool.inputSchema());
                    return descriptor;
                })
                .toList();
    }

    /**
     * Invoke a tool by name with the given arguments.
     */
    @PostMapping("/tools/{name}/call")
    public Map<String, Object> callTool(
            @PathVariable String name,
            @RequestBody Map<String, Object> arguments) {

        McpToolHandler tool = toolRegistry.get(name).orElse(null);
        if (tool == null) {
            return Map.of("error", "Tool not found: " + name, "isError", true);
        }

        try {
            io.modelcontextprotocol.spec.McpSchema.CallToolResult toolResult = tool.execute(null, arguments);
            log.info("[MCP] Tool '{}' invoked — success: {}", name, !toolResult.isError());
            
            var response = new java.util.HashMap<String, Object>();
            response.put("isError", toolResult.isError());
            
            var contentList = new java.util.ArrayList<Map<String, Object>>();
            if (toolResult.content() != null) {
                for (var content : toolResult.content()) {
                    if (content instanceof io.modelcontextprotocol.spec.McpSchema.TextContent tc) {
                        contentList.add(Map.of("type", "text", "text", tc.text()));
                    }
                }
            }
            response.put("content", contentList);
            return response;
        } catch (Exception e) {
            log.error("[MCP] Tool '{}' failed: {}", name, e.getMessage(), e);
            return Map.of(
                    "content", List.of(Map.of("type", "text", "text", "Error: " + e.getMessage())),
                    "isError", true
            );
        }
    }

    /**
     * Get server info.
     */
    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "name", "spector-synapse",
                "version", "1.0.0",
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of(
                        "tools", Map.of("listChanged", true),
                        "prompts", Map.of("listChanged", false)
                ),
                "toolCount", toolRegistry.all().size()
        );
    }
}
