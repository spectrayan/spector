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
import com.spectrayan.spector.memory.cortex.CognitiveRecordMemory;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

/**
 * Detector for finding near-duplicate memory records within a specific memory store tier.
 */
public final class DuplicateDetector {

    private static final Logger log = LoggerFactory.getLogger(DuplicateDetector.class);

    private final float distanceThreshold; // L2 distance threshold (< 0.05 is near-duplicate)

    public DuplicateDetector(float distanceThreshold) {
        this.distanceThreshold = distanceThreshold;
    }

    public DuplicateDetector() {
        this(0.05f); // default L2 threshold (~0.95+ cosine similarity)
    }

    public record DuplicatePair(int indexA, int indexB, String idA, String idB, float distance) {}

    /**
     * Associates a partition sequence number with a tier memory store.
     */
    public record PartitionStore(int partitionSeq, CognitiveRecordMemory store) {}

    private record ScannedEntry(int partitionSeq, int recordIndex, String id, float[] decodedVector) {}

    /**
     * Scans the given store for duplicate pairs.
     */
    public List<DuplicatePair> findDuplicates(CognitiveRecordMemory store, MemoryIndex index, ScalarQuantizer quantizer) {
        if (store == null) return List.of();
        int partitionSeq = index != null ? index.activePartitionSeq() : 0;
        return findDuplicatesAcrossPartitions(List.of(new PartitionStore(partitionSeq, store)), index, quantizer);
    }

    /**
     * Scans multiple partition stores for duplicate pairs across frozen and active partitions (#446).
     */
    public List<DuplicatePair> findDuplicatesAcrossPartitions(
            List<PartitionStore> partitionStores,
            MemoryIndex index,
            ScalarQuantizer quantizer) {
        List<DuplicatePair> pairs = new ArrayList<>();
        if (partitionStores == null || partitionStores.isEmpty() || index == null || quantizer == null) {
            return pairs;
        }

        List<ScannedEntry> entries = new ArrayList<>();

        for (PartitionStore ps : partitionStores) {
            CognitiveRecordMemory store = ps.store();
            if (store == null) continue;
            int recordCount = store.visibleCount();
            if (recordCount == 0) continue;

            MemorySegment segment = store.segment();
            CognitiveRecordLayout layout = store.cognitiveLayout();
            long baseOffset = store.isPersistent() ? CognitiveRecordMemory.METADATA_HEADER_BYTES : 0L;
            int stride = layout.stride();
            int qVecBytes = layout.quantizedVecBytes();
            byte[] quantizedBuf = new byte[qVecBytes];

            for (int i = 0; i < recordCount; i++) {
                long offset = baseOffset + (long) i * stride;
                byte flags = segment.get(SynapticHeaderConstants.LAYOUT_FLAGS, offset + SynapticHeaderConstants.OFFSET_FLAGS);

                if (SynapticHeaderConstants.isTombstoned(flags)) {
                    continue;
                }

                String id = index.findIdByOffset(ps.partitionSeq(), store.type(), offset);
                if (id == null) {
                    continue;
                }

                long vecOffset = layout.vectorOffset(offset);
                MemorySegment.copy(segment, java.lang.foreign.ValueLayout.JAVA_BYTE, vecOffset,
                        MemorySegment.ofArray(quantizedBuf), java.lang.foreign.ValueLayout.JAVA_BYTE, 0, qVecBytes);
                float[] decoded = new float[quantizer.dimensions()];
                quantizer.decode(quantizedBuf, 0, decoded, 0);

                entries.add(new ScannedEntry(ps.partitionSeq(), i, id, decoded));
            }
        }

        int totalEntries = entries.size();
        for (int i = 0; i < totalEntries; i++) {
            ScannedEntry entryA = entries.get(i);
            for (int j = i + 1; j < totalEntries; j++) {
                ScannedEntry entryB = entries.get(j);
                if (entryA.id().equals(entryB.id())) continue;

                float dist = SimilarityFunction.EUCLIDEAN.compute(entryA.decodedVector(), entryB.decodedVector());
                if (dist <= distanceThreshold) {
                    log.debug("DuplicateDetector: found near-duplicate pair [{}, {}] with L2={}",
                            entryA.id(), entryB.id(), dist);
                    pairs.add(new DuplicatePair(entryA.recordIndex(), entryB.recordIndex(),
                            entryA.id(), entryB.id(), dist));
                }
            }
        }

        return pairs;
    }
}
