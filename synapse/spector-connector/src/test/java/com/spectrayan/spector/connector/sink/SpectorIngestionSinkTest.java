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
package com.spectrayan.spector.connector.sink;

import com.spectrayan.spector.connector.model.ExecutionRecord;
import com.spectrayan.spector.connector.spi.InMemoryExecutionLogger;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import com.spectrayan.spector.ingestion.IngestionTarget;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for {@link SpectorIngestionSink}.
 *
 * <p>Tests cover: happy path, header resolution, empty content,
 * embedding failures, ingestion failures, null logger, and metrics.</p>
 */
@ExtendWith(MockitoExtension.class)
class SpectorIngestionSinkTest {

    @Mock private IngestionTarget target;
    @Mock private EmbeddingProvider embeddingProvider;
    private InMemoryExecutionLogger logger;
    private SpectorIngestionSink sink;

    @BeforeEach
    void setUp() {
        logger = new InMemoryExecutionLogger();
        lenient().when(embeddingProvider.dimensions()).thenReturn(384);
        sink = new SpectorIngestionSink(target, embeddingProvider, logger);
    }

    // ─────────────── Happy Path ───────────────

    @Test
    @DisplayName("Processes exchange: embeds text and ingests into Spector")
    void happyPath() throws Exception {
        Exchange exchange = mockExchange("doc-1", "Hello World", "route-1", "default");
        when(embeddingProvider.embed("Hello World"))
                .thenReturn(new EmbeddingResult(new float[384], 384, "test-model"));

        sink.process(exchange);

        verify(target).ingest(eq("doc-1"), eq("Hello World"), any(float[].class));
        assertThat(sink.totalProcessed()).isEqualTo(1);
        assertThat(sink.totalErrors()).isZero();

        // Verify execution was logged
        var records = logger.allRecords();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).status()).isEqualTo(ExecutionRecord.ExecutionStatus.COMPLETED);
        assertThat(records.get(0).documentsProcessed()).isEqualTo(1);
    }

    @Test
    @DisplayName("Multi-Tenant Isolation: routes ingestion to tenant-specific memory workspace")
    void multiTenantRouting() throws Exception {
        // Create a tenant-aware sink with registry for this test
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("spector-sink-test-");
        var tenantRegistry = new TenantMemoryRegistry(tempDir, embeddingProvider, 384);
        lenient().when(embeddingProvider.dimensions()).thenReturn(384);
        var tenantSink = new SpectorIngestionSink(target, embeddingProvider, logger, null, tenantRegistry);

        Exchange exchange = mockExchange("doc-tenant", "Isolated data", "route-1", "tenant-a");
        when(embeddingProvider.embed("Isolated data"))
                .thenReturn(new EmbeddingResult(new float[384], 384, "test-model"));

        tenantSink.process(exchange);

        // Verify that it did NOT ingest into default target
        verifyNoInteractions(target);

        // Verify that a tenant-specific SpectorMemory was registered
        var tenantMemory = tenantRegistry.getMemoryForTenant("tenant-a");
        assertThat(tenantMemory).isNotNull();
        assertThat(tenantSink.totalProcessed()).isEqualTo(1);

        tenantRegistry.close();
    }

    // ─────────────── Header Resolution ───────────────

    @Test
    @DisplayName("Falls back to CamelFileName when spector-doc-id is absent")
    void fallsBackToFileName() throws Exception {
        Exchange exchange = mockExchangeWithFileName("report.pdf", "PDF content");
        when(embeddingProvider.embed("PDF content"))
                .thenReturn(new EmbeddingResult(new float[384], 384, "m"));

        sink.process(exchange);

        verify(target).ingest(eq("report.pdf"), eq("PDF content"), any(float[].class));
    }

    @Test
    @DisplayName("Falls back to exchange ID when both headers absent")
    void fallsBackToExchangeId() throws Exception {
        Exchange exchange = mockExchangeNoHeaders("exchange-123", "Some text");
        when(embeddingProvider.embed("Some text"))
                .thenReturn(new EmbeddingResult(new float[384], 384, "m"));

        sink.process(exchange);

        verify(target).ingest(eq("exchange-123"), eq("Some text"), any(float[].class));
    }

    // ─────────────── Edge Cases ───────────────

    @Test
    @DisplayName("Skips empty content without error")
    void skipsEmptyContent() throws Exception {
        Exchange exchange = mockExchange("doc-1", "", "route-1", "default");

        sink.process(exchange);

        verifyNoInteractions(target);
        assertThat(sink.totalProcessed()).isZero();
        assertThat(sink.totalErrors()).isZero();
    }

    @Test
    @DisplayName("Skips null content without error")
    void skipsNullContent() throws Exception {
        Exchange exchange = mockExchange("doc-1", null, "route-1", "default");

        sink.process(exchange);

        verifyNoInteractions(target);
    }

    @Test
    @DisplayName("Skips blank-only content without error")
    void skipsBlankContent() throws Exception {
        Exchange exchange = mockExchange("doc-1", "   \n  \t  ", "route-1", "default");

        sink.process(exchange);

        verifyNoInteractions(target);
    }

    // ─────────────── Error Handling ───────────────

    @Test
    @DisplayName("Embedding failure increments error counter and re-throws")
    void embeddingFailureReThrows() {
        Exchange exchange = mockExchange("doc-1", "some text", "route-1", "default");
        when(embeddingProvider.embed("some text"))
                .thenThrow(new RuntimeException("Model unavailable"));

        assertThatThrownBy(() -> sink.process(exchange))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Model unavailable");

        assertThat(sink.totalProcessed()).isZero();
        assertThat(sink.totalErrors()).isEqualTo(1);

        // Verify failure was logged
        var records = logger.allRecords();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).status()).isEqualTo(ExecutionRecord.ExecutionStatus.FAILED);
        assertThat(records.get(0).errorMessage()).contains("Model unavailable");
    }

    @Test
    @DisplayName("Ingestion failure increments error counter and re-throws")
    void ingestionFailureReThrows() {
        Exchange exchange = mockExchange("doc-1", "text", "route-1", "default");
        when(embeddingProvider.embed("text"))
                .thenReturn(new EmbeddingResult(new float[384], 384, "m"));
        doThrow(new RuntimeException("Storage full"))
                .when(target).ingest(anyString(), anyString(), any(float[].class));

        assertThatThrownBy(() -> sink.process(exchange))
                .hasMessageContaining("Storage full");

        assertThat(sink.totalErrors()).isEqualTo(1);
    }

    // ─────────────── Null Logger ───────────────

    @Test
    @DisplayName("Works correctly when execution logger is null")
    void worksWithNullLogger() throws Exception {
        var sinkNoLogger = new SpectorIngestionSink(target, embeddingProvider, null);
        Exchange exchange = mockExchange("doc-1", "hello", "route-1", "default");
        when(embeddingProvider.embed("hello"))
                .thenReturn(new EmbeddingResult(new float[384], 384, "m"));

        sinkNoLogger.process(exchange);

        verify(target).ingest(eq("doc-1"), eq("hello"), any(float[].class));
        assertThat(sinkNoLogger.totalProcessed()).isEqualTo(1);
    }

    // ─────────────── Metrics Reset ───────────────

    @Test
    @DisplayName("resetCounters clears all metrics")
    void resetCountersClearsAll() throws Exception {
        Exchange exchange = mockExchange("doc-1", "text", "route-1", "default");
        when(embeddingProvider.embed("text"))
                .thenReturn(new EmbeddingResult(new float[384], 384, "m"));

        sink.process(exchange);
        assertThat(sink.totalProcessed()).isEqualTo(1);

        sink.resetCounters();
        assertThat(sink.totalProcessed()).isZero();
        assertThat(sink.totalErrors()).isZero();
    }

    // ─────────────── Constructor Validation ───────────────

    @Test
    @DisplayName("Constructor rejects null IngestionTarget")
    void rejectsNullTarget() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SpectorIngestionSink(null, embeddingProvider, logger));
    }

    @Test
    @DisplayName("Constructor rejects null EmbeddingProvider")
    void rejectsNullProvider() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SpectorIngestionSink(target, null, logger));
    }

    // ─────────────── Multiple Documents ───────────────

    @Test
    @DisplayName("Processes multiple documents and tracks counters correctly")
    void processesMultipleDocs() throws Exception {
        when(embeddingProvider.embed(anyString()))
                .thenReturn(new EmbeddingResult(new float[384], 384, "m"));

        for (int i = 0; i < 5; i++) {
            sink.process(mockExchange("doc-" + i, "content " + i, "route-1", "default"));
        }

        assertThat(sink.totalProcessed()).isEqualTo(5);
        verify(target, times(5)).ingest(anyString(), anyString(), any(float[].class));
    }

    // ─────────────── Helpers ───────────────

    private Exchange mockExchange(String docId, String body, String routeId, String tenantId) {
        Exchange exchange = mock(Exchange.class);
        Message message = mock(Message.class);
        when(exchange.getIn()).thenReturn(message);
        when(message.getBody(String.class)).thenReturn(body);
        when(message.getHeader(SpectorIngestionSink.HEADER_ROUTE_ID, "unknown", String.class)).thenReturn(routeId);
        when(message.getHeader(SpectorIngestionSink.HEADER_TENANT_ID, "default", String.class)).thenReturn(tenantId);
        // These may not be called if body is null/blank (early return), so mark lenient
        lenient().when(exchange.getExchangeId()).thenReturn("ex-" + docId);
        lenient().when(message.getHeader(SpectorIngestionSink.HEADER_DOC_ID, String.class)).thenReturn(docId);
        lenient().when(message.getHeader(SpectorIngestionSink.HEADER_COLLECTION, "default", String.class)).thenReturn("default");
        return exchange;
    }

    private Exchange mockExchangeWithFileName(String fileName, String body) {
        Exchange exchange = mock(Exchange.class);
        Message message = mock(Message.class);
        when(exchange.getIn()).thenReturn(message);
        when(message.getBody(String.class)).thenReturn(body);
        when(message.getHeader(SpectorIngestionSink.HEADER_DOC_ID, String.class)).thenReturn(null);
        when(message.getHeader(Exchange.FILE_NAME, String.class)).thenReturn(fileName);
        when(message.getHeader(SpectorIngestionSink.HEADER_ROUTE_ID, "unknown", String.class)).thenReturn("route");
        when(message.getHeader(SpectorIngestionSink.HEADER_TENANT_ID, "default", String.class)).thenReturn("default");
        lenient().when(exchange.getExchangeId()).thenReturn("ex-file");
        return exchange;
    }

    private Exchange mockExchangeNoHeaders(String exchangeId, String body) {
        Exchange exchange = mock(Exchange.class);
        Message message = mock(Message.class);
        when(exchange.getIn()).thenReturn(message);
        when(exchange.getExchangeId()).thenReturn(exchangeId);
        when(message.getBody(String.class)).thenReturn(body);
        when(message.getHeader(SpectorIngestionSink.HEADER_DOC_ID, String.class)).thenReturn(null);
        when(message.getHeader(Exchange.FILE_NAME, String.class)).thenReturn(null);
        when(message.getHeader(SpectorIngestionSink.HEADER_ROUTE_ID, "unknown", String.class)).thenReturn("route");
        when(message.getHeader(SpectorIngestionSink.HEADER_TENANT_ID, "default", String.class)).thenReturn("default");
        return exchange;
    }
}

