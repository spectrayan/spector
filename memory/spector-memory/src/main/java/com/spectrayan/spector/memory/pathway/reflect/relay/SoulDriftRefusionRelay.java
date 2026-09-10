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
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.kernel.store.AbstractEngramMemory;
import com.spectrayan.spector.kernel.store.EngramRegion;
import com.spectrayan.spector.kernel.layout.FixedEngramLayout;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.memory.model.ImportanceContext;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.neuromod.neurodivergent.RememberHints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            EngramRegion store,
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
            EngramRegion[] stores = new EngramRegion[]{
                    handle.router().semantic(),
                    handle.router().working()
            };

            for (EngramRegion store : stores) {
                if (store != null) {
                    int size = store.size();

                    for (int i = 0; i < size && count < 2000; i++) {
                        long offset = store.recordOffset(i);
                        if (store.isTombstoned(offset)) continue;

                        byte[] qBytes = store.readVector(offset);
                        if (qBytes == null) continue;

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

    private void scanStore(EngramRegion store, short currentSoulVersion,
                           PriorityQueue<DriftCandidate> heap, ReflectSignal signal) {
        if (store == null) return;
        int size = store.size();

        for (int i = 0; i < size; i++) {
            long offset = store.recordOffset(i);
            if (store.isTombstoned(offset)) continue;

            EncodingHeader header = store.readHeader(offset);
            if (header == null) continue;

            short recordSoulVersion = header.soulVersion();
            if (recordSoulVersion < currentSoulVersion) {
                signal.addSoulDrifted(1);
                float importance = header.importance();
                float encodingSurprise = header.encodingSurprise();

                heap.offer(new DriftCandidate(store, offset, encodingSurprise, importance, recordSoulVersion));
            }
        }
    }

    private void refuseMemory(DriftCandidate candidate, short targetVersion, ReflectSignal signal) {
        EngramRegion store = candidate.store();
        if (store == null) return;
        long offset = candidate.offset();

        EncodingHeader header = store.readHeader(offset);
        if (header == null || EncodingHeaderFields.isTombstoned(header.flags())) return;

        byte[] quantized = store.readVector(offset);
        if (quantized == null) return;

        ScalarQuantizer quantizer = signal.quantizer();
        if (quantizer == null && signal.rememberPathway() != null) {
            quantizer = signal.rememberPathway().quantizer();
        }
        int vecBytes = quantized.length;
        float[] vector = (quantizer != null) ? quantizer.decode(quantized) : new float[vecBytes];

        MemoryType memoryType = EncodingHeaderFields.memoryTypeOf(header.flags());
        RememberHints hints = new RememberHints(
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
        if (store instanceof AbstractEngramMemory<?> aem) {
            aem.writeImportance(offset, newImportance);
            aem.writeSoulVersion(offset, targetVersion);
        }

        double delta = Math.abs(newImportance - candidate.oldImportance());
        signal.addSoulRefused(1);
        signal.recordImportanceDelta(delta);

        log.trace("Soul-drift re-fused offset {} v{} -> v{}: importance {} -> {} (delta={})",
                offset, candidate.oldSoulVersion(), targetVersion,
                candidate.oldImportance(), newImportance, delta);
    }
}
