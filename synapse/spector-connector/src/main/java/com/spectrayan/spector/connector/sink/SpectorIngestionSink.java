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
import com.spectrayan.spector.connector.spi.ChunkChangeDetector;
import com.spectrayan.spector.connector.spi.ExecutionLogger;
import com.spectrayan.spector.ingestion.IngestionTarget;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Camel processor that bridges Camel exchanges to Spector's ingestion pipeline.
 *
 * <p>When a Camel route processes a document, it flows through this sink which:</p>
 * <ol>
 *   <li>Extracts document metadata and text payload from the Camel exchange</li>
 *   <li>Scrubs PII and secrets via {@link PiiScrubber}</li>
 *   <li>Optionally skips unchanged chunks using {@link ChunkChangeDetector}</li>
 *   <li>Embeds the scrubbed text using the configured {@link EmbeddingProvider}</li>
 *   <li>Ingests the result into Spector via {@link IngestionTarget}</li>
 *   <li>Logs the execution result to {@link ExecutionLogger}</li>
 * </ol>
 *
 * <h3>Exchange Headers</h3>
 * <ul>
 *   <li>{@code spector-doc-id} — document ID (required; falls back to CamelFileName or exchange ID)</li>
 *   <li>{@code spector-collection} — target collection (optional, default: "default")</li>
 *   <li>{@code spector-tenant-id} — tenant ID for logging (optional, default: "default")</li>
 *   <li>{@code spector-route-id} — originating route ID for logging (optional)</li>
 *   <li>{@code spector-pipeline-id} — pipeline ID for delta upsert tracking (optional)</li>
 *   <li>{@code spector-chunk-index} — chunk index for delta upsert tracking (optional)</li>
 * </ul>
 *
 * <h3>Thread Safety</h3>
 * <p>Fully thread-safe. Multiple Camel routes can share a single sink instance.
 * Embedded counters use atomic operations.</p>
 */
public class SpectorIngestionSink implements Processor {

    private static final Logger log = LoggerFactory.getLogger(SpectorIngestionSink.class);

    public static final String HEADER_DOC_ID = "spector-doc-id";
    public static final String HEADER_COLLECTION = "spector-collection";
    public static final String HEADER_TENANT_ID = "spector-tenant-id";
    public static final String HEADER_ROUTE_ID = "spector-route-id";
    public static final String HEADER_PIPELINE_ID = "spector-pipeline-id";
    public static final String HEADER_CHUNK_INDEX = "spector-chunk-index";

    private final IngestionTarget target;
    private final EmbeddingProvider embeddingProvider;
    private final ExecutionLogger executionLogger;
    private final ChunkChangeDetector chunkChangeDetector;

    // Metrics counters
    private final AtomicInteger totalProcessed = new AtomicInteger();
    private final AtomicInteger totalErrors = new AtomicInteger();
    private final AtomicInteger totalSkippedUnchanged = new AtomicInteger();

    /**
     * Creates an ingestion sink.
     *
     * @param target            Spector ingestion target (engine or memory)
     * @param embeddingProvider provider for embedding text → vectors
     * @param executionLogger   logger for execution audit trail (nullable)
     */
    public SpectorIngestionSink(IngestionTarget target,
                                EmbeddingProvider embeddingProvider,
                                ExecutionLogger executionLogger) {
        this(target, embeddingProvider, executionLogger, null);
    }

