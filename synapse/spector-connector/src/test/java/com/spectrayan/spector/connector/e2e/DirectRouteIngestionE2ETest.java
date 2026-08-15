/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.spectrayan.spector.connector.e2e;

import com.spectrayan.spector.connector.core.CamelConnectorEngine;
import com.spectrayan.spector.connector.model.RouteConfig;
import com.spectrayan.spector.connector.sink.BatchIngestionRegistry;
import com.spectrayan.spector.connector.sink.SpectorIngestionSink;
import com.spectrayan.spector.connector.spi.InMemoryExecutionLogger;
import com.spectrayan.spector.connector.template.TemplateRegistry;
import com.spectrayan.spector.ingestion.IngestionTarget;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.CognitiveResult;

import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end integration test: Camel direct route → SpectorIngestionSink → real SpectorMemory.
 *
 * <h3>What This Tests</h3>
 * <ul>
 *   <li>Real CamelContext starts and loads route templates from YAML</li>
 *   <li>A "direct" route is deployed for a tenant</li>
 *   <li>Text is sent via ProducerTemplate and flows through the full pipeline</li>
 *   <li>PII scrubbing, embedding, and ingestion all happen on the real path</li>
 *   <li>Data lands in a real SpectorMemory instance and is recallable</li>
 * </ul>
 *
 * <p>The only stub is the EmbeddingProvider (deterministic hash-based vectors
 * instead of Ollama) — everything else is production code.</p>
 */
class DirectRouteIngestionE2ETest {

    private static final int DIMS = 384;

    private StubEmbeddingProvider embeddingProvider;
    private SpectorMemory memory;
    private SpectorIngestionSink sink;
    private CamelConnectorEngine engine;
    private InMemoryRouteConfigProvider routeConfigProvider;
    private InMemoryExecutionLogger executionLogger;

    @BeforeEach
    void setUp() throws Exception {
        // 0. Reset static registries for test isolation
        BatchIngestionRegistry.clearAll();

        // 1. Real SpectorMemory with stub embedder
        embeddingProvider = new StubEmbeddingProvider(DIMS);
        memory = DefaultSpectorMemory.builder()
                .dimensions(DIMS)
                .embeddingProvider(embeddingProvider)
                .build();

        // 2. Real IngestionTarget from SpectorMemory
        IngestionTarget target = memory.target();

        // 3. Real SpectorIngestionSink
        executionLogger = new InMemoryExecutionLogger();
        sink = new SpectorIngestionSink(target, embeddingProvider, executionLogger);

        // 4. Real TemplateRegistry (loads route-templates.yaml from classpath)
        TemplateRegistry templateRegistry = new TemplateRegistry(null);

        // 5. Real CamelConnectorEngine
        routeConfigProvider = new InMemoryRouteConfigProvider();
        engine = new CamelConnectorEngine(sink, routeConfigProvider, templateRegistry);
        engine.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null) engine.close();
        if (memory != null) memory.close();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Happy Path: Single Document Ingestion
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("E2E: Direct route ingests text → embeds → stores in SpectorMemory → recallable")
    void directRouteIngestsAndRecalls() throws Exception {
        // Deploy a direct route for 'test-tenant'
        RouteConfig config = RouteConfig.builder("e2e-direct-1", "E2E Direct Test", "direct")
                .tenantId("default")
                .enabled(true)
                .build();
        engine.deployRoute(config);

        // Send a document through the route
        ProducerTemplate producer = engine.camelContext().createProducerTemplate();
        producer.sendBodyAndHeaders("direct:e2e-direct-1",
                "Spector is a cognitive memory engine that provides human-like recall capabilities.",
                Map.of(
                        SpectorIngestionSink.HEADER_DOC_ID, "doc-spector-overview",
                        SpectorIngestionSink.HEADER_TENANT_ID, "default",
                        SpectorIngestionSink.HEADER_ROUTE_ID, "e2e-direct-1"
                ));
        producer.close();

        // Verify ingestion metrics
        assertThat(sink.totalProcessed()).isEqualTo(1);
        assertThat(sink.totalErrors()).isZero();

        // Verify execution was logged
        assertThat(executionLogger.allRecords()).hasSize(1);

        // Verify the document is in memory and recallable
        List<CognitiveResult> results = memory.recall("cognitive memory engine");
        assertThat(results).isNotEmpty();
        assertThat(results)
                .extracting(CognitiveResult::text)
                .anyMatch(text -> text.contains("cognitive memory"));
    }

