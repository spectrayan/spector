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
package com.spectrayan.spector.memory.pathway.pipeline;

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

    public record MutationPayload(
            List<ExtractedEntity> entities,
            int memoryIdx,
            String memoryId,
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

    private final SpectorTaskQueue<EntityPayload> extractQueue;
    private final SpectorTaskQueue<MutationPayload> mutationQueue;
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
        this(null, entityExtractor, postIngestSync, TaskQueueConfig.of(queueCapacity, parallelism));
    }

    public AsyncEntityExtractionQueue(
            String namespaceId,
            EntityExtractor entityExtractor,
            PostIngestSync postIngestSync,
            int parallelism,
            int queueCapacity) {
        this(namespaceId, entityExtractor, postIngestSync, TaskQueueConfig.of(queueCapacity, parallelism));
    }

    public AsyncEntityExtractionQueue(
            EntityExtractor entityExtractor,
            PostIngestSync postIngestSync,
            TaskQueueConfig config) {
        this(null, entityExtractor, postIngestSync, config);
    }

    public AsyncEntityExtractionQueue(
            String namespaceId,
            EntityExtractor entityExtractor,
            PostIngestSync postIngestSync,
            TaskQueueConfig config) {
        this.entityExtractor = entityExtractor;
        this.postIngestSync = Objects.requireNonNull(postIngestSync, "postIngestSync");

        TaskQueueConfig extractConfig = config != null ? config : TaskQueueConfig.ofDefaults();
        TaskQueueConfig resolvedExtractConfig = new TaskQueueConfig(
                extractConfig.capacity(),
                extractConfig.parallelism(),
                extractConfig.pollTimeoutMs(),
                extractConfig.drainTimeoutMs(),
                extractConfig.maxRetries(),
                extractConfig.retryBackoffMs(),
                extractConfig.backpressurePolicy(),
                com.spectrayan.spector.commons.concurrent.ThreadPlane.VIRTUAL,
                extractConfig.batchDrainSize()
        );

        String extractQueueName = (namespaceId != null && !namespaceId.isBlank())
                ? "entity-extraction-" + namespaceId
                : "entity-extraction";
        String extractPoolName = (namespaceId != null && !namespaceId.isBlank())
                ? "entity-extract-" + namespaceId
                : "entity-extract";
        String mutationQueueName = (namespaceId != null && !namespaceId.isBlank())
                ? "entity-graph-mutation-" + namespaceId
                : "entity-graph-mutation";
        String mutationPoolName = (namespaceId != null && !namespaceId.isBlank())
                ? "graph-writer-" + namespaceId
                : "graph-writer";

        this.extractQueue = new SpectorTaskQueue<>(
                extractQueueName,
                resolvedExtractConfig,
                this::processExtractTask,
                null,
                com.spectrayan.spector.commons.concurrent.SpectorExecutors.executor(com.spectrayan.spector.commons.concurrent.ThreadPlane.VIRTUAL, extractPoolName)
        );

        TaskQueueConfig mutationConfig = new TaskQueueConfig(
                extractConfig.capacity(),
                1,
                extractConfig.pollTimeoutMs(),
                extractConfig.drainTimeoutMs(),
                extractConfig.maxRetries(),
                extractConfig.retryBackoffMs(),
                com.spectrayan.spector.commons.concurrent.BackpressurePolicy.BLOCK,
                com.spectrayan.spector.commons.concurrent.ThreadPlane.PLATFORM_WRITER,
                1
        );
        this.mutationQueue = new SpectorTaskQueue<>(
                mutationQueueName,
                mutationConfig,
                this::processMutationTask,
                null,
                com.spectrayan.spector.commons.concurrent.SpectorExecutors.executor(com.spectrayan.spector.commons.concurrent.ThreadPlane.PLATFORM_WRITER, mutationPoolName)
        );

        log.info("[AsyncEntityExtractionQueue] Initialized dual-plane queues for namespace [{}]: extractQueue (VIRTUAL, par={}) and mutationQueue (PLATFORM_WRITER, par=1)",
                namespaceId, resolvedExtractConfig.parallelism());
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

        return extractQueue.submit(task);
    }

    /**
     * Backward-compatible overload submitting with scoped session ID.
     */
    public boolean submit(String memoryId, String text, int memoryIdx,
                          long timestampSeconds, String sessionId) {
        return submit(memoryId, text, memoryIdx, timestampSeconds, sessionId, MemoryScope.namespaceId());
    }

    private void processExtractTask(ScopedTask<EntityPayload> task) throws Exception {
        if (entityExtractor == null || !entityExtractor.isAvailable()) {
            return;
        }

        EntityPayload payload = task.payload();
        long start = System.currentTimeMillis();
        List<ExtractedEntity> entities = hook.observe(ENTITY_EXTRACTION, java.util.Map.of(TAG_MEMORY_ID, payload.memoryId()), () ->
            entityExtractor.extract(payload.memoryId(), payload.text())
        );
        if (entities != null && !entities.isEmpty()) {
            MutationPayload mutationPayload = new MutationPayload(entities, payload.memoryIdx(), payload.memoryId(), payload.timestampSeconds());
            ScopedTask<MutationPayload> mutationTask = new ScopedTask<>(
                    task.taskId() + "-sync",
                    mutationPayload,
                    task.sessionId(),
                    task.namespaceId(),
                    task.priority(),
                    System.currentTimeMillis(),
                    task.traceContext()
            );
            mutationQueue.submit(mutationTask);
        }

        long duration = System.currentTimeMillis() - start;
        log.debug("[AsyncEntityExtractionQueue] Extracted {} entities for '{}' in {} ms (extractQueueDepth={}, mutationQueueDepth={})",
                entities != null ? entities.size() : 0, payload.memoryId(), duration, extractQueue.size(), mutationQueue.size());
    }

    private void processMutationTask(ScopedTask<MutationPayload> task) throws Exception {
        MutationPayload payload = task.payload();
        hook.observe(GRAPH_SYNC, java.util.Map.of(TAG_MEMORY_ID, payload.memoryId()), () -> {
            postIngestSync.syncPreExtractedEntities(payload.entities(), payload.memoryIdx(), payload.memoryId());
            postIngestSync.syncTemporalFacts(payload.entities(), payload.memoryIdx(), payload.memoryId(), payload.timestampSeconds());
        });
        totalEntitiesExtracted.addAndGet(payload.entities().size());
    }

    /**
     * Returns an immutable snapshot of queue operational statistics.
     */
    public QueueStats stats() {
        var m = extractQueue.metrics();
        return new QueueStats(
                m.size() + mutationQueue.size(),
                m.capacity(),
                m.parallelism(),
                m.submitted(),
                mutationQueue.metrics().processed(),
                m.failed() + mutationQueue.metrics().failed(),
                totalEntitiesExtracted.get(),
                m.avgLatencyMs(),
                m.isRunning() && mutationQueue.metrics().isRunning()
        );
    }

    /**
     * Returns the underlying extraction task queue (on VIRTUAL plane).
     */
    public SpectorTaskQueue<EntityPayload> taskQueue() {
        return extractQueue;
    }

    /**
     * Returns the underlying mutation task queue (on PLATFORM_WRITER plane).
     */
    public SpectorTaskQueue<MutationPayload> mutationQueue() {
        return mutationQueue;
    }

    @Override
    public void close() {
        try {
            extractQueue.close();
        } finally {
            mutationQueue.close();
        }
    }
}
