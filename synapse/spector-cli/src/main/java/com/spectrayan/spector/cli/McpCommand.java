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
package com.spectrayan.spector.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.spectrayan.spector.mcp.SpectorMcpServer;
import com.spectrayan.spector.memory.SpectorMemory;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Starts the high-performance Spector Model Context Protocol (MCP) server over STDIO.
 */
@Component
@Command(
        name = "mcp",
        description = "Start the Spector MCP server (STDIO JSON-RPC 2.0 transport for AI agents).",
        mixinStandardHelpOptions = true
)
public class McpCommand implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(McpCommand.class);

    private final ObjectProvider<SpectorMemory> memoryProvider;

    @Autowired
    public McpCommand(@Lazy ObjectProvider<SpectorMemory> memoryProvider) {
        this.memoryProvider = memoryProvider;
    }

    @Option(names = {"--config", "-c"}, description = "Path to spector.yml configuration file.")
    private String configFile;

    @Option(names = {"--profile"}, description = "Configuration profile (e.g., local, dev, prod).")
    private String profile;

    @Option(names = {"--dims"}, description = "Vector dimensions (default from config or 768).")
    private Integer dims;

    @Option(names = {"--capacity"}, description = "Memory tier capacity.")
    private Integer capacity;

    @Option(names = {"--data-dir"}, description = "Persistence directory for on-disk memory.")
    private String dataDir;

    @Option(names = {"--namespace"}, description = "Tenant / memory namespace.")
    private String namespace;

    @Option(names = {"--ollama-url"}, description = "Ollama base URL (default: http://localhost:11434).")
    private String ollamaUrl;

    @Option(names = {"--ollama-model"}, description = "Ollama embedding model (default: qwen3-embedding:latest).")
    private String ollamaModel;

    @Option(names = {"--mode"}, description = "Special preset mode (e.g., openclaw, odysseus).")
    private String mode;

    @Override
    public void run() {
        SpectorMemory memory = memoryProvider != null ? memoryProvider.getIfAvailable() : null;
        if (memory == null) {
            throw new IllegalStateException("SpectorMemory bean is not available in the Spring context. " +
                    "Ensure memory is enabled (run with embedded profile: cli-embedded, spector.memory.enabled=true).");
        }

        SpectorMcpServer server = new SpectorMcpServer(memory);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            // Note: SpectorMemory lifecycle and closing is managed by the Spring ApplicationContext.
            log.info("[Spector CLI MCP] MCP server stopped on shutdown.");
        }));

        server.start();
    }
}
