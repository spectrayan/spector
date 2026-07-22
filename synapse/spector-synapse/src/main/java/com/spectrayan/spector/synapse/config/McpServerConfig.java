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
package com.spectrayan.spector.synapse.config;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.spectrayan.spector.synapse.agent.ToolRegistry;
import com.spectrayan.spector.synapse.mcp.McpRequestMemory;
import com.spectrayan.spector.synapse.memory.UserMemoryRegistry;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Exposes the official Model Context Protocol (MCP) server over stateless HTTP.
 *
 * <p>Uses {@link HttpServletStatelessServerTransport} which handles each request
 * independently — no sessions, no SSE streaming, no persistent connections.
 * Each POST to {@code /mcp} receives a direct JSON response and closes.</p>
 *
 * <p>This is ideal for IDE integrations (Antigravity, Cursor, etc.) that only need
 * tool discovery and invocation without server-initiated notifications.</p>
 */
@Configuration
public class McpServerConfig {

    @Bean
    public McpJsonMapper mcpJsonMapper() {
        return new JacksonMcpJsonMapper(
                tools.jackson.databind.json.JsonMapper.builder()
                        .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .build());
    }

    @Bean
    public HttpServletStatelessServerTransport mcpTransport(McpJsonMapper jsonMapper) {
        return HttpServletStatelessServerTransport.builder()
                .jsonMapper(jsonMapper)
                .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletStatelessServerTransport> mcpServletRegistrationBean(
            HttpServletStatelessServerTransport transport) {
        ServletRegistrationBean<HttpServletStatelessServerTransport> registration =
                new ServletRegistrationBean<>(transport);
        registration.addUrlMappings("/mcp");
        registration.setLoadOnStartup(1);
        return registration;
    }

    @Bean
    public McpStatelessSyncServer mcpStatelessServer(HttpServletStatelessServerTransport transport,
                                                      ToolRegistry toolRegistry,
                                                      UserMemoryRegistry userMemoryRegistry,
                                                      SynapseProperties synapseProperties) {

        boolean authEnabled = synapseProperties.auth().enabled();

        // Dynamically translate local McpToolHandler beans to official MCP SDK ToolSpecifications.
        // Each tool executes synchronously on the servlet request thread, so we resolve the caller's
        // per-user memory (via UserMemoryRegistry) on that same thread and bind it for the duration
        // of the invocation. The tool routes exclusively to the authenticated user's namespace;
        // client-supplied namespace/workspace_id/agent_id hints never widen scope to another user.
        List<McpStatelessServerFeatures.SyncToolSpecification> toolSpecs = toolRegistry.all().values().stream()
                .map(mcpTool -> {
                    var tool = McpSchema.Tool.builder(mcpTool.name())
                            .description(mcpTool.description())
                            .inputSchema(mcpTool.inputSchema())
                            .build();

                    return new McpStatelessServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
                        Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();

                        // Resolve + bind the caller's memory on the servlet request thread. Denies
                        // (auth-required / resolution-failed) fail closed without touching memory.
                        Optional<McpRequestMemory.DenyReason> deny =
                                McpRequestMemory.bindForCurrentRequest(userMemoryRegistry, authEnabled);
                        if (deny.isPresent()) {
                            return toolError(McpRequestMemory.message(deny.get()));
                        }
                        try {
                            return mcpTool.execute(null, args);
                        } catch (Exception e) {
                            return toolError(e.getMessage());
                        } finally {
                            McpRequestMemory.clear();
                        }
                    });
                })
                .toList();

        return McpServer.sync(transport)
                .serverInfo("spector", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .resources(false, false)
                        .prompts(false)
                        .build())
                .tools(toolSpecs)
                .build();
    }

    /** Builds an MCP tool error result carrying the given message. */
    private static McpSchema.CallToolResult toolError(String message) {
        return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("Error: " + message)),
                true, null, null);
    }
}
