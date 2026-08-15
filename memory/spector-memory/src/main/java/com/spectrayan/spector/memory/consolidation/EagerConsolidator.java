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
import com.spectrayan.spector.memory.temporal.TemporalKnowledgeGraph;
import com.spectrayan.spector.provider.generation.LlmProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * contradiction scan (tombstone → bloom filter → vector distance → LLM) and applies
 * CADP directional resolution (#507) immediately after ingestion, closing the
 * confusion window before the next batch consolidation cycle.</p>
 */
public final class EagerConsolidator implements AutoCloseable {

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
    private final ContradictionDetector contradictionDetector;
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
                             Function<String, CognitiveRecord> inspectFunction,
                             float distanceThreshold,
                             int queueCapacity) {
        this.cognitiveRouter = Objects.requireNonNull(cognitiveRouter, "cognitiveRouter");
        this.index = Objects.requireNonNull(index, "index");
        this.quantizer = Objects.requireNonNull(quantizer, "quantizer");
        this.entityDirectory = entityDirectory;
        this.hyperEntityGraph = hyperEntityGraph;
        this.temporalKnowledgeGraph = temporalKnowledgeGraph;
        this.contradictionDetector = new ContradictionDetector(textGenerator);
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
                continue;
            }

            byte flagsJ = segment.get(SynapticHeaderConstants.LAYOUT_FLAGS, offsetJ + SynapticHeaderConstants.OFFSET_FLAGS);
            if (SynapticHeaderConstants.isTombstoned(flagsJ) || SynapticHeaderConstants.isContradicted(flagsJ)) {
                continue;
            }

            String idB = index.findIdByOffset(store.type(), offsetJ);
            if (idB == null || idB.equals(recordA.id())) {
                continue;
            }

            // Phase 3: Vector distance check
            float dist = SimilarityFunction.EUCLIDEAN.computeQuantizedFromSegment(
                    decodedVectorA, segment, layout.vectorOffset(offsetJ),
                    mins, scales, vecBytes);

            if (dist <= distanceThreshold) {
                CognitiveRecord recordB = inspectFunction.apply(idB);
                if (recordB == null || recordB.isTombstoned() || recordB.isContradicted()) {
                    continue;
                }

                // Phase 4: LLM contradiction check
                boolean isContradictory = contradictionDetector.areContradictory(recordA.text(), recordB.text());
                if (isContradictory) {
                    log.info("EagerConsolidator: Detected contradiction between '{}' and '{}' (L2={})",
                            recordA.id(), recordB.id(), dist);

                    // Phase 5: CADP Directional resolution (#507)
                    long offsetA = recordA.byteOffset();
                    long offsetB = recordB.byteOffset();

                    CognitiveRecord winner;
                    CognitiveRecord loser;
                    long offsetLoser;

                    int cmp = Long.compare(recordA.timestampMs(), recordB.timestampMs());
                    if (cmp == 0) {
                        cmp = Float.compare(recordA.storageStrength(), recordB.storageStrength());
                    }
                    if (cmp == 0) {
                        cmp = recordB.id().compareTo(recordA.id());
                    }

                    if (cmp >= 0) {
                        winner = recordA; loser = recordB;
                        offsetLoser = offsetB;
                    } else {
                        winner = recordB; loser = recordA;
                        offsetLoser = offsetA;
                    }

                    layout.markContradicted(segment, offsetLoser);
                    log.info("EagerConsolidator: CADP resolved — winner='{}' corrects loser='{}'",
                            winner.id(), loser.id());

                    // Hyperedge update
                    int slotWinner = (int) ((winner.byteOffset() - (store.isPersistent() ? CognitiveRecordMemory.METADATA_HEADER_BYTES : 0L)) / layout.stride());
                    int slotLoser = (int) ((loser.byteOffset() - (store.isPersistent() ? CognitiveRecordMemory.METADATA_HEADER_BYTES : 0L)) / layout.stride());

                    List<Integer> entitiesWinner = null;
                    List<Integer> entitiesLoser = null;

                    if (entityDirectory != null) {
                        entitiesWinner = findEntitiesForSlot(slotWinner);
                        entitiesLoser = findEntitiesForSlot(slotLoser);

                        if (hyperEntityGraph != null && entitiesWinner != null && entitiesLoser != null) {
                            for (int eW : entitiesWinner) {
                                for (int eL : entitiesLoser) {
                                    if (eW != eL) {
                                        hyperEntityGraph.addHyperedge(
                                                new int[]{eW, eL},
                                                new int[]{HyperEntityGraphMemory.ROLE_CORRECTOR, HyperEntityGraphMemory.ROLE_CORRECTED},
                                                HyperEntityGraphMemory.TYPE_CONTRADICTS,
                                                1.0f, -1, System.currentTimeMillis());
                                    }
                                }
                            }
                        }
                    }

                    // Bridge to TemporalKnowledgeGraph: Retract loser's facts (#527)
                    if (temporalKnowledgeGraph != null && entitiesLoser != null) {
                        for (int eL : entitiesLoser) {
                            try {
                                var facts = temporalKnowledgeGraph.factsAbout(eL).resolveAll();
                                if (facts != null) {
                                    for (var fact : facts) {
                                        if (entitiesWinner == null || !entitiesWinner.contains(fact.objectEntityId())) {
                                            temporalKnowledgeGraph.retractFact(fact.factId());
                                            log.info("EagerConsolidator: Retracted temporal fact {} for corrected entity {}",
                                                    fact.factId(), eL);
                                        }
                                    }
                                }
                            } catch (RuntimeException e) {
                                log.debug("EagerConsolidator: Failed to retract temporal fact for entity {}: {}",
                                        eL, e.getMessage());
                            }
                        }
                    }

                    // Once resolved, stop comparing recordA against other records in this pass
                    break;
                }
            }
        }
    }

    private List<Integer> findEntitiesForSlot(int slot) {
        if (entityDirectory == null) return null;
        List<Integer> result = new ArrayList<>(2);
        int ecnt = entityDirectory.entityCount();
        for (int e = 0; e < ecnt; e++) {
            int refCount = entityDirectory.memoryRefCount(e);
            for (int r = 0; r < refCount; r++) {
                if (entityDirectory.memoryRefAt(e, r) == slot) {
                    result.add(e);
                }
            }
        }
        return result.isEmpty() ? null : result;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
