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
import com.spectrayan.spector.engine.SpectorEngine;
import com.spectrayan.spector.runtime.SpectorRuntime;
import com.spectrayan.spector.mcp.prompts.SpectorPromptProvider;
import com.spectrayan.spector.mcp.resources.SpectorResourceProvider;
import com.spectrayan.spector.mcp.tools.SpectorToolRegistry;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * High-performance MCP Server for Spector.
 *
 * <p>Thin orchestrator that assembles tool, resource, and prompt providers
 * into an MCP server. All search operations run in-process with zero
 * network overhead — tool handlers call {@link SpectorEngine} directly.</p>
 *
 * <h3>Transport Modes</h3>
 * <ul>
 *   <li><b>STDIO</b> — JSON-RPC over stdin/stdout (local agents: Claude Desktop, Cursor).
 *       Use {@link #start()} for blocking stdio mode.</li>
 *   <li><b>HTTP/SSE</b> — For Docker/server deployments, use SpectorNode which
 *       embeds the MCP server on the Armeria HTTP stack at {@code /mcp/*}.</li>
 * </ul>
 *
 * @see SpectorMcpMain
 * @see SpectorToolRegistry
 */
public class SpectorMcpServer {

    private static final Logger log = LoggerFactory.getLogger(SpectorMcpServer.class);

    static final String SERVER_NAME = "spector";
    static final String SERVER_VERSION = "1.0.0";

    private final SpectorRuntime runtime;
    private final SpectorEngine engine;
    private volatile McpSyncServer mcpServer;

    /**
     * Creates an MCP server backed by the given runtime.
     */
    public SpectorMcpServer(SpectorRuntime runtime) {
        this.runtime = runtime;
        this.engine = runtime.engine();
    }

    /**
     * Starts the MCP server on stdio transport (blocking).
     *
     * <p>Blocks indefinitely, reading JSON-RPC messages from stdin
     * and writing responses to stdout.</p>
     */
    public void start() {
        log.info("[Spector MCP] Starting server: {}, transport=STDIO, dims={}, indexType={}, embedding={}, {}",
                SERVER_NAME,
                engine.config().dimensions(),
                engine.config().indexType(),
                engine.hasEmbeddingProvider() ? "configured" : "none",
                SimdCapability.report());

        McpJsonMapper jsonMapper = new JacksonMcpJsonMapper(
                tools.jackson.databind.json.JsonMapper.builder().build());
        var transportProvider = new StdioServerTransportProvider(jsonMapper);

        mcpServer = buildMcpServer(transportProvider);

        // The SDK handles the stdio read loop internally.
        // Block the main thread to keep the server alive.
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            log.info("[Spector MCP] Server interrupted, shutting down");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Builds an MCP server on an arbitrary transport.
     *
     * <p>Used by SpectorNode to build the MCP server on the Armeria SSE
     * transport without pulling in a separate HTTP server.</p>
     *
     * @param transportProvider the transport to bind to
     * @return the built MCP server
     */
    public McpSyncServer buildMcpServer(McpServerTransportProvider transportProvider) {
        var toolSpecs  = SpectorToolRegistry.createAll(runtime, SERVER_VERSION);
        var resources  = SpectorResourceProvider.create(engine, SERVER_VERSION);
        var prompts    = SpectorPromptProvider.create(engine);

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
     * Stops the MCP server and releases resources.
     */
    public void stop() {
        if (mcpServer != null) {
            mcpServer.close();
            log.info("[Spector MCP] Server stopped");
        }
    }
}
