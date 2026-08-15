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
import com.spectrayan.spector.memory.cortex.CognitiveRecordMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.id.TsidGenerator;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;
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
     * @param ingestionTarget        ingestion target for merged memories (optional)
     * @param index                  memory index for tombstoning (optional)
     * @param wal                    memory WAL for tombstoning (optional)
     * @param enableMerge            whether to execute duplicate fusion for non-contradictions
     * @return true if the pair was processed (either resolved or merged), false otherwise
     */
    protected boolean evaluateAndResolvePair(
            CognitiveRecord recordA,
            CognitiveRecord recordB,
            CognitiveRecordMemory store,
            ScalarQuantizer quantizer,
            EntityDirectory entityDirectory,
            HyperEntityGraphMemory hyperEntityGraph,
            TemporalKnowledgeGraph temporalKnowledgeGraph,
            CognitiveIngestionTarget ingestionTarget,
            MemoryIndex index,
            MemoryWal wal,
            boolean enableMerge) {

        if (recordA == null || recordB == null || store == null) {
            return false;
        }

        // 1. Contradiction classification
        boolean isContradictory = contradictionDetector.areContradictory(recordA.text(), recordB.text());

        if (isContradictory) {
            log.info("Consolidator: Detected contradiction between '{}' and '{}'", recordA.id(), recordB.id());
            CadpContradictionResolver.resolve(
                    recordA, recordB, store, hyperEntityGraph, entityDirectory, temporalKnowledgeGraph);
            return true;
        } else if (enableMerge && memoryMerger != null && ingestionTarget != null && quantizer != null && index != null) {
            log.info("Consolidator: Merging duplicate memories '{}' and '{}'", recordA.id(), recordB.id());
            mergeDuplicate(recordA, recordB, store, quantizer, ingestionTarget, index, wal);
            return true;
        }

        return false;
    }

    /**
     * Merges two non-contradictory duplicate records into a new consolidated memory,
     * ingests it, and tombstones the original records.
     */
    protected void mergeDuplicate(
            CognitiveRecord recordA,
            CognitiveRecord recordB,
            CognitiveRecordMemory store,
            ScalarQuantizer quantizer,
            CognitiveIngestionTarget ingestionTarget,
            MemoryIndex index,
            MemoryWal wal) {

        MemoryMerger.MergedMemory merged = memoryMerger.merge(recordA, recordB, quantizer);
        String newId = "cns-" + tsidGenerator.generate();

        // Tombstone old records in off-heap, WAL, and index
        tombstoneRecord(recordA, store, index, wal);
        tombstoneRecord(recordB, store, index, wal);

        byte semanticFlags = SynapticHeaderConstants.withMemoryType(
                SynapticHeaderConstants.FLAG_CONSOLIDATED,
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

        ingestionTarget.ingestCognitiveWithHeader(
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
     * Tombstones a record off-heap, logs to WAL, and removes it from the index.
     */
    protected void tombstoneRecord(
            CognitiveRecord record,
            CognitiveRecordMemory store,
            MemoryIndex index,
            MemoryWal wal) {

        MemorySegment segment = store.segment();
        CognitiveRecordLayout layout = store.cognitiveLayout();
        layout.tombstone(segment, record.byteOffset());

        if (wal != null) {
            wal.appendForget(record.id());
        }
        if (index != null) {
            index.remove(record.id());
        }
        log.debug("Consolidator: Tombstoned and de-indexed memory '{}'", record.id());
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