    // ═══════════════════════════════════════════════════════════════
    //  Multiple Documents
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("E2E: Multiple documents ingested through same route are all recallable")
    void multipleDocumentsAllRecallable() throws Exception {
        RouteConfig config = RouteConfig.builder("e2e-multi", "E2E Multi Doc", "direct")
                .tenantId("default")
                .enabled(true)
                .build();
        engine.deployRoute(config);

        ProducerTemplate producer = engine.camelContext().createProducerTemplate();

        String[] docs = {
                "Apache Camel is an integration framework that implements Enterprise Integration Patterns.",
                "Vector databases store high-dimensional embeddings for similarity search and retrieval.",
                "The Saga pattern provides distributed transaction management through compensating actions."
        };

        for (int i = 0; i < docs.length; i++) {
            producer.sendBodyAndHeaders("direct:e2e-multi", docs[i],
                    Map.of(
                            SpectorIngestionSink.HEADER_DOC_ID, "multi-doc-" + i,
                            SpectorIngestionSink.HEADER_TENANT_ID, "default",
                            SpectorIngestionSink.HEADER_ROUTE_ID, "e2e-multi"
                    ));
        }
        producer.close();

        // All 3 documents ingested
        assertThat(sink.totalProcessed()).isEqualTo(3);
        assertThat(embeddingProvider.callCount()).isEqualTo(3);

        // Each document is recallable by its distinctive content
        assertThat(memory.recall("Enterprise Integration Patterns")).isNotEmpty();
        assertThat(memory.recall("similarity search embeddings")).isNotEmpty();
        assertThat(memory.recall("Saga compensating actions")).isNotEmpty();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Empty / Blank Content is Skipped
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("E2E: Empty content is silently skipped without error")
    void emptyContentSkipped() throws Exception {
        RouteConfig config = RouteConfig.builder("e2e-empty", "E2E Empty", "direct")
                .tenantId("default")
                .enabled(true)
                .build();
        engine.deployRoute(config);

        ProducerTemplate producer = engine.camelContext().createProducerTemplate();

        // Send blank content
        producer.sendBodyAndHeaders("direct:e2e-empty", "   ",
                Map.of(
                        SpectorIngestionSink.HEADER_DOC_ID, "blank-doc",
                        SpectorIngestionSink.HEADER_TENANT_ID, "default",
                        SpectorIngestionSink.HEADER_ROUTE_ID, "e2e-empty"
                ));

        // Send real content after
        producer.sendBodyAndHeaders("direct:e2e-empty",
                "This is valid content that should be ingested.",
                Map.of(
                        SpectorIngestionSink.HEADER_DOC_ID, "valid-doc",
                        SpectorIngestionSink.HEADER_TENANT_ID, "default",
                        SpectorIngestionSink.HEADER_ROUTE_ID, "e2e-empty"
                ));
        producer.close();

        // Only 1 document processed (blank was skipped)
        assertThat(sink.totalProcessed()).isEqualTo(1);
        assertThat(sink.totalErrors()).isZero();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Route Lifecycle: Deploy → Stop → Remove
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("E2E: Route lifecycle — deploy, use, stop, remove")
    void routeLifecycle() throws Exception {
        RouteConfig config = RouteConfig.builder("e2e-lifecycle", "E2E Lifecycle", "direct")
                .tenantId("default")
                .enabled(true)
                .build();

        // Deploy
        engine.deployRoute(config);
        assertThat(engine.activeRouteIds()).contains("e2e-lifecycle");

        // Use
        ProducerTemplate producer = engine.camelContext().createProducerTemplate();
        producer.sendBodyAndHeaders("direct:e2e-lifecycle",
                "Document ingested before route removal.",
                Map.of(
                        SpectorIngestionSink.HEADER_DOC_ID, "lifecycle-doc",
                        SpectorIngestionSink.HEADER_TENANT_ID, "default",
                        SpectorIngestionSink.HEADER_ROUTE_ID, "e2e-lifecycle"
                ));
        producer.close();
        assertThat(sink.totalProcessed()).isEqualTo(1);

        // Remove
        boolean removed = engine.removeRoute("e2e-lifecycle");
        assertThat(removed).isTrue();
        assertThat(engine.activeRouteIds()).doesNotContain("e2e-lifecycle");

        // Data should still be in memory even after route removal
        assertThat(memory.recall("route removal")).isNotEmpty();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Execution Logging
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("E2E: Execution logger captures route, tenant, and timing info")
    void executionLoggingCaptures() throws Exception {
        RouteConfig config = RouteConfig.builder("e2e-logging", "E2E Logging", "direct")
                .tenantId("default")
                .enabled(true)
                .build();
        engine.deployRoute(config);

        ProducerTemplate producer = engine.camelContext().createProducerTemplate();
        producer.sendBodyAndHeaders("direct:e2e-logging",
                "Content for execution logging verification.",
                Map.of(
                        SpectorIngestionSink.HEADER_DOC_ID, "log-doc",
                        SpectorIngestionSink.HEADER_TENANT_ID, "default",
                        SpectorIngestionSink.HEADER_ROUTE_ID, "e2e-logging"
                ));
        producer.close();

        var records = executionLogger.allRecords();
        assertThat(records).hasSize(1);

        var record = records.get(0);
        assertThat(record.routeId()).isEqualTo("e2e-logging");
        assertThat(record.tenantId()).isEqualTo("default");
        assertThat(record.documentsProcessed()).isEqualTo(1);
        assertThat(record.duration()).isNotNull();
    }
}
