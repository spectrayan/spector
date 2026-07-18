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

import com.spectrayan.spector.synapse.agent.ToolRegistry;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

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
                                                      ToolRegistry toolRegistry) {

        // Dynamically translate local AgentTool beans to official MCP SDK ToolSpecifications
        List<McpStatelessServerFeatures.SyncToolSpecification> toolSpecs = toolRegistry.all().values().stream()
                .map(agentTool -> {
                    var tool = McpSchema.Tool.builder(agentTool.name())
                            .description(agentTool.description())
                            .inputSchema(agentTool.parameterSchema())
                            .build();

                    return new McpStatelessServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
                        Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
                        try {
                            String result = agentTool.execute(args);
                            return McpSchema.CallToolResult.builder()
                                    .addTextContent(result)
                                    .isError(false)
                                    .build();
                        } catch (Exception e) {
                            return McpSchema.CallToolResult.builder()
                                    .addTextContent("Error: " + e.getMessage())
                                    .isError(true)
                                    .build();
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
}
