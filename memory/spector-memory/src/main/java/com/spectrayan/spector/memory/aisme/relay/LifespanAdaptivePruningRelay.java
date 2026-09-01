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
package com.spectrayan.spector.memory.aisme.relay;

import com.spectrayan.spector.commons.concurrent.ConcurrentExecutionException;
import com.spectrayan.spector.commons.concurrent.ConcurrentTasks;
import com.spectrayan.spector.commons.concurrent.NativeOsMemory;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.aisme.lifespan.LifespanRetentionController;
import com.spectrayan.spector.memory.cortex.EpisodicRecordMemory.EpisodicPartition;
import com.spectrayan.spector.memory.hippocampus.TombstoneCompactor;
import com.spectrayan.spector.memory.reflect.relay.ReflectSignal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Sleep Reflection Relay executing lifespan-adaptive forgetting and capacity-driven synaptic pruning.
 *
 * <p>Dynamically computes the retention threshold \(\tau(t)\) as a function of the agent's
 * cumulative operational lifespan epochs \(t\) and current episodic volume pressure \(V(t) / V_{\text{target}}\).
 * Evaluates autobiographical tiers, ensuring permanent protection for core milestones while tombstoning
 * ephemeral observations whose importance falls below \(\tau(t)\).</p>
 */
public final class LifespanAdaptivePruningRelay implements SynapticRelay<ReflectSignal> {

    private static final Logger log = LoggerFactory.getLogger(LifespanAdaptivePruningRelay.class);

    @Override
    public boolean transmit(final ReflectSignal signal) {
        if (signal == null) {
            return true;
        }

        LifespanRetentionController controller = signal.lifespanController();
        if (controller == null) {
            log.debug("LifespanRetentionController not present on ReflectSignal; skipping lifespan pruning.");
            return true;
        }

        long epoch = controller.advanceEpoch();

        if (signal.partitionManager() == null) {
            float tau = controller.currentTau();
            signal.setEffectiveLifespanTau(tau);
            log.info("Advanced lifespan epoch to {} (tau={}) with null partition manager", epoch, tau);
            return true;
        }

        var handles = signal.partitionManager().snapshot();
        List<EpisodicPartition> partitions = new ArrayList<>();
        long totalVolume = 0L;

        for (var handle : handles) {
            if (handle.router() != null && !handle.router().isEpisodicLogMode()) {
                var episodicStore = handle.router().episodic();
                if (episodicStore != null) {
                    for (EpisodicPartition p : episodicStore.partitions()) {
                        partitions.add(p);
                        totalVolume += Math.max(0, p.count() - p.tombstoneCount());
                    }
                }
            }
        }

        controller.updateVolume(totalVolume);
        float tau = controller.computeCurrentTau(totalVolume);
        signal.setEffectiveLifespanTau(tau);

        if (partitions.isEmpty()) {
            log.info("Lifespan adaptive pruning epoch {}: activeVolume={}, tau={}", epoch, totalVolume, tau);
            return true;
        }

        TombstoneCompactor compactor = new TombstoneCompactor(signal.policy().tombstoneThreshold());
        long nowMs = System.currentTimeMillis();

        // Native POSIX sequential read advise
        for (EpisodicPartition partition : partitions) {
            if (partition.segment() != null && partition.segment().isMapped()) {
                NativeOsMemory.advise(partition.segment(), NativeOsMemory.MADV_SEQUENTIAL);
            }
        }

        int tombstoned = 0;
        try {
            List<Callable<Integer>> pruneTasks = new ArrayList<>(partitions.size());
            for (EpisodicPartition partition : partitions) {
                pruneTasks.add(() -> compactor.pruneDecayed(partition, tau, nowMs));
            }
            List<Integer> prunedCounts = ConcurrentTasks.forkJoinAll(pruneTasks);
            for (int p : prunedCounts) {
                tombstoned += p;
            }
        } catch (ConcurrentExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Parallel lifespan prune failed, falling back to sequential: {}", e.getMessage());
            for (EpisodicPartition partition : partitions) {
                tombstoned += compactor.pruneDecayed(partition, tau, nowMs);
            }
        }

        signal.addTombstoned(tombstoned);
        signal.addEphemeralPruned(tombstoned);

        // Compaction check (sequential partition swaps)
        int compacted = 0;
        for (var handle : handles) {
            if (handle.router() != null && !handle.router().isEpisodicLogMode()) {
                var episodicStore = handle.router().episodic();
                if (episodicStore != null) {
                    for (EpisodicPartition partition : episodicStore.partitions()) {
                        if (compactor.shouldCompact(partition)) {
                            String key = episodicStore.keyForPartition(partition);
                            if (key != null && !episodicStore.partitions().isEmpty()) {
                                EpisodicPartition newPartition = compactor.compact(
                                        partition, episodicStore.partitions().getFirst().path().getParent(), key);
                                if (newPartition != null) {
                                    episodicStore.replacePartition(key, partition, newPartition);
                                    compacted++;
                                }
                            }
                        }
                    }
                }
            }
        }
        signal.addCompacted(compacted);

        log.info("Lifespan adaptive pruning complete at epoch {}: volume={}, tau={}, pruned={}, compacted={}",
                epoch, totalVolume, tau, tombstoned, compacted);

        return true;
    }
}
