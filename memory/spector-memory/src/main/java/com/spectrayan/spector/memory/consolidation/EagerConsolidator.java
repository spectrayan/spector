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
package com.spectrayan.spector.memory.consolidation;

import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.core.similarity.SimilarityFunction;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.CognitiveRecordMemory;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.pipeline.CognitiveIngestionTarget;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.temporal.TemporalKnowledgeGraph;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Asynchronous eager consolidation coordinator (#526).
 *
 * <p>Processes newly ingested semantic and procedural memories on a dedicated
 * single-thread virtual executor with a bounded task queue. Performs a gated
 * contradiction scan (tombstone &rarr; bloom filter &rarr; vector distance &rarr; LLM) and applies
 * CADP directional resolution (#507) immediately after ingestion, closing the
 * confusion window before the next batch consolidation cycle.</p>
 */
public final class EagerConsolidator extends AbstractConsolidator implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EagerConsolidator.class);

    public record EagerConsolidationTask(String memoryId, MemoryType type) {}

    private final BlockingQueue<EagerConsolidationTask> taskQueue;
    private final ExecutorService executor;
    private final CognitiveMemoryRouter cognitiveRouter;
    private final MemoryIndex index;
    private final ScalarQuantizer quantizer;
    private final EntityDirectory entityDirectory;
    private final HyperEntityGraphMemory hyperEntityGraph;
    private final TemporalKnowledgeGraph temporalKnowledgeGraph;
    private final Function<String, CognitiveRecord> inspectFunction;
    private final float distanceThreshold;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public EagerConsolidator(CognitiveMemoryRouter cognitiveRouter,
                             MemoryIndex index,
                             ScalarQuantizer quantizer,
                             EntityDirectory entityDirectory,
                             HyperEntityGraphMemory hyperEntityGraph,
                             TemporalKnowledgeGraph temporalKnowledgeGraph,
                             LlmProvider textGenerator,
                             EmbeddingProvider embeddingProvider,
                             Function<String, CognitiveRecord> inspectFunction,
                             float distanceThreshold,
                             int queueCapacity) {
        super(textGenerator, embeddingProvider);
        this.cognitiveRouter = Objects.requireNonNull(cognitiveRouter, "cognitiveRouter");
        this.index = Objects.requireNonNull(index, "index");
        this.quantizer = Objects.requireNonNull(quantizer, "quantizer");
        this.entityDirectory = entityDirectory;
        this.hyperEntityGraph = hyperEntityGraph;
        this.temporalKnowledgeGraph = temporalKnowledgeGraph;
        this.inspectFunction = Objects.requireNonNull(inspectFunction, "inspectFunction");
        this.distanceThreshold = distanceThreshold;
        this.taskQueue = new LinkedBlockingQueue<>(Math.max(16, queueCapacity));

        this.executor = Executors.newSingleThreadExecutor(
                Thread.ofVirtual().name("spector-eager-consolidator-", 0).factory());
        this.executor.submit(this::processLoop);
    }

    /**
     * Submits a newly ingested memory for eager contradiction evaluation.
     *
     * @param memoryId ID of the new memory
     * @param type     memory type (must be SEMANTIC or PROCEDURAL)
     * @return true if accepted, false if the queue is full or closed
     */
    public boolean submit(String memoryId, MemoryType type) {
        if (closed.get() || memoryId == null || type == null) {
            return false;
        }
        if (type != MemoryType.SEMANTIC && type != MemoryType.PROCEDURAL) {
            return false;
        }

        boolean accepted = taskQueue.offer(new EagerConsolidationTask(memoryId, type));
        if (!accepted) {
            log.debug("Eager consolidation queue full ({}) — task for '{}' skipped; batch consolidation will catch it",
                    taskQueue.size(), memoryId);
        }
        return accepted;
    }

    private void processLoop() {
        while (!closed.get() && !Thread.currentThread().isInterrupted()) {
            try {
                EagerConsolidationTask task = taskQueue.poll(500, TimeUnit.MILLISECONDS);
                if (task != null) {
                    processTask(task);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("Eager consolidation task error: {}", e.getMessage(), e);
            }
        }
    }

    private void processTask(EagerConsolidationTask task) {
        CognitiveRecordMemory store;
        try {
            store = cognitiveRouter.get(task.type());
        } catch (RuntimeException e) {
            return;
        }
        if (store == null || store.visibleCount() < 2) {
            return;
        }

        CognitiveRecord recordA = inspectFunction.apply(task.memoryId());
        if (recordA == null || recordA.isTombstoned() || recordA.isContradicted()) {
            return;
        }

        MemorySegment segment = store.segment();
        CognitiveRecordLayout layout = store.cognitiveLayout();
        long baseOffset = store.isPersistent() ? CognitiveRecordMemory.METADATA_HEADER_BYTES : 0L;
        int stride = layout.stride();
        int vecBytes = layout.quantizedVecBytes();
        float[] mins = quantizer.mins();
        float[] scales = quantizer.scales();

        float[] decodedVectorA = new float[quantizer.dimensions()];
        byte[] quantizedBufA = new byte[vecBytes];
        long vecOffsetA = layout.vectorOffset(recordA.byteOffset());
        MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, vecOffsetA,
                MemorySegment.ofArray(quantizedBufA), ValueLayout.JAVA_BYTE, 0, vecBytes);
        quantizer.decode(quantizedBufA, 0, decodedVectorA, 0);

        int recordCount = store.visibleCount();

        for (int j = 0; j < recordCount; j++) {
            long offsetJ = baseOffset + (long) j * stride;
            if (offsetJ == recordA.byteOffset()) {
                continue; // don't compare against itself
            }

            // Phase 1: Gated checks (tombstone, contradicted)
            byte flagsJ = segment.get(SynapticHeaderConstants.LAYOUT_FLAGS, offsetJ + SynapticHeaderConstants.OFFSET_FLAGS);
            if (SynapticHeaderConstants.isTombstoned(flagsJ) || SynapticHeaderConstants.isContradicted(flagsJ)) {
                continue;
            }

            String idB = index.findIdByOffset(index.activePartitionSeq(), store.type(), offsetJ);
            if (idB == null || idB.equals(recordA.id())) {
                continue;
            }

            // Phase 2: Vector distance check
            float dist = SimilarityFunction.EUCLIDEAN.computeQuantizedFromSegment(
                    decodedVectorA, segment, layout.vectorOffset(offsetJ),
                    mins, scales, vecBytes);

            if (dist <= distanceThreshold) {
                CognitiveRecord recordB = inspectFunction.apply(idB);
                if (recordB == null || recordB.isTombstoned() || recordB.isContradicted()) {
                    continue;
                }

                log.info("EagerConsolidator: Detected candidate pair ['{}', '{}'] with L2={}",
                        recordA.id(), recordB.id(), dist);

                // Phase 3 & 4: Template evaluation & CADP resolution
                boolean processed = evaluateAndResolvePair(
                        recordA,
                        recordB,
                        store,
                        quantizer,
                        entityDirectory,
                        hyperEntityGraph,
                        temporalKnowledgeGraph,
                        null,
                        null,
                        null,
                        false // no merge in eager mode, CADP contradiction resolution only
                );

                if (processed) {
                    // Once resolved, stop comparing recordA against other records in this pass
                    break;
                }
            }
        }
    }

    @Override
    public void consolidate(
            CognitiveMemoryRouter cognitiveRouter,
            MemoryIndex index,
            ScalarQuantizer quantizer,
            EntityDirectory entityDirectory,
            HyperEntityGraphMemory hyperEntityGraph,
            TemporalKnowledgeGraph temporalKnowledgeGraph,
            CognitiveIngestionTarget ingestionTarget,
            MemoryWal wal,
            Function<String, CognitiveRecord> inspectFunction) {
        // Drains and synchronously processes all pending eager tasks
        EagerConsolidationTask task;
        while ((task = taskQueue.poll()) != null) {
            processTask(task);
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
