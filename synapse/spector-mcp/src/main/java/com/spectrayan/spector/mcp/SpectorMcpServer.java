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
package com.spectrayan.spector.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.core.simd.SimdCapability;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.runtime.SpectorRuntime;
import com.spectrayan.spector.mcp.prompts.SpectorPromptProvider;
import com.spectrayan.spector.mcp.resources.SpectorResourceProvider;
import com.spectrayan.spector.mcp.tools.SpectorToolRegistry;

import java.util.function.Supplier;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * High-performance MCP Server for Spector.
 */
public class SpectorMcpServer {

    private static final Logger log = LoggerFactory.getLogger(SpectorMcpServer.class);

    static final String SERVER_NAME = "spector";
    static final String SERVER_VERSION = "1.0.0";

    private final SpectorRuntime runtime;
    private final SpectorMemory memory;
    private volatile McpSyncServer mcpServer;

    /**
     * Creates an MCP server backed by the given runtime.
     */
    public SpectorMcpServer(SpectorRuntime runtime) {
        this.runtime = runtime;
        this.memory = runtime.memory().orElse(null);
    }

    /**
     * Starts the MCP server on stdio transport (blocking).
     */
    public void start() {
        log.info("[Spector MCP] Starting server: {}, transport=STDIO, {}",
                SERVER_NAME,
                SimdCapability.report());

        McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(
                tools.jackson.databind.json.JsonMapper.builder().build());
        var transportProvider = new StdioServerTransportProvider(jsonMapper);

        mcpServer = buildMcpServer(transportProvider);

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            log.info("[Spector MCP] Server interrupted, shutting down");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Builds an MCP server on an arbitrary transport.
     */
    public McpSyncServer buildMcpServer(McpServerTransportProvider transportProvider) {
        var toolSpecs  = SpectorToolRegistry.createAll(runtime, SERVER_VERSION);
        var resources  = SpectorResourceProvider.create(memory, SERVER_VERSION);
        var prompts    = SpectorPromptProvider.create(memory);

        mcpServer = McpServer.sync(transportProvider)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .resources(false, false)
                        .prompts(false)
                        .build())
                .tools(toolSpecs)
                .resources(resources)
                .prompts(prompts)
                .build();

        log.info("[Spector MCP] Server initialized with {} tools, {} resources, {} prompts",
                toolSpecs.size(), resources.size(), prompts.size());

        return mcpServer;
    }

    /**
     * Builds an MCP server with enterprise memory resolver for tenant isolation.
     */
    public McpSyncServer buildMcpServer(McpServerTransportProvider transportProvider,
                                        Supplier<SpectorMemory> memoryResolver) {
        var toolSpecs  = SpectorToolRegistry.createAll(runtime, SERVER_VERSION, memoryResolver);
        var resolvedMemory = memoryResolver != null ? memoryResolver.get() : null;
        var resources  = SpectorResourceProvider.create(resolvedMemory, SERVER_VERSION);
        var prompts    = SpectorPromptProvider.create(resolvedMemory);

        mcpServer = McpServer.sync(transportProvider)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .resources(false, false)
                        .prompts(false)
                        .build())
                .tools(toolSpecs)
                .resources(resources)
                .prompts(prompts)
                .build();

        log.info("[Spector MCP] Server initialized (tenant-aware) with {} tools, {} resources, {} prompts",
                toolSpecs.size(), resources.size(), prompts.size());

        return mcpServer;
    }

    /**
     * Stops the MCP server.
     */
    public void stop() {
        if (mcpServer != null) {
            try {
                mcpServer.close();
            } catch (Exception e) {
                log.warn("[Spector MCP] Error closing server: {}", e.getMessage());
            }
        }
    }
}
