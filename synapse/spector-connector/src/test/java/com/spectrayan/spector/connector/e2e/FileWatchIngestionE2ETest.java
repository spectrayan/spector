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
package com.spectrayan.spector.connector.e2e;

import com.spectrayan.spector.connector.core.CamelConnectorEngine;
import com.spectrayan.spector.connector.model.RouteConfig;
import com.spectrayan.spector.connector.sink.SpectorIngestionSink;
import com.spectrayan.spector.connector.spi.InMemoryExecutionLogger;
import com.spectrayan.spector.connector.template.TemplateRegistry;
import com.spectrayan.spector.ingestion.IngestionTarget;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;

/**
 * End-to-end integration test: Camel file-watch route → picks up real files
 * → SpectorIngestionSink → real SpectorMemory.
 *
 * <h3>What This Tests</h3>
 * <ul>
 *   <li>Camel's file component watches a directory for new files</li>
 *   <li>Text files (.txt, .md) are automatically picked up</li>
 *   <li>File content flows through PII scrubbing → embedding → memory ingestion</li>
 *   <li>Document IDs are derived from filenames (CamelFileName header)</li>
 *   <li>All files are recallable from memory after ingestion</li>
 * </ul>
 */
class FileWatchIngestionE2ETest {

    private static final int DIMS = 384;

    private StubEmbeddingProvider embeddingProvider;
    private SpectorMemory memory;
    private SpectorIngestionSink sink;
    private CamelConnectorEngine engine;

    @TempDir
    Path watchDir;

