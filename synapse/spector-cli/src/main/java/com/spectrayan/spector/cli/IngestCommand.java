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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.spectrayan.spector.cli.client.IngestRequest;
import com.spectrayan.spector.cli.client.IngestResponse;
import com.spectrayan.spector.cli.client.SpectorClientException;
import com.spectrayan.spector.cli.client.SpectorConnectionException;
import com.spectrayan.spector.commons.chunker.ChunkConfig;
import com.spectrayan.spector.commons.chunker.MarkdownChunker;
import com.spectrayan.spector.config.SpectorConfigFactory;
import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.ingestion.FileDiscoveryService;
import com.spectrayan.spector.ingestion.IngestionPipeline;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.graph.EntityExtractionMode;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.ollama.OllamaLlmProvider;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Ingest documents into Spector.
 *
 * <p>Supports two modes, auto-detected from the flags provided:</p>
 * <ul>
 *   <li><strong>Remote</strong> -- {@code --content} or {@code --file}: sends a single
 *       document to a running Spector server via HTTP.</li>
 *   <li><strong>Local batch</strong> -- {@code --root}: discovers and ingests files
 *       locally directly into {@link SpectorMemory}, honoring {@code spector.yml} config.</li>
 * </ul>
 *
 * <h3>Examples</h3>
 * <pre>
 *   spectorctl ingest --content "Hello world"             # remote
 *   spectorctl ingest --file README.md                    # remote
 *   spectorctl ingest --root /docs --pattern "**\/*.md"   # local batch
 *   spectorctl ingest --root . --config spector.yml       # local batch
 * </pre>
 */
@Command(
        name = "ingest",
        description = "Ingest documents into Spector (remote or local batch).",
        mixinStandardHelpOptions = true
)
class IngestCommand extends BaseCommand {

    // Remote mode options
    @CommandLine.Option(names = {"--id"}, description = "Document ID (auto-generated if not provided).")
    private String documentId;

    @CommandLine.Option(names = {"--title"}, description = "Document title.")
    private String title;

    @CommandLine.Option(names = {"--content"}, description = "Document content (text). Remote mode.")
    private String content;

    @CommandLine.Option(names = {"--file"}, description = "Path to file to ingest. Remote mode.")
    private Path file;

    // Local batch mode options
    @CommandLine.Option(names = {"--root"}, description = "Root directory for local batch ingestion.")
    private Path rootDir;

    @CommandLine.Option(names = {"--pattern"}, description = "File glob pattern (default from config).")
    private String pattern;

    @CommandLine.Option(names = {"--chunk-size"}, description = "Chunk size in characters (default from config).")
    private Integer chunkSize;

    @CommandLine.Option(names = {"--config"}, description = "Path to spector.yml config file.")
    private Path configFile;

    @Override
    public void run() {
        if (rootDir != null) {
            runLocalBatch();
        } else if (configFile != null) {
            var props = SpectorProperties.builder().configFile(configFile).build();
            var ingestionConfig = SpectorConfigFactory.ingestionProperties(props);
            if (ingestionConfig.rootDirectory() != null) {
                rootDir = ingestionConfig.rootDirectory();
                runLocalBatch();
            } else {
                runRemote();
            }
        } else if (content != null || file != null) {
            runRemote();
        } else {
            err().println("Error: Provide --content, --file, or --root (or --config with root-directory).");
            spec.commandLine().usage(err());
        }
    }

    // Local Batch Mode

