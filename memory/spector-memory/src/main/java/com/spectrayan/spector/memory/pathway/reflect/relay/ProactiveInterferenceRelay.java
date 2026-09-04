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
package com.spectrayan.spector.memory.pathway.reflect.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.core.similarity.SimilarityFunction;
import com.spectrayan.spector.memory.cortex.EpisodicRecordMemory.EpisodicPartition;
import com.spectrayan.spector.memory.kernel.layout.EngramLayout;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeader;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields;
import com.spectrayan.spector.memory.synapse.IdentityCalibration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REM Sleep Proactive Interference Relay.
 *
 * <p>Performs competitive degradation of near-duplicate memories within centroid clusters,
 * ensuring newer representations prevail while older redundant traces fade smoothly.</p>
 */
public final class ProactiveInterferenceRelay implements SynapticRelay<ReflectSignal> {

    private static final Logger log = LoggerFactory.getLogger(ProactiveInterferenceRelay.class);
    private static final int MAX_INTERFERENCE_CANDIDATES = 20;

    @Override
    public boolean transmit(final ReflectSignal signal) {
        if (signal.partitionManager() == null) {
            return true;
        }

        var handles = signal.partitionManager().snapshot();
        for (var handle : handles) {
            if (handle.router() != null && !handle.router().isEpisodicLogMode()) {
                var episodicStore = handle.router().episodic();
                if (episodicStore != null) {
                    for (EpisodicPartition partition : episodicStore.partitions()) {
                        processPartition(partition, signal);
                    }
                }
            }
        }
        return true;
    }

    private void processPartition(EpisodicPartition partition, ReflectSignal signal) {
        int count = partition.count();
        if (count < 2) return;

        EngramLayout layout = partition.layout();
        MemorySegment segment = partition.segment();
        if (segment == null) return;

        Map<Integer, List<Integer>> centroidClusters = new HashMap<>();
        for (int i = 0; i < count; i++) {
            long offset = partition.recordOffset(i);
            EncodingHeader header = layout.readHeader(segment, offset);
            if (EncodingHeaderFields.isTombstoned(header.flags()) || EncodingHeaderFields.isConsolidated(header.flags())) {
                continue;
            }
            int centroidId = header.centroidId();
            centroidClusters.computeIfAbsent(centroidId, k -> new ArrayList<>()).add(i);
        }

        for (List<Integer> clusterIndices : centroidClusters.values()) {
            if (clusterIndices.size() < 2) continue;
            applyInterference(partition, clusterIndices, signal);
        }
    }

    private void applyInterference(EpisodicPartition partition, List<Integer> clusterIndices, ReflectSignal signal) {
        EngramLayout layout = partition.layout();
        MemorySegment segment = partition.segment();
        float threshold = signal.policy().interferenceThreshold();
        float decayFactor = signal.policy().interferenceDecayFactor();

        List<Integer> candidates;
        if (clusterIndices.size() <= MAX_INTERFERENCE_CANDIDATES) {
            candidates = clusterIndices;
        } else {
            candidates = new ArrayList<>(clusterIndices);
            candidates.sort((a, b) -> {
                float ia = layout.readImportance(segment, partition.recordOffset(a));
                float ib = layout.readImportance(segment, partition.recordOffset(b));
                return Float.compare(ib, ia);
            });
            candidates = candidates.subList(0, MAX_INTERFERENCE_CANDIDATES);
        }

        int vecBytes = layout.quantizedVecBytes();
        float[] identityMins = IdentityCalibration.mins(vecBytes);
        float[] identityScales = IdentityCalibration.scales(vecBytes);
        float[] scratchVecA = new float[vecBytes];

        for (int i = 0; i < candidates.size(); i++) {
            long offsetA = partition.recordOffset(candidates.get(i));
            EncodingHeader headerA = layout.readHeader(segment, offsetA);
            if (EncodingHeaderFields.isTombstoned(headerA.flags())) continue;

            long vecOffsetA = layout.vectorOffset(offsetA);
            for (int d = 0; d < vecBytes; d++) {
                scratchVecA[d] = (segment.get(ValueLayout.JAVA_BYTE, vecOffsetA + d) & 0xFF);
            }

            for (int j = i + 1; j < candidates.size(); j++) {
                long offsetB = partition.recordOffset(candidates.get(j));
                EncodingHeader headerB = layout.readHeader(segment, offsetB);
                if (EncodingHeaderFields.isTombstoned(headerB.flags())) continue;

                float dist = SimilarityFunction.EUCLIDEAN.computeQuantizedFromSegment(
                        scratchVecA, segment, layout.vectorOffset(offsetB),
                        identityMins, identityScales, vecBytes);

                if (dist <= threshold) {
                    long olderOffset = headerA.timestampMs() <= headerB.timestampMs() ? offsetA : offsetB;
                    float olderImportance = layout.readImportance(segment, olderOffset);
                    layout.writeImportance(segment, olderOffset, olderImportance * decayFactor);
                }
            }
        }
    }
}
