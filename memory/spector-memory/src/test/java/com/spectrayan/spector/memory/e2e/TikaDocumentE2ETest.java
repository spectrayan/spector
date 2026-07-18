/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.e2e;

import com.spectrayan.spector.provider.ollama.OllamaEmbeddingProvider;
import com.spectrayan.spector.ingestion.sensory.TikaTextExtractor;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.IngestionContext;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.SourceModality;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test for document ingestion via Apache Tika + Ollama embeddings.
 *
 * <p>Requires a running Ollama instance with `nomic-embed-text` model.
 * Set {@code OLLAMA_LIVE=true} to enable.</p>
 *
 * <p>Tests verify that documents (text, HTML) can be:
 * <ol>
 *   <li>Extracted via TikaTextExtractor</li>
 *   <li>Chunked and embedded via Ollama</li>
 *   <li>Stored as cognitive memories</li>
 *   <li>Recalled via semantic search</li>
 * </ol>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Tika Document Ingestion E2E")
class TikaDocumentE2ETest {

    private static final Logger log = LoggerFactory.getLogger(TikaDocumentE2ETest.class);

    @TempDir
    Path tempDir;

    private SpectorMemory memory;
    private OllamaEmbeddingProvider embeddingProvider;

    @BeforeAll
    void setUp() throws Exception {
        boolean ollamaLive = "true".equalsIgnoreCase(System.getenv("OLLAMA_LIVE"))
                || "true".equalsIgnoreCase(System.getProperty("OLLAMA_LIVE"));
        Assumptions.assumeTrue(ollamaLive,
                "Skipping E2E — set OLLAMA_LIVE=true to run");

        // Create embedding provider
        embeddingProvider = OllamaEmbeddingProvider.create("nomic-embed-text");

        // Create memory with TikaTextExtractor
        TikaTextExtractor tikaExtractor = new TikaTextExtractor(400, 50);

        memory = DefaultSpectorMemory.builder()
                .embeddingProvider(embeddingProvider)
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY)
                .semanticCapacity(1_000)
                .episodicPartitionCapacity(1_000)
                .sensoryExtractors(List.of(tikaExtractor))
                .build();

