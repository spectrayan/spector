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
package com.spectrayan.spector.memory;

import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.SemanticRecallStrategy;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.pipeline.HebbianCoActivationListener;
import com.spectrayan.spector.memory.pipeline.LtpReconsolidationListener;
import com.spectrayan.spector.memory.pipeline.RecallHistory;
import com.spectrayan.spector.memory.pipeline.RecallPipeline;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Assembles the read-path {@link RecallPipeline}: the semantic recall strategy
 * (rebuilding the HNSW index from persisted vectors when needed), the recall
 * history buffer, the pipeline itself, and the LTP-reconsolidation +
 * Hebbian-coactivation listeners.
 *
 * <p>Extracted verbatim from {@code SpectorMemoryFactory.assemble} as part of the
 * #437 god-class decomposition. The HNSW rebuild still runs after WAL recovery
 * (the builder is invoked after {@code MemoryWalRecovery.recover}), preserving the
 * critical ordering dependency.</p>
 *
 * @since 1.1.0
 */
public final class RecallPipelineBuilder {

    private static final Logger log = LoggerFactory.getLogger(RecallPipelineBuilder.class);

    public RecallPipelineBuilder() {}

    static RecallPipeline build(
            SpectorMemoryBuilder builder,
            EmbeddingProvider embeddingProvider,
            CognitiveCortexBuilder.CortexFoundation cortex,
            BiologicalSubsystemsBuilder.BiologicalSubsystems bio,
            CognitiveGraphBuilder.CognitiveGraphs graphs,
            RetrievalIndexBuilder.RetrievalIndices retrieval,
            MemoryIndex index,
            PartitionManager partitionManager,
            com.spectrayan.spector.memory.sync.MemoryWal wal) {

        CognitiveMemoryRouter cognitiveRouter = cortex.cognitiveRouter();
        ScalarQuantizer quantizer = cortex.quantizer();

        // ── Semantic Recall Strategy + HNSW Rebuild (ADR-0009, #445) ──
        SemanticRecallStrategy semanticStrategy = null;
        if (builder.semanticIndex != null) {
            semanticStrategy = new SemanticRecallStrategy(builder.semanticIndex, partitionManager, index);
            rebuildHnswIfNeeded(builder, partitionManager, index, quantizer);
        }

        // ── RecallHistory (Executive Dysfunction context buffer) ──
        RecallHistory recallHistory = new RecallHistory();

        // ── MMR Reranker ──
        com.spectrayan.spector.memory.pipeline.reranker.MmrReranker mmrReranker = 
            new com.spectrayan.spector.memory.pipeline.reranker.MmrReranker(index, partitionManager, quantizer.mins(), quantizer.scales());

        // ── Recall Pipeline ──
        RecallPipeline recallPipeline = new RecallPipeline(
                embeddingProvider, partitionManager, index,
                bio.suppressionSet(), bio.habituationPenalty(), bio.prospectiveScheduler(), wal,
                quantizer.mins(), quantizer.scales(), semanticStrategy,
                null, graphs.hebbianGraph(), graphs.temporalChain(),
                graphs.entityDirectory(), graphs.hyperEntityGraph(), graphs.temporalKnowledgeGraph(), graphs.entityExtractor(),
                builder.graphScoringPolicy, retrieval.bm25Index(),
                retrieval.memorySpladeIndex(), builder.SparseEmbeddingProvider, retrieval.colbertReranker(),
                recallHistory, mmrReranker, bio.surpriseDetector(), builder.hook);

        recallPipeline.addListener(new LtpReconsolidationListener(index, partitionManager, wal));
        recallPipeline.addListener(new HebbianCoActivationListener(bio.coActivationTracker()));

        return recallPipeline;
    }

    private static void rebuildHnswIfNeeded(SpectorMemoryBuilder builder, PartitionManager partitionManager, MemoryIndex index, ScalarQuantizer quantizer) {
        if (builder.semanticIndex == null || builder.semanticIndex.isReadOnly() || builder.semanticIndex.size() > 0) {
            return;
        }
        var partitions = partitionManager.snapshot();
        int totalRebuilt = 0;
        long startMs = System.currentTimeMillis();

        for (var handle : partitions) {
            int partitionSeq = handle.seq();
            var semStore = handle.router() != null ? handle.router().semantic() : null;
            if (semStore == null || semStore.size() == 0) continue;

            int storeSize = semStore.size();
            var seg = semStore.primarySegment();
            var recLayout = semStore.layout();
            int stride = recLayout.stride();
            int vecBytes = recLayout.quantizedVecBytes();
            long baseOffset = semStore.dataOffset();

            for (int i = 0; i < storeSize; i++) {
                long recordOff = baseOffset + (long) i * stride;
                byte flags = recLayout.readFlags(seg, recordOff);
                if (com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants.isTombstoned(flags)) {
                    continue;
                }

                String id = index.findIdByOffset(partitionSeq, MemoryType.SEMANTIC, recordOff);
                if (id != null) {
                    byte[] quantized = new byte[vecBytes];
                    java.lang.foreign.MemorySegment.copy(
                            seg, java.lang.foreign.ValueLayout.JAVA_BYTE,
                            recLayout.vectorOffset(recordOff),
                            java.lang.foreign.MemorySegment.ofArray(quantized),
                            java.lang.foreign.ValueLayout.JAVA_BYTE, 0, vecBytes);

                    float[] vector = quantizer.decode(quantized);
                    var loc = index.location(id);
                    int graphSlot = (loc != null) ? loc.graphSlot() : i;
                    builder.semanticIndex.add(id, graphSlot, vector);
                    totalRebuilt++;
                }
            }
        }
        if (totalRebuilt > 0) {
            long elapsed = System.currentTimeMillis() - startMs;
            log.info("HNSW multi-partition rebuild complete: {} vectors indexed across {} partitions in {}ms",
                    totalRebuilt, partitions.size(), elapsed);
        }
    }
}
