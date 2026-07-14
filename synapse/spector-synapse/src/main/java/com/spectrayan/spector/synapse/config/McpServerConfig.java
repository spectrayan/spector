/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.synapse.config;

import com.spectrayan.spector.synapse.agent.ToolRegistry;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * Exposes the official Model Context Protocol (MCP) server over HTTP/SSE.
 *
 * Exposes registered Spring-managed tools dynamically, conforming strictly to the
 * official MCP specification.
 */
@Configuration
public class McpServerConfig {

    @Bean
    public HttpServletSseServerTransportProvider mcpTransportProvider() {
        McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(
                tools.jackson.databind.json.JsonMapper.builder().build());

        return HttpServletSseServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .sseEndpoint("/mcp")
                .messageEndpoint("/mcp/message")
                .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletSseServerTransportProvider> mcpServletRegistrationBean(
            HttpServletSseServerTransportProvider transportProvider) {
        ServletRegistrationBean<HttpServletSseServerTransportProvider> registration =
                new ServletRegistrationBean<>(transportProvider);
        registration.addUrlMappings("/mcp", "/mcp/*");
        registration.setLoadOnStartup(1);
        return registration;
    }

    @Bean
    public McpSyncServer mcpSyncServer(HttpServletSseServerTransportProvider transportProvider,
                                       ToolRegistry toolRegistry) {

        // Dynamically translate local AgentTool beans to official MCP SDK ToolSpecifications
        List<McpServerFeatures.SyncToolSpecification> toolSpecs = toolRegistry.all().values().stream()
                .map(agentTool -> {
                    var tool = McpSchema.Tool.builder(agentTool.name())
                            .description(agentTool.description())
                            .inputSchema(agentTool.parameterSchema())
                            .build();

                    return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
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

        McpSyncServer server = McpServer.sync(transportProvider)
                .serverInfo("spector", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .resources(false, false)
                        .prompts(false)
                        .build())
                .tools(toolSpecs)
                .build();

        return server;
    }
}
