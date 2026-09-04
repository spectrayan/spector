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
import com.spectrayan.spector.memory.cortex.EngramMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.kernel.id.TsidGenerator;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.util.HashSet;
import java.util.Set;

/**
 * Abstract base class for memory consolidation implementations.
 *
 * <p>Provides template methods for candidate pair evaluation, CADP contradiction
 * resolution delegation via {@link CadpContradictionResolver}, and non-contradictory
 * duplicate fusion via {@link MemoryMerger}.</p>
 */
public abstract class AbstractConsolidator implements Consolidator {

    private static final Logger log = LoggerFactory.getLogger(AbstractConsolidator.class);

    protected final LlmProvider textGenerator;
    protected final EmbeddingProvider embeddingProvider;
    protected final ContradictionDetector contradictionDetector;
    protected final MemoryMerger memoryMerger;
    protected final TsidGenerator tsidGenerator = new TsidGenerator();

    protected AbstractConsolidator(LlmProvider textGenerator, EmbeddingProvider embeddingProvider) {
        this.textGenerator = textGenerator;
        this.embeddingProvider = embeddingProvider;
        this.contradictionDetector = new ContradictionDetector(textGenerator);
        this.memoryMerger = new MemoryMerger(textGenerator, embeddingProvider);
    }

    /**
     * Evaluates a candidate pair of memories. If contradictory, applies CADP resolution.
     * If non-contradictory and {@code enableMerge} is true, merges the duplicate pair.
     *
     * @param recordA                first record
     * @param recordB                second record
     * @param store                  cognitive tier store
     * @param quantizer              scalar quantizer (optional)
     * @param entityDirectory        entity directory (optional)
     * @param hyperEntityGraph       hypergraph memory (optional)
     * @param temporalKnowledgeGraph temporal knowledge graph (optional)
     * @param rememberPathway        ingestion target for merged memories (optional)
     * @param index                  memory index for tombstoning (optional)
     * @param wal                    memory WAL for tombstoning (optional)
     * @param enableMerge            whether to execute duplicate fusion for non-contradictions
     * @return true if the pair was processed (either resolved or merged), false otherwise
     */
    /**
     * Evaluates a candidate pair of memories across partitions. If contradictory, applies CADP resolution.
     * If non-contradictory and {@code enableMerge} is true, merges the duplicate pair.
     *
     * @param recordA                first record
     * @param recordB                second record
     * @param partitionManager       partition manager resolving partition routers (optional)
     * @param store                  cognitive tier store fallback (optional)
     * @param quantizer              scalar quantizer (optional)
     * @param entityDirectory        entity directory (optional)
     * @param hyperEntityGraph       hypergraph memory (optional)
     * @param temporalKnowledgeGraph temporal knowledge graph (optional)
     * @param rememberPathway        ingestion target for merged memories (optional)
     * @param index                  memory index for tombstoning (optional)
     * @param wal                    memory WAL for tombstoning (optional)
     * @param enableMerge            whether to execute duplicate fusion for non-contradictions
     * @return true if the pair was processed (either resolved or merged), false otherwise
     */
    protected boolean evaluateAndResolvePair(
            CognitiveRecord recordA,
            CognitiveRecord recordB,
            com.spectrayan.spector.memory.persist.PartitionManager partitionManager,
            EngramMemory store,
            ScalarQuantizer quantizer,
            EntityDirectory entityDirectory,
            HyperEntityGraphMemory hyperEntityGraph,
            TemporalKnowledgeGraph temporalKnowledgeGraph,
            RememberPathway rememberPathway,
            MemoryIndex index,
            MemoryWal wal,
            boolean enableMerge) {

        if (recordA == null || recordB == null) {
            return false;
        }

        // 1. Contradiction classification
        boolean isContradictory = contradictionDetector.areContradictory(recordA.text(), recordB.text());

        if (isContradictory) {
            log.info("Consolidator: Detected contradiction between '{}' and '{}'", recordA.id(), recordB.id());
            CadpContradictionResolver.resolve(
                    recordA, recordB, partitionManager, store, hyperEntityGraph, entityDirectory, temporalKnowledgeGraph);
            return true;
        } else if (enableMerge && memoryMerger != null && rememberPathway != null && quantizer != null && index != null) {
            log.info("Consolidator: Merging duplicate memories '{}' and '{}'", recordA.id(), recordB.id());
            mergeDuplicate(recordA, recordB, partitionManager, store, quantizer, rememberPathway, index, wal);
            return true;
        }

        return false;
    }

