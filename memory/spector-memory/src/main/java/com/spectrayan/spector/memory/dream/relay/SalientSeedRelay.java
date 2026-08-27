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
package com.spectrayan.spector.memory.dream.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.memory.PartitionManager;
import com.spectrayan.spector.memory.cortex.CognitiveRecordMemory;
import com.spectrayan.spector.memory.cortex.PartitionHandle;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Stage 2 relay in {@link com.spectrayan.spector.memory.DreamPathway}.
 *
 * <h3>Biological Analog: Targeted Memory Reactivation (TMR) & Salience Gating</h3>
 * <p>Scans autobiographical and episodic stores for high prediction error, unresolved Zeigarnik
 * tensions, high emotional arousal, and recency to seed the offline generative dream cycle.</p>
 *
 * @since 1.4.0
 */
public final class SalientSeedRelay implements SynapticRelay<DreamSignal> {

    private static final Logger log = LoggerFactory.getLogger(SalientSeedRelay.class);

    private record SeedCandidate(String id, float[] vector, float salienceScore) {}

    @Override
    public boolean transmit(final DreamSignal signal) {
        if (signal == null) return false;

        // If seeds were already populated upstream (e.g. targeted thought experiments), keep them
        if (!signal.seedMemoryIds().isEmpty() && !signal.seedVectors().isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("SalientSeedRelay: utilizing {} pre-existing seeds", signal.seedMemoryIds().size());
            }
            return true;
        }

        PartitionManager pm = signal.partitionManager();
        if (pm == null) {
            return true;
        }

        List<PartitionHandle> handles = pm.snapshot();
        if (handles == null || handles.isEmpty()) {
            return true;
        }

        int maxSeeds = signal.config().maxDreamsPerCycle();
        int candidatePoolLimit = maxSeeds * 4;
        List<SeedCandidate> candidates = new ArrayList<>();

        for (PartitionHandle handle : handles) {
            if (handle.router() == null || candidates.size() >= candidatePoolLimit) {
                continue;
            }

            collectCandidates(handle.router().episodic(), candidates, candidatePoolLimit, "epi-" + handle.seq());
            if (candidates.size() < candidatePoolLimit) {
                collectCandidates(handle.router().semantic(), candidates, candidatePoolLimit, "sem-" + handle.seq());
            }
        }

        // Rank candidates descending by composite salience score
        candidates.sort(Comparator.comparingDouble(SeedCandidate::salienceScore).reversed());

        int selectedCount = Math.min(maxSeeds, candidates.size());
        for (int i = 0; i < selectedCount; i++) {
            SeedCandidate sc = candidates.get(i);
            signal.seedMemoryIds().add(sc.id());
            signal.seedVectors().add(sc.vector());
        }

        if (log.isDebugEnabled()) {
            log.debug("SalientSeedRelay: selected {} salient seeds for dream synthesis (pool={})",
                    signal.seedMemoryIds().size(), candidates.size());
        }

        return true;
    }

    private void collectCandidates(CognitiveRecordMemory store, List<SeedCandidate> candidates, int limit, String prefix) {
        if (store == null || store.segment() == null) return;

        CognitiveRecordLayout layout = store.cognitiveLayout();
        MemorySegment segment = store.segment();
        int size = store.size();
        if (size <= 0) return;

        int vecBytes = layout.quantizedVecBytes();
        int dim = vecBytes;
        byte[] qBytes = new byte[vecBytes];

        int stride = Math.max(1, size / limit);
        for (int i = 0; i < size && candidates.size() < limit; i += stride) {
            long offset = store.recordOffset(i);
            byte flags = layout.readFlags(segment, offset);
            if (SynapticHeaderConstants.isTombstoned(flags)) {
                continue;
            }

            // Read raw bytes and decode quantized vector
            MemorySegment.copy(segment, layout.vectorOffset(offset), MemorySegment.ofArray(qBytes), 0, vecBytes);
            float[] vector = new float[dim];
            for (int d = 0; d < dim; d++) {
                int byteIdx = d % vecBytes;
                vector[d] = (qBytes[byteIdx] & 0xFF) / 255.0f * 2.0f - 1.0f;
            }

            // Read metadata for composite salience score
            long epochSecs = layout.readTimestamp(segment, offset);
            byte profile = layout.readEncodingProfile(segment, offset);
            boolean simulated = SynapticHeaderConstants.isSimulated(flags);
            boolean dreamed = SynapticHeaderConstants.isDreamed(flags);

            // Composite salience: prioritize un-consolidated, non-dreamed, high-intensity memories
            float recencyWeight = (float) Math.exp(-Math.max(0L, System.currentTimeMillis() / 1000L - epochSecs) / 86400.0);
            float noveltyWeight = (!simulated && !dreamed) ? 1.0f : 0.4f;
            float profileSalience = (profile & 0x0F) / 15.0f;

            float salienceScore = 0.50f * recencyWeight + 0.30f * noveltyWeight + 0.20f * profileSalience;

            candidates.add(new SeedCandidate(prefix + "-" + i, vector, salienceScore));
        }
    }

    @Override
    public String relayName() {
        return "salient_seed";
    }
}
