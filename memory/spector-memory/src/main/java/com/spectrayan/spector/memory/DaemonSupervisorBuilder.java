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

import com.spectrayan.spector.commons.concurrent.DaemonPolicy;
import com.spectrayan.spector.commons.concurrent.DaemonSupervisor;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.kernel.StorageLayout;
import com.spectrayan.spector.memory.sync.CheckpointDaemon;
import com.spectrayan.spector.memory.sync.MemoryWal;

import java.nio.file.Path;

/**
 * Assembles the background checkpoint daemon and its {@link DaemonSupervisor}
 * (DISK mode only, when a checkpoint interval is configured).
 *
 * <p>Extracted verbatim from {@code SpectorMemoryFactory.assemble} as part of the
 * #437 god-class decomposition. The daemon wiring, index save path resolution and
 * scheduling policy are unchanged. Both fields are {@code null} when checkpointing
 * is not enabled, exactly as before.</p>
 *
 * @since 1.1.0
 */
final class DaemonSupervisorBuilder {

    private DaemonSupervisorBuilder() {}

    /** Immutable holder for the checkpoint daemon and its supervisor (both may be null). */
    record DaemonBundle(
            CheckpointDaemon checkpointDaemon,
            DaemonSupervisor daemonSupervisor
    ) {}

    static DaemonBundle build(
            SpectorMemoryBuilder builder,
            CognitiveCortexBuilder.CortexFoundation cortex,
            BiologicalSubsystemsBuilder.BiologicalSubsystems bio,
            CognitiveGraphBuilder.CognitiveGraphs graphs,
            MemoryIndex index,
            MemoryWal wal) {

        boolean isDisk = cortex.isDisk();
        Path basePath = cortex.basePath();
        Path resolvedPartitionDir = cortex.resolvedPartitionDir();

        //  Daemon Supervisor + Checkpoint Daemon  (DISK mode only)
        CheckpointDaemon checkpointDaemon;
        DaemonSupervisor daemonSupervisor;
        if (isDisk && basePath != null && builder.checkpointIntervalSeconds > 0) {
            Path indexSavePath = resolvedPartitionDir != null
                    ? resolvedPartitionDir.resolve(StorageLayout.FILE_INDEX)
                    : StorageLayout.indexMidxRuntime(basePath);
            java.lang.foreign.MemorySegment ckptSlice = cortex.useBundleMode() && cortex.runtimeBundle() != null
                    ? cortex.runtimeBundle().regionSegment(com.spectrayan.spector.memory.kernel.bundle.RegionId.CHECKPOINT)
                    : null;
            checkpointDaemon = new CheckpointDaemon(
                    cortex.cognitiveRouter(), wal,
                    StorageLayout.checkpointMeta(basePath),
                    index, indexSavePath,
                    graphs.hebbianGraph(), graphs.temporalChain(),
                    graphs.entityDirectory(), graphs.hyperEntityGraph(), bio.coActivationTracker(),
                    graphs.temporalKnowledgeGraph(),
                    resolvedPartitionDir, basePath, ckptSlice);
            daemonSupervisor = new DaemonSupervisor("memory");
            daemonSupervisor.schedule(
                    "checkpoint",
                    checkpointDaemon::checkpoint,
                    java.time.Duration.ofSeconds(builder.checkpointIntervalSeconds),
                    DaemonPolicy.CRITICAL);
        } else {
            checkpointDaemon = null;
            daemonSupervisor = null;
        }

        return new DaemonBundle(checkpointDaemon, daemonSupervisor);
    }
}
