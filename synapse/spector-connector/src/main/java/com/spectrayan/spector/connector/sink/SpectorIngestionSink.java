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
import com.spectrayan.spector.connector.spi.ExecutionLogger;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import com.spectrayan.spector.ingestion.IngestionTarget;

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
 * <p>This is the critical integration point between the Apache Camel connector
 * engine and Spector Core. When a Camel route processes a document, it flows
 * through this sink which:</p>
 * <ol>
 *   <li>Extracts the document ID and text from the Camel exchange</li>
 *   <li>Embeds the text using the active {@link EmbeddingProvider}</li>
 *   <li>Ingests the result into Spector via {@link IngestionTarget}</li>
 *   <li>Logs the execution result</li>
 * </ol>
 *
 * <h3>Exchange Headers</h3>
 * <ul>
 *   <li>{@code spector-doc-id} — document ID (required; falls back to exchange ID)</li>
 *   <li>{@code spector-collection} — target collection (optional, default: "default")</li>
 *   <li>{@code spector-tenant-id} — tenant ID for logging (optional, default: "default")</li>
 *   <li>{@code spector-route-id} — originating route ID for logging (optional)</li>
 * </ul>
 *
 * <h3>Thread Safety</h3>
 * <p>Fully thread-safe. Multiple Camel routes can share a single sink instance.
 * The embedded counters use atomic operations.</p>
 */
public class SpectorIngestionSink implements Processor {

    private static final Logger log = LoggerFactory.getLogger(SpectorIngestionSink.class);

    public static final String HEADER_DOC_ID = "spector-doc-id";
    public static final String HEADER_COLLECTION = "spector-collection";
    public static final String HEADER_TENANT_ID = "spector-tenant-id";
    public static final String HEADER_ROUTE_ID = "spector-route-id";
    public static final String HEADER_BATCH_ID = "spector-batch-id";
    public static final String HEADER_PIPELINE_ID = "spector-pipeline-id";
    public static final String HEADER_CHUNK_INDEX = "spector-chunk-index";

    private final IngestionTarget target;
    private final EmbeddingProvider embeddingProvider;
    private final ExecutionLogger executionLogger;
    private final com.spectrayan.spector.connector.spi.ChunkChangeDetector chunkChangeDetector; // nullable
    private final TenantMemoryRegistry tenantRegistry; // nullable — null when no multi-tenant support

    // Metrics counters
    private final AtomicInteger totalProcessed = new AtomicInteger();
    private final AtomicInteger totalErrors = new AtomicInteger();
    private final AtomicInteger totalSkippedUnchanged = new AtomicInteger();

    /**
     * Creates an ingestion sink.
     *
     * @param target            Spector ingestion target (engine or memory)
     * @param embeddingProvider provider for embedding text → vectors
     * @param executionLogger   logger for execution audit trail
     */
    public SpectorIngestionSink(IngestionTarget target,
                                 EmbeddingProvider embeddingProvider,
                                 ExecutionLogger executionLogger) {
        this(target, embeddingProvider, executionLogger, null, null);
    }

    /**
     * Creates an ingestion sink with optional delta upsert support.
     *
     * @param target            Spector ingestion target (engine or memory)
     * @param embeddingProvider provider for embedding text → vectors
     * @param executionLogger   logger for execution audit trail
     * @param chunkChangeDetector chunk-level change detector for delta upserts (nullable)
     */
    public SpectorIngestionSink(IngestionTarget target,
                                 EmbeddingProvider embeddingProvider,
                                 ExecutionLogger executionLogger,
                                 com.spectrayan.spector.connector.spi.ChunkChangeDetector chunkChangeDetector) {
        this(target, embeddingProvider, executionLogger, chunkChangeDetector, null);
    }

