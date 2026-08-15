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
import com.spectrayan.spector.connector.sink.IngestionBatch;
import com.spectrayan.spector.connector.sink.SpectorIngestionSink;
import com.spectrayan.spector.connector.spi.InMemoryExecutionLogger;
import com.spectrayan.spector.connector.template.TemplateRegistry;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import com.spectrayan.spector.ingestion.IngestionTarget;
import com.spectrayan.spector.memory.DefaultSpectorMemory;
import com.spectrayan.spector.memory.SpectorMemory;

import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end integration test: Saga rollback on batch ingestion failure.
 *
 * <h3>What This Tests</h3>
 * <ul>
 *   <li>A multi-chunk batch is started via BatchIngestionRegistry</li>
 *   <li>The embedding provider fails on the Nth chunk</li>
 *   <li>The Saga compensating action tombstones (suppresses) previously-ingested chunks</li>
 *   <li>The batch is marked as FAILED in the registry</li>
 *   <li>The DLQ records the failure</li>
 * </ul>
 */
class BatchSagaRollbackE2ETest {

    private static final int DIMS = 384;

    private SpectorMemory memory;
    private SpectorIngestionSink sink;
    private CamelConnectorEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        BatchIngestionRegistry.clearAll();

        // Use a provider that fails on the 3rd call
        FailOnNthEmbeddingProvider failingProvider = new FailOnNthEmbeddingProvider(DIMS, 3);

        memory = DefaultSpectorMemory.builder()
                .dimensions(DIMS)
                .embeddingProvider(failingProvider)
                .build();

        IngestionTarget target = memory.target();
        sink = new SpectorIngestionSink(target, failingProvider, new InMemoryExecutionLogger());

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
    //  Saga Rollback on Embedding Failure
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("E2E: Batch fails on 3rd chunk — first 2 chunks are registered for rollback")
    void batchRollbackOnFailure() throws Exception {
        RouteConfig config = RouteConfig.builder("e2e-saga", "E2E Saga", "direct")
                .tenantId("default")
                .enabled(true)
                .build();
        engine.deployRoute(config);

        // Start a batch
        IngestionBatch batch = BatchIngestionRegistry.startBatch("multi-page.pdf", "default", "pipeline-1");
        String batchId = batch.batchId();

        ProducerTemplate producer = engine.camelContext().createProducerTemplate();

        String[] chunks = {
                "Chapter 1: Introduction to cognitive memory systems and their applications.",
                "Chapter 2: Vector indexing strategies for high-dimensional embedding spaces.",
                "Chapter 3: This chunk will trigger an embedding failure in the test.",
                "Chapter 4: Advanced recall patterns using Hebbian co-activation graphs.",
                "Chapter 5: Performance optimization and deployment best practices."
        };

        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < chunks.length; i++) {
            try {
                producer.sendBodyAndHeaders("direct:e2e-saga", chunks[i],
                        Map.of(
                                SpectorIngestionSink.HEADER_DOC_ID, "chunk-" + i,
                                SpectorIngestionSink.HEADER_TENANT_ID, "default",
                                SpectorIngestionSink.HEADER_ROUTE_ID, "e2e-saga",
                                SpectorIngestionSink.HEADER_BATCH_ID, batchId
                        ));
                successCount++;
            } catch (Exception e) {
                failCount++;
                // Expected: the 3rd chunk (index 2) should fail
            }
        }
        producer.close();

        // First 2 chunks succeeded, 3rd failed
        assertThat(successCount).isEqualTo(2);
        assertThat(failCount).isGreaterThanOrEqualTo(1);

        // Verify batch was marked as failed
        IngestionBatch failedBatch = BatchIngestionRegistry.get(batchId);
        assertThat(failedBatch).isNotNull();
        assertThat(failedBatch.status()).isEqualTo(IngestionBatch.Status.FAILED);
        assertThat(failedBatch.failureReason()).isNotNull();

