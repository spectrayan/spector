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

import com.spectrayan.spector.memory.cortex.PartitionHandle;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.kernel.StorageLayout;
import com.spectrayan.spector.memory.pipeline.CognitiveIngestionTarget;

import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Assembles the {@link PartitionManager} together with the #443 Phase-2
 * open-all-partitions registry: it opens every frozen (older) partition
 * read-only, constructs the manager over the active partition plus those frozen
 * handles, installs the partition-roll callback, and installs the
 * colocated-partition text resolver on the shared index.
 *
 * <p>Extracted verbatim from {@code SpectorMemoryFactory.assemble} as part of the
 * #437 god-class decomposition. The frozen-handle construction, the disk vs
 * in-memory {@code PartitionManager} constructors, the roll callback and the text
 * resolver are byte-for-byte preserved.</p>
 *
 * @since 1.1.0
 */
final class PartitionManagerBuilder {

    private static final Logger log = LoggerFactory.getLogger(PartitionManagerBuilder.class);

    private PartitionManagerBuilder() {}

    static PartitionManager build(
            SpectorMemoryBuilder builder,
            CognitiveCortexBuilder.CortexFoundation cortex,
            RetrievalIndexBuilder.RetrievalIndices retrieval,
            MemoryIndex index,
            CognitiveGraphBuilder.CognitiveGraphs graphs,
            CognitiveIngestionTarget cognitiveTarget) {

        boolean isDisk = cortex.isDisk();
        Path basePath = cortex.basePath();
        int quantizedVecBytes = cortex.quantizedVecBytes();
        Path resolvedPartitionDir = cortex.resolvedPartitionDir();
        int initialPartitionSeq = cortex.initialPartitionSeq();
        var cognitiveRouter = cortex.cognitiveRouter();
        var workingStore = cortex.workingStore();
        var textDataStore = retrieval.textDataStore();

        //  Frozen partition handles (#443 Phase 2 open-all-on-load) 
        // Open every older partition dir read-only; each gets its own tier stores + text.dat
        // and shares the global working store. These join the registry as frozen handles.
        List<PartitionHandle> frozenHandles = new java.util.ArrayList<>();
        if (isDisk && basePath != null && !cortex.frozenPartitionDirs().isEmpty()) {
            for (Path frozenDir : cortex.frozenPartitionDirs()) {
                int frozenSeq = StorageLayout.parsePartitionSeqNo(frozenDir.getFileName().toString());
                try {
                    frozenHandles.add(PartitionManager.openFrozenPartition(
                            frozenDir, frozenSeq, workingStore, quantizedVecBytes,
                            builder.semanticCapacity, builder.episodicPartitionCapacity,
                            builder.proceduralCapacity, builder.dataEncryptor));
                } catch (RuntimeException e) {
                    log.error("Failed to open frozen partition {} — records there will be unreadable: {}",
                            frozenDir.getFileName(), e.getMessage(), e);
                }
            }
        }

        //  Partition Manager 
        PartitionManager partitionManager;
        if (isDisk) {
            partitionManager = new PartitionManager(
                    basePath, quantizedVecBytes, builder.semanticCapacity,
                    builder.episodicPartitionCapacity, builder.proceduralCapacity,
                    cognitiveRouter, resolvedPartitionDir, textDataStore, initialPartitionSeq,
                    frozenHandles,
                    index, graphs.hebbianGraph(), graphs.temporalChain(), cognitiveTarget, builder.dataEncryptor);
            cognitiveTarget.setPartitionRollCallback(partitionManager::rollPartition);
        } else {
            partitionManager = new PartitionManager(
                    null, quantizedVecBytes, builder.semanticCapacity,
                    builder.episodicPartitionCapacity, builder.proceduralCapacity,
                    cognitiveRouter, null, textDataStore, initialPartitionSeq,
                    List.of(),
                    index, graphs.hebbianGraph(), graphs.temporalChain(), cognitiveTarget, builder.dataEncryptor);
        }

        // #443 (D3b): resolve MemoryIndex.text(id) via the memory's colocated partition,
        // backed by the live registry (replaces the single setTextDataStore for reads).
        index.setTextResolver(seq -> {
            var handle = partitionManager.handleFor(seq);
            return handle != null ? handle.text() : null;
        });

        return partitionManager;
    }
}
