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

        short currentSoulVersion = 0;
        if (signal.rememberPathway() != null) {
            currentSoulVersion = signal.rememberPathway().currentSoulVersion();
        } else if (signal.ingestionTarget() != null) {
            currentSoulVersion = signal.ingestionTarget().currentSoulVersion();
        }

        if (currentSoulVersion <= 0) {
            return true; // No active soul version configured
        }

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

        return true;
    }

    private void scanStore(CognitiveRecordMemory store, short currentSoulVersion,
                           PriorityQueue<DriftCandidate> heap, ReflectSignal signal) {
        if (store == null) return;
        CognitiveRecordLayout layout = store.cognitiveLayout();
        MemorySegment segment = store.segment();
        if (segment == null) return;
        int count = store.size();

        for (int i = 0; i < count; i++) {
            long offset = store.recordOffset(i);
            byte flags = layout.readFlags(segment, offset);
            if (SynapticHeaderConstants.isTombstoned(flags)) continue;

            short version = layout.readSoulVersion(segment, offset);
            if (version < currentSoulVersion) {
                signal.addSoulDrifted(1);
                float surprise = layout.readEncodingSurprise(segment, offset);
                float oldImportance = layout.readImportance(segment, offset);
                heap.offer(new DriftCandidate(store, offset, surprise, oldImportance, version));
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
        if (quantizer == null && signal.ingestionTarget() != null) {
            quantizer = signal.ingestionTarget().quantizer();
        }
        float[] vector = (quantizer != null) ? quantizer.decode(quantized) : new float[vecBytes];

        IngestionHints hints = new IngestionHints(0.5f, 0.5f, 0.5f, header.valence(), header.arousal());
        ImportanceContext ctx = new ImportanceContext(
                null,
                vector,
                hints,
                signal.salienceProfile(),
                MemoryType.SEMANTIC,
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
