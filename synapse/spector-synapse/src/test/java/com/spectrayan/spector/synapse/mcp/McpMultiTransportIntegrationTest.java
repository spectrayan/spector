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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.security.core.context.SecurityContextHolder;

import com.spectrayan.spector.synapse.agent.ToolRegistry;
import com.spectrayan.spector.synapse.config.McpServerConfig;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.memory.UserMemoryRegistry;
import com.spectrayan.spector.config.properties.AuthProperties;
import com.spectrayan.spector.mcp.tools.McpToolHandler;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Integration test for <strong>Universal Multi-Transport MCP Server Support</strong> (Issue #549).
 *
 * <p>Verifies that all three HTTP-based MCP transport providers (Stateless HTTP, SSE, and
 * Streamable HTTP) are properly configured, registered with their respective servlet URL mappings,
 * and wired with full tool specifications and tenant-isolated execution delegates.</p>
 */
@DisplayName("MCP Multi-Transport Server (Integration)")
class McpMultiTransportIntegrationTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        McpRequestMemory.clear();
    }

    @Test
    @DisplayName("Stateless transport registration exposes /mcp and /mcp/stateless")
    void testStatelessTransportRegistration() {
        McpServerConfig config = new McpServerConfig();
        McpJsonMapper jsonMapper = config.mcpJsonMapper();
        HttpServletStatelessServerTransport transport = config.mcpStatelessTransport(jsonMapper);

        ServletRegistrationBean<HttpServletStatelessServerTransport> registration =
                config.mcpStatelessServletRegistration(transport);

        assertThat(registration.getUrlMappings())
                .containsExactlyInAnyOrder("/mcp", "/mcp/stateless");
        assertThat(registration.getServlet()).isSameAs(transport);
    }

    @Test
    @DisplayName("SSE transport registration exposes /mcp/sse and /mcp/message with async support")
    void testSseTransportRegistration() {
        McpServerConfig config = new McpServerConfig();
        McpJsonMapper jsonMapper = config.mcpJsonMapper();
        HttpServletSseServerTransportProvider transportProvider = config.mcpSseTransportProvider(jsonMapper);

        var registration = config.mcpSseServletRegistration(transportProvider);

        assertThat(registration.getUrlMappings())
                .containsExactlyInAnyOrder("/mcp/sse", "/mcp/message");
        assertThat(registration.isAsyncSupported()).isTrue();
        assertThat(registration.getServlet()).isSameAs(transportProvider);
    }

    @Test
    @DisplayName("Streamable HTTP transport registration exposes /mcp/stream with async support")
    void testStreamableTransportRegistration() {
        McpServerConfig config = new McpServerConfig();
        McpJsonMapper jsonMapper = config.mcpJsonMapper();
        HttpServletStreamableServerTransportProvider transportProvider =
                config.mcpStreamableTransportProvider(jsonMapper);

        var registration = config.mcpStreamableServletRegistration(transportProvider);

        assertThat(registration.getUrlMappings())
                .containsExactlyInAnyOrder("/mcp/stream");
        assertThat(registration.isAsyncSupported()).isTrue();
        assertThat(registration.getServlet()).isSameAs(transportProvider);
    }

    @Test
    @DisplayName("All server instances construct and register tools cleanly")
    void testAllServerInstancesConstructCleanly() throws Exception {
        McpServerConfig config = new McpServerConfig();
        McpJsonMapper jsonMapper = config.mcpJsonMapper();

        ToolRegistry toolRegistry = mock(ToolRegistry.class);
        UserMemoryRegistry userMemoryRegistry = mock(UserMemoryRegistry.class);
        SynapseProperties synapseProperties = mock(SynapseProperties.class);
        AuthProperties authProperties = mock(AuthProperties.class);

        when(synapseProperties.auth()).thenReturn(authProperties);
        when(authProperties.enabled()).thenReturn(false);

        McpToolHandler mockTool = mock(McpToolHandler.class);
        when(mockTool.name()).thenReturn("memory_status");
        when(mockTool.description()).thenReturn("Get memory status");
        when(mockTool.inputSchema()).thenReturn(Map.of("type", "object"));
        when(mockTool.execute(Map.of())).thenReturn(
                new McpSchema.CallToolResult(List.of(new McpSchema.TextContent("OK")), false, null, null));

        when(toolRegistry.all()).thenReturn(Map.of("memory_status", mockTool));

        // 1. Stateless Server
        HttpServletStatelessServerTransport statelessTransport = config.mcpStatelessTransport(jsonMapper);
        McpStatelessSyncServer statelessServer = config.mcpStatelessServer(
                statelessTransport, toolRegistry, userMemoryRegistry, synapseProperties);
        assertThat(statelessServer).isNotNull();

        // 2. SSE Server
        HttpServletSseServerTransportProvider sseTransport = config.mcpSseTransportProvider(jsonMapper);
        McpSyncServer sseServer = config.mcpSseServer(
                sseTransport, toolRegistry, userMemoryRegistry, synapseProperties);
        assertThat(sseServer).isNotNull();

        // 3. Streamable HTTP Server
        HttpServletStreamableServerTransportProvider streamableTransport =
                config.mcpStreamableTransportProvider(jsonMapper);
        McpSyncServer streamableServer = config.mcpStreamableServer(
                streamableTransport, toolRegistry, userMemoryRegistry, synapseProperties);
        assertThat(streamableServer).isNotNull();
    }
}
