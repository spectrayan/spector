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

import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.graph.EntityDirectory;

import com.spectrayan.spector.memory.graph.HyperEntityGraphMemory;
import com.spectrayan.spector.memory.hebbian.CoActivationRecordMemory;
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.hebbian.HebbianGraphMemory;
import com.spectrayan.spector.memory.error.SpectorWalCorruptionException;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.kernel.Memory;
import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.SystemMemoryId;
import com.spectrayan.spector.memory.kernel.StorageLayout;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.RememberPathway;
import com.spectrayan.spector.memory.sync.CheckpointDaemon;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.memory.sync.WalEvent;
import com.spectrayan.spector.memory.sync.WalRecoveryDispatcher;
import com.spectrayan.spector.memory.temporal.TemporalChainMemory;
import com.spectrayan.spector.memory.temporal.TemporalKnowledgeGraph;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Replays the write-ahead log into freshly-assembled memory subsystems on
 * startup, dispatching shape mutations to their target segments and rebuilding
 * the on-heap {@link MemoryIndex} maps from high-level REMEMBER/FORGET events.
 *
 * <p>Extracted verbatim from {@code SpectorMemoryFactory.performWalRecovery} as
 * part of the #437 god-class decomposition. The recovery algorithm, checkpoint
 * HWM handling and event dispatch are unchanged.</p>
 *
 * @since 1.1.0
 */
final class MemoryWalRecovery {

    private static final Logger log = LoggerFactory.getLogger(MemoryWalRecovery.class);

    private MemoryWalRecovery() {}

