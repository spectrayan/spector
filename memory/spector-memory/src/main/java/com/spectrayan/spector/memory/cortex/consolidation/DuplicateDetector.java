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

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.core.similarity.SimilarityFunction;
import com.spectrayan.spector.kernel.store.EngramRegion;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.layout.EngramLayout;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.layout.FixedEngramLayout;

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
    public record PartitionStore(int partitionSeq, EngramRegion store) {}

    private record ScannedEntry(int partitionSeq, int recordIndex, String id, float[] decodedVector) {}

    /**
     * Scans the given store for duplicate pairs.
     */
    public List<DuplicatePair> findDuplicates(EngramRegion store, MemoryIndex index, ScalarQuantizer quantizer) {
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
            EngramRegion store = ps.store();
            if (store == null) continue;
            int recordCount = store.visibleCount();
            if (recordCount == 0) continue;

            long baseOffset = store.dataOffset();
            int stride = store.layout().recordStride();

            for (int i = 0; i < recordCount; i++) {
                long offset = baseOffset + (long) i * stride;
                if (store.isTombstoned(offset)) {
                    continue;
                }

                String id = index.findIdByOffset(ps.partitionSeq(), store.type(), offset);
                if (id == null) {
                    continue;
                }

                byte[] quantizedBuf = store.readVector(offset);
                if (quantizedBuf == null) {
                    continue;
                }
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
