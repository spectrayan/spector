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
import com.spectrayan.spector.memory.cortex.AbstractTierStore;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.synapse.CognitiveRecordLayout;
import com.spectrayan.spector.memory.synapse.SynapticHeaderConstants;

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
     * Scans the given store for duplicate pairs.
     */
    public List<DuplicatePair> findDuplicates(AbstractTierStore store, MemoryIndex index, ScalarQuantizer quantizer) {
        List<DuplicatePair> pairs = new ArrayList<>();
        int recordCount = store.visibleCount();
        if (recordCount < 2) {
            return pairs;
        }

        MemorySegment segment = store.segment();
        CognitiveRecordLayout layout = store.layout();
        long baseOffset = store.isPersistent() ? AbstractTierStore.METADATA_HEADER_BYTES : 0L;
        int stride = layout.stride();
        int vecBytes = layout.quantizedVecBytes();
        float[] mins = quantizer.mins();
        float[] scales = quantizer.scales();

        byte[] quantizedBuf = new byte[vecBytes];
        float[] decodedVector = new float[quantizer.dimensions()];

        for (int i = 0; i < recordCount; i++) {
            long offsetI = baseOffset + (long) i * stride;
            byte flagsI = segment.get(SynapticHeaderConstants.LAYOUT_FLAGS, offsetI + SynapticHeaderConstants.OFFSET_FLAGS);

            // Skip tombstoned records
            if (SynapticHeaderConstants.isTombstoned(flagsI)) {
                continue;
            }

            // Resolve ID of record A
            String idA = index.findIdByOffset(store.type(), offsetI);
            if (idA == null) {
                continue;
            }

            // Read and dequantize vector A
            long vecOffsetI = layout.vectorOffset(offsetI);
            MemorySegment.copy(segment, java.lang.foreign.ValueLayout.JAVA_BYTE, vecOffsetI,
                    MemorySegment.ofArray(quantizedBuf), java.lang.foreign.ValueLayout.JAVA_BYTE, 0, vecBytes);
            quantizer.decode(quantizedBuf, 0, decodedVector, 0);

            // Compare against subsequent records
            for (int j = i + 1; j < recordCount; j++) {
                long offsetJ = baseOffset + (long) j * stride;
                byte flagsJ = segment.get(SynapticHeaderConstants.LAYOUT_FLAGS, offsetJ + SynapticHeaderConstants.OFFSET_FLAGS);

                if (SynapticHeaderConstants.isTombstoned(flagsJ)) {
                    continue;
                }

                String idB = index.findIdByOffset(store.type(), offsetJ);

                if (idB == null) {
                    continue;
                }

                // Compute calibrated L2 distance via SimilarityFunction
                float dist = SimilarityFunction.EUCLIDEAN.computeQuantizedFromSegment(
                        decodedVector, segment, layout.vectorOffset(offsetJ),
                        mins, scales, vecBytes);

                if (dist <= distanceThreshold) {
                    log.debug("DuplicateDetector: found near-duplicate pair [{}, {}] with L2={}", idA, idB, dist);
                    pairs.add(new DuplicatePair(i, j, idA, idB, dist));
                }
            }
        }

        return pairs;
    }
}
