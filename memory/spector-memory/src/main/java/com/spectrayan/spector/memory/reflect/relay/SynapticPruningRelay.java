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

import com.spectrayan.spector.commons.concurrent.ConcurrentExecutionException;
import com.spectrayan.spector.commons.concurrent.ConcurrentTasks;
import com.spectrayan.spector.commons.concurrent.NativeOsMemory;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.cortex.EpisodicRecordMemory.EpisodicPartition;
import com.spectrayan.spector.memory.hippocampus.TombstoneCompactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * NREM Deep Sleep Synaptic Homeostasis Relay.
 *
 * <p>Prunes weak synaptic connections whose decayed importance falls below threshold
 * and compacts partitions that exceed the tombstone ratio.</p>
 */
public final class SynapticPruningRelay implements SynapticRelay<ReflectSignal> {

    private static final Logger log = LoggerFactory.getLogger(SynapticPruningRelay.class);

    @Override
    public boolean transmit(final ReflectSignal signal) {
        if (signal.partitionManager() == null) {
            return true;
        }

        var handles = signal.partitionManager().snapshot();
        List<EpisodicPartition> allLegacyPartitions = new ArrayList<>();
        for (var handle : handles) {
            if (handle.router() != null && !handle.router().isEpisodicLogMode()) {
                var episodicStore = handle.router().episodic();
                if (episodicStore != null) {
                    allLegacyPartitions.addAll(episodicStore.partitions());
                }
            }
        }

        if (allLegacyPartitions.isEmpty()) {
            return true;
        }

        TombstoneCompactor compactor = new TombstoneCompactor(signal.policy().tombstoneThreshold());
        long nowMs = System.currentTimeMillis();
        float pruneThreshold = signal.policy().decayPruneThreshold();

        // Native POSIX sequential read advise
        for (EpisodicPartition partition : allLegacyPartitions) {
            if (partition.segment() != null && partition.segment().isMapped()) {
                NativeOsMemory.advise(partition.segment(), NativeOsMemory.MADV_SEQUENTIAL);
            }
        }

        int tombstoned = 0;
        try {
            List<Callable<Integer>> pruneTasks = new ArrayList<>(allLegacyPartitions.size());
            for (EpisodicPartition partition : allLegacyPartitions) {
                pruneTasks.add(() -> compactor.pruneDecayed(partition, pruneThreshold, nowMs));
            }
            List<Integer> prunedCounts = ConcurrentTasks.forkJoinAll(pruneTasks);
            for (int p : prunedCounts) tombstoned += p;
        } catch (ConcurrentExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Parallel prune failed, falling back to sequential: {}", e.getMessage());
            for (EpisodicPartition partition : allLegacyPartitions) {
                tombstoned += compactor.pruneDecayed(partition, pruneThreshold, nowMs);
            }
        }
        signal.addTombstoned(tombstoned);

        // Compaction check (sequential partition swaps)
        int compacted = 0;
        for (var handle : handles) {
            if (handle.router() != null && !handle.router().isEpisodicLogMode()) {
                var episodicStore = handle.router().episodic();
                if (episodicStore != null) {
                    for (EpisodicPartition partition : episodicStore.partitions()) {
                        if (compactor.shouldCompact(partition)) {
                            String key = episodicStore.keyForPartition(partition);
                            if (key != null) {
                                EpisodicPartition newPartition = compactor.compact(
                                        partition, episodicStore.partitions().getFirst().path().getParent(), key);
                                if (newPartition != null) {
                                    episodicStore.replacePartition(key, partition, newPartition);
                                    compacted++;
                                    if (partition.segment() != null && partition.segment().isMapped()) {
                                        NativeOsMemory.advise(partition.segment(), NativeOsMemory.MADV_DONTNEED);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        signal.addCompacted(compacted);
        return true;
    }
}
