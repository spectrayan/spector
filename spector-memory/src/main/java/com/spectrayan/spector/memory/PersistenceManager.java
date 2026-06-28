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
package com.spectrayan.spector.memory;

import com.spectrayan.spector.memory.cortex.TierRouter;
import com.spectrayan.spector.memory.graph.EntityGraph;
import com.spectrayan.spector.memory.hebbian.CoActivationTracker;
import com.spectrayan.spector.memory.hebbian.HebbianGraph;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.temporal.TemporalChain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Manages persistence lifecycle — flush-on-close and resource cleanup.
 *
 * <p>Encapsulates the save-ordering logic that ensures data is flushed in the
 * correct dependency order before subsystem resources are released:</p>
 * <ol>
 *   <li><b>MemoryIndex</b> — runtime/ directory (V3 layout)</li>
 *   <li><b>HebbianGraph</b> — runtime/ directory</li>
 *   <li><b>TemporalChain</b> — runtime/ directory</li>
 *   <li><b>EntityGraph</b> — runtime/ directory (if enabled)</li>
 *   <li><b>CoActivationTracker</b> — runtime/ directory</li>
 *   <li><b>CoActivationTracker</b> — always global</li>
 * </ol>
 *
 * <p>After persistence, subsystem resources (tier stores, WAL, graphs) are closed
 * in order.</p>
 */
final class PersistenceManager {

    private static final Logger log = LoggerFactory.getLogger(PersistenceManager.class);

    private PersistenceManager() {} // static utility

    /**
     * Flushes all subsystems to disk and closes resources.
     *
     * @param persistenceMode    the persistence mode (DISK or IN_MEMORY)
     * @param persistencePath    the base persistence path (may be null for IN_MEMORY)
     * @param activePartitionDir the active partition directory (may be null)
     * @param index              the memory index
     * @param hebbianGraph       the Hebbian graph
     * @param temporalChain      the temporal chain
     * @param entityGraph        the entity graph (nullable)
     * @param coActivationTracker the co-activation tracker
     * @param tierRouter         the tier router
     * @param wal                the write-ahead log
     */
    static void flushAndClose(MemoryPersistenceMode persistenceMode,
                              Path persistencePath,
                              Path activePartitionDir,
                              MemoryIndex index,
                              HebbianGraph hebbianGraph,
                              TemporalChain temporalChain,
                              EntityGraph entityGraph,
                              CoActivationTracker coActivationTracker,
                              TierRouter tierRouter,
                              MemoryWal wal) {

        // ── Phase 1: Persist to disk (DISK mode only) ──
        if (persistenceMode == MemoryPersistenceMode.DISK && persistencePath != null) {

            // 1. MemoryIndex: runtime/ (V3 layout)
            saveIndex(index, persistencePath);

            // 2. HebbianGraph: runtime/
            saveSubsystem("HebbianGraph", () ->
                    hebbianGraph.save(StorageLayout.hebbianGraphRuntime(persistencePath)));

            // 3. TemporalChain: runtime/
            saveSubsystem("TemporalChain", () ->
                    temporalChain.save(StorageLayout.temporalChainRuntime(persistencePath)));

            // 4. EntityGraph: runtime/ (if enabled)
            if (entityGraph != null) {
                saveSubsystem("EntityGraph", () ->
                        entityGraph.save(StorageLayout.entityGraphRuntime(persistencePath)));
            }

            // 5. CoActivationTracker: runtime/
            saveSubsystem("CoActivationTracker", () ->
                    coActivationTracker.save(
                            StorageLayout.coactivationTracker(persistencePath)));
        }

        // ── Phase 2: Close resources ──
        tierRouter.close();
        wal.close();
        hebbianGraph.close();
        temporalChain.close();
        coActivationTracker.close();
        if (entityGraph != null) entityGraph.close();
    }

    private static void saveIndex(MemoryIndex index, Path persistencePath) {
        try {
            Path indexPath = StorageLayout.indexMidxRuntime(persistencePath);
            index.save(indexPath);
        } catch (Exception e) {
            log.error("Failed to save MemoryIndex on close: {}", e.getMessage(), e);
        }
    }

    private static void saveSubsystem(String name, Runnable saver) {
        try {
            saver.run();
        } catch (Exception e) {
            log.error("Failed to save {} on close: {}", name, e.getMessage(), e);
        }
    }
}
