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
package com.spectrayan.spector.memory.cortex.consolidation;

import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.SemanticMemory;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Batch consolidator executing periodic all-pairs scans across cognitive memory.
 *
 * <p>Scans the semantic tier with {@link DuplicateDetector}, evaluates candidate pairs
 * for contradictions with CADP directional resolution via {@link CadpContradictionResolver},
 * or merges non-contradictory duplicate records via {@link MemoryMerger}.</p>
 */
public final class BatchConsolidator extends AbstractConsolidator {

    private static final Logger log = LoggerFactory.getLogger(BatchConsolidator.class);

    private final DuplicateDetector duplicateDetector;

    public BatchConsolidator(LlmProvider textGenerator, EmbeddingProvider embeddingProvider) {
        super(textGenerator, embeddingProvider);
        this.duplicateDetector = new DuplicateDetector();
    }

    /**
     * Executes the consolidation cycle across the semantic store (without TKG bridge).
     */
    public void consolidate(CognitiveMemoryRouter cognitiveRouter, MemoryIndex index, ScalarQuantizer quantizer,
                            EntityDirectory entityDirectory, HyperEntityGraphMemory hyperEntityGraph,
                            RememberPathway rememberPathway,
                            MemoryWal wal, Function<String, CognitiveRecord> inspectFunction) {
        consolidate(cognitiveRouter, index, quantizer, entityDirectory, hyperEntityGraph, null, rememberPathway, wal, inspectFunction);
    }

    /**
     * Executes the consolidation cycle across all partitions (frozen + active) (#446).
     */
    public void consolidate(com.spectrayan.spector.memory.persist.PartitionManager partitionManager, MemoryIndex index, ScalarQuantizer quantizer,
                            EntityDirectory entityDirectory, HyperEntityGraphMemory hyperEntityGraph,
                            TemporalKnowledgeGraph temporalKnowledgeGraph,
                            RememberPathway rememberPathway,
                            MemoryWal wal, Function<String, CognitiveRecord> inspectFunction) {
        if (partitionManager == null) {
            return;
        }

        var handles = partitionManager.snapshot();
        List<DuplicateDetector.PartitionStore> partitionStores = new java.util.ArrayList<>();
        int totalVisible = 0;
        for (var handle : handles) {
            var semantic = handle.router() != null ? handle.router().semantic() : null;
            if (semantic != null && semantic.visibleCount() > 0) {
                partitionStores.add(new DuplicateDetector.PartitionStore(handle.seq(), semantic));
                totalVisible += semantic.visibleCount();
            }
        }

        if (totalVisible < 2 || partitionStores.isEmpty()) {
            log.debug("BatchConsolidator: semantic store across partitions too small to run consolidation (totalVisible={})",
                    totalVisible);
            return;
        }

        log.info("BatchConsolidator: scanning semantic memory across {} partitions for duplicates... ({} visible)",
                partitionStores.size(), totalVisible);
        List<DuplicateDetector.DuplicatePair> duplicatePairs = duplicateDetector.findDuplicatesAcrossPartitions(partitionStores, index, quantizer);
        if (duplicatePairs.isEmpty()) {
            log.info("BatchConsolidator: no duplicate pairs found.");
            return;
        }

        log.info("BatchConsolidator: found {} duplicate pairs. Evaluating contradictions & merging...", duplicatePairs.size());

        Set<String> processedIds = new HashSet<>();

        for (DuplicateDetector.DuplicatePair pair : duplicatePairs) {
            if (processedIds.contains(pair.idA()) || processedIds.contains(pair.idB())) {
                continue; // already handled in a previous merge/contradiction in this cycle
            }

            CognitiveRecord recordA = inspectFunction.apply(pair.idA());
            CognitiveRecord recordB = inspectFunction.apply(pair.idB());

            if (recordA == null || recordB == null || recordA.isTombstoned() || recordB.isTombstoned()) {
                continue;
            }

            boolean processed = evaluateAndResolvePair(
                    recordA,
                    recordB,
                    partitionManager,
                    null,
                    quantizer,
                    entityDirectory,
                    hyperEntityGraph,
                    temporalKnowledgeGraph,
                    rememberPathway,
                    index,
                    wal,
                    true // enable duplicate merge in batch mode
            );

            if (processed) {
                processedIds.add(pair.idA());
                processedIds.add(pair.idB());
            }
        }
    }

    /**
     * Executes the consolidation cycle across the semantic store with TemporalKnowledgeGraph bridge (#527).
     */
    public void consolidate(CognitiveMemoryRouter cognitiveRouter, MemoryIndex index, ScalarQuantizer quantizer,
                            EntityDirectory entityDirectory, HyperEntityGraphMemory hyperEntityGraph,
                            TemporalKnowledgeGraph temporalKnowledgeGraph,
                            RememberPathway rememberPathway,
                            MemoryWal wal, Function<String, CognitiveRecord> inspectFunction) {
        SemanticMemory semanticStore = cognitiveRouter.semantic();
        if (semanticStore == null || semanticStore.visibleCount() < 2) {
            log.debug("BatchConsolidator: semantic store too small to run consolidation (visibleCount={})",
                    semanticStore == null ? 0 : semanticStore.visibleCount());
            return;
        }

        log.info("BatchConsolidator: scanning semantic memory for duplicates... ({} visible)", semanticStore.visibleCount());
        List<DuplicateDetector.DuplicatePair> duplicatePairs = duplicateDetector.findDuplicates(semanticStore, index, quantizer);
        if (duplicatePairs.isEmpty()) {
            log.info("BatchConsolidator: no duplicate pairs found.");
            return;
        }

        log.info("BatchConsolidator: found {} duplicate pairs. Evaluating contradictions & merging...", duplicatePairs.size());

        Set<String> processedIds = new HashSet<>();

        for (DuplicateDetector.DuplicatePair pair : duplicatePairs) {
            if (processedIds.contains(pair.idA()) || processedIds.contains(pair.idB())) {
                continue; // already handled in a previous merge/contradiction in this cycle
            }

            CognitiveRecord recordA = inspectFunction.apply(pair.idA());
            CognitiveRecord recordB = inspectFunction.apply(pair.idB());

            if (recordA == null || recordB == null || recordA.isTombstoned() || recordB.isTombstoned()) {
                continue;
            }

            boolean processed = evaluateAndResolvePair(
                    recordA,
                    recordB,
                    semanticStore,
                    quantizer,
                    entityDirectory,
                    hyperEntityGraph,
                    temporalKnowledgeGraph,
                    rememberPathway,
                    index,
                    wal,
                    true // enable duplicate merge in batch mode
            );

            if (processed) {
                processedIds.add(pair.idA());
                processedIds.add(pair.idB());
            }
        }
    }
}
