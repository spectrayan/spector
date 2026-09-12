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
import com.spectrayan.spector.kernel.api.HeaderCursor;
import com.spectrayan.spector.kernel.api.MemoryLocation;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.PartitionRegistry;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.sync.WalEvent;

import java.util.List;

/**
 * LTP Reconsolidation listener — records recall timestamps and WAL events.
 *
 * <h3>Biological Analog: Long-Term Potentiation (LTP)</h3>
 * <p>Each time a memory is successfully recalled, its synaptic strength increases.
 * In Spector's model, this manifests as:</p>
 * <ul>
 *   <li><b>ACT-R recall timestamps</b>: recorded in the 8-slot ring buffer in
 *       the strength region. These enable the full ACT-R base-level activation
 *       computation: {@code B_i = ln(Σ t_j^{-d})}.</li>
 *   <li><b>Recall count</b>: incremented only on explicit {@code reinforce()}
 *       calls to prevent inflation from passive retrieval.</li>
 * </ul>
 *
 * <h3>Recall Listener Hook</h3>
 * <p>Registered with {@code RecallPipeline#addListener}, operating strictly via
 * {@link HeaderCursor} with zero raw segment access (R6.4).</p>
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
                // #443: resolve the header cursor by the memory's colocated partition.
                CognitiveMemoryRouter router = partitionRegistry.routerFor(loc.colocatedPartition());
                if (router != null) {
                    try (HeaderCursor cursor = router.cursor(loc.type())) {
                        if (cursor != null) {
                            cursor.seekOffset(loc.offset());
                            long creationMs = cursor.timestampMs();
                            cursor.recordActRRecall(creationMs, nowMs);

                            long lastAutoLtp = cursor.lastAccessEpochMs();
                            if (nowMs - lastAutoLtp >= AUTO_LTP_COOLDOWN_MS) {
                                cursor.spectorRecallCount(cursor.spectorRecallCount() + 1);
                                cursor.updateStorageStrength(s -> Math.min(SpectorPropertyConstants.DEFAULT_MEMORY_TWOFACTOR_S_MAX,
                                        s + SpectorPropertyConstants.DEFAULT_MEMORY_AUTO_LTP_STORAGE_INCREMENT));
                                cursor.lastAccessEpochMs(nowMs);
                            }
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