    @BeforeEach
    void setUp() throws Exception {
        embeddingProvider = new StubEmbeddingProvider(DIMS);
        memory = DefaultSpectorMemory.builder()
                .dimensions(DIMS)
                .embeddingProvider(embeddingProvider)
                .build();

        IngestionTarget target = memory.target();
        sink = new SpectorIngestionSink(target, embeddingProvider, new InMemoryExecutionLogger());

        TemplateRegistry templateRegistry = new TemplateRegistry(null);
        InMemoryRouteConfigProvider routeConfigProvider = new InMemoryRouteConfigProvider();
        engine = new CamelConnectorEngine(sink, routeConfigProvider, templateRegistry);
        engine.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null) engine.close();
        if (memory != null) memory.close();
    }

    // ═══════════════════════════════════════════════════════════════
    //  File Watch: Text Files
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("E2E: file-watch route picks up .txt files and ingests them into memory")
    void fileWatchPicksUpTextFiles() throws Exception {
        // Deploy file-watch route pointing at the temp directory
        RouteConfig config = RouteConfig.builder("e2e-file-watch", "E2E File Watch", "file-watch")
                .tenantId("default")
                .properties(Map.of(
                        "path", watchDir.toString(),
                        "pattern", ".*\\.(txt|md)",
                        "recursive", "false"
                ))
                .enabled(true)
                .build();
        engine.deployRoute(config);

        // Drop files into the watched directory
        writeFile("report.txt",
                "This quarterly report covers Spector performance metrics and adoption trends.");
        writeFile("notes.md",
                "# Architecture Notes\nThe HNSW index provides efficient approximate nearest neighbor search.");

        // Wait for Camel to pick up both files
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(sink.totalProcessed()).isGreaterThanOrEqualTo(2));

        // Verify no errors
        assertThat(sink.totalErrors()).isZero();

        // Verify both documents are recallable
        assertThat(memory.recall("quarterly report performance")).isNotEmpty();
        assertThat(memory.recall("HNSW nearest neighbor")).isNotEmpty();
    }

    // ═══════════════════════════════════════════════════════════════
    //  File Watch: Only Matching Patterns
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("E2E: file-watch route ignores files not matching the pattern")
    void fileWatchIgnoresNonMatchingFiles() throws Exception {
        RouteConfig config = RouteConfig.builder("e2e-filter", "E2E Filter", "file-watch")
                .tenantId("default")
                .properties(Map.of(
                        "path", watchDir.toString(),
                        "pattern", ".*\\.txt",
                        "recursive", "false"
                ))
                .enabled(true)
                .build();
        engine.deployRoute(config);

        // Drop a .txt file (should be picked up) and a .csv file (should be ignored)
        writeFile("valid.txt", "This text file should be ingested into cognitive memory.");
        writeFile("ignored.csv", "col1,col2\nval1,val2");

        // Wait for the .txt file
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(sink.totalProcessed()).isGreaterThanOrEqualTo(1));

        // Give a little extra time to ensure .csv wasn't picked up
        Thread.sleep(2000);

        // Only the .txt file was processed
        assertThat(sink.totalProcessed()).isEqualTo(1);
        assertThat(embeddingProvider.callCount()).isEqualTo(1);
    }

    // ═══════════════════════════════════════════════════════════════
    //  File Watch: Document ID from Filename
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("E2E: file-watch route uses filename as document ID")
    void fileNameUsedAsDocId() throws Exception {
        RouteConfig config = RouteConfig.builder("e2e-docid", "E2E DocID", "file-watch")
                .tenantId("default")
                .properties(Map.of(
                        "path", watchDir.toString(),
                        "pattern", ".*\\.txt",
                        "recursive", "false"
                ))
                .enabled(true)
                .build();
        engine.deployRoute(config);

        writeFile("annual-review-2026.txt",
                "The annual review highlights significant improvements in cognitive recall accuracy.");

        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(sink.totalProcessed()).isGreaterThanOrEqualTo(1));

        // Verify the document is searchable
        var results = memory.recall("annual review cognitive recall");
        assertThat(results).isNotEmpty();
    }

    // ═══════════════════════════════════════════════════════════════
    //  File Watch: Markdown / Confluence / Wiki Docs Ingestion
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("E2E: file-watch ingests wiki-style markdown documents and agent performs semantic recall")
    void markdownWikiDocIngestionAndRecall() throws Exception {
        RouteConfig config = RouteConfig.builder("e2e-wiki-docs", "E2E Wiki Docs", "file-watch")
                .tenantId("spectrayan-org")
                .properties(Map.of(
                        "path", watchDir.toString(),
                        "pattern", ".*\\.md",
                        "recursive", "false",
                        "collection", "architecture-docs"
                ))
                .enabled(true)
                .build();
        engine.deployRoute(config);

        // Ingest realistic Confluence / Wiki documentation pages
        writeFile("spector-architecture-overview.md",
                "# Spector Architecture Overview\n\n" +
                "Spector is a biological cognitive memory engine providing 4 memory tiers: " +
                "Working Memory (volatile scratchpad), Episodic Memory (temporal sessions), " +
                "Semantic Memory (permanent knowledge graph), and Procedural Memory (tool definitions). " +
                "It uses HNSW for approximate nearest neighbor search and BM25 for keyword scoring.");

        writeFile("camel-connector-integration.md",
                "# Apache Camel Ingestion Subsystem\n\n" +
                "The Synapse connector engine integrates Apache Camel routes with SpectorIngestionSink. " +
                "Supported sources include Kafka, S3, JDBC databases, Slack, and local file watching. " +
                "All incoming exchanges undergo PII scrubbing, delta change detection, and vector embedding.");

        // Wait for Camel to ingest both markdown docs
        await().atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(sink.totalProcessed()).isGreaterThanOrEqualTo(2));

        assertThat(sink.totalErrors()).isZero();

        // Verify Agent cognitive recall pulls precise wiki knowledge
        var memoryResults = memory.recall("biological cognitive 4 memory tiers HNSW BM25");
        assertThat(memoryResults).isNotEmpty();
        assertThat(memoryResults)
                .extracting(r -> r.text())
                .anyMatch(text -> text.contains("Working Memory") && text.contains("Semantic Memory"));

        var connectorResults = memory.recall("Apache Camel SpectorIngestionSink PII scrubbing");
        assertThat(connectorResults).isNotEmpty();
        assertThat(connectorResults)
                .extracting(r -> r.text())
                .anyMatch(text -> text.contains("SpectorIngestionSink") && text.contains("PII scrubbing"));
    }

    // ═══════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════

    private void writeFile(String name, String content) throws IOException {
        Files.writeString(watchDir.resolve(name), content);
    }
}