    private void runLocalBatch() {
        SpectorProperties.Builder propsBuilder = SpectorProperties.builder();

        if (configFile != null) propsBuilder.configFile(configFile);
        if (pattern != null)
            propsBuilder.override("spector.ingestion.file-pattern", pattern);
        if (chunkSize != null)
            propsBuilder.override("spector.ingestion.chunk-size", chunkSize.toString());

        if (rootDir != null)
            propsBuilder.override("spector.ingestion.root-directory", rootDir.toString());

        SpectorProperties props = propsBuilder.build();

        var ingestionConfig = SpectorConfigFactory.ingestionProperties(props);
        var embedConfig = SpectorConfigFactory.embeddingProperties(props);
        var memoryConfig = SpectorConfigFactory.memoryProperties(props);
        var mode = SpectorConfigFactory.mode(props);
        Path root = ingestionConfig.rootDirectory().toAbsolutePath().normalize();

        out().printf("========================================%n");
        out().printf("  Spector Ingestion (local batch)%n");
        out().printf("  Mode:    %s%n", mode);
        out().printf("  Root:    %s%n", root);
        out().printf("  Pattern: %s%n", ingestionConfig.filePattern());
        out().printf("  Data:    %s%n", memoryConfig.persistencePath());
        out().printf("  Model:   %s @ %s%n", embedConfig.model(), embedConfig.baseUrl());
        out().printf("  Chunk:   %d chars%n", ingestionConfig.chunkSize());
        out().printf("  Threads: %d parallel, %d retries (delay: %dms)%n",
                ingestionConfig.parallelism(), ingestionConfig.maxRetries(),
                ingestionConfig.retryDelayMs());
        out().printf("========================================%n%n");

        var config = new com.spectrayan.spector.provider.ProviderConfig(
                "ollama", embedConfig.type(), embedConfig.model(), embedConfig.apiKey(), embedConfig.baseUrl(), embedConfig.dimensions(), embedConfig.properties());
        var registry = com.spectrayan.spector.provider.ProviderDiscovery.discover(java.util.List.of(config));
        EmbeddingProvider embedder = registry.activeEmbedding().orElseThrow();
        int dims = embedder.embed("probe").dimensions();
        out().printf("[Embedding] Dimensions: %d%n%n", dims);

        propsBuilder.override("spector.memory.dimensions", String.valueOf(dims));
        propsBuilder.override("spector.provider.embedding.dimensions", String.valueOf(dims));
        props = propsBuilder.build();

        LlmProvider textGenProvider = null;
        memoryConfig = SpectorConfigFactory.memoryProperties(props);
        if (memoryConfig.tagExtractor() == com.spectrayan.spector.config.model.TagExtractorMode.LLM) {
            String tagModel = memoryConfig.tagExtractorModel();
            if (tagModel == null || tagModel.isBlank()) {
                tagModel = "qwen3:1.7b";
            }
            textGenProvider = OllamaLlmProvider.create(tagModel, embedConfig.baseUrl());
            out().printf("[Tags] LLM extraction: %s @ %s%n", tagModel, embedConfig.baseUrl());
        }

        var chunker = new MarkdownChunker();
        var chunkConfig = new ChunkConfig(
                ingestionConfig.chunkSize(),
                ingestionConfig.chunkOverlap(),
                "text/markdown",
                "text/markdown",
                true,
                true,
                false
        );

        Path persistencePath = memoryConfig.persistencePath() != null ? Path.of(memoryConfig.persistencePath()) : null;
        var memoryBuilder = DefaultSpectorMemory.builder()
                .dimensions(memoryConfig.dimensions())
                .embeddingProvider(embedder)
                .persistenceMode(MemoryPersistenceMode.valueOf(memoryConfig.persistenceMode().name()))
                .persistence(persistencePath)
                .semanticCapacity(memoryConfig.capacity())
                .nodesPerPartition(memoryConfig.nodesPerPartition())
                .hebbianGraphCapacity(memoryConfig.capacity())
                .temporalChainCapacity(memoryConfig.capacity())
                .chunker(chunker, chunkConfig);

        if (textGenProvider != null) {
            memoryBuilder.entityExtractionMode(EntityExtractionMode.LLM).LlmProvider(textGenProvider);
        } else {
            memoryBuilder.entityExtractionMode(EntityExtractionMode.NONE);
        }

        try (SpectorMemory memory = memoryBuilder.build()) {
            long startMs = System.currentTimeMillis();

            IngestionPipeline pipeline = IngestionPipeline.builder()
                    .target(memory.target())
                    .embeddingProvider(embedder)
                    .chunker(chunker)
                    .chunkConfig(chunkConfig)
                    .chunkThreshold(ingestionConfig.chunkSize())
                    .build();

            var discovery = FileDiscoveryService.builder()
                    .rootDirectory(root)
                    .filePattern(ingestionConfig.filePattern())
                    .skipDirs(ingestionConfig.skipDirs().split(","))
                    .chunkSize(ingestionConfig.chunkSize())
                    .chunkOverlap(ingestionConfig.chunkOverlap())
                    .build();

            List<Path> files = discovery.discover();
            int fileIdx = 0;
            int totalFiles = files.size();
            int totalChunks = 0;
            int failures = 0;

            for (Path f : files) {
                fileIdx++;
                String relPath = root.relativize(f).toString();
                out().printf("  [%d/%d] > %s ...%n", fileIdx, totalFiles, relPath);
                out().flush();

                long fileStart = System.currentTimeMillis();
                try {
                    String fileContent = Files.readString(f);
                    if (!fileContent.isBlank()) {
                        var res = pipeline.ingest(relPath, fileContent);
                        long ms = System.currentTimeMillis() - fileStart;
                        totalChunks += res.chunksStored();
                        if (!res.isFullSuccess()) {
                            failures++;
                            out().printf("  [%d/%d] X %s -- FAILED (%dms)%n", fileIdx, totalFiles, relPath, ms);
                        } else {
                            out().printf("  [%d/%d] OK %s -- %d chunk%s, %dms%n", fileIdx, totalFiles, relPath, res.chunksStored(), res.chunksStored() == 1 ? "" : "s", ms);
                        }
                    } else {
                        out().printf("  [%d/%d] SKIP %s -- empty%n", fileIdx, totalFiles, relPath);
                    }
                } catch (Exception e) {
                    failures++;
                    long ms = System.currentTimeMillis() - fileStart;
                    out().printf("  [%d/%d] X %s -- FAILED (%dms): %s%n", fileIdx, totalFiles, relPath, ms, e.getMessage());
                }
                out().flush();
            }

            long elapsed = System.currentTimeMillis() - startMs;
            out().printf("%n========================================%n");
            out().printf("  Ingestion Complete%n");
            out().printf("  Mode:     MEMORY%n");
            out().printf("  Files:    %d%n", totalFiles);
            out().printf("  Chunks:   %d%n", totalChunks);
            out().printf("  Failures: %d%n", failures);
            out().printf("  Docs:     %d (in memory)%n", memory.totalMemories());
            out().printf("  Time:     %dms%n", elapsed);
            out().printf("========================================%n");
        } catch (Exception e) {
            err().println("Error during ingestion: " + e.getMessage());
        }
    }

