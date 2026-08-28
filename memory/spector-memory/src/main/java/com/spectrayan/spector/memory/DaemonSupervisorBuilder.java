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

import com.spectrayan.spector.memory.graph.GraphEnrichmentDaemon;
import com.spectrayan.spector.memory.graph.NoOpEntityExtractor;

import java.nio.file.Path;

/**
 * Assembles the background checkpoint and graph enrichment daemons and their {@link DaemonSupervisor}
 * (DISK mode only, when daemons are configured).
 *
 * @since 1.1.0
 */
final class DaemonSupervisorBuilder {

    private DaemonSupervisorBuilder() {}

    /** Immutable holder for the checkpoint daemon, graph enrichment daemon, and supervisor. */
    record DaemonBundle(
            CheckpointDaemon checkpointDaemon,
            GraphEnrichmentDaemon graphEnrichmentDaemon,
            DaemonSupervisor daemonSupervisor
    ) {}

    static DaemonBundle build(
            SpectorMemoryBuilder builder,
            CognitiveCortexBuilder.CortexFoundation cortex,
            BiologicalSubsystemsBuilder.BiologicalSubsystems bio,
            CognitiveGraphBuilder.CognitiveGraphs graphs,
            MemoryIndex index,
            MemoryWal wal,
            WanderPathway wanderPathway,
            PartitionManager partitionManager) {

        boolean isDisk = cortex.isDisk();
        Path basePath = cortex.basePath();
        Path resolvedPartitionDir = cortex.resolvedPartitionDir();

        //  Graph Enrichment Daemon
        GraphEnrichmentDaemon graphEnrichmentDaemon;
        if (graphs.entityExtractor() != null
                && !(graphs.entityExtractor() instanceof NoOpEntityExtractor)
                && graphs.entityDirectory() != null) {
            graphEnrichmentDaemon = new GraphEnrichmentDaemon(
                    index,
                    graphs.entityExtractor(),
                    graphs.entityDirectory(),
                    graphs.hyperEntityGraph(),
                    graphs.temporalKnowledgeGraph());
        } else {
            graphEnrichmentDaemon = null;
        }

        //  Daemon Supervisor + Checkpoint Daemon  (DISK mode only)
        CheckpointDaemon checkpointDaemon;
        DaemonSupervisor daemonSupervisor;
        if (isDisk && basePath != null) {
            daemonSupervisor = new DaemonSupervisor("memory");

            if (builder.checkpointIntervalSeconds > 0) {
                java.lang.foreign.MemorySegment ckptSlice = cortex.useBundleMode() && cortex.runtimeBundle() != null
                        ? cortex.runtimeBundle().regionSegment(com.spectrayan.spector.memory.kernel.bundle.RegionId.CHECKPOINT)
                        : null;
                Path bundlePath = cortex.runtimeBundle() != null ? cortex.runtimeBundle().bundlePath() : null;
                checkpointDaemon = new CheckpointDaemon(
                        cortex.cognitiveRouter(), wal,
                        bundlePath,
                        index, null,
                        graphs.hebbianGraph(), graphs.temporalChain(),
                        graphs.entityDirectory(), graphs.hyperEntityGraph(), bio.coActivationTracker(),
                        graphs.temporalKnowledgeGraph(),
                        resolvedPartitionDir, basePath, ckptSlice);
                // Deprecated: Checkpointing is now scheduled and managed exclusively by Quartz CheckpointJob (#683)
                // daemonSupervisor.schedule("checkpoint", checkpointDaemon::checkpoint,
                //         java.time.Duration.ofSeconds(builder.checkpointIntervalSeconds), DaemonPolicy.CRITICAL);
            } else {
                checkpointDaemon = null;
            }

            if (graphEnrichmentDaemon != null) {
                // Deprecated: Graph enrichment is now scheduled and managed exclusively by Quartz GraphEnrichmentJob (#683)
                // daemonSupervisor.schedule("graph-enricher", graphEnrichmentDaemon::enrichPending,
                //         java.time.Duration.ofSeconds(30), DaemonPolicy.DEFAULT);
            }

            if (wanderPathway != null && builder.aismeConfig != null && builder.aismeConfig.enabled() && builder.aismeConfig.enableDmnSpontaneous()) {
                // Deprecated: DMN spontaneous wandering is now scheduled and managed exclusively by Quartz DmnWanderingJob (#683)
                // com.spectrayan.spector.memory.aisme.dmn.DmnSpontaneousDaemon dmnDaemon =
                //         new com.spectrayan.spector.memory.aisme.dmn.DmnSpontaneousDaemon(
                //                 wanderPathway, partitionManager, System::currentTimeMillis);
                // daemonSupervisor.schedule("dmn-wandering", dmnDaemon,
                //         java.time.Duration.ofSeconds(Math.max(10, builder.aismeConfig.dmnIdleIntervalSeconds())), DaemonPolicy.DEFAULT);
            }
        } else {
            checkpointDaemon = null;
            daemonSupervisor = null;
        }

        return new DaemonBundle(checkpointDaemon, graphEnrichmentDaemon, daemonSupervisor);
    }
}
