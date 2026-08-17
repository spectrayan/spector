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
import com.spectrayan.spector.commons.concurrent.ScopedTask;
import com.spectrayan.spector.commons.concurrent.SpectorTaskQueue;
import com.spectrayan.spector.commons.concurrent.TaskPriority;
import com.spectrayan.spector.commons.concurrent.TaskQueueConfig;
import com.spectrayan.spector.memory.graph.EntityExtractor;
import com.spectrayan.spector.commons.observation.MemoryObservationHook;
import static com.spectrayan.spector.commons.observation.MemoryObservationHook.*;
import com.spectrayan.spector.memory.graph.ExtractedEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Supervised asynchronous entity and relationship extraction queue wrapping {@link SpectorTaskQueue}.
 *
 * <h3>Architecture</h3>
 * <p>Ingesting a memory must never block the client on slow LLM text-generation calls (~15s–25s).
 * {@code AsyncEntityExtractionQueue} decouples entity extraction from the ingestion critical path,
 * buffering tasks in a generic {@link SpectorTaskQueue} and processing them via centralized virtual
 * threads with configurable parallelism, automatic transient retries, and scoped context propagation.</p>
 *
 * <h3>Scoped Context Propagation</h3>
 * <p>Captures {@link MemoryScope#SESSION_ID} and {@link MemoryScope#NAMESPACE_ID} at submission
 * time and restores them in the executing virtual thread worker, ensuring session identity
 * and namespace isolation contexts are preserved end-to-end.</p>
 */
public final class AsyncEntityExtractionQueue implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AsyncEntityExtractionQueue.class);

    public record EntityPayload(
            String memoryId,
            String text,
            int memoryIdx,
            long timestampSeconds
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

    private final SpectorTaskQueue<EntityPayload> taskQueue;
    private final EntityExtractor entityExtractor;
    private final PostIngestSync postIngestSync;
    private final AtomicLong totalEntitiesExtracted = new AtomicLong(0);
    private volatile MemoryObservationHook hook = MemoryObservationHook.NOOP;

    public void setObservationHook(MemoryObservationHook hook) {
        this.hook = hook != null ? hook : MemoryObservationHook.NOOP;
    }

    public AsyncEntityExtractionQueue(
            EntityExtractor entityExtractor,
            PostIngestSync postIngestSync,
            int parallelism,
            int queueCapacity) {
        this(entityExtractor, postIngestSync, TaskQueueConfig.of(queueCapacity, parallelism));
    }

    public AsyncEntityExtractionQueue(
            EntityExtractor entityExtractor,
            PostIngestSync postIngestSync,
            TaskQueueConfig config) {
        this.entityExtractor = entityExtractor;
        this.postIngestSync = Objects.requireNonNull(postIngestSync, "postIngestSync");
        this.taskQueue = new SpectorTaskQueue<>(
                "entity-extraction",
                config != null ? config : TaskQueueConfig.ofDefaults(),
                this::processTask
        );

        log.info("[AsyncEntityExtractionQueue] Initialized SpectorTaskQueue: parallelism={}, capacity={}, retries={}",
                this.taskQueue.metrics().parallelism(),
                this.taskQueue.metrics().capacity(),
                config != null ? config.maxRetries() : TaskQueueConfig.DEFAULT_MAX_RETRIES);
    }

    /**
     * Submits a memory for asynchronous entity extraction with session and namespace contexts.
     *
     * @param memoryId         memory ID
     * @param text             raw text content
     * @param memoryIdx        graph slot / memory index
     * @param timestampSeconds epoch timestamp in seconds
     * @param sessionId        scoped session ID (nullable)
     * @param namespaceId      scoped namespace ID (nullable)
     * @return true if accepted, false if dropped or closed
     */
    public boolean submit(String memoryId, String text, int memoryIdx,
                          long timestampSeconds, String sessionId, String namespaceId) {
        if (memoryId == null || text == null) {
            return false;
        }
        if (entityExtractor == null || !entityExtractor.isAvailable()) {
            return false;
        }

        String effectiveSessionId = (sessionId != null && !sessionId.isBlank())
                ? sessionId
                : MemoryScope.sessionId();
        String effectiveNamespaceId = (namespaceId != null && !namespaceId.isBlank())
                ? namespaceId
                : MemoryScope.namespaceId();

        EntityPayload payload = new EntityPayload(memoryId, text, memoryIdx, timestampSeconds);
        ScopedTask<EntityPayload> task = ScopedTask.of(
                memoryId, payload, effectiveSessionId, effectiveNamespaceId, TaskPriority.NORMAL);

        return taskQueue.submit(task);
    }

    /**
     * Backward-compatible overload submitting with scoped session ID.
     */
    public boolean submit(String memoryId, String text, int memoryIdx,
                          long timestampSeconds, String sessionId) {
        return submit(memoryId, text, memoryIdx, timestampSeconds, sessionId, MemoryScope.namespaceId());
    }

    private void processTask(ScopedTask<EntityPayload> task) throws Exception {
        if (entityExtractor == null || !entityExtractor.isAvailable()) {
            return;
        }

        EntityPayload payload = task.payload();
        long start = System.currentTimeMillis();
        List<ExtractedEntity> entities = hook.observe(ENTITY_EXTRACTION, java.util.Map.of(TAG_MEMORY_ID, payload.memoryId()), () ->
            entityExtractor.extract(payload.memoryId(), payload.text())
        );
        if (entities != null && !entities.isEmpty()) {
            hook.observe(GRAPH_SYNC, java.util.Map.of(TAG_MEMORY_ID, payload.memoryId()), () -> {
                postIngestSync.syncPreExtractedEntities(entities, payload.memoryIdx(), payload.memoryId());
                postIngestSync.syncTemporalFacts(entities, payload.memoryIdx(), payload.memoryId(), payload.timestampSeconds());
            });
            totalEntitiesExtracted.addAndGet(entities.size());
        }

        long duration = System.currentTimeMillis() - start;
        log.debug("[AsyncEntityExtractionQueue] Extracted {} entities for '{}' in {} ms (queueDepth={})",
                entities != null ? entities.size() : 0, payload.memoryId(), duration, taskQueue.size());
    }

    /**
     * Returns an immutable snapshot of queue operational statistics.
     */
    public QueueStats stats() {
        var m = taskQueue.metrics();
        return new QueueStats(
                m.size(),
                m.capacity(),
                m.parallelism(),
                m.submitted(),
                m.processed(),
                m.failed(),
                totalEntitiesExtracted.get(),
                m.avgLatencyMs(),
                m.isRunning()
        );
    }

    /**
     * Returns the underlying generic task queue.
     */
    public SpectorTaskQueue<EntityPayload> taskQueue() {
        return taskQueue;
    }

    @Override
    public void close() {
        taskQueue.close();
    }
}