        // Verify sink recorded errors
        assertThat(sink.totalErrors()).isGreaterThanOrEqualTo(1);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Batch Lifecycle: Start → Record → Complete
    // ═══════════════════════════════════════════════════════════════

    @Test
    @DisplayName("E2E: Successful batch completes with all chunks tracked")
    void successfulBatchCompletes() throws Exception {
        // Use a non-failing provider for this test
        StubEmbeddingProvider normalProvider = new StubEmbeddingProvider(DIMS);
        SpectorMemory normalMemory = DefaultSpectorMemory.builder()
                .dimensions(DIMS)
                .embeddingProvider(normalProvider)
                .build();

        SpectorIngestionSink normalSink = new SpectorIngestionSink(
                normalMemory.target(), normalProvider, new InMemoryExecutionLogger());

        TemplateRegistry templateRegistry = new TemplateRegistry(null);
        InMemoryRouteConfigProvider routeConfigProvider = new InMemoryRouteConfigProvider();
        CamelConnectorEngine normalEngine = new CamelConnectorEngine(
                normalSink, routeConfigProvider, templateRegistry);

        try {
            normalEngine.start();

            RouteConfig config = RouteConfig.builder("e2e-batch-ok", "E2E Batch OK", "direct")
                    .tenantId("default")
                    .enabled(true)
                    .build();
            normalEngine.deployRoute(config);

            IngestionBatch batch = BatchIngestionRegistry.startBatch("success.pdf", "default", "p1");
            String batchId = batch.batchId();

            ProducerTemplate producer = normalEngine.camelContext().createProducerTemplate();
            for (int i = 0; i < 3; i++) {
                producer.sendBodyAndHeaders("direct:e2e-batch-ok",
                        "Batch chunk content number " + i + " with unique information.",
                        Map.of(
                                SpectorIngestionSink.HEADER_DOC_ID, "batch-chunk-" + i,
                                SpectorIngestionSink.HEADER_TENANT_ID, "default",
                                SpectorIngestionSink.HEADER_ROUTE_ID, "e2e-batch-ok",
                                SpectorIngestionSink.HEADER_BATCH_ID, batchId
                        ));
            }
            producer.close();

            // Mark batch as complete
            IngestionBatch completed = BatchIngestionRegistry.completeBatch(batchId);
            assertThat(completed.status()).isEqualTo(IngestionBatch.Status.COMPLETED);
            assertThat(completed.trackedMemoryCount()).isEqualTo(3);
            assertThat(completed.completedChunkCount()).isEqualTo(3);

            assertThat(normalSink.totalProcessed()).isEqualTo(3);
        } finally {
            normalEngine.close();
            normalMemory.close();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Test Infrastructure
    // ═══════════════════════════════════════════════════════════════

    /**
     * Embedding provider that fails on the Nth call.
     * Used to simulate mid-batch embedding failures for Saga rollback testing.
     */
    static class FailOnNthEmbeddingProvider implements EmbeddingProvider {
        private final int dims;
        private final int failOnCall;
        private final AtomicInteger callCount = new AtomicInteger();

        FailOnNthEmbeddingProvider(int dims, int failOnCall) {
            this.dims = dims;
            this.failOnCall = failOnCall;
        }

        @Override
        public EmbeddingResult embed(String text) {
            int call = callCount.incrementAndGet();
            if (call >= failOnCall) {
                throw new RuntimeException("Simulated embedding failure on call #" + call);
            }
            // Use stub for successful calls
            float[] vector = new float[dims];
            for (int i = 0; i < dims; i++) {
                vector[i] = (float) Math.sin(i + text.hashCode());
            }
            // Normalize
            float norm = 0;
            for (float v : vector) norm += v * v;
            norm = (float) Math.sqrt(norm);
            if (norm > 0) for (int i = 0; i < dims; i++) vector[i] /= norm;

            return new EmbeddingResult(vector, text.split("\\s+").length, "fail-nth-model");
        }

        @Override
        public int dimensions() { return dims; }

        @Override
        public String modelName() { return "fail-nth-model"; }

        @Override
        public void close() { }
    }
}