    /**
     * Creates an ingestion sink with optional delta upsert support.
     *
     * @param target              Spector ingestion target (engine or memory)
     * @param embeddingProvider   provider for embedding text → vectors
     * @param executionLogger     logger for execution audit trail (nullable)
     * @param chunkChangeDetector chunk-level change detector for delta upserts (nullable)
     */
    public SpectorIngestionSink(IngestionTarget target,
                                EmbeddingProvider embeddingProvider,
                                ExecutionLogger executionLogger,
                                ChunkChangeDetector chunkChangeDetector) {
        this.target = Objects.requireNonNull(target, "IngestionTarget must not be null");
        this.embeddingProvider = Objects.requireNonNull(embeddingProvider, "EmbeddingProvider must not be null");
        this.executionLogger = executionLogger;
        this.chunkChangeDetector = chunkChangeDetector;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Instant start = Instant.now();
        String routeId = exchange.getIn().getHeader(HEADER_ROUTE_ID, "unknown", String.class);
        String tenantId = exchange.getIn().getHeader(HEADER_TENANT_ID, "default", String.class);
        String docId = resolveDocId(exchange);
        String originalContent = exchange.getIn().getBody(String.class);

        if (originalContent == null || originalContent.isBlank()) {
            log.warn("[Sink] Empty content for doc '{}' from route '{}', skipping", docId, routeId);
            return;
        }

        try {
            // 1. PII and Secret Scrubbing
            String scrubbedContent = PiiScrubber.scrub(originalContent);

            // 1b. Delta Upsert: Skip unchanged chunks if detector is active
            String pipelineId = exchange.getIn().getHeader(HEADER_PIPELINE_ID, String.class);
            Integer chunkIndexHeader = exchange.getIn().getHeader(HEADER_CHUNK_INDEX, Integer.class);
            int chunkIndex = chunkIndexHeader != null ? chunkIndexHeader : -1;
            if (chunkChangeDetector != null && pipelineId != null && chunkIndex >= 0) {
                if (!chunkChangeDetector.hasChunkChanged(pipelineId, docId, chunkIndex, scrubbedContent)) {
                    totalSkippedUnchanged.incrementAndGet();
                    log.debug("[Sink] Chunk {}:{} unchanged, skipping re-embedding (delta upsert)",
                            docId, chunkIndex);
                    return;
                }
            }

            // 2. Embed the clean content
            EmbeddingResult embeddingResult = embeddingProvider.embed(scrubbedContent);
            float[] vector = embeddingResult.vector();

            // 3. Ingest into Spector
            target.ingest(docId, scrubbedContent, vector);

            // 3b. Delta Upsert: Track the chunk hash + memory ID
            if (chunkChangeDetector != null && pipelineId != null && chunkIndex >= 0) {
                chunkChangeDetector.trackChunk(pipelineId, docId, chunkIndex, scrubbedContent, docId);
            }

            int processed = totalProcessed.incrementAndGet();
            Duration elapsed = Duration.between(start, Instant.now());

            log.debug("[Sink] Ingested doc '{}' ({}ms, {} tokens, tenant={}, total={})",
                    docId, elapsed.toMillis(), embeddingResult.tokenCount(), tenantId, processed);

            if (executionLogger != null) {
                executionLogger.log(ExecutionRecord.success(routeId, tenantId, 1, elapsed));
            }
        } catch (Exception e) {
            totalErrors.incrementAndGet();
            Duration elapsed = Duration.between(start, Instant.now());

            log.error("[Sink] Failed to ingest document from route '{}' for tenant '{}': {}",
                    routeId, tenantId, e.getMessage(), e);

            if (executionLogger != null) {
                executionLogger.log(ExecutionRecord.failure(routeId, tenantId, 0, 1, elapsed, e.getMessage()));
            }

            throw e;
        }
    }

    /**
     * Resolves the document ID from the exchange.
     *
     * <p>Priority: spector-doc-id header &gt; CamelFileName header &gt; exchange ID.</p>
     */
    private String resolveDocId(Exchange exchange) {
        String docId = exchange.getIn().getHeader(HEADER_DOC_ID, String.class);
        if (docId != null && !docId.isBlank()) {
            return docId;
        }
        String fileName = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
        if (fileName != null && !fileName.isBlank()) {
            return fileName;
        }
        return exchange.getExchangeId();
    }

    /** Returns total documents successfully ingested. */
    public int totalProcessed() {
        return totalProcessed.get();
    }

    /** Returns total ingestion errors. */
    public int totalErrors() {
        return totalErrors.get();
    }

    /** Returns total chunks skipped due to unchanged content (delta upserts). */
    public int totalSkippedUnchanged() {
        return totalSkippedUnchanged.get();
    }

    /** Resets all counters (for testing). */
    public void resetCounters() {
        totalProcessed.set(0);
        totalErrors.set(0);
        totalSkippedUnchanged.set(0);
    }

    /** Returns the configured ExecutionLogger, or null if not enabled. */
    public ExecutionLogger executionLogger() {
        return executionLogger;
    }
}
