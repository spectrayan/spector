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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks the lifecycle of a multi-chunk document ingestion batch.
 *
 * <h3>Saga Pattern</h3>
 * <p>When a Camel ingestion route processes a multi-page document, each page/chunk
 * is ingested individually through {@link SpectorIngestionSink}. This record tracks
 * all memory IDs created during the batch so that if any chunk fails, the entire
 * batch can be rolled back by tombstoning (suppressing) all previously-ingested chunks.</p>
 *
 * <h3>Visibility Model</h3>
 * <p>Chunks are visible to readers immediately upon ingestion (eventual consistency).
 * During the brief window between "batch started" and "batch failed + rollback",
 * readers may see partial data. This is acceptable for cognitive memory — the
 * CognitiveScorer will naturally rank fresher, more complete data higher.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>The {@code memoryIds} list is synchronized. Atomic counters are used for
 * chunk tracking. Safe for concurrent access from multiple Camel threads
 * processing chunks of the same document in parallel.</p>
 *
 * @see BatchIngestionRegistry
 * @see SpectorIngestionSink
 */
public final class IngestionBatch {

    /**
     * Batch lifecycle states.
     */
    public enum Status {
        /** Batch is actively receiving chunks. */
        ACTIVE,
        /** All chunks ingested successfully. */
        COMPLETED,
        /** Batch failed — rollback in progress or completed. */
        FAILED,
        /** Batch was manually rolled back via API. */
        ROLLED_BACK
    }

    private final String batchId;
    private final String sourceDocId;
    private final String tenantId;
    private final String pipelineId;
    private final String userId;
    private final List<String> memoryIds;
    private final Instant startedAt;
    private final AtomicInteger completedChunks;
    private final AtomicInteger failedChunks;
    private volatile Status status;
    private volatile Instant completedAt;
    private volatile String failureReason;

    /**
     * Creates a new ingestion batch.
     *
     * @param sourceDocId the source document identifier (e.g., "annual_report.pdf")
     * @param tenantId    the tenant owning this batch
     * @param pipelineId  the pipeline that initiated ingestion (nullable)
     */
    public IngestionBatch(String sourceDocId, String tenantId, String pipelineId) {
        this(sourceDocId, tenantId, pipelineId, null);
    }

    /**
     * Creates a new ingestion batch with user-level ownership.
     *
     * @param sourceDocId the source document identifier
     * @param tenantId    the tenant owning this batch
     * @param pipelineId  the pipeline that initiated ingestion (nullable)
     * @param userId      the user who initiated this batch (nullable)
     */
    public IngestionBatch(String sourceDocId, String tenantId, String pipelineId, String userId) {
        this.batchId = UUID.randomUUID().toString();
        this.sourceDocId = sourceDocId;
        this.tenantId = tenantId;
        this.pipelineId = pipelineId;
        this.userId = userId;
        this.memoryIds = Collections.synchronizedList(new ArrayList<>());
        this.startedAt = Instant.now();
        this.completedChunks = new AtomicInteger();
        this.failedChunks = new AtomicInteger();
        this.status = Status.ACTIVE;
    }

    /** Records a successfully ingested chunk's memory ID. */
    public void recordChunk(String memoryId) {
        memoryIds.add(memoryId);
        completedChunks.incrementAndGet();
    }

    /** Records a failed chunk (does not add to memoryIds). */
    public void recordFailure() {
        failedChunks.incrementAndGet();
    }

    /** Marks the batch as successfully completed. */
    public void markCompleted() {
        this.status = Status.COMPLETED;
        this.completedAt = Instant.now();
    }

    /** Marks the batch as failed with a reason. */
    public void markFailed(String reason) {
        this.status = Status.FAILED;
        this.failureReason = reason;
        this.completedAt = Instant.now();
    }

    /** Marks the batch as manually rolled back. */
    public void markRolledBack() {
        this.status = Status.ROLLED_BACK;
        this.completedAt = Instant.now();
    }

    // ── Accessors ──

    public String batchId() { return batchId; }
    public String sourceDocId() { return sourceDocId; }
    public String tenantId() { return tenantId; }
    public String pipelineId() { return pipelineId; }
    /** Returns the user who initiated this batch (nullable in OSS mode). */
    public String userId() { return userId; }
    public Instant startedAt() { return startedAt; }
    public Instant completedAt() { return completedAt; }
    public Status status() { return status; }
    public String failureReason() { return failureReason; }
    public int completedChunkCount() { return completedChunks.get(); }
    public int failedChunkCount() { return failedChunks.get(); }

    /**
     * Returns a snapshot of all memory IDs ingested in this batch.
     * <p>Returns a copy to prevent concurrent modification during rollback iteration.</p>
     */
    public List<String> memoryIds() {
        synchronized (memoryIds) {
            return List.copyOf(memoryIds);
        }
    }

    /** Returns the number of memories tracked (may differ from completedChunks if counting races). */
    public int trackedMemoryCount() {
        return memoryIds.size();
    }

    @Override
    public String toString() {
        return String.format("IngestionBatch[id=%s, doc=%s, tenant=%s, status=%s, chunks=%d/%d]",
                batchId, sourceDocId, tenantId, status,
                completedChunks.get(), completedChunks.get() + failedChunks.get());
    }
}
