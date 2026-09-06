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

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.commons.cache.TtlConcurrentMapCacheManager;
import com.spectrayan.spector.commons.chunker.ChunkConfig;
import com.spectrayan.spector.commons.chunker.MarkdownChunker;
import com.spectrayan.spector.config.SpectorConfigFactory;
import com.spectrayan.spector.config.SpectorConfigSource;
import com.spectrayan.spector.mcp.SpectorMcpServer;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.graph.EntityExtractionMode;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.provider.embedding.CachingEmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.generic.DenseDerivedSparseProvider;
import com.spectrayan.spector.provider.embedding.generic.DenseDerivedTokenProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.ollama.OllamaLlmProvider;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

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

    public McpCommand() {
        this(null);
    }

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
            SpectorConfigSource.Builder propsBuilder = SpectorConfigSource.builder();

            if (configFile != null) {
                propsBuilder.configFile(Path.of(configFile));
            }
            if (profile != null) {
                propsBuilder.profile(profile);
            }
            if (dims != null) {
                propsBuilder.override("spector.memory.dimensions", String.valueOf(dims));
                propsBuilder.override("spector.provider.embedding.dimensions", String.valueOf(dims));
            }
            if (capacity != null) {
                propsBuilder.override("spector.memory.capacity", String.valueOf(capacity));
            }
            if (ollamaUrl != null) {
                propsBuilder.override("spector.provider.embedding.base-url", ollamaUrl);
                propsBuilder.override("spector.embedding.base-url", ollamaUrl);
            }
            if (ollamaModel != null) {
                propsBuilder.override("spector.provider.embedding.model", ollamaModel);
                propsBuilder.override("spector.embedding.model", ollamaModel);
            }
            if (dataDir != null) {
                propsBuilder.override("spector.memory.persistence-path", dataDir);
                propsBuilder.override("spector.memory.persistence-mode", "DISK");
            }
            if (namespace != null) {
                propsBuilder.override("spector.memory.namespace", namespace);
            }

            if ("openclaw".equalsIgnoreCase(mode)) {
                propsBuilder.override("spector.mode", "memory");
                propsBuilder.override("spector.memory.enabled", "true");
                propsBuilder.override("spector.memory.persistence-mode", "DISK");
                if (dataDir == null && configFile == null) {
                    String openclawDataDir = System.getProperty("user.home") + "/.openclaw/spector/data";
                    propsBuilder.override("spector.memory.persistence-path", openclawDataDir + "/memory");
                }
            } else if ("odysseus".equalsIgnoreCase(mode)) {
                propsBuilder.override("spector.mode", "memory");
                propsBuilder.override("spector.memory.enabled", "true");
                propsBuilder.override("spector.memory.persistence-mode", "DISK");
                if (dataDir == null && configFile == null) {
                    String odysseusDataDir = System.getProperty("user.home") + "/.odysseus/spector/data";
                    propsBuilder.override("spector.memory.persistence-path", odysseusDataDir + "/memory");
                }
                propsBuilder.override("spector.memory.default-ingestion-tier", "SEMANTIC");
            }

            SpectorConfigSource configSource = propsBuilder.build();
            com.spectrayan.spector.config.SpectorProperties props = com.spectrayan.spector.config.SpectorProperties.from(configSource);
            memory = com.spectrayan.spector.memory.config.SpectorMemoryConfigurator.builder(props).build();
        }

        final SpectorMemory finalMemory = memory;
        SpectorMcpServer server = new SpectorMcpServer(finalMemory);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            try {
                finalMemory.close();
            } catch (Exception e) {
                log.warn("[Spector CLI MCP] Error closing memory on shutdown", e);
            }
            log.info("[Spector CLI MCP] Shutdown complete");
        }));

        server.start();
    }
}
