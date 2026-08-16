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
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServlet;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.spectrayan.spector.synapse.agent.ToolRegistry;
import com.spectrayan.spector.synapse.mcp.McpRequestMemory;
import com.spectrayan.spector.synapse.memory.UserMemoryRegistry;
import com.spectrayan.spector.mcp.tools.McpToolHandler;
import com.spectrayan.spector.mcp.tools.SpectorToolRegistry;
import com.spectrayan.spector.memory.SpectorMemory;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Universal Multi-Transport Model Context Protocol (MCP) server configuration.
 *
 * <p>Exposes all official MCP transport protocols simultaneously to support any
 * external AI agent, IDE, or client runtime without workarounds:</p>
 * <ul>
 *   <li><strong>SSE (Server-Sent Events)</strong>: {@code /mcp/sse} (downstream event stream)
 *       and {@code /mcp/message} (upstream JSON-RPC message receiver) for Antigravity remote,
 *       Cursor remote, and Claude Desktop remote.</li>
 *   <li><strong>Streamable HTTP</strong>: {@code /mcp/stream} for modern streamable HTTP clients.</li>
 *   <li><strong>Stateless HTTP</strong>: {@code /mcp} and {@code /mcp/stateless} for serverless,
 *       microservices, and CLI bridges (e.g. {@code mcp-remote}).</li>
 * </ul>
 *
 * <p>Multi-tenant caller isolation is enforced uniformly across all transports via
 * {@link McpRequestMemory}.</p>
 */
@Configuration
public class McpServerConfig {

    private static final String SERVER_NAME = "spector";
    private static final String SERVER_VERSION = "1.0.0";

    @Bean
    public McpJsonMapper mcpJsonMapper() {
        return new JacksonMcpJsonMapper(
                tools.jackson.databind.json.JsonMapper.builder()
                        .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .build());
    }

    // ─────────────────────────────────────────────────────────────
    // 1. STATELESS HTTP TRANSPORT: /mcp and /mcp/stateless
    // ─────────────────────────────────────────────────────────────