    // Remote Mode

    private void runRemote() {
        String text = resolveContent();
        if (text == null) {
            err().println("Error: Provide --content, --file, or --root.");
            spec.commandLine().usage(err());
            return;
        }

        try (var client = createClient()) {
            IngestRequest request = new IngestRequest(documentId, text);

            IngestResponse response = client.ingest(request);

            if (isJson()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("id", response.getId());
                result.put("status", response.getStatus());
                result.put("durationMs", response.getDurationMs());
                result.put("chunks", response.getChunks());
                OutputFormatter.printJson(out(), result);
            } else {
                out().println("Document ingested successfully.");
                out().println("  ID:         " + response.getId());
                out().println("  Status:     " + response.getStatus());
                out().println("  DurationMs: " + response.getDurationMs());
                out().println("  Chunks:     " + response.getChunks());
            }
        } catch (SpectorConnectionException e) {
            handleConnectionError(e);
        } catch (SpectorClientException e) {
            err().println("Error: " + e.getMessage());
        }
    }

    private String resolveContent() {
        if (content != null && !content.isBlank()) {
            return content;
        }
        if (file != null) {
            try {
                return Files.readString(file);
            } catch (IOException e) {
                err().println("Error: Cannot read file '" + file + "': " + e.getMessage());
                return null;
            }
        }
        return null;
    }
}
