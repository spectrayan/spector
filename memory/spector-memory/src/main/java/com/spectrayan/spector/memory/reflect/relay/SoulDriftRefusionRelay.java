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
package com.spectrayan.spector.memory.reflect.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.memory.cortex.CognitiveRecordMemory;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import com.spectrayan.spector.memory.model.ImportanceContext;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.neurodivergent.IngestionHints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.util.PriorityQueue;

/**
 * REM Sleep Soul-Drift Re-Fusion Relay (#503).
 *
 * <p>Identifies memories ingested under older agent/user soul configurations, prioritizes
 * re-fusion by encoding surprise z-scores, recalculates importance using current personality
 * and ICNU parameters, and stamps updated headers in-place.</p>
 */
public final class SoulDriftRefusionRelay implements SynapticRelay<ReflectSignal> {

    private static final Logger log = LoggerFactory.getLogger(SoulDriftRefusionRelay.class);

    private record DriftCandidate(
            CognitiveRecordMemory store,
            long offset,
            float encodingSurprise,
            float oldImportance,
            short oldSoulVersion
    ) implements Comparable<DriftCandidate> {
        @Override
        public int compareTo(DriftCandidate other) {
            // Max-Heap: highest surprise first
            return Float.compare(other.encodingSurprise, this.encodingSurprise);
        }
    }

    @Override
    public boolean transmit(final ReflectSignal signal) {
        if (!signal.soulDriftRefusionEnabled() || signal.partitionManager() == null) {
            return true;
        }

        // 1. Generative Prior Plasticity: Adapt Generative Prior Mean toward Autobiographical Centroid (MR-07)
        if (signal.mentalStateTracker() != null) {
            float[] centroid = computeAutobiographicalCentroid(signal);
            if (centroid != null) {
                signal.mentalStateTracker().adaptPriorMean(centroid, 0.005f);
                log.info("Generative prior adapted toward autobiographical centroid (dim={})", centroid.length);
            }
        }

        // 2. Soul-Drift Re-Fusion: Re-stamp stale soul-version memories using the evolved prior
        short currentSoulVersion = 0;
        if (signal.rememberPathway() != null) {
            currentSoulVersion = signal.rememberPathway().currentSoulVersion();
        }

        if (currentSoulVersion > 0) {
            int maxBatchSize = signal.soulDriftRefusionBatchSize();
            PriorityQueue<DriftCandidate> heap = new PriorityQueue<>();

            var handles = signal.partitionManager().snapshot();
            for (var handle : handles) {
                if (handle.router() == null) continue;
                scanStore(handle.router().semantic(), currentSoulVersion, heap, signal);
                scanStore(handle.router().working(), currentSoulVersion, heap, signal);
                if (!handle.router().isEpisodicLogMode()) {
                    scanStore(handle.router().episodic(), currentSoulVersion, heap, signal);
                }
            }

            int reFused = 0;
            while (!heap.isEmpty() && reFused < maxBatchSize) {
                DriftCandidate candidate = heap.poll();
                refuseMemory(candidate, currentSoulVersion, signal);
                reFused++;
            }

            if (reFused > 0) {
                log.info("Soul-Drift Re-Fusion: re-fused {} / {} detected memories (avg delta={})",
                        reFused, signal.soulDriftedCount(), String.format("%.3f", signal.averageImportanceDelta()));
            }
        }

        return true;
    }

