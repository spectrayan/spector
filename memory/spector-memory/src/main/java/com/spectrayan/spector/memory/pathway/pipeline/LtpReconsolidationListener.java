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
package com.spectrayan.spector.memory.pathway.pipeline;

import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.PartitionRegistry;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.cortex.index.IndexRecordMemory.MemoryLocation;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.sync.WalEvent;
import com.spectrayan.spector.memory.kernel.layout.FixedEngramLayout;

import java.lang.foreign.MemorySegment;
import java.util.List;

/**
 * LTP Reconsolidation listener — records recall timestamps and WAL events.
 *
 * <h3>Biological Analog: Long-Term Potentiation (LTP)</h3>
 * <p>Each time a memory is successfully recalled, its synaptic strength increases.
 * In Spector's model, this manifests as:</p>
 * <ul>
 *   <li><b>ACT-R recall timestamps</b>: recorded in the 4-slot ring buffer in
 *       the strength region. These enable the full ACT-R base-level activation
 *       computation: {@code B_i = ln(Σ t_j^{-d})}.</li>
 *   <li><b>Recall count</b>: incremented only on explicit {@code reinforce()}
 *       calls to prevent inflation from passive retrieval.</li>
 * </ul>
 *
 * <h3>Design Pattern: Observer</h3>
 * <p>Previously hardcoded in SpectorMemory.recall() Step 7, now a standalone
 * listener registered with {@link RecallPipeline#addListener}.</p>
 */
public final class LtpReconsolidationListener implements RecallListener {

    /**
     * Minimum interval between auto-LTP reinforcements for the same memory (5 minutes).
     * Prevents runaway LTP from repeated queries hitting the same results.
     */
    private static final long AUTO_LTP_COOLDOWN_MS = SpectorPropertyConstants.DEFAULT_MEMORY_STRENGTH_AUTO_LTP_COOLDOWN_MS;

    private final MemoryIndex index;
    private final PartitionRegistry partitionRegistry;
    private final MemoryWal wal;

    public LtpReconsolidationListener(MemoryIndex index, PartitionRegistry partitionRegistry, MemoryWal wal) {
        this.index = index;
        this.partitionRegistry = partitionRegistry;
        this.wal = wal;
    }

    @Override
    public void onRecallComplete(List<CognitiveResult> results) {
        long nowMs = System.currentTimeMillis();
        for (CognitiveResult r : results) {
            MemoryLocation loc = index.locate(r.id());
            if (loc != null) {
                // #443: resolve the header segment by the memory's colocated partition.
                CognitiveMemoryRouter router = partitionRegistry.routerFor(loc.colocatedPartition());
                MemorySegment segment = router.segmentFor(loc.type());
                if (segment != null) {
                    FixedEngramLayout layout = router.layoutFor(loc.type());

                    if (router.strength() != null && layout != null) {
                        int slotIndex = (int) (loc.offset() / layout.stride());
                        long creationMs = layout.readTimestamp(segment, loc.offset());
                        router.strength().recordRecall(loc.type(), slotIndex, creationMs, nowMs, (byte) 0, 0);

                        long lastAutoLtp = router.strength().readStrengthState(loc.type(), slotIndex).lastAutoLtp();
                        if (nowMs - lastAutoLtp >= AUTO_LTP_COOLDOWN_MS) {
                            router.strength().incrementSpectorRecallCount(loc.type(), slotIndex);
                            router.strength().casStorageStrength(loc.type(), slotIndex,
                                    s -> Math.min(SpectorPropertyConstants.DEFAULT_MEMORY_TWOFACTOR_S_MAX,
                                            s + SpectorPropertyConstants.DEFAULT_MEMORY_AUTO_LTP_STORAGE_INCREMENT));
                            router.strength().writeLastAutoLtp(loc.type(), slotIndex, nowMs);
                        }
                    }
                }

                // Log recall hit for analytics
                wal.append(WalEvent.EventType.RECALL_HIT,
                        index.findIdByOffset(loc.colocatedPartition(), loc.type(), loc.offset()), null);
            }
        }
    }
}
