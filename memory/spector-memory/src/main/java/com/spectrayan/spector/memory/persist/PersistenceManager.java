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
package com.spectrayan.spector.memory.persist;

import com.spectrayan.spector.memory.*;

import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.graph.EntityDirectory;

import com.spectrayan.spector.memory.hebbian.CoActivationRecordMemory;
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.temporal.TemporalChainMemory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.memory.kernel.StorageLayout;
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
public final class PersistenceManager {

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
     * @param coActivationTracker the co-activation tracker
     * @param cognitiveRouter    the cognitive memory router
     * @param wal                the write-ahead log
     */
    public static void flushAndClose(MemoryPersistenceMode persistenceMode,
                              Path persistencePath,
                              Path activePartitionDir,
                              MemoryIndex index,
                              HebbianGraphBase hebbianGraph,
                              TemporalChainMemory temporalChain,
                              EntityDirectory entityDirectory,
                              com.spectrayan.spector.memory.graph.HyperEntityGraphMemory hyperEntityGraph,
                              CoActivationRecordMemory coActivationTracker,
                              com.spectrayan.spector.memory.temporal.TemporalKnowledgeGraph temporalKnowledgeGraph,
                              CognitiveMemoryRouter cognitiveRouter,
                              MemoryWal wal) {

        // ── Phase 1: Persist to disk (DISK mode only) ──
        if (persistenceMode == MemoryPersistenceMode.DISK && persistencePath != null) {
            Path bundlePath = StorageLayout.runtimeBundleFile(persistencePath);

            // 1. MemoryIndex
            saveIndex(index, bundlePath);

            // 2. HebbianGraph
            saveSubsystem("HebbianGraph", () ->
                    hebbianGraph.save(bundlePath));

            // 3. TemporalChain
            saveSubsystem("TemporalChain", () ->
                    temporalChain.save(bundlePath));

            // 5. HyperEntityGraph (if enabled)
            if (hyperEntityGraph != null) {
                saveSubsystem("HyperEntityGraph", () ->
                        hyperEntityGraph.save(bundlePath));
            }

            // 5b. EntityDirectory (ADR-0003 #455 — identity companion)
            if (entityDirectory != null) {
                saveSubsystem("EntityDirectory", () ->
                        entityDirectory.save(bundlePath));
                saveSubsystem("EntityTypeRegistry", () -> {
                    try {
                        entityDirectory.entityTypeRegistry().save(bundlePath);
                    } catch (java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                });
            }

            if (temporalKnowledgeGraph != null) {
                saveSubsystem("RelationTypeRegistry", () -> {
                    try {
                        temporalKnowledgeGraph.predicateRegistry().save(bundlePath);
                    } catch (java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                });
            }

            // 6. CoActivationTracker
            saveSubsystem("CoActivationTracker", () ->
                    coActivationTracker.save(bundlePath));
        }

        // ── Phase 2: Close resources ──
        cognitiveRouter.close();
        wal.close();
        hebbianGraph.close();
        temporalChain.close();
        if (temporalKnowledgeGraph != null) {
            temporalKnowledgeGraph.flush();
            try {
                temporalKnowledgeGraph.close();
            } catch (Exception e) {
                log.error("Failed to close TemporalKnowledgeGraph: {}", e.getMessage(), e);
            }
        }
        coActivationTracker.close();
        if (entityDirectory != null) entityDirectory.close();
        if (hyperEntityGraph != null) hyperEntityGraph.close();
    }

    private static void saveIndex(MemoryIndex index, Path bundlePath) {
        try {
            index.save(bundlePath);
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
