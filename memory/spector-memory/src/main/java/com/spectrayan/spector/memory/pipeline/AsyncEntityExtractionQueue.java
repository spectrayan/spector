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
package com.spectrayan.spector.memory.pipeline;

import com.spectrayan.spector.commons.concurrent.MemoryScope;
import com.spectrayan.spector.memory.graph.EntityExtractor;
import com.spectrayan.spector.memory.graph.ExtractedEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Supervised asynchronous entity and relationship extraction queue.
 *
 * <h3>Architecture</h3>
 * <p>Ingesting a memory must never block the client on slow LLM text-generation calls (~15s–25s).
 * {@code AsyncEntityExtractionQueue} decouples entity extraction from the ingestion critical path,
 * buffering tasks in a bounded FIFO queue and processing them via a supervised pool of virtual
 * threads with configurable parallelism (defaulting to 1 for sequential execution to prevent
 * Ollama/LLM concurrency congestion).</p>
 *
 * <h3>Scoped Context Propagation</h3>
 * <p>Captures {@link MemoryScope#SESSION_ID} at submission time and re-binds it via {@link ScopedValue}
 * in the executing virtual thread, ensuring session identity and temporal link contexts are preserved.</p>
 */
public final class AsyncEntityExtractionQueue implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AsyncEntityExtractionQueue.class);

    public record EntityExtractionTask(
            String memoryId,
            String text,
            int memoryIdx,
            long timestampSeconds,
            String sessionId
    ) {}

    public record QueueStats(
            int queueSize,
            int queueCapacity,
            int parallelism,
            long totalSubmitted,
            long totalProcessed,
            long totalFailed,
            long totalEntitiesExtracted,
            long avgProcessingLatencyMs,
            boolean isRunning
    ) {}

    private final BlockingQueue<EntityExtractionTask> queue;
    private final int queueCapacity;
    private final int parallelism;
    private final EntityExtractor entityExtractor;
    private final PostIngestSync postIngestSync;
    private final ExecutorService executor;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final AtomicLong totalSubmitted = new AtomicLong(0);
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalFailed = new AtomicLong(0);
    private final AtomicLong totalEntitiesExtracted = new AtomicLong(0);
    private final AtomicLong totalProcessingDurationMs = new AtomicLong(0);

    public AsyncEntityExtractionQueue(
            EntityExtractor entityExtractor,
            PostIngestSync postIngestSync,
            int parallelism,
            int queueCapacity) {
        this.entityExtractor = entityExtractor;
        this.postIngestSync = Objects.requireNonNull(postIngestSync, "postIngestSync");
        this.parallelism = Math.max(1, parallelism);
        this.queueCapacity = Math.max(16, queueCapacity);
        this.queue = new LinkedBlockingQueue<>(this.queueCapacity);

        this.executor = Executors.newFixedThreadPool(
                this.parallelism,
                Thread.ofVirtual().name("spector-entity-extractor-", 0).factory()
        );

        for (int i = 0; i < this.parallelism; i++) {
            this.executor.submit(this::processLoop);
        }

        log.info("[AsyncEntityExtractionQueue] Initialized with parallelism={}, capacity={}",
                this.parallelism, this.queueCapacity);
    }

    /**
     * Submits a memory for asynchronous entity extraction.
     *
     * @param memoryId         memory ID
     * @param text             raw text content
     * @param memoryIdx        graph slot / memory index
     * @param timestampSeconds epoch timestamp in seconds
     * @param sessionId        scoped session ID (nullable)
     * @return true if accepted, false if dropped or closed
     */
    public boolean submit(String memoryId, String text, int memoryIdx,
                          long timestampSeconds, String sessionId) {
        if (closed.get() || memoryId == null || text == null) {
            return false;
        }
        if (entityExtractor == null || !entityExtractor.isAvailable()) {
            return false;
        }

        EntityExtractionTask task = new EntityExtractionTask(
                memoryId, text, memoryIdx, timestampSeconds, sessionId);
        boolean accepted = queue.offer(task);
        if (accepted) {
            totalSubmitted.incrementAndGet();
        } else {
            totalFailed.incrementAndGet();
            log.warn("[AsyncEntityExtractionQueue] Queue full ({}/{}) - dropped task for '{}'",
                    queue.size(), queueCapacity, memoryId);
        }
        return accepted;
    }

    private void processLoop() {
        while (!closed.get() && !Thread.currentThread().isInterrupted()) {
            try {
                EntityExtractionTask task = queue.poll(500, TimeUnit.MILLISECONDS);
                if (task != null) {
                    Runnable worker = () -> processTask(task);
                    if (task.sessionId() != null && !task.sessionId().isBlank()) {
                        ScopedValue.where(MemoryScope.SESSION_ID, task.sessionId()).run(worker);
                    } else {
                        worker.run();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("[AsyncEntityExtractionQueue] Worker loop error: {}", e.getMessage(), e);
            }
        }
    }

    private void processTask(EntityExtractionTask task) {
        if (entityExtractor == null || !entityExtractor.isAvailable()) {
            return;
        }

        long start = System.currentTimeMillis();
        try {
            List<ExtractedEntity> entities = entityExtractor.extract(task.memoryId(), task.text());
            if (entities != null && !entities.isEmpty()) {
                postIngestSync.syncPreExtractedEntities(entities, task.memoryIdx(), task.memoryId());
                postIngestSync.syncTemporalFacts(entities, task.memoryIdx(), task.memoryId(), task.timestampSeconds());
                totalEntitiesExtracted.addAndGet(entities.size());
            }

            long elapsed = System.currentTimeMillis() - start;
            totalProcessingDurationMs.addAndGet(elapsed);
            totalProcessed.incrementAndGet();

            log.debug("[AsyncEntityExtractionQueue] Extracted {} entities for '{}' in {} ms (queueDepth={})",
                    entities != null ? entities.size() : 0, task.memoryId(), elapsed, queue.size());
        } catch (Exception e) {
            totalFailed.incrementAndGet();
            log.warn("[AsyncEntityExtractionQueue] Extraction failed for '{}': {}",
                    task.memoryId(), e.getMessage(), e);
        }
    }

    /**
     * Returns current telemetry and stats for the extraction queue.
     */
    public QueueStats stats() {
        long processed = totalProcessed.get();
        long avgLatency = processed > 0 ? (totalProcessingDurationMs.get() / processed) : 0;
        return new QueueStats(
                queue.size(),
                queueCapacity,
                parallelism,
                totalSubmitted.get(),
                processed,
                totalFailed.get(),
                totalEntitiesExtracted.get(),
                avgLatency,
                !closed.get()
        );
    }

    public int queueSize() {
        return queue.size();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("[AsyncEntityExtractionQueue] Shutdown complete (remaining queue size: {})", queue.size());
        }
    }
}