    /**
     * Evaluates a candidate pair in a single store (backward compatibility).
     */
    protected boolean evaluateAndResolvePair(
            CognitiveRecord recordA,
            CognitiveRecord recordB,
            EngramMemory store,
            ScalarQuantizer quantizer,
            EntityDirectory entityDirectory,
            HyperEntityGraphMemory hyperEntityGraph,
            TemporalKnowledgeGraph temporalKnowledgeGraph,
            RememberPathway rememberPathway,
            MemoryIndex index,
            MemoryWal wal,
            boolean enableMerge) {
        return evaluateAndResolvePair(recordA, recordB, null, store, quantizer,
                entityDirectory, hyperEntityGraph, temporalKnowledgeGraph, rememberPathway, index, wal, enableMerge);
    }

    /**
     * Merges two non-contradictory duplicate records across partitions into a new consolidated memory,
     * ingests it, and tombstones the original records.
     */
    protected void mergeDuplicate(
            CognitiveRecord recordA,
            CognitiveRecord recordB,
            com.spectrayan.spector.memory.persist.PartitionManager partitionManager,
            EngramMemory store,
            ScalarQuantizer quantizer,
            RememberPathway rememberPathway,
            MemoryIndex index,
            MemoryWal wal) {

        MemoryMerger.MergedMemory merged = memoryMerger.merge(recordA, recordB, quantizer);
        String newId = "cns-" + tsidGenerator.generate();

        // Tombstone old records in off-heap, WAL, and index
        tombstoneRecord(recordA, partitionManager, store, index, wal);
        tombstoneRecord(recordB, partitionManager, store, index, wal);

        byte semanticFlags = EncodingHeaderFields.withMemoryType(
                EncodingHeaderFields.FLAG_CONSOLIDATED,
                MemoryType.SEMANTIC.ordinal());

        CognitiveHeader header = new CognitiveHeader(
                merged.timestampMs(),
                merged.synapticTags(),
                1.0f,
                merged.importance(),
                recordA.agentRecallCount() + recordB.agentRecallCount(),
                (short) 0,
                merged.valence(),
                semanticFlags,
                merged.arousal(),
                merged.storageStrength()
        );

        String[] tags = mergeTags(recordA.tags(), recordB.tags());

        rememberPathway.ingestCognitiveWithHeader(
                newId,
                merged.text(),
                merged.vector(),
                MemoryType.SEMANTIC,
                tags,
                MemorySource.REFLECTED,
                header
        );
    }

    /**
     * Merges two non-contradictory duplicate records in a single store (backward compatibility).
     */
    protected void mergeDuplicate(
            CognitiveRecord recordA,
            CognitiveRecord recordB,
            EngramMemory store,
            ScalarQuantizer quantizer,
            RememberPathway rememberPathway,
            MemoryIndex index,
            MemoryWal wal) {
        mergeDuplicate(recordA, recordB, null, store, quantizer, rememberPathway, index, wal);
    }

    /**
     * Tombstones a record off-heap in its colocated partition, logs to WAL, and removes it from the index.
     */
    protected void tombstoneRecord(
            CognitiveRecord record,
            com.spectrayan.spector.memory.persist.PartitionManager partitionManager,
            EngramMemory fallbackStore,
            MemoryIndex index,
            MemoryWal wal) {

        if (partitionManager != null) {
            var router = partitionManager.routerFor(record.partitionIndex());
            if (router != null) {
                var layout = router.layoutFor(record.memoryType());
                var segment = router.segmentFor(record.memoryType());
                if (layout != null && segment != null) {
                    layout.tombstone(segment, record.byteOffset());
                }
            }
        } else if (fallbackStore != null) {
            MemorySegment segment = fallbackStore.segment();
            CognitiveRecordLayout layout = fallbackStore.cognitiveLayout();
            layout.tombstone(segment, record.byteOffset());
        }

        if (wal != null) {
            wal.appendForget(record.id());
        }
        if (index != null) {
            index.remove(record.id());
        }
        log.debug("Consolidator: Tombstoned and de-indexed memory '{}'", record.id());
    }

    /**
     * Tombstones a record off-heap, logs to WAL, and removes it from the index (backward compatibility).
     */
    protected void tombstoneRecord(
            CognitiveRecord record,
            EngramMemory store,
            MemoryIndex index,
            MemoryWal wal) {
        tombstoneRecord(record, null, store, index, wal);
    }

    /**
     * Merges two string tag arrays into a distinct array.
     */
    protected String[] mergeTags(String[] tagsA, String[] tagsB) {
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