    @Bean
    public HttpServletStatelessServerTransport mcpStatelessTransport(McpJsonMapper jsonMapper) {
        return HttpServletStatelessServerTransport.builder()
                .jsonMapper(jsonMapper)
                .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletStatelessServerTransport> mcpStatelessServletRegistration(
            HttpServletStatelessServerTransport transport) {
        ServletRegistrationBean<HttpServletStatelessServerTransport> registration =
                new ServletRegistrationBean<>(transport);
        registration.addUrlMappings("/mcp", "/mcp/stateless");
        registration.setLoadOnStartup(1);
        return registration;
    }

    @Bean
    public McpStatelessSyncServer mcpStatelessServer(HttpServletStatelessServerTransport transport,
                                                      ToolRegistry toolRegistry,
                                                      UserMemoryRegistry userMemoryRegistry,
                                                      SynapseProperties synapseProperties) {
        boolean authEnabled = synapseProperties.auth().enabled();
        List<McpStatelessServerFeatures.SyncToolSpecification> toolSpecs =
                buildStatelessToolSpecs(toolRegistry, userMemoryRegistry, authEnabled);

        return McpServer.sync(transport)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .resources(false, false)
                        .prompts(false)
                        .build())
                .tools(toolSpecs)
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // 2. SSE TRANSPORT: /mcp/sse (stream) & /mcp/message (messages)
    // ─────────────────────────────────────────────────────────────

    @Bean
    public HttpServletSseServerTransportProvider mcpSseTransportProvider(McpJsonMapper jsonMapper) {
        return HttpServletSseServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .sseEndpoint("/mcp/sse")
                .messageEndpoint("/mcp/message")
                .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServlet> mcpSseServletRegistration(
            HttpServletSseServerTransportProvider transportProvider) {
        ServletRegistrationBean<HttpServlet> registration =
                new ServletRegistrationBean<>(transportProvider);
        registration.addUrlMappings("/mcp/sse", "/mcp/message");
        registration.setAsyncSupported(true);
        registration.setLoadOnStartup(1);
        return registration;
    }

    @Bean
    public McpSyncServer mcpSseServer(HttpServletSseServerTransportProvider transportProvider,
                                      ToolRegistry toolRegistry,
                                      UserMemoryRegistry userMemoryRegistry,
                                      SynapseProperties synapseProperties) {
        boolean authEnabled = synapseProperties.auth().enabled();
        List<McpServerFeatures.SyncToolSpecification> toolSpecs =
                buildSyncToolSpecs(toolRegistry, userMemoryRegistry, authEnabled);

        return McpServer.sync(transportProvider)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .resources(false, false)
                        .prompts(false)
                        .build())
                .tools(toolSpecs)
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // 3. STREAMABLE HTTP TRANSPORT: /mcp/stream
    // ─────────────────────────────────────────────────────────────

    @Bean
    public HttpServletStreamableServerTransportProvider mcpStreamableTransportProvider(McpJsonMapper jsonMapper) {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(jsonMapper)
                .mcpEndpoint("/mcp/stream")
                .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServlet> mcpStreamableServletRegistration(
            HttpServletStreamableServerTransportProvider transportProvider) {
        ServletRegistrationBean<HttpServlet> registration =
                new ServletRegistrationBean<>(transportProvider);
        registration.addUrlMappings("/mcp/stream");
        registration.setAsyncSupported(true);
        registration.setLoadOnStartup(1);
        return registration;
    }

    @Bean
    public McpSyncServer mcpStreamableServer(HttpServletStreamableServerTransportProvider transportProvider,
                                             ToolRegistry toolRegistry,
                                             UserMemoryRegistry userMemoryRegistry,
                                             SynapseProperties synapseProperties) {
        boolean authEnabled = synapseProperties.auth().enabled();
        List<McpServerFeatures.SyncToolSpecification> toolSpecs =
                buildSyncToolSpecs(toolRegistry, userMemoryRegistry, authEnabled);

        return McpServer.sync(transportProvider)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .resources(false, false)
                        .prompts(false)
                        .build())
                .tools(toolSpecs)
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // TOOL SPECIFICATION BUILDERS & MEMORY BINDING
    // ─────────────────────────────────────────────────────────────

    private static List<McpStatelessServerFeatures.SyncToolSpecification> buildStatelessToolSpecs(
            ToolRegistry toolRegistry, UserMemoryRegistry userMemoryRegistry, boolean authEnabled) {
        return toolRegistry.all().values().stream()
                .map(mcpTool -> {
                    var tool = McpSchema.Tool.builder(mcpTool.name())
                            .description(mcpTool.description())
                            .inputSchema(mcpTool.inputSchema())
                            .build();

                    return new McpStatelessServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
                        Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
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
    }

    private static List<McpServerFeatures.SyncToolSpecification> buildSyncToolSpecs(
            ToolRegistry toolRegistry, UserMemoryRegistry userMemoryRegistry, boolean authEnabled) {
        return toolRegistry.all().values().stream()
                .map(mcpTool -> {
                    var tool = McpSchema.Tool.builder(mcpTool.name())
                            .description(mcpTool.description())
                            .inputSchema(mcpTool.inputSchema())
                            .build();

                    return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
                        Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
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
    }

    /** Builds an MCP tool error result carrying the given message. */
    private static McpSchema.CallToolResult toolError(String message) {
        return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("Error: " + message)),
                true, null, null);
    }

    @Bean(name = "coreMemoryTools")
    public List<McpToolHandler> coreMemoryTools(ObjectProvider<SpectorMemory> sharedMemory) {
        Supplier<SpectorMemory> resolver = () -> {
            SpectorMemory perUser = McpRequestMemory.current();
            return perUser != null ? perUser : sharedMemory.getIfAvailable();
        };
        return SpectorToolRegistry.handlers(SERVER_VERSION, resolver);
    }
}
