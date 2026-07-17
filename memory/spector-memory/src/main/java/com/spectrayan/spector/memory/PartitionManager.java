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

import com.spectrayan.spector.memory.cortex.EpisodicMemoryStore;
import com.spectrayan.spector.memory.cortex.ProceduralMemoryStore;
import com.spectrayan.spector.memory.cortex.SemanticMemoryStore;
import com.spectrayan.spector.memory.cortex.TierRouter;
import com.spectrayan.spector.memory.cortex.WorkingMemoryStore;
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.pipeline.CognitiveIngestionTarget;
import com.spectrayan.spector.memory.temporal.TemporalChain;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorServerException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Manages colocated partition directories for DISK persistence mode.
 *
 * <p>Encapsulates partition discovery, creation, and rolling. Owns the volatile
 * {@code tierRouter} and {@code activePartitionDir} fields, ensuring all
 * synchronization is centralized in one place.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>Partition rolls are synchronized on an internal lock. The volatile
 * {@code tierRouter} field ensures concurrent readers see the latest swap
 * without acquiring the lock. This provides safe publication for the
 * happens-before guarantee required by the ingestion pipeline.</p>
 *
 * @see StorageLayout
 */
final class PartitionManager {

    private static final Logger log = LoggerFactory.getLogger(PartitionManager.class);

    private final Path basePath;
    private final int quantizedVecBytes;
    private final int semanticCapacity;
    private final int episodicPartitionCapacity;
    private final int proceduralCapacity;
    private final MemoryIndex index;
    private final HebbianGraphBase hebbianGraph;
    private final TemporalChain temporalChain;
    private final CognitiveIngestionTarget cognitiveTarget;

    private volatile TierRouter tierRouter;
    private volatile Path activePartitionDir;
    private final java.util.concurrent.locks.ReentrantLock partitionRollLock = new java.util.concurrent.locks.ReentrantLock();

    PartitionManager(Path basePath,
                     int quantizedVecBytes,
                     int semanticCapacity,
                     int episodicPartitionCapacity,
                     int proceduralCapacity,
                     TierRouter initialRouter,
                     Path initialPartitionDir,
                     MemoryIndex index,
                     HebbianGraphBase hebbianGraph,
                     TemporalChain temporalChain,
                     CognitiveIngestionTarget cognitiveTarget) {
        this.basePath = basePath;
        this.quantizedVecBytes = quantizedVecBytes;
        this.semanticCapacity = semanticCapacity;
        this.episodicPartitionCapacity = episodicPartitionCapacity;
        this.proceduralCapacity = proceduralCapacity;
        this.tierRouter = initialRouter;
        this.activePartitionDir = initialPartitionDir;
        this.index = index;
        this.hebbianGraph = hebbianGraph;
        this.temporalChain = temporalChain;
        this.cognitiveTarget = cognitiveTarget;
    }

    /** Returns the current tier router (volatile read — safe for concurrent access). */
    TierRouter tierRouter() { return tierRouter; }

    /** Returns the active partition directory (volatile read). */
    Path activePartitionDir() { return activePartitionDir; }

    /**
     * Discovers existing partitions or creates partition 000 if none exist.
     *
     * @param basePath the memory persistence root
     * @return path to the active (latest) partition directory
     */
    static Path discoverOrCreatePartition(Path basePath) throws IOException {
        Path partitionsDir = StorageLayout.partitionsDir(basePath);
        Files.createDirectories(partitionsDir);

        // Scan for existing partition directories
        Path latestPartition = null;
        int maxSeq = -1;
        try (var stream = Files.newDirectoryStream(partitionsDir)) {
            for (Path dir : stream) {
                if (!Files.isDirectory(dir)) continue;
                String name = dir.getFileName().toString();
                if (StorageLayout.isPartitionDir(name)) {
                    int seq = StorageLayout.parsePartitionSeqNo(name);
                    if (seq > maxSeq) {
                        maxSeq = seq;
                        latestPartition = dir;
                    }
                }
            }
        }

        if (latestPartition != null) {
            return latestPartition;
        }

        // No partitions found → create partition 000
        long epochSecs = Instant.now().getEpochSecond();
        Path newPartition = StorageLayout.partitionDir(basePath, 0, epochSecs);
        Files.createDirectories(newPartition);
        log.info("Created initial partition: {}", newPartition.getFileName());
        return newPartition;
    }

