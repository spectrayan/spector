/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.memory.pipeline.scan;

import com.spectrayan.spector.memory.cortex.EpisodicRecordMemory.EpisodicPartition;
import com.spectrayan.spector.memory.cortex.PartitionHandle;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.CognitiveRecordMemory;
import com.spectrayan.spector.memory.model.MemoryType;

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
            CognitiveRecordMemory working = ctx.active().working();
            if (working.visibleCount() <= 0) return;
            emitter.emitSlabScan(working::segment, working::visibleCount,
                    working.cognitiveLayout(), MemoryType.WORKING, 0L, ctx.activeSeq());
        }
    }

    /** Episodic — one scan per episodic partition of the handle. */
    final class EpisodicTierScanStrategy implements TierScanStrategy {
        @Override public MemoryType tier() { return MemoryType.EPISODIC; }

        @Override
        public void contribute(ScanContext ctx, PartitionHandle handle, ScanEmitter emitter) {
            if (!CognitiveMemoryRouter.shouldScan(MemoryType.EPISODIC, ctx.targetTypes())) return;
            for (EpisodicPartition partition : handle.router().episodic().partitions()) {
                if (partition.visibleCount() > 0) {
                    emitter.emitSlabScan(partition::segment, partition::visibleCount,
                            partition.layout(), MemoryType.EPISODIC,
                            partition.dataOffset(), handle.seq());
                }
            }
        }
    }

    /** Semantic tier strategy. */
    final class SemanticTierScanStrategy implements TierScanStrategy {
        @Override public MemoryType tier() { return MemoryType.SEMANTIC; }

        @Override
        public void contribute(ScanContext ctx, PartitionHandle handle, ScanEmitter emitter) {
            if (!CognitiveMemoryRouter.shouldScan(MemoryType.SEMANTIC, ctx.targetTypes())) return;
            CognitiveRecordMemory semantic = handle.router().semantic();
            if (semantic == null || semantic.visibleCount() <= 0) return;
            boolean useHnsw = handle.writable() && ctx.singlePartition() && ctx.semanticHnswAvailable();
            if (useHnsw) {
                emitter.emitSemanticHnsw();
            } else {
                emitter.emitSlabScan(semantic::segment, semantic::visibleCount,
                        semantic.cognitiveLayout(), MemoryType.SEMANTIC,
                        semantic.dataOffset(), handle.seq());
            }
        }
    }

    /** Procedural tier strategy. */
    final class ProceduralTierScanStrategy implements TierScanStrategy {
        @Override public MemoryType tier() { return MemoryType.PROCEDURAL; }

        @Override
        public void contribute(ScanContext ctx, PartitionHandle handle, ScanEmitter emitter) {
            if (!CognitiveMemoryRouter.shouldScan(MemoryType.PROCEDURAL, ctx.targetTypes())) return;
            CognitiveRecordMemory procedural = handle.router().procedural();
            if (procedural.visibleCount() <= 0) return;
            emitter.emitSlabScan(procedural::segment, procedural::visibleCount,
                    procedural.cognitiveLayout(), MemoryType.PROCEDURAL,
                    procedural.dataOffset(), handle.seq());
        }
    }
}
