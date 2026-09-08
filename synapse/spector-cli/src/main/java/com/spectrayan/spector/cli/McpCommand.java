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
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.spectrayan.spector.config.SpectorConfigSource;
import com.spectrayan.spector.config.SpectorConfigFactory;
import com.spectrayan.spector.config.model.PersistenceMode;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.mcp.SpectorMcpServer;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.provider.embedding.EmbeddingConfig;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.ollama.OllamaEmbeddingProvider;
import com.spectrayan.spector.spring.autoconfigure.SpectorConfigProperties;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Starts the high-performance Spector Model Context Protocol (MCP) server over STDIO.
 * <p>
 * Honors all CLI parameter overrides ({@code --config}, {@code --dims}, {@code --capacity},
 * {@code --data-dir}, {@code --namespace}, {@code --ollama-url}, {@code --ollama-model}, {@code --mode})
 * to instantiate and configure the underlying {@link SpectorMemory} runtime.
 * </p>
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
    private final ObjectProvider<EmbeddingProvider> embedderProvider;
    private final ObjectProvider<SpectorConfigProperties> configPropsProvider;

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

    private MemoryProperties lastResolvedProperties;

    public McpCommand(ObjectProvider<SpectorMemory> memoryProvider) {
        this(memoryProvider, null, null);
    }

    @Autowired
    public McpCommand(@Lazy ObjectProvider<SpectorMemory> memoryProvider,
                      @Lazy ObjectProvider<EmbeddingProvider> embedderProvider,
                      @Lazy ObjectProvider<SpectorConfigProperties> configPropsProvider) {
        this.memoryProvider = memoryProvider;
        this.embedderProvider = embedderProvider;
        this.configPropsProvider = configPropsProvider;
    }

    public boolean hasCustomFlags() {
        return configFile != null || dims != null || capacity != null || dataDir != null
                || namespace != null || ollamaUrl != null || ollamaModel != null || mode != null;
    }

    public MemoryProperties getLastResolvedProperties() {
        return lastResolvedProperties;
    }

    /**
     * Resolves the {@link SpectorMemory} instance, constructing a customized instance
     * whenever CLI flags are provided.
     */
    public SpectorMemory resolveMemory() {
        if (!hasCustomFlags()) {
            SpectorMemory defaultMemory = memoryProvider != null ? memoryProvider.getIfAvailable() : null;
            if (defaultMemory != null) {
                return defaultMemory;
            }
        }

        MemoryProperties memProps = new MemoryProperties();
        if (configFile != null && !configFile.isBlank()) {
            var src = SpectorConfigSource.builder().configFile(Path.of(configFile)).build();
            memProps = SpectorConfigFactory.memoryProperties(src);
        } else if (configPropsProvider != null && configPropsProvider.getIfAvailable() != null) {
            var existing = configPropsProvider.getIfAvailable().getMemory();
            if (existing != null) {
                memProps = existing;
            }
        }

        if (dataDir != null && !dataDir.isBlank()) {
            memProps.setPersistencePath(dataDir);
            memProps.setPersistenceMode(PersistenceMode.DISK);
        }
        if (dims != null && dims > 0) {
            memProps.setDimensions(dims);
        }
        if (capacity != null && capacity > 0) {
            memProps.setCapacity(capacity);
        }
        if (mode != null && !mode.isBlank()) {
            if ("openclaw".equalsIgnoreCase(mode)) {
                memProps.setPersistenceMode(PersistenceMode.DISK);
                if (dataDir == null) {
                    memProps.setPersistencePath(System.getProperty("user.home") + "/.openclaw/spector/data/memory");
                }
            } else if ("odysseus".equalsIgnoreCase(mode)) {
                memProps.setPersistenceMode(PersistenceMode.DISK);
                if (dataDir == null) {
                    memProps.setPersistencePath(System.getProperty("user.home") + "/.odysseus/spector/data/memory");
                }
                memProps.setDefaultIngestionTier("SEMANTIC");
            }
        }

        // Embedding provider resolution: Ollama override vs default in-process embedder
        EmbeddingProvider embedder;
        if (ollamaUrl != null || ollamaModel != null) {
            String url = ollamaUrl != null && !ollamaUrl.isBlank() ? ollamaUrl : "http://localhost:11434";
            String model = ollamaModel != null && !ollamaModel.isBlank() ? ollamaModel : "qwen3-embedding:latest";
            log.info("[Spector MCP] Using Ollama embedding provider: {} @ {}", model, url);
            embedder = new OllamaEmbeddingProvider(new EmbeddingConfig(model, url, Duration.ofSeconds(30), 32, 0));
        } else {
            embedder = embedderProvider != null ? embedderProvider.getIfAvailable() : null;
        }

        this.lastResolvedProperties = memProps;
        var builder = DefaultSpectorMemory.builder(memProps);
        if (namespace != null && !namespace.isBlank()) {
            builder.namespaceId(namespace);
        }
        if (embedder != null) {
            builder.embeddingProvider(embedder);
        }
        return builder.build();
    }

    @Override
    public void run() {
        SpectorMemory memory = resolveMemory();
        if (memory == null) {
            throw new IllegalStateException("SpectorMemory bean is not available in the Spring context. " +
                    "Ensure memory is enabled (run with embedded profile: cli-embedded, spector.memory.enabled=true).");
        }

        SpectorMcpServer server = new SpectorMcpServer(memory);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            try {
                memory.close();
            } catch (Exception e) {
                log.debug("Error closing SpectorMemory on shutdown: {}", e.getMessage());
            }
            log.info("[Spector CLI MCP] MCP server stopped on shutdown.");
        }));

        server.start();
    }

    // Accessors for testing and introspection
    public String getConfigFile() { return configFile; }
    public void setConfigFile(String configFile) { this.configFile = configFile; }
    public String getProfile() { return profile; }
    public void setProfile(String profile) { this.profile = profile; }
    public Integer getDims() { return dims; }
    public void setDims(Integer dims) { this.dims = dims; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public String getDataDir() { return dataDir; }
    public void setDataDir(String dataDir) { this.dataDir = dataDir; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public String getOllamaUrl() { return ollamaUrl; }
    public void setOllamaUrl(String ollamaUrl) { this.ollamaUrl = ollamaUrl; }
    public String getOllamaModel() { return ollamaModel; }
    public void setOllamaModel(String ollamaModel) { this.ollamaModel = ollamaModel; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
}
