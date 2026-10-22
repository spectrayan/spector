/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.memory.cortex.consolidation;

import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.kernel.store.EngramRegion;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.memory.graph.EntityDirectory;
import com.spectrayan.spector.kernel.store.HyperEntityGraphMemory;
import com.spectrayan.spector.kernel.id.TsidGenerator;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.kernel.layout.EngramLayout;
import com.spectrayan.spector.kernel.layout.ProvenanceLayout;
import com.spectrayan.spector.kernel.store.ProvenanceEdge;
import com.spectrayan.spector.kernel.store.ProvenanceMemory;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.layout.FixedEngramLayout;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.CRC32C;

/**
 * Abstract base class for memory consolidation implementations.
 *
 * <p>Provides template methods for candidate pair evaluation, CADP contradiction
 * resolution delegation via {@link CadpContradictionResolver}, and non-contradictory
 * duplicate fusion via {@link MemoryMerger}.</p>
 *
 * <p>Pairs are merged only when the {@link ContradictionDetector} returns
 * {@link ContradictionDetector.Verdict#COMPATIBLE}. When no verdict is available
 * (no LLM provider, provider error, unparseable reply) the pair is skipped and both
 * records are left intact; skipped pairs are counted in {@link #unverifiedPairsSkipped()}.</p>
 */
public abstract class AbstractConsolidator implements Consolidator {

    private static final Logger log = LoggerFactory.getLogger(AbstractConsolidator.class);

    /** ID prefix of a memory produced by merging duplicates. */
    public static final String MERGED_ID_PREFIX = "cns-";

    protected final LlmProvider textGenerator;
    protected final EmbeddingProvider embeddingProvider;
    protected final ContradictionDetector contradictionDetector;
    protected final MemoryMerger memoryMerger;
    protected final TsidGenerator tsidGenerator = new TsidGenerator();
    private final AtomicLong unverifiedPairsSkipped = new AtomicLong();
    private final AtomicReference<ProvenanceMemory> provenanceMemory = new AtomicReference<>();

    protected AbstractConsolidator(LlmProvider textGenerator, EmbeddingProvider embeddingProvider) {
        this.textGenerator = textGenerator;
        this.embeddingProvider = embeddingProvider;
        this.contradictionDetector = new ContradictionDetector(textGenerator);
        this.memoryMerger = new MemoryMerger(textGenerator, embeddingProvider);
        if (!contradictionDetector.canVerify()) {
            log.warn("Consolidator: no LLM provider configured; contradiction checks are unavailable, "
                    + "so duplicate merging and CADP resolution are disabled (pairs are left intact)");
        }
    }

    /**
     * Attaches the provenance store that merged memories record their lineage in (#1145).
     *
     * @param provenance the provenance store, or {@code null} to stop recording lineage
     */
    public void attachProvenance(ProvenanceMemory provenance) {
        provenanceMemory.set(provenance);
    }