    /**
     * Rolls to a new colocated partition directory.
     *
     * <p>Called automatically when a tier store reaches capacity during ingestion.
     * Creates a new partition directory, fresh tier stores, and atomically swaps
     * the TierRouter so subsequent writes go to the new partition.</p>
     *
     * <p>Thread-safe: synchronized on internal lock so concurrent ingestion threads
     * see a consistent swap.</p>
     */
    void rollPartition() {
        partitionRollLock.lock();
        try {
            if (basePath == null) {
                log.warn("Cannot roll partition — no basePath (IN_MEMORY mode)");
                return;
            }

            try {
                // Determine next sequence number
                Path partitionsDir = StorageLayout.partitionsDir(basePath);
                int maxSeq = -1;
                try (var stream = Files.newDirectoryStream(partitionsDir)) {
                    for (Path dir : stream) {
                        if (Files.isDirectory(dir)
                                && StorageLayout.isPartitionDir(dir.getFileName().toString())) {
                            maxSeq = Math.max(maxSeq,
                                    StorageLayout.parsePartitionSeqNo(
                                            dir.getFileName().toString()));
                        }
                    }
                }

                int nextSeq = maxSeq + 1;
                long epochSecs = Instant.now().getEpochSecond();
                Path newPartition = StorageLayout.partitionDir(basePath, nextSeq, epochSecs);
                Files.createDirectories(newPartition);

                // Create fresh tier stores in new partition
                EpisodicMemoryStore newEpisodic = new EpisodicMemoryStore(
                        StorageLayout.episodicMem(newPartition),
                        quantizedVecBytes, episodicPartitionCapacity);

                ProceduralMemoryStore newProcedural = new ProceduralMemoryStore(
                        quantizedVecBytes, proceduralCapacity,
                        StorageLayout.proceduralMem(newPartition));

                SemanticMemoryStore newSemantic = new SemanticMemoryStore(
                        quantizedVecBytes, semanticCapacity,
                        StorageLayout.semanticMem(newPartition));

                // Preserve working memory (global, not partitioned)
                WorkingMemoryStore workingStore = tierRouter.working();

                // Flush index + graphs to runtime/ before rolling
                flushGlobalState();

                // Atomic swap
                this.tierRouter = new TierRouter(workingStore, newEpisodic,
                        newSemantic, newProcedural);
                this.activePartitionDir = newPartition;

                // Update ingestion target's router reference
                cognitiveTarget.updateTierRouter(this.tierRouter);

                log.info("Rolled to new partition: {} (seq={}, semantic capacity={})",
                        newPartition.getFileName(), nextSeq, semanticCapacity);

            } catch (IOException e) {
                throw new SpectorServerException(ErrorCode.INTERNAL_ERROR,
                        "Failed to roll partition: " + e.getMessage(), e);
            }
        } finally {
            partitionRollLock.unlock();
        }
    }

    /**
     * Flushes index and graphs to the runtime/ directory (V3 layout).
     *
     * <p>Called before a partition roll to ensure global structures are
     * persisted. Entity graph flush is included (was missing in V2).</p>
     */
    private void flushGlobalState() {
        try {
            index.save(StorageLayout.indexMidxRuntime(basePath));
            log.info("Flushed MemoryIndex to runtime/ during partition roll");
        } catch (Exception e) {
            log.error("Failed to flush MemoryIndex during partition roll: {}",
                    e.getMessage(), e);
        }
        try {
            hebbianGraph.save(StorageLayout.hebbianGraphRuntime(basePath));
        } catch (Exception e) {
            log.error("Failed to flush HebbianGraph during partition roll: {}",
                    e.getMessage(), e);
        }
        try {
            temporalChain.save(StorageLayout.temporalChainRuntime(basePath));
        } catch (Exception e) {
            log.error("Failed to flush TemporalChain during partition roll: {}",
                    e.getMessage(), e);
        }
    }
}