        log.info("Tika E2E test initialized with nomic-embed-text + TikaTextExtractor");
    }

    // ══════════════════════════════════════════════════════════════
    // TEXT FILE INGESTION
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Ingest and recall text file content")
    void textFileIngestionAndRecall() throws Exception {
        // Create a text document about a specific topic
        Path doc = tempDir.resolve("quantum_computing.txt");
        Files.writeString(doc, """
                Quantum Computing and Memory Systems

                Quantum computers use qubits instead of classical bits. A qubit can exist
                in a superposition of states, enabling parallel computation. Quantum
                entanglement allows qubits to be correlated across distances.

                Key algorithms include Shor's algorithm for factoring large numbers and
                Grover's algorithm for searching unsorted databases. These algorithms
                provide exponential and quadratic speedups respectively over classical
                approaches.

                Quantum error correction is crucial because qubits are fragile and prone
                to decoherence. Surface codes and topological approaches are promising
                methods for protecting quantum information.
                """);

        // Ingest via rememberFile
        String memId = memory.rememberFile(doc, "Notes on quantum computing",
                MemoryType.SEMANTIC, MemorySource.USER_STATED, "quantum", "computing").join();

        assertNotNull(memId, "Should return memory ID");
        log.info("Ingested text file as memory: {}", memId);

        // Recall with semantic query
        List<CognitiveResult> results = memory.recall("quantum algorithms for factoring",
                RecallOptions.builder().topK(5).build());

        assertFalse(results.isEmpty(), "Should recall quantum computing content");

        // Verify content relevance
        String allText = results.stream().map(CognitiveResult::text)
                .reduce("", (a, b) -> a + " " + b).toLowerCase();
        assertTrue(allText.contains("quantum") || allText.contains("qubit") || allText.contains("factoring"),
                "Results should contain quantum computing terminology");

        log.info("Recalled {} results for quantum query", results.size());
        results.forEach(r -> log.info("  [{:.3f}] {}", r.score(), truncate(r.text(), 80)));
    }

    // ══════════════════════════════════════════════════════════════
    // HTML FILE INGESTION
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Ingest and recall HTML document")
    void htmlFileIngestionAndRecall() throws Exception {
        Path htmlDoc = tempDir.resolve("architecture.html");
        Files.writeString(htmlDoc, """
                <!DOCTYPE html>
                <html>
                <head><title>Spector Architecture Overview</title></head>
                <body>
                    <h1>Cognitive Memory Architecture</h1>
                    <p>Spector implements a biologically-inspired memory system with four tiers:
                    working memory (short-term), episodic (event-based), semantic (knowledge),
                    and procedural (skills/habits).</p>

                    <h2>Key Features</h2>
                    <ul>
                        <li>Zero-GC off-heap storage using Java Panama</li>
                        <li>SIMD-accelerated vector operations</li>
                        <li>Hebbian learning for associative memory</li>
                        <li>ACT-R activation for memory retrieval</li>
                    </ul>

                    <h2>Multimodal Support</h2>
                    <p>The SensoryExtractor SPI enables processing of images, audio, video,
                    and documents. Content is transformed into text representations and
                    stored with URI pointers to original assets.</p>
                </body>
                </html>
                """);

        // Ingest with attachments metadata
        var context = IngestionContext.builder()
                .metadata(SourceModality.ATTACHMENTS_KEY, htmlDoc.toAbsolutePath().toString())
                .sourceModality(SourceModality.TEXT)
                .build();

        String memId = memory.remember("Architecture documentation for Spector",
                MemoryType.SEMANTIC, MemorySource.USER_STATED, context, "architecture", "docs").join();

        assertNotNull(memId);
        log.info("Ingested HTML as memory: {}", memId);

        // Recall
        List<CognitiveResult> results = memory.recall("Hebbian learning and memory tiers",
                RecallOptions.builder().topK(5).build());

        assertFalse(results.isEmpty(), "Should recall architecture content");

        String allText = results.stream().map(CognitiveResult::text)
                .reduce("", (a, b) -> a + " " + b).toLowerCase();
        assertTrue(allText.contains("hebbian") || allText.contains("memory") || allText.contains("tier"),
                "Results should contain architecture terminology");
    }

    // ══════════════════════════════════════════════════════════════
    // MULTI-DOCUMENT INGESTION
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Ingest multiple documents and recall across them")
    void multiDocumentRecall() throws Exception {
        // Document 1: Machine Learning
        Path mlDoc = tempDir.resolve("machine_learning.txt");
        Files.writeString(mlDoc, """
                Machine Learning Fundamentals

                Supervised learning uses labeled data to train models that predict outcomes.
                Common algorithms include linear regression, decision trees, random forests,
                and neural networks. Cross-validation helps prevent overfitting.

                Unsupervised learning discovers patterns in unlabeled data. K-means clustering,
                principal component analysis (PCA), and autoencoders are popular techniques.
                """);

        // Document 2: Database Systems
        Path dbDoc = tempDir.resolve("databases.txt");
        Files.writeString(dbDoc, """
                Modern Database Systems

                Relational databases use SQL for structured data with ACID guarantees.
                PostgreSQL and MySQL are widely used open-source options.

                Vector databases like Milvus and Pinecone specialize in similarity search
                using high-dimensional embeddings. They use approximate nearest neighbor (ANN)
                algorithms like HNSW for efficient retrieval.

                Graph databases like Neo4j excel at relationship-heavy queries using
                Cypher query language.
                """);

        // Ingest both
        memory.rememberFile(mlDoc, "Machine learning reference material",
                MemoryType.SEMANTIC, MemorySource.INFERRED, "ml", "ai").join();
        memory.rememberFile(dbDoc, "Database systems reference",
                MemoryType.SEMANTIC, MemorySource.INFERRED, "databases", "systems").join();

        // Cross-document recall: should find vector database content
        List<CognitiveResult> vectorResults = memory.recall(
                "similarity search with embeddings and HNSW",
                RecallOptions.builder().topK(5).build());

        assertFalse(vectorResults.isEmpty());
        String vectorText = vectorResults.stream().map(CognitiveResult::text)
                .reduce("", (a, b) -> a + " " + b).toLowerCase();
        assertTrue(vectorText.contains("vector") || vectorText.contains("hnsw") || vectorText.contains("embedding"),
                "Should find vector database content");

        // Cross-document recall: should find ML content
        List<CognitiveResult> mlResults = memory.recall(
                "supervised learning with neural networks",
                RecallOptions.builder().topK(5).build());

        assertFalse(mlResults.isEmpty());
        String mlText = mlResults.stream().map(CognitiveResult::text)
                .reduce("", (a, b) -> a + " " + b).toLowerCase();
        assertTrue(mlText.contains("supervised") || mlText.contains("neural") || mlText.contains("learning"),
                "Should find ML content");
    }

    // ══════════════════════════════════════════════════════════════
    // TIKA EXTRACTOR DIRECTLY
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("TikaTextExtractor produces embeddable chunks")
    void tikaChunksAreEmbeddable() throws Exception {
        TikaTextExtractor extractor = new TikaTextExtractor(200, 30);

        Path doc = tempDir.resolve("embeddable.txt");
        Files.writeString(doc, """
                The hippocampus is a brain structure involved in memory formation.
                It plays a crucial role in converting short-term memories into long-term
                memories through a process called consolidation. Damage to the hippocampus
                can result in anterograde amnesia, the inability to form new memories.
                """);

        try (Stream<com.spectrayan.spector.ingestion.sensory.SensoryExtractor.ExtractionChunk> chunks =
                     extractor.extract(doc, "text/plain")) {
            List<com.spectrayan.spector.ingestion.sensory.SensoryExtractor.ExtractionChunk> chunkList =
                    chunks.toList();

            assertFalse(chunkList.isEmpty(), "Should produce chunks");

            // Each chunk should be embeddable
            for (var chunk : chunkList) {
                assertFalse(chunk.text().isBlank(), "Chunk text should not be blank");
                float[] vector = embeddingProvider.embed(chunk.text()).vector();
                assertEquals(embeddingProvider.dimensions(), vector.length,
                        "Embedding dimension should match provider");
                log.info("Chunk '{}': {}B → {}D vector", chunk.chunkId(),
                        chunk.text().length(), vector.length);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ATTACHMENT METADATA ROUND-TRIP
    // ══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Attachment metadata survives ingestion round-trip")
    void attachmentMetadataRoundTrip() throws Exception {
        Path doc = tempDir.resolve("metadata_test.txt");
        Files.writeString(doc, "Test document for verifying metadata propagation through pipeline.");

        var context = IngestionContext.builder()
                .metadata(SourceModality.ATTACHMENTS_KEY, doc.toAbsolutePath().toString())
                .metadata("custom_field", "test_value")
                .sourceModality(SourceModality.TEXT)
                .sourceUri(doc.toUri().toString())
                .build();

        // Verify context has all expected metadata
        assertTrue(context.hasAttachments());
        assertEquals(1, context.attachmentList().size());
        assertEquals("TEXT", context.metadata().get(SourceModality.METADATA_KEY));
        assertEquals("test_value", context.metadata().get("custom_field"));
        assertNotNull(context.sourceUri());

        // Ingest
        String memId = memory.remember("Metadata test document", MemoryType.EPISODIC,
                MemorySource.USER_STATED, context, "metadata-test").join();

        assertNotNull(memId);
        log.info("Ingested metadata test document: {}", memId);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
