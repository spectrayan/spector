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
package com.spectrayan.spector.memory.pathway.pipeline.scan;

import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.layout.FixedEngramLayout;
import com.spectrayan.spector.kernel.store.EpisodicMemory;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.PartitionHandle;

/**
 * Produces the scan work for a single memory tier given a {@link PartitionHandle}.
 */
public interface TierScanStrategy {

    MemoryType tier();

    void contribute(ScanContext ctx, PartitionHandle handle, ScanEmitter emitter);

    /** Working memory strategy — scanned once via active router. */
    final class WorkingTierScanStrategy implements TierScanStrategy {
        @Override public MemoryType tier() { return MemoryType.WORKING; }

        @Override
        public void contribute(ScanContext ctx, PartitionHandle handle, ScanEmitter emitter) {
            if (!CognitiveMemoryRouter.shouldScan(MemoryType.WORKING, ctx.targetTypes())) return;
            var working = ctx.active().working();
            if (working.visibleCount() <= 0) return;
            emitter.emitSlabScan(ctx.activeSeq(), MemoryType.WORKING,
                    (FixedEngramLayout) working.layout(), working::visibleCount, 0L);
        }
    }

    /** Episodic — one scan per episodic partition of the handle. */
    final class EpisodicTierScanStrategy implements TierScanStrategy {
        @Override public MemoryType tier() { return MemoryType.EPISODIC; }

        @Override
        public void contribute(ScanContext ctx, PartitionHandle handle, ScanEmitter emitter) {
            if (!CognitiveMemoryRouter.shouldScan(MemoryType.EPISODIC, ctx.targetTypes())) return;
            if (handle.router() == null || handle.router().episodic() == null) return;
            EpisodicMemory episodic = handle.router().episodic();
            if (episodic.unconsolidatedTurnOffsets().isEmpty()) return;
            emitter.emitEpisodicScan(episodic, handle.seq());
        }
    }

    /** Semantic tier strategy (ADR-0009, #445). */
    final class SemanticTierScanStrategy implements TierScanStrategy {
        @Override public MemoryType tier() { return MemoryType.SEMANTIC; }

        @Override
        public void contribute(ScanContext ctx, PartitionHandle handle, ScanEmitter emitter) {
            if (!CognitiveMemoryRouter.shouldScan(MemoryType.SEMANTIC, ctx.targetTypes())) return;
            if (!ctx.semanticHnswAvailable()) {
                var semantic = handle.router().semantic();
                if (semantic == null || semantic.visibleCount() <= 0) return;
                emitter.emitSlabScan(handle.seq(), MemoryType.SEMANTIC,
                        (FixedEngramLayout) semantic.layout(), semantic::visibleCount,
                        semantic.dataOffset());
            }
        }
    }

    /** Procedural tier strategy. */
    final class ProceduralTierScanStrategy implements TierScanStrategy {
        @Override public MemoryType tier() { return MemoryType.PROCEDURAL; }

        @Override
        public void contribute(ScanContext ctx, PartitionHandle handle, ScanEmitter emitter) {
            if (!CognitiveMemoryRouter.shouldScan(MemoryType.PROCEDURAL, ctx.targetTypes())) return;
            var procedural = handle.router().procedural();
            if (procedural.visibleCount() <= 0) return;
            emitter.emitSlabScan(handle.seq(), MemoryType.PROCEDURAL,
                    (FixedEngramLayout) procedural.layout(), procedural::visibleCount,
                    procedural.dataOffset());
        }
    }
}