    private float[] computeAutobiographicalCentroid(ReflectSignal signal) {
        if (signal.partitionManager() == null) return null;
        ScalarQuantizer quantizer = signal.quantizer();
        if (quantizer == null && signal.rememberPathway() != null) {
            quantizer = signal.rememberPathway().quantizer();
        }
        if (quantizer == null) return null;

        float[] accumulator = null;
        int count = 0;

        var handles = signal.partitionManager().snapshot();
        for (var handle : handles) {
            if (handle.router() == null) continue;
            CognitiveRecordMemory[] stores = new CognitiveRecordMemory[]{
                    handle.router().semantic(),
                    handle.router().working(),
                    !handle.router().isEpisodicLogMode() ? handle.router().episodic() : null
            };

            for (CognitiveRecordMemory store : stores) {
                if (store != null && store.segment() != null) {
                    CognitiveRecordLayout layout = store.cognitiveLayout();
                    MemorySegment segment = store.segment();
                    int size = store.size();
                    int vecBytes = layout.quantizedVecBytes();
                    byte[] qBytes = new byte[vecBytes];

                    for (int i = 0; i < size && count < 2000; i++) {
                        long offset = store.recordOffset(i);
                        byte flags = layout.readFlags(segment, offset);
                        if (SynapticHeaderConstants.isTombstoned(flags)) continue;

                        MemorySegment.copy(segment, layout.vectorOffset(offset), MemorySegment.ofArray(qBytes), 0, vecBytes);
                        float[] vec = quantizer.decode(qBytes);
                        if (accumulator == null) {
                            accumulator = new float[vec.length];
                        }
                        for (int d = 0; d < vec.length; d++) {
                            accumulator[d] += vec[d];
                        }
                        count++;
                    }
                }
            }
        }

        if (accumulator != null && count > 0) {
            for (int d = 0; d < accumulator.length; d++) {
                accumulator[d] /= count;
            }
        }

        return accumulator;
    }

    private void scanStore(CognitiveRecordMemory store, short currentSoulVersion,
                           PriorityQueue<DriftCandidate> heap, ReflectSignal signal) {
        if (store == null || store.segment() == null) return;

        CognitiveRecordLayout layout = store.cognitiveLayout();
        MemorySegment segment = store.segment();
        int size = store.size();

        for (int i = 0; i < size; i++) {
            long offset = store.recordOffset(i);
            byte flags = layout.readFlags(segment, offset);
            if (SynapticHeaderConstants.isTombstoned(flags)) continue;

            short recordSoulVersion = layout.readSoulVersion(segment, offset);
            if (recordSoulVersion < currentSoulVersion) {
                signal.addSoulDrifted(1);
                float importance = layout.readImportance(segment, offset);
                float encodingSurprise = layout.readEncodingSurprise(segment, offset);

                heap.offer(new DriftCandidate(store, offset, encodingSurprise, importance, recordSoulVersion));
            }
        }
    }

    private void refuseMemory(DriftCandidate candidate, short targetVersion, ReflectSignal signal) {
        CognitiveRecordMemory store = candidate.store();
        CognitiveRecordLayout layout = store.cognitiveLayout();
        MemorySegment segment = store.segment();
        long offset = candidate.offset();

        CognitiveHeader header = layout.readHeader(segment, offset);
        if (SynapticHeaderConstants.isTombstoned(header.flags())) return;

        int vecBytes = layout.quantizedVecBytes();
        byte[] quantized = new byte[vecBytes];
        MemorySegment.copy(segment, layout.vectorOffset(offset), MemorySegment.ofArray(quantized), 0, vecBytes);

        ScalarQuantizer quantizer = signal.quantizer();
        if (quantizer == null && signal.rememberPathway() != null) {
            quantizer = signal.rememberPathway().quantizer();
        }
        float[] vector = (quantizer != null) ? quantizer.decode(quantized) : new float[vecBytes];

        MemoryType memoryType = SynapticHeaderConstants.memoryTypeOf(header.flags());
        IngestionHints hints = new IngestionHints(
                Math.clamp(header.importance() / 10.0f, 0.0f, 1.0f),
                0.5f,
                0.5f,
                header.valence(),
                header.arousal()
        );
        ImportanceContext ctx = new ImportanceContext(
                null,
                vector,
                hints,
                signal.salienceProfile(),
                memoryType,
                0.0f,
                candidate.encodingSurprise(),
                true
        );

        var importanceResult = signal.importanceProvider().score(ctx);
        float newImportance = importanceResult.importance();

        // In-place mutation
        layout.writeImportance(segment, offset, newImportance);
        layout.writeSoulVersion(segment, offset, targetVersion);

        double delta = Math.abs(newImportance - candidate.oldImportance());
        signal.addSoulRefused(1);
        signal.recordImportanceDelta(delta);

        log.trace("Soul-drift re-fused offset {} v{} -> v{}: importance {} -> {} (delta={})",
                offset, candidate.oldSoulVersion(), targetVersion,
                candidate.oldImportance(), newImportance, delta);
    }
}
