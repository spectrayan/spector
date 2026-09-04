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
import com.spectrayan.spector.memory.synapse.ActRActivation;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;

import java.lang.foreign.MemorySegment;
import java.util.List;

/**
 * LTP Reconsolidation listener — records recall timestamps and WAL events.
 *
 * <h3>Biological Analog: Long-Term Potentiation (LTP)</h3>
 * <p>Each time a memory is successfully recalled, its synaptic strength increases.
 * In Spector's model, this manifests as:</p>
 * <ul>
 *   <li><b>ACT-R recall timestamps</b>: recorded in the 4-slot ring buffer
 *       (V3 layouts only) via {@link ActRActivation#recordRecall}. These
 *       enable the full ACT-R base-level activation computation:
 *       {@code B_i = ln(Σ t_j^{-d})}.</li>
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
    private static final long AUTO_LTP_COOLDOWN_MS = SpectorPropertyConstants.DEFAULT_MEMORY_AUDIT_AUTO_LTP_COOLDOWN_MS;

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
                    CognitiveRecordLayout layout = router.layoutFor(loc.type());

                    if (router.audit() != null) {
                        int slotIndex = (int) (loc.offset() / layout.stride());
                        long creationMs = layout.readTimestamp(segment, loc.offset());
                        router.audit().recordRecall(loc.type(), slotIndex, creationMs, nowMs, (byte) 0, 0);

                        long lastAutoLtp = router.audit().readAuditRecord(loc.type(), slotIndex).lastAutoLtp();
                        if (nowMs - lastAutoLtp >= AUTO_LTP_COOLDOWN_MS) {
                            router.audit().incrementSpectorRecallCount(loc.type(), slotIndex);
                            router.audit().casStorageStrength(loc.type(), slotIndex,
                                    s -> Math.min(SpectorPropertyConstants.DEFAULT_MEMORY_TWOFACTOR_S_MAX,
                                            s + SpectorPropertyConstants.DEFAULT_MEMORY_AUTO_LTP_STORAGE_INCREMENT));
                            long strengthOff = router.audit().strengthOffset(loc.type(), slotIndex);
                            com.spectrayan.spector.memory.kernel.layout.StrengthLayout.INSTANCE.writeLastAutoLtp(router.audit().segment(), strengthOff, nowMs);
                        }
                    } else if (layout.headerLayout().version() >= 3) {
                        long creationMs = layout.readTimestamp(segment, loc.offset());

                        // Record recall timestamp for ACT-R base-level activation.
                        // This captures the spacing effect: spaced recalls produce higher
                        // activation than massed recalls, without inflating agent_recall_count.
                        ActRActivation.recordRecall(segment, loc.offset(), creationMs, nowMs);

                        // Auto-LTP: passively reinforce memories that surface in results,
                        // subject to a cooldown to prevent inflation from repeated queries.
                        long lastAutoLtp = layout.readLastAutoLtp(segment, loc.offset());
                        if (nowMs - lastAutoLtp >= AUTO_LTP_COOLDOWN_MS) {
                            // Atomically increment spector-internal recall count
                            layout.incrementSpectorRecallCount(segment, loc.offset());

                            // Update storage strength using Two-Factor formula:
                            // S(t+1) = S(t) + α·(1/R(t)) where R(t) = retrieval strength
                            // Simplified: each auto-LTP adds a small fixed increment (0.05)
                            float currentStrength = layout.readStorageStrength(segment, loc.offset());
                            float newStrength = Math.min(SpectorPropertyConstants.DEFAULT_MEMORY_TWOFACTOR_S_MAX,
                                    currentStrength + SpectorPropertyConstants.DEFAULT_MEMORY_AUTO_LTP_STORAGE_INCREMENT);
                            layout.writeStorageStrength(segment, loc.offset(), newStrength);

                            // Record cooldown timestamp
                            layout.writeLastAutoLtp(segment, loc.offset(), nowMs);
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