    static void recover(
            MemoryWal wal,
            CognitiveMemoryRouter cognitiveRouter,
            MemoryIndex index,
            HebbianGraphBase hebbianGraph,
            TemporalChainMemory temporalChain,
            TemporalKnowledgeGraph temporalKnowledgeGraph,
            EntityDirectory entityDirectory,
            HyperEntityGraphMemory hyperEntityGraph,
            CoActivationRecordMemory coActivationTracker,
            RememberPathway cognitiveTarget,
            Path basePath,
            int activePartitionSeq) {

        if (wal == null || !wal.isPersistent()) {
            return;
        }

        long checkpointHwm = 0;
        if (basePath != null) {
            Path metaPath = StorageLayout.checkpointMeta(basePath);
            if (Files.exists(metaPath)) {
                checkpointHwm = CheckpointDaemon.readCheckpointHwm(metaPath);
                log.info("WAL recovery: loaded checkpoint HWM {}", checkpointHwm);
            }
        }

        List<WalEvent> events = wal.replay(checkpointHwm);
        if (events.isEmpty()) {
            log.info("WAL recovery: no events to replay after HWM {}", checkpointHwm);
            return;
        }

        log.info("WAL recovery: replaying {} events after HWM {}", events.size(), checkpointHwm);

        // 1. Gather all shape-specific Memory instances
        java.util.Map<MemoryId, Memory<?>> memories = new java.util.HashMap<>();

        if (cognitiveRouter != null) {
            if (cognitiveRouter.working() != null) {
                memories.put(cognitiveRouter.working().id(), cognitiveRouter.working());
            }
            if (cognitiveRouter.semantic() != null) {
                memories.put(cognitiveRouter.semantic().id(), cognitiveRouter.semantic());
            }
            if (cognitiveRouter.procedural() != null) {
                memories.put(cognitiveRouter.procedural().id(), cognitiveRouter.procedural());
            }
        }


        // ADR-0003 #456: the directory is the WAL-recovered identity store; GRAPH_ADD_NODE /
        // GRAPH_LINK_MEMORY events (emitted under the directory's id) dispatch here.
        if (entityDirectory != null) {
            memories.put(entityDirectory.id(), entityDirectory);
        }
        // ADR-0003 #460 / #417: the hypergraph is now WAL-recovered (HYPEREDGE_ADD replay).
        if (hyperEntityGraph != null) {
            memories.put(hyperEntityGraph.id(), hyperEntityGraph);
        }
        if (hebbianGraph instanceof HebbianGraphMemory hg) {
            memories.put(hg.id(), hg);
        }
        if (temporalChain != null) {
            memories.put(SystemMemoryId.TEMPORAL_CHAIN.id(), temporalChain);
        }
        if (temporalKnowledgeGraph != null) {
            memories.put(temporalKnowledgeGraph.id(), temporalKnowledgeGraph.backing());
        }

        // Add textDataStore memory if available
        MemoryId textId = SystemMemoryId.CORTEX_TEXT.id();
        if (index.textDataStore() != null) {
            memories.put(textId, index.textDataStore());
        }

        long countBeforeRecovery = 0;
        Memory<?> textMem = memories.get(textId);
        if (textMem instanceof com.spectrayan.spector.memory.kernel.shape.AppendMemory<?> am) {
            countBeforeRecovery = am.appendCursor();
        }

        // 2. Dispatch shape mutations directly to target memory segments
        WalRecoveryDispatcher.recover(wal, memories);

        // 3. Rebuild MemoryIndex on-heap maps from high-level REMEMBER/FORGET/REINFORCE events
        long lastRecordOffset = -1;
        MemoryType lastRecordType = null;
        long lastTextOffset = -1;
        int lastTextLength = -1;
        long currentTextCursor = countBeforeRecovery;

        for (WalEvent event : events) {
            if (event.sequence() <= checkpointHwm) {
                continue;
            }

            try {
                switch (event.type()) {
                    case RECORD_WRITE -> {
                        ByteBuffer buf = java.nio.ByteBuffer.wrap(event.payload());
                        long recordId = buf.getLong();
                        MemoryId targetId = MemoryId.parse(event.memoryId());
                        Memory<?> target = memories.get(targetId);
                        if (target instanceof com.spectrayan.spector.memory.kernel.shape.RecordMemory<?> rm) {
                            lastRecordOffset = rm.recordOffset(recordId);
                            String pathName = targetId.memoryName();
                            try {
                                lastRecordType = MemoryType.valueOf(pathName.toUpperCase(java.util.Locale.ROOT));
                            } catch (IllegalArgumentException e) {
                                // Unknown or legacy record memory name
                            }
                        }
                    }
                    case APPEND -> {
                        MemoryId targetId = MemoryId.parse(event.memoryId());
                        if (SystemMemoryId.CORTEX_TEXT.id().equals(targetId)) {
                            lastTextOffset = currentTextCursor;
                            lastTextLength = event.payload().length;
                            currentTextCursor += 4 + lastTextLength;
                        }
                    }
                    case REMEMBER -> {
                        if (lastRecordOffset != -1 && lastRecordType != null) {
                            // CognitiveRecordLayout has dynamic stride
                            int stride = 164; // default
                            MemoryId targetId = switch (lastRecordType) {
                                case WORKING -> SystemMemoryId.WORKING.id();
                                case SEMANTIC -> SystemMemoryId.SEMANTIC.id();
                                case PROCEDURAL -> SystemMemoryId.PROCEDURAL.id();
                                case EPISODIC -> SystemMemoryId.EPISODIC.id();
                            };
                            Memory<?> target = memories.get(targetId);
                            if (target != null) {
                                stride = target.layout().recordStride();
                            }
                            int storeIndex = (int) (lastRecordOffset / stride);
                            com.spectrayan.spector.memory.index.MemoryIndex.MemoryLocation loc =
                                    new com.spectrayan.spector.memory.index.MemoryIndex.MemoryLocation(
                                            lastRecordType, lastRecordOffset, -1, activePartitionSeq,
                                            lastTextOffset, lastTextLength);

                            String text = "";
                            if (index.textDataStore() != null && lastTextOffset != -1) {
                                try {
                                    text = index.textDataStore().readTextDirect(lastTextOffset, lastTextLength);
                                } catch (Exception e) {
                                    throw new SpectorWalCorruptionException("Failed to read text from DataStore during WAL index recovery", e);
                                }
                            }

                            index.register(event.memoryId(), loc, text, MemorySource.OBSERVED, new String[0]);
                        }
                    }
                    case FORGET -> {
                        index.remove(event.memoryId());
                    }
                    default -> {}
                }
            } catch (Exception e) {
                throw new SpectorWalCorruptionException("WAL recovery index sync failed for event seq=" 
                        + event.sequence() + ", type=" + event.type(), e);
            }
        }
    }
}
