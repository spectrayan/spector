/*
 * Copyright 2026 Spectrayan
 */
package com.spectrayan.spector.connector.sink;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link BatchIngestionRegistry} — Saga batch lifecycle.
 */
class BatchIngestionRegistryTest {

    @BeforeEach
    void clearState() {
        // Clear all batches between tests
        BatchIngestionRegistry.activeBatches().forEach(b ->
                BatchIngestionRegistry.completeBatch(b.batchId()));
    }

    @Test
    void startBatchCreatesActiveBatch() {
        IngestionBatch batch = BatchIngestionRegistry.startBatch("doc-1", "tenant-a", "pipeline-1");

        assertThat(batch).isNotNull();
        assertThat(batch.batchId()).isNotBlank();
        assertThat(batch.sourceDocId()).isEqualTo("doc-1");
        assertThat(batch.tenantId()).isEqualTo("tenant-a");
        assertThat(batch.pipelineId()).isEqualTo("pipeline-1");
        assertThat(batch.status()).isEqualTo(IngestionBatch.Status.ACTIVE);
        assertThat(batch.startedAt()).isNotNull();
    }

    @Test
    void recordChunkIncrementsCount() {
        IngestionBatch batch = BatchIngestionRegistry.startBatch("doc-1", "tenant-a", "p-1");
        String batchId = batch.batchId();

        BatchIngestionRegistry.recordChunk(batchId, "memory-1");
        BatchIngestionRegistry.recordChunk(batchId, "memory-2");

        IngestionBatch updated = BatchIngestionRegistry.get(batchId);
        assertThat(updated.trackedMemoryCount()).isEqualTo(2);
        assertThat(updated.completedChunkCount()).isEqualTo(2);
    }

    @Test
    void completeBatchSetsStatus() {
        IngestionBatch batch = BatchIngestionRegistry.startBatch("doc-1", "t", "p");
        String batchId = batch.batchId();

        BatchIngestionRegistry.recordChunk(batchId, "m-1");
        BatchIngestionRegistry.completeBatch(batchId);

        IngestionBatch completed = BatchIngestionRegistry.get(batchId);
        assertThat(completed.status()).isEqualTo(IngestionBatch.Status.COMPLETED);
        assertThat(completed.completedAt()).isNotNull();
    }

    @Test
    void failBatchRecordsReason() {
        IngestionBatch batch = BatchIngestionRegistry.startBatch("doc-1", "t", "p");
        String batchId = batch.batchId();

        BatchIngestionRegistry.failBatch(batchId, "Embedding model unavailable");

        IngestionBatch failed = BatchIngestionRegistry.get(batchId);
        assertThat(failed.status()).isEqualTo(IngestionBatch.Status.FAILED);
        assertThat(failed.failureReason()).isEqualTo("Embedding model unavailable");
    }

    @Test
    void getReturnsNullForUnknownId() {
        assertThat(BatchIngestionRegistry.get("nonexistent-id")).isNull();
    }

    @Test
    void recordChunkOnNullBatchIdIsNoOp() {
        // Should not throw
        assertThatCode(() -> BatchIngestionRegistry.recordChunk(null, "memory-1"))
                .doesNotThrowAnyException();
    }

    @Test
    void activeCountReflectsCurrentState() {
        int before = BatchIngestionRegistry.activeCount();
        IngestionBatch batch = BatchIngestionRegistry.startBatch("doc", "t", "p");
        assertThat(BatchIngestionRegistry.activeCount()).isEqualTo(before + 1);

        BatchIngestionRegistry.completeBatch(batch.batchId());
        assertThat(BatchIngestionRegistry.activeCount()).isEqualTo(before);
    }

    @Test
    void recentHistoryIncludesCompletedBatches() {
        IngestionBatch batch = BatchIngestionRegistry.startBatch("doc", "t", "p");
        BatchIngestionRegistry.completeBatch(batch.batchId());

        var history = BatchIngestionRegistry.recentHistory(10);
        assertThat(history).anyMatch(b -> b.batchId().equals(batch.batchId()));
    }

    @Test
    void trackedMemoryIdsReturnsAllRecordedIds() {
        IngestionBatch batch = BatchIngestionRegistry.startBatch("doc", "t", "p");
        String batchId = batch.batchId();

        BatchIngestionRegistry.recordChunk(batchId, "mem-a");
        BatchIngestionRegistry.recordChunk(batchId, "mem-b");
        BatchIngestionRegistry.recordChunk(batchId, "mem-c");

        IngestionBatch updated = BatchIngestionRegistry.get(batchId);
        assertThat(updated.memoryIds()).containsExactly("mem-a", "mem-b", "mem-c");
    }
}