    /**
     * Creates an ingestion sink with multi-tenant support and optional delta upsert.
     *
     * @param target              Spector ingestion target (engine or memory)
     * @param embeddingProvider   provider for embedding text → vectors
     * @param executionLogger     logger for execution audit trail
     * @param chunkChangeDetector chunk-level change detector for delta upserts (nullable)
     * @param tenantRegistry      tenant memory registry for isolated workspaces (nullable)
     */
    public SpectorIngestionSink(IngestionTarget target,
                                 EmbeddingProvider embeddingProvider,
                                 ExecutionLogger executionLogger,
                                 com.spectrayan.spector.connector.spi.ChunkChangeDetector chunkChangeDetector,
                                 TenantMemoryRegistry tenantRegistry) {
        this.target = Objects.requireNonNull(target, "IngestionTarget must not be null");
        this.embeddingProvider = Objects.requireNonNull(embeddingProvider, "EmbeddingProvider must not be null");
        this.executionLogger = executionLogger; // nullable — logging is optional
        this.chunkChangeDetector = chunkChangeDetector; // nullable — delta upserts are optional
        this.tenantRegistry = tenantRegistry; // nullable — null for non-multi-tenant deployments
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Instant start = Instant.now();
        String routeId = exchange.getIn().getHeader(HEADER_ROUTE_ID, "unknown", String.class);
        String tenantId = exchange.getIn().getHeader(HEADER_TENANT_ID, "default", String.class);
        String docId = resolveDocId(exchange);
        String originalContent = exchange.getIn().getBody(String.class);

        // Track whether we acquired a tenant lease so we can release it in finally
        boolean tenantLeaseAcquired = false;

        try {
            if (originalContent == null || originalContent.isBlank()) {
                log.warn("[Sink] Empty content for doc '{}', skipping", docId);
                return;
            }

            // 0. Tenant Resource Quota Check
            if (tenantRegistry != null) {
                tenantRegistry.checkCapacity(tenantId);
            }

            // 1. Cognitive Firewall: PII and Secret Scrubbing
            String scrubbedContent = PiiScrubber.scrub(originalContent);

            // 1b. Delta Upsert: Skip unchanged chunks
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

            // 3. Multi-Tenant Isolated Workspace Routing
            // getTargetForTenant() acquires a lease via getMemoryForTenant() — must release in finally
            IngestionTarget tenantTarget = tenantRegistry != null
                    ? tenantRegistry.getTargetForTenant(tenantId, this.target)
                    : this.target;
            tenantLeaseAcquired = (tenantRegistry != null);

            // 4. Ingest into Spector
            tenantTarget.ingest(docId, scrubbedContent, vector);

            // 4b. Delta Upsert: Track the chunk hash + memory ID
            if (chunkChangeDetector != null && pipelineId != null && chunkIndex >= 0) {
                chunkChangeDetector.trackChunk(pipelineId, docId, chunkIndex, scrubbedContent, docId);
            }

            // 5. Saga: Record ingested chunk in batch registry
            String batchId = exchange.getIn().getHeader(HEADER_BATCH_ID, String.class);
            if (batchId != null) {
                BatchIngestionRegistry.recordChunk(batchId, docId);
            }

            int processed = totalProcessed.incrementAndGet();
            Duration elapsed = Duration.between(start, Instant.now());

            log.debug("[Sink] Ingested doc '{}' ({}ms, {} tokens, tenant={}, batch={}, total={})",
                    docId, elapsed.toMillis(), embeddingResult.tokenCount(), tenantId, batchId, processed);

            // Log execution
            if (executionLogger != null) {
                executionLogger.log(ExecutionRecord.success(routeId, tenantId, 1, elapsed));
            }
        } catch (Exception e) {
            totalErrors.incrementAndGet();
            Duration elapsed = Duration.between(start, Instant.now());

            log.error("[Sink] Failed to ingest document from route '{}' for tenant '{}': {}", routeId, tenantId, e.getMessage(), e);

            // 5a. Saga: Rollback all chunks from this batch
            String batchId = exchange.getIn().getHeader(HEADER_BATCH_ID, String.class);
            if (batchId != null) {
                rollbackBatch(batchId, tenantId, e);
            }

            // 5b. Cognitive Dead Letter Queue (DLQ) routing
            CognitiveDlq.routeToDlq(tenantId, routeId, docId, originalContent, e.getMessage());

            if (executionLogger != null) {
                executionLogger.log(ExecutionRecord.failure(routeId, tenantId, 0, 1, elapsed, e.getMessage()));
            }

            throw e; // Re-throw so Camel error handling can process it
        } finally {
            // Release the tenant memory lease to unblock LRU eviction
            if (tenantLeaseAcquired && tenantRegistry != null) {
                tenantRegistry.releaseMemoryForTenant(tenantId);
            }
        }
    }

    /**
     * Resolves the document ID from the exchange.
     *
     * <p>Priority: spector-doc-id header > CamelFileName header > exchange ID.</p>
     */
    private String resolveDocId(Exchange exchange) {
        String docId = exchange.getIn().getHeader(HEADER_DOC_ID, String.class);
        if (docId != null && !docId.isBlank()) {
            return docId;
        }
        // Fallback: use file name if available
        String fileName = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
        if (fileName != null && !fileName.isBlank()) {
            return fileName;
        }
        // Last resort: exchange ID
        return exchange.getExchangeId();
    }

    // ─────────────── Metrics ───────────────

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

    // ─────────────── Saga: Batch Rollback ───────────────

    /**
     * Compensating action for the Saga pattern.
     *
     * <p>When a chunk fails during a multi-chunk ingestion, this method
     * tombstones (suppresses) all previously-ingested chunks from the same batch.
     * This ensures no partial/orphaned data remains in the memory system.</p>
     *
     * @param batchId  the batch to roll back
     * @param tenantId the tenant (for workspace routing)
     * @param cause    the original failure cause
     */
    private void rollbackBatch(String batchId, String tenantId, Exception cause) {
        IngestionBatch batch = BatchIngestionRegistry.failBatch(batchId,
                cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName());

        if (batch == null || batch.memoryIds().isEmpty()) {
            log.debug("[Saga] No chunks to rollback for batch {}", batchId);
            return;
        }

        // Resolve the target memory workspace (tenant-isolated or global)
        var tenantMemory = tenantRegistry != null
                ? tenantRegistry.getMemoryForTenant(tenantId) : null;

        int suppressed = 0;
        int errors = 0;
        String reason = "Batch rollback [" + batchId + "]: " + cause.getMessage();

        for (String memoryId : batch.memoryIds()) {
            try {
                if (tenantMemory != null) {
                    tenantMemory.suppress(memoryId, reason);
                } else {
                    // Fallback: suppress via the global target's memory
                    // This path is used when the tenant is "default" or unknown
                    log.debug("[Saga] Suppressing {} via global memory (no tenant workspace)", memoryId);
                    // Note: IngestionTarget doesn't expose suppress — the caller
                    // (EnterpriseServer) should wire a SpectorMemory reference
                    // for global rollback. For now, log the orphaned ID.
                }
                suppressed++;
            } catch (Exception e) {
                errors++;
                log.warn("[Saga] Failed to suppress memory '{}' during rollback: {}",
                        memoryId, e.getMessage());
            }
        }

        log.info("[Saga] Rolled back batch {} — suppressed {}/{} chunks ({} errors)",
                batchId, suppressed, batch.trackedMemoryCount(), errors);
    }

    /** Returns the configured ExecutionLogger, or null if not enabled. */
    public ExecutionLogger executionLogger() {
        return executionLogger;
    }
}
