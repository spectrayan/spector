/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.spectrayan.spector.connector.sink;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of active and recently-completed ingestion batches.
 *
 * <h3>Saga Pattern — Batch Lifecycle</h3>
 * <pre>
 *   startBatch()  →  recordChunk() × N  →  completeBatch()   (happy path)
 *                                        →  failBatch()       (error → rollback)
 *                                        →  rollbackBatch()   (manual via API)
 * </pre>
 *
 * <h3>History Retention</h3>
 * <p>Completed/failed batches are retained for audit and DLQ redrive purposes.
 * The registry is bounded — oldest completed batches are evicted when the
 * history exceeds {@value #MAX_HISTORY} entries. Active batches are never evicted.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>All maps are {@link ConcurrentHashMap}. Safe for concurrent access from
 * multiple Camel routes and REST API threads.</p>
 *
 * @see IngestionBatch
 * @see SpectorIngestionSink
 */
public final class BatchIngestionRegistry {

    private static final Logger log = LoggerFactory.getLogger(BatchIngestionRegistry.class);

    /** Maximum number of completed/failed batches to retain in history. */
    private static final int MAX_HISTORY = 1000;

    /** Active batches: batchId → IngestionBatch. */
    private static final ConcurrentHashMap<String, IngestionBatch> ACTIVE = new ConcurrentHashMap<>();

    /** Completed/failed batch history: batchId → IngestionBatch. */
    private static final ConcurrentHashMap<String, IngestionBatch> HISTORY = new ConcurrentHashMap<>();

    private BatchIngestionRegistry() {} // static utility

    /**
     * Starts a new ingestion batch for a document.
     *
     * @param sourceDocId the source document identifier
     * @param tenantId    the tenant owning this batch
     * @param pipelineId  the pipeline that initiated ingestion (nullable)
     * @return the new batch with a generated UUID
     */
    public static IngestionBatch startBatch(String sourceDocId, String tenantId, String pipelineId) {
        return startBatch(sourceDocId, tenantId, pipelineId, null);
    }

    /**
     * Starts a new ingestion batch with user-level ownership.
     *
     * @param sourceDocId the source document identifier
     * @param tenantId    the tenant owning this batch
     * @param pipelineId  the pipeline that initiated ingestion (nullable)
     * @param userId      the user who initiated this batch (nullable)
     * @return the new batch with a generated UUID
     */
    public static IngestionBatch startBatch(String sourceDocId, String tenantId,
                                             String pipelineId, String userId) {
        IngestionBatch batch = new IngestionBatch(sourceDocId, tenantId, pipelineId, userId);
        ACTIVE.put(batch.batchId(), batch);
        log.info("[Batch] Started batch {} for doc '{}' (tenant={}, user={})",
                batch.batchId(), sourceDocId, tenantId, userId);
        return batch;
    }

    /**
     * Records a successfully ingested chunk in an active batch.
     *
     * @param batchId  the batch ID
     * @param memoryId the memory ID of the ingested chunk
     */
    public static void recordChunk(String batchId, String memoryId) {
        if (batchId == null) return;
        IngestionBatch batch = ACTIVE.get(batchId);
        if (batch != null) {
            batch.recordChunk(memoryId);
        }
    }

    /**
     * Marks a batch as successfully completed and moves it to history.
     *
     * @param batchId the batch ID
     * @return the completed batch, or null if not found
     */
    public static IngestionBatch completeBatch(String batchId) {
        if (batchId == null) return null;
        IngestionBatch batch = ACTIVE.remove(batchId);
        if (batch != null) {
            batch.markCompleted();
            moveToHistory(batch);
            log.info("[Batch] Completed batch {} — {} chunks ingested in {}ms",
                    batchId, batch.completedChunkCount(),
                    Duration.between(batch.startedAt(), Instant.now()).toMillis());
        }
        return batch;
    }

    /**
     * Marks a batch as failed and moves it to history.
     * <p>The caller is responsible for performing the actual rollback
     * (tombstoning ingested chunks) after calling this method.</p>
     *
     * @param batchId the batch ID
     * @param reason  the failure reason
     * @return the failed batch (with memoryIds for rollback), or null if not found
     */
    public static IngestionBatch failBatch(String batchId, String reason) {
        if (batchId == null) return null;
        IngestionBatch batch = ACTIVE.remove(batchId);
        if (batch == null) {
            // Check history — might have already been completed
            batch = HISTORY.get(batchId);
        }
        if (batch != null) {
            batch.markFailed(reason);
            moveToHistory(batch);
            log.warn("[Batch] Failed batch {} — {} chunks to rollback. Reason: {}",
                    batchId, batch.trackedMemoryCount(), reason);
        }
        return batch;
    }

    /**
     * Marks a batch as manually rolled back (via REST API).
     *
     * @param batchId the batch ID
     * @return the batch, or null if not found
     */
    public static IngestionBatch rollbackBatch(String batchId) {
        if (batchId == null) return null;
        // Check active first, then history
        IngestionBatch batch = ACTIVE.remove(batchId);
        if (batch == null) {
            batch = HISTORY.get(batchId);
        }
        if (batch != null) {
            batch.markRolledBack();
            moveToHistory(batch);
        }
        return batch;
    }

    /**
     * Retrieves a batch by ID (active or historical).
     *
     * @param batchId the batch ID
     * @return the batch, or null if not found
     */
    public static IngestionBatch get(String batchId) {
        if (batchId == null) return null;
        IngestionBatch batch = ACTIVE.get(batchId);
        return batch != null ? batch : HISTORY.get(batchId);
    }

    /**
     * Retrieves a batch by ID only if it belongs to the given tenant and user.
     *
     * <p>Prevents cross-tenant information disclosure and destructive actions:
     * User A cannot read or rollback User B's batch even if they know the ID.</p>
     *
     * @param batchId  the batch ID
     * @param tenantId the requesting user's tenant ID
     * @param userId   the requesting user's ID (nullable = skip user check)
     * @return the batch if it belongs to the tenant/user, or null
     */
    public static IngestionBatch getForUser(String batchId, String tenantId, String userId) {
        IngestionBatch batch = get(batchId);
        if (batch == null) return null;
        // Tenant-level gate: always enforced
        if (tenantId != null && batch.tenantId() != null
                && !tenantId.equals(batch.tenantId())) {
            log.warn("[Batch] Cross-tenant access denied: batch {} owned by tenant={}, requested by tenant={}",
                    batchId, batch.tenantId(), tenantId);
            return null;
        }
        // User-level gate: enforced when both sides have userId
        if (userId != null && batch.userId() != null
                && !userId.equals(batch.userId())) {
            log.warn("[Batch] Cross-user access denied: batch {} owned by user={}, requested by user={}",
                    batchId, batch.userId(), userId);
            return null;
        }
        return batch;
    }

    /**
     * Returns all active batches (currently being ingested).
     */
    public static List<IngestionBatch> activeBatches() {
        return List.copyOf(ACTIVE.values());
    }

    /**
     * Returns active batches scoped to a specific tenant and user.
     *
     * @param tenantId the tenant ID to filter by
     * @param userId   the user ID to filter by (nullable = skip user check)
     * @return batches belonging to the given tenant/user
     */
    public static List<IngestionBatch> activeBatchesForUser(String tenantId, String userId) {
        return ACTIVE.values().stream()
                .filter(b -> tenantId == null || tenantId.equals(b.tenantId()))
                .filter(b -> userId == null || b.userId() == null || userId.equals(b.userId()))
                .toList();
    }

    /**
     * Returns recent batch history (completed + failed), newest first.
     *
     * @param limit maximum number of entries to return
     */
    public static List<IngestionBatch> recentHistory(int limit) {
        List<IngestionBatch> sorted = new ArrayList<>(HISTORY.values());
        sorted.sort(Comparator.comparing(IngestionBatch::startedAt).reversed());
        return Collections.unmodifiableList(
                sorted.subList(0, Math.min(limit, sorted.size())));
    }

    /** Returns the number of currently active batches. */
    public static int activeCount() {
        return ACTIVE.size();
    }

    /** Returns the number of batches in history. */
    public static int historyCount() {
        return HISTORY.size();
    }

    // ── Internal ──

    private static void moveToHistory(IngestionBatch batch) {
        HISTORY.put(batch.batchId(), batch);
        evictOldHistory();
    }

    /**
     * Evicts oldest completed batches when history exceeds the limit.
     * Only evicts COMPLETED batches — FAILED and ROLLED_BACK are retained longer
     * for audit purposes.
     */
    private static void evictOldHistory() {
        if (HISTORY.size() <= MAX_HISTORY) return;

        // Find completed batches sorted by age (oldest first)
        List<Map.Entry<String, IngestionBatch>> completedEntries = HISTORY.entrySet().stream()
                .filter(e -> e.getValue().status() == IngestionBatch.Status.COMPLETED)
                .sorted(Comparator.comparing(e -> e.getValue().startedAt()))
                .toList();

        int toEvict = HISTORY.size() - MAX_HISTORY;
        for (int i = 0; i < Math.min(toEvict, completedEntries.size()); i++) {
            HISTORY.remove(completedEntries.get(i).getKey());
        }
    }

    /** Clears all registries. <b>FOR TESTING ONLY.</b> */
    public static void clearAll() {
        ACTIVE.clear();
        HISTORY.clear();
    }
}
