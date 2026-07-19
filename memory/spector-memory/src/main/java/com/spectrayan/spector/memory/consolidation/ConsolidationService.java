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
import com.spectrayan.spector.memory.cortex.AbstractTierStore;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.cortex.TierRouter;
import com.spectrayan.spector.memory.graph.EntityGraph;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.pipeline.CognitiveIngestionTarget;
import com.spectrayan.spector.memory.synapse.CognitiveRecordLayout;
import com.spectrayan.spector.memory.synapse.SynapticHeaderConstants;
import com.spectrayan.spector.memory.synapse.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.memory.sync.MemoryWal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Orchestrator service for background memory consolidation.
 */
public final class ConsolidationService {

    private static final Logger log = LoggerFactory.getLogger(ConsolidationService.class);

    private final DuplicateDetector duplicateDetector;
    private final ContradictionDetector contradictionDetector;
    private final MemoryMerger memoryMerger;

    public ConsolidationService(LlmProvider textGenerator, EmbeddingProvider embeddingProvider) {
        this.duplicateDetector = new DuplicateDetector();
        this.contradictionDetector = new ContradictionDetector(textGenerator);
        this.memoryMerger = new MemoryMerger(textGenerator, embeddingProvider);
    }

    /**
     * Executes the consolidation cycle across the semantic store.
     */
    public void consolidate(TierRouter tierRouter, MemoryIndex index, ScalarQuantizer quantizer,
                            EntityGraph entityGraph, CognitiveIngestionTarget ingestionTarget,
                            MemoryWal wal, Function<String, CognitiveRecord> inspectFunction) {
        AbstractTierStore semanticStore = (AbstractTierStore) tierRouter.semantic();
        if (semanticStore == null || semanticStore.visibleCount() < 2) {
            log.debug("Consolidation: semantic store too small to run consolidation (visibleCount={})",
                    semanticStore == null ? 0 : semanticStore.visibleCount());
            return;
        }

        log.info("Consolidation: scanning semantic memory for duplicates... ({} visible)", semanticStore.visibleCount());
        List<DuplicateDetector.DuplicatePair> duplicatePairs = duplicateDetector.findDuplicates(semanticStore, index, quantizer);
        if (duplicatePairs.isEmpty()) {
            log.info("Consolidation: no duplicate pairs found.");
            return;
        }

        log.info("Consolidation: found {} duplicate pairs. Evaluating contradictions & merging...", duplicatePairs.size());

        // Map memory slot indices to entity IDs
        Map<Integer, List<Integer>> memToEntities = new HashMap<>();
        if (entityGraph != null) {
            int ecnt = entityGraph.entityCount();
            for (int e = 0; e < ecnt; e++) {
                int refCount = entityGraph.memoryRefCount(e);
                for (int r = 0; r < refCount; r++) {
                    int memIdx = entityGraph.memoryRefAt(e, r);
                    if (memIdx >= 0) {
                        memToEntities.computeIfAbsent(memIdx, k -> new ArrayList<>(2)).add(e);
                    }
                }
            }
        }

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

            // 1. Check if contradictory
            boolean isContradictory = contradictionDetector.areContradictory(recordA.text(), recordB.text());

            if (isContradictory) {
                log.info("Consolidation: Detected contradiction between memory '{}' and '{}'", pair.idA(), pair.idB());

                // Set FLAG_CONTRADICTED in headers off-heap
                MemorySegment segment = semanticStore.segment();
                CognitiveRecordLayout layout = semanticStore.layout();

                long offsetA = recordA.byteOffset();
                long offsetB = recordB.byteOffset();

                layout.markContradicted(segment, offsetA);
                layout.markContradicted(segment, offsetB);

                // Add CONTRADICTS relation in EntityGraph
                if (entityGraph != null) {
                    int slotA = (int) ((offsetA - (semanticStore.isPersistent() ? AbstractTierStore.METADATA_HEADER_BYTES : 0L)) / layout.stride());
                    int slotB = (int) ((offsetB - (semanticStore.isPersistent() ? AbstractTierStore.METADATA_HEADER_BYTES : 0L)) / layout.stride());

                    List<Integer> entitiesA = memToEntities.get(slotA);
                    List<Integer> entitiesB = memToEntities.get(slotB);

                    if (entitiesA != null && entitiesB != null) {
                        for (int eA : entitiesA) {
                            for (int eB : entitiesB) {
                                if (eA != eB) {
                                    entityGraph.addRelation(eA, eB, "CONTRADICTS");
                                }
                            }
                        }
                    }
                }

                processedIds.add(pair.idA());
                processedIds.add(pair.idB());

            } else {
                log.info("Consolidation: Merging duplicate memories '{}' and '{}'", pair.idA(), pair.idB());

                // 2. Merge non-contradictory records
                MemoryMerger.MergedMemory merged = memoryMerger.merge(recordA, recordB, quantizer);

                // Generate new time-sorted ID for merged result
                String newId = "cns-" + new com.spectrayan.spector.memory.id.TsidGenerator().generate();


                // Tombstone old records in off-heap, WAL, and index
                tombstoneRecord(recordA, semanticStore, index, wal);
                tombstoneRecord(recordB, semanticStore, index, wal);

                // Ingest new merged record
                byte semanticFlags = SynapticHeaderConstants.withMemoryType(
                        SynapticHeaderConstants.FLAG_CONSOLIDATED,
                        MemoryType.SEMANTIC.ordinal());

                CognitiveHeader header = new CognitiveHeader(
                        merged.timestampMs(),
                        merged.synapticTags(),
                        1.0f, // norm will be calculated inside ingest
                        merged.importance(),
                        recordA.agentRecallCount() + recordB.agentRecallCount(),
                        (short) 0,
                        merged.valence(),
                        semanticFlags,
                        merged.arousal(),
                        merged.storageStrength()
                );

                String[] tags = mergeTags(recordA.tags(), recordB.tags());

                ingestionTarget.ingestCognitiveWithHeader(
                        newId,
                        merged.text(),
                        merged.vector(),
                        MemoryType.SEMANTIC,
                        tags,
                        MemorySource.REFLECTED,
                        header
                );

                processedIds.add(pair.idA());
                processedIds.add(pair.idB());
            }
        }
    }

    private void tombstoneRecord(CognitiveRecord record, AbstractTierStore store, MemoryIndex index, MemoryWal wal) {
        MemorySegment segment = store.segment();
        CognitiveRecordLayout layout = store.layout();
        layout.tombstone(segment, record.byteOffset());

        if (wal != null) {
            wal.appendForget(record.id());
        }
        index.remove(record.id());
        log.debug("Consolidation: Tombstoned and de-indexed memory '{}'", record.id());
    }

    private String[] mergeTags(String[] tagsA, String[] tagsB) {
        Set<String> merged = new HashSet<>();
        if (tagsA != null) {
            for (String t : tagsA) merged.add(t.trim().toLowerCase());
        }
        if (tagsB != null) {
            for (String t : tagsB) merged.add(t.trim().toLowerCase());
        }
        return merged.toArray(new String[0]);
    }
}
