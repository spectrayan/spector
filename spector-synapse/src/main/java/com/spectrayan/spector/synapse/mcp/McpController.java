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
package com.spectrayan.spector.synapse.mcp;

import com.spectrayan.spector.synapse.agent.AgentTool;
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
                    descriptor.put("inputSchema", tool.parameterSchema());
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

        AgentTool tool = toolRegistry.get(name).orElse(null);
        if (tool == null) {
            return Map.of("error", "Tool not found: " + name, "isError", true);
        }

        try {
            String result = tool.execute(arguments);
            log.info("[MCP] Tool '{}' invoked — result: {} chars", name, result.length());
            return Map.of(
                    "content", List.of(Map.of("type", "text", "text", result)),
                    "isError", false
            );
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
