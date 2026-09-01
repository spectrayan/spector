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

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.commons.concurrent.MemoryScope;
import com.spectrayan.spector.commons.concurrent.ScopedTask;
import com.spectrayan.spector.commons.concurrent.SpectorTaskQueue;
import com.spectrayan.spector.commons.concurrent.TaskPriority;
import com.spectrayan.spector.commons.concurrent.TaskQueueConfig;
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
import com.spectrayan.spector.memory.pathway.RememberPathway;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.temporal.TemporalKnowledgeGraph;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;
import java.util.function.Function;

/**
 * Supervised asynchronous consolidator wrapping {@link SpectorTaskQueue} for immediate CADP
 * contradiction evaluation upon memory ingestion.
 */
public final class EagerConsolidator extends AbstractConsolidator implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EagerConsolidator.class);

    public record EagerConsolidationPayload(
            String memoryId,
            MemoryType type
    ) {}

    private final SpectorTaskQueue<EagerConsolidationPayload> taskQueue;
    private final CognitiveMemoryRouter cognitiveRouter;
    private final MemoryIndex index;
    private final ScalarQuantizer quantizer;
    private final EntityDirectory entityDirectory;
    private final HyperEntityGraphMemory hyperEntityGraph;
    private final TemporalKnowledgeGraph temporalKnowledgeGraph;
    private final Function<String, CognitiveRecord> inspectFunction;
    private final float distanceThreshold;

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
        this(cognitiveRouter, index, quantizer, entityDirectory, hyperEntityGraph,
                temporalKnowledgeGraph, textGenerator, embeddingProvider, inspectFunction,
                distanceThreshold, TaskQueueConfig.of(queueCapacity, 1));
    }

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
                             TaskQueueConfig config) {
        super(textGenerator, embeddingProvider);
        this.cognitiveRouter = Objects.requireNonNull(cognitiveRouter, "cognitiveRouter");
        this.index = Objects.requireNonNull(index, "index");
        this.quantizer = Objects.requireNonNull(quantizer, "quantizer");
        this.entityDirectory = entityDirectory;
        this.hyperEntityGraph = hyperEntityGraph;
        this.temporalKnowledgeGraph = temporalKnowledgeGraph;
        this.inspectFunction = Objects.requireNonNull(inspectFunction, "inspectFunction");
        this.distanceThreshold = distanceThreshold;
        this.taskQueue = new SpectorTaskQueue<>(
                "eager-consolidation",
                config != null ? config : TaskQueueConfig.ofDefaults(),
                this::processTask
        );
    }

    /**
     * Submits a newly ingested memory for eager contradiction evaluation.
     *
     * @param memoryId ID of the new memory
     * @param type     memory type (must be SEMANTIC or PROCEDURAL)
     * @return true if accepted, false if the queue is full or closed
     */
    public boolean submit(String memoryId, MemoryType type) {
        if (memoryId == null || type == null) {
            return false;
        }
        if (type != MemoryType.SEMANTIC && type != MemoryType.PROCEDURAL) {
            return false;
        }

        String sessionId = MemoryScope.sessionId();
        String namespaceId = MemoryScope.namespaceId();
        EagerConsolidationPayload payload = new EagerConsolidationPayload(memoryId, type);
        ScopedTask<EagerConsolidationPayload> task = ScopedTask.of(
                memoryId, payload, sessionId, namespaceId, TaskPriority.NORMAL);

        return taskQueue.submit(task);
    }

    private void processTask(ScopedTask<EagerConsolidationPayload> task) {
        EagerConsolidationPayload payload = task.payload();
        CognitiveRecordMemory store;
        try {
            store = cognitiveRouter.get(payload.type());
        } catch (RuntimeException e) {
            return;
        }
        if (store == null || store.visibleCount() < 2) {
            return;
        }

        CognitiveRecord recordA = inspectFunction.apply(payload.memoryId());
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
            RememberPathway rememberPathway,
            MemoryWal wal,
            Function<String, CognitiveRecord> inspectFunction) {
        // Queue is drained automatically by SpectorTaskQueue on close
    }

    public SpectorTaskQueue<EagerConsolidationPayload> taskQueue() {
        return taskQueue;
    }

    @Override
    public void close() {
        taskQueue.close();
    }
}
