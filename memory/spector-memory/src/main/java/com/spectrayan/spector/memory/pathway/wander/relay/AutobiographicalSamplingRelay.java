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
package com.spectrayan.spector.memory.pathway.wander.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.memory.persist.PartitionManager;
import com.spectrayan.spector.memory.cortex.EngramMemory;
import com.spectrayan.spector.memory.cortex.PartitionHandle;
import com.spectrayan.spector.core.similarity.CosineSimilarity;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeader;
import com.spectrayan.spector.memory.kernel.layout.EngramLayout;
import com.spectrayan.spector.memory.kernel.layout.EncodingHeaderFields;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.util.List;

/**
 * Stage 2 relay in {@link com.spectrayan.spector.memory.pathway.wander.WanderPathway} that samples seed autobiographical and episodic memories.
 *
 * <h3>Biological Analog: Spontaneous Episodic Memory Replay</h3>
 * <p>Randomly selects seed memory representations from high-salience semantic and autobiographical
 * stores to serve as starting points for continuous Hopfield energy relaxation.</p>
 *
 * @since 1.2.0
 */
public final class AutobiographicalSamplingRelay implements SynapticRelay<WanderSignal> {

    private static final Logger log = LoggerFactory.getLogger(AutobiographicalSamplingRelay.class);

    @Override
    public boolean transmit(final WanderSignal signal) {
        if (signal == null || signal.partitionManager() == null) {
            return true;
        }

        ScalarQuantizer quantizer = signal.quantizer();
        if (quantizer == null) {
            return true;
        }

        PartitionManager pm = signal.partitionManager();
        List<PartitionHandle> handles = pm.snapshot();
        if (handles == null || handles.isEmpty()) {
            return true;
        }

        int maxSamples = Math.max(10, signal.minSampleCount() * 4);
        int collected = 0;

        for (PartitionHandle handle : handles) {
            if (handle.router() == null || collected >= maxSamples) {
                continue;
            }

            collected += sampleFromStore(handle.router().semantic(), quantizer, signal, maxSamples - collected, "sem-" + handle.seq());
        }

        if (log.isDebugEnabled()) {
            log.debug("AutobiographicalSamplingRelay: sampled {} memory representations for mind-wandering",
                    signal.sampledVectors().size());
        }

        return true;
    }

    private int sampleFromStore(EngramMemory store, ScalarQuantizer quantizer, WanderSignal signal, int limit, String prefix) {
        if (store == null || store.segment() == null) {
            return 0;
        }

        EngramLayout layout = store.cognitiveLayout();
        MemorySegment segment = store.segment();
        int size = store.size();
        if (size <= 0) {
            return 0;
        }

        int vecBytes = layout.quantizedVecBytes();
        byte[] qBytes = new byte[vecBytes];
        int count = 0;

        int strideStep = Math.max(1, size / limit);
        for (int i = 0; i < size && count < limit; i += strideStep) {
            long offset = store.recordOffset(i);
            byte flags = layout.readFlags(segment, offset);
            if (EncodingHeaderFields.isTombstoned(flags)) {
                continue;
            }

            MemorySegment.copy(segment, layout.vectorOffset(offset), MemorySegment.ofArray(qBytes), 0, vecBytes);
            float[] vector = quantizer.decode(qBytes);

            if (signal.soulPriorPreference() != null) {
                float similarity = CosineSimilarity.compute(vector, signal.soulPriorPreference());
                if (similarity < -0.2f) {
                    if (log.isDebugEnabled()) {
                        log.debug("Skipping memory {} due to low soul similarity: {}", prefix + "-" + i, similarity);
                    }
                    continue;
                }
            }

            signal.sampledVectors().add(vector);
            signal.sampledMemoryIds().add(prefix + "-" + i);
            count++;
        }

        return count;
    }

    @Override
    public String relayName() {
        return "autobiographical_sampling";
    }
}