    /**
     * Returns the number of candidate pairs left untouched because no contradiction
     * verdict was available (fail-closed, #1146).
     *
     * @return cumulative count of skipped pairs
     */
    public long unverifiedPairsSkipped() {
        return unverifiedPairsSkipped.get();
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
            EngramRegion store,
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

        // 1. Contradiction classification — fail closed when no verdict is available (#1146)
        ContradictionDetector.Verdict verdict = contradictionDetector.verdict(recordA.text(), recordB.text());

        if (verdict == ContradictionDetector.Verdict.UNKNOWN) {
            long skipped = unverifiedPairsSkipped.incrementAndGet();
            log.warn("Consolidator: no contradiction verdict for '{}' and '{}'; leaving both intact "
                    + "(unverified pairs skipped so far: {})", recordA.id(), recordB.id(), skipped);
            return false;
        }

        if (verdict == ContradictionDetector.Verdict.CONTRADICTORY) {
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
            EngramRegion store,
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
     * ingests it, and tombstones the original records only once the merged memory is stored.
     */
    protected void mergeDuplicate(
            CognitiveRecord recordA,
            CognitiveRecord recordB,
            com.spectrayan.spector.memory.persist.PartitionManager partitionManager,
            EngramRegion store,
            ScalarQuantizer quantizer,
            RememberPathway rememberPathway,
            MemoryIndex index,
            MemoryWal wal) {

        MemoryMerger.MergedMemory merged = memoryMerger.merge(recordA, recordB, quantizer);
        long newTsid = tsidGenerator.generateLong();
        String newId = MERGED_ID_PREFIX + TsidGenerator.encodeCrockford(newTsid);

        byte semanticFlags = EncodingHeaderFields.withMemoryType(
                EncodingHeaderFields.FLAG_CONSOLIDATED,
                MemoryType.SEMANTIC.ordinal());

        EncodingHeader header = new EncodingHeader(
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

        boolean ingested = rememberPathway.ingestCognitiveWithHeader(
                newId,
                merged.text(),
                merged.vector(),
                MemoryType.SEMANTIC,
                tags,
                MemorySource.REFLECTED,
                header
        );
        if (!ingested) {
            // The originals are only retired once the merged memory is stored, so a rejected ingest
            // loses nothing (#1168).
            log.warn("Consolidator: merged memory '{}' was not stored; keeping '{}' and '{}' intact",
                    newId, recordA.id(), recordB.id());
            return;
        }
        // Tombstone old records in off-heap, WAL, and index
        tombstoneRecord(recordA, partitionManager, store, index, wal);
        tombstoneRecord(recordB, partitionManager, store, index, wal);
        recordMergeProvenance(newTsid, merged.text(), recordA, recordB);
    }

    /**
     * Writes one provenance row per merged source (semantic or procedural source kind), so
     * {@code explainAll(newId)} resolves to both originals and a later forget of either
     * source can reach the merged memory (#1145).
     */
    private void recordMergeProvenance(long newTsid, String mergedText, CognitiveRecord... sources) {
        ProvenanceMemory provenance = provenanceMemory.get();
        if (provenance == null) {
            return;
        }
        CRC32C crc = new CRC32C();
        crc.update(mergedText != null ? mergedText.getBytes(StandardCharsets.UTF_8) : new byte[0]);
        short contentHashHi = (short) (crc.getValue() >>> 16);
        long now = System.currentTimeMillis();
        for (int i = 0; i < sources.length; i++) {
            CognitiveRecord source = sources[i];
            byte sourceKind = source.memoryType() == MemoryType.PROCEDURAL
                    ? ProvenanceLayout.SOURCE_PROCEDURAL
                    : ProvenanceLayout.SOURCE_SEMANTIC;
            int sourcePartition = Math.max(0, source.partitionIndex());
            ProvenanceEdge edge = new ProvenanceEdge(
                    0L, newTsid, (short) 1, (byte) i, (byte) sources.length,
                    sourcePartition, 0, 0, 0, 0, (short) 0, contentHashHi, now,
                    sourceKind, ProvenanceLayout.TARGET_SEMANTIC, (byte) 0,
                    ProvenanceKeys.of(source.id()), sourcePartition);
            try {
                if (provenance.append(edge) < 0) {
                    log.warn("Consolidator: provenance region full; lineage of '{}' from '{}' not recorded",
                            MERGED_ID_PREFIX + TsidGenerator.encodeCrockford(newTsid), source.id());
                }
            } catch (RuntimeException e) {
                log.warn("Consolidator: failed to record provenance from '{}': {}", source.id(), e.getMessage());
            }
        }
    }

    /**
     * Merges two non-contradictory duplicate records in a single store (backward compatibility).
     */
    protected void mergeDuplicate(
            CognitiveRecord recordA,
            CognitiveRecord recordB,
            EngramRegion store,
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
            EngramRegion fallbackStore,
            MemoryIndex index,
            MemoryWal wal) {

        if (partitionManager != null) {
            var router = partitionManager.routerFor(record.partitionIndex());
            if (router != null) {
                router.tombstone(record.memoryType(), record.byteOffset());
            }
        } else if (fallbackStore != null) {
            fallbackStore.tombstone(record.byteOffset());
        }

        if (wal != null) {
            wal.appendForget(record.id());
        }
        if (index != null) {
            // retire, not remove: like forget(), keep the location so purge can still reach the bytes
            // and provenance can still resolve the merged source's ID (#1145)
            index.retire(record.id());
        }
        log.debug("Consolidator: Tombstoned and de-indexed memory '{}'", record.id());
    }

    /**
     * Tombstones a record off-heap, logs to WAL, and removes it from the index (backward compatibility).
     */
    protected void tombstoneRecord(
            CognitiveRecord record,
            EngramRegion store,
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
