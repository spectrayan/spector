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

import com.spectrayan.spector.memory.cortex.EpisodicRecordMemory;
import com.spectrayan.spector.memory.cortex.PartitionHandle;
import com.spectrayan.spector.memory.cortex.PartitionRegistry;
import com.spectrayan.spector.memory.cortex.ProceduralRecordMemory;
import com.spectrayan.spector.memory.cortex.SemanticRecordMemory;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.TextAppendMemory;
import com.spectrayan.spector.memory.cortex.WorkingRecordMemory;
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.pipeline.CognitiveIngestionTarget;
import com.spectrayan.spector.memory.temporal.TemporalChainMemory;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorServerException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import com.spectrayan.spector.memory.kernel.StorageLayout;
import java.time.Instant;

/**
 * Manages colocated partition directories for DISK persistence mode and owns the
 * partition registry (issue #443, Phase 1).
 *
 * <p>Encapsulates partition discovery, creation, and rolling. Holds a
 * copy-on-write {@link #registry} — a {@code volatile} immutable
 * {@code List<PartitionHandle>} whose last element is the single writable/active
 * partition; all earlier handles are frozen, read-only, and remain OPEN so recall
 * can fan out across them. Keeping frozen stores open is the fix for the pre-#443
 * arena/mmap leak.</p>
 *
 * <h3>Roll handshake</h3>
 * <p>On {@link #rollPartition()} the manager: (1) creates a new partition dir with
 * fresh tier stores and a fresh {@code text.dat}; (2) freezes the current active
 * handle (kept open); (3) publishes a new immutable snapshot with a single volatile
 * store; (4) atomically points the ingestion target at the new router, the new text
 * store and the new active sequence, and updates the index's active-partition seq.
 * A missed field here would reintroduce split-brain, so all four updates happen
 * together under {@link #partitionRollLock}.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>Frozen handles are immutable and read-only — no lock is required to read them.
 * Readers take a single volatile {@link #snapshot()} read and iterate that fixed list.
 * Rolls hold {@link #partitionRollLock} for the create-then-publish sequence. Never
 * uses {@code synchronized}.</p>
 *
 * @see StorageLayout
 * @see PartitionHandle
 */
final class PartitionManager implements PartitionRegistry, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PartitionManager.class);

    private final Path basePath;
    private final int quantizedVecBytes;
    private final int semanticCapacity;
    private final int episodicPartitionCapacity;
    private final int proceduralCapacity;
    private final MemoryIndex index;
    private final HebbianGraphBase hebbianGraph;
    private final TemporalChainMemory temporalChain;
    private final CognitiveIngestionTarget cognitiveTarget;
    private final DataEncryptor encryptor;

    /** Copy-on-write registry: immutable list, last element = active. Never null. */
    private volatile List<PartitionHandle> registry;
    private final java.util.concurrent.locks.ReentrantLock partitionRollLock =
            new java.util.concurrent.locks.ReentrantLock();
    private final java.util.concurrent.atomic.AtomicBoolean closed =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    PartitionManager(Path basePath,
                     int quantizedVecBytes,
                     int semanticCapacity,
                     int episodicPartitionCapacity,
                     int proceduralCapacity,
                     CognitiveMemoryRouter initialRouter,
                     Path initialPartitionDir,
                     TextAppendMemory initialText,
                     int initialSeq,
                     MemoryIndex index,
                     HebbianGraphBase hebbianGraph,
                     TemporalChainMemory temporalChain,
                     CognitiveIngestionTarget cognitiveTarget,
                     DataEncryptor encryptor) {
        this.basePath = basePath;
        this.quantizedVecBytes = quantizedVecBytes;
        this.semanticCapacity = semanticCapacity;
        this.episodicPartitionCapacity = episodicPartitionCapacity;
        this.proceduralCapacity = proceduralCapacity;
        this.index = index;
        this.hebbianGraph = hebbianGraph;
        this.temporalChain = temporalChain;
        this.cognitiveTarget = cognitiveTarget;
        this.encryptor = encryptor != null ? encryptor : DataEncryptor.NOOP;

        PartitionHandle initial = new PartitionHandle(
                initialSeq, initialPartitionDir, initialRouter, initialText, true);
        this.registry = List.of(initial);
    }

    // ══════════════════════════════════════════════════════════════
    // PartitionRegistry (read side — used by recall + direct-resolve)
    // ══════════════════════════════════════════════════════════════

    @Override
    public List<PartitionHandle> snapshot() {
        return registry; // single volatile read of an immutable list
    }

    @Override
    public PartitionHandle handleFor(int seq) {
        List<PartitionHandle> snap = registry;
        for (int i = snap.size() - 1; i >= 0; i--) {
            PartitionHandle h = snap.get(i);
            if (h.seq() == seq) return h;
        }
        return null;
    }

    @Override
    public CognitiveMemoryRouter activeRouter() {
        List<PartitionHandle> snap = registry;
        return snap.get(snap.size() - 1).router();
    }

    /** Returns the active (writable) partition handle. */
    PartitionHandle activeHandle() {
        List<PartitionHandle> snap = registry;
        return snap.get(snap.size() - 1);
    }

    /** Returns the current cognitive memory router (volatile read — safe for concurrent access). */
    CognitiveMemoryRouter cognitiveRouter() { return activeRouter(); }

    /** Returns the active partition directory (volatile read). */
    Path activePartitionDir() { return activeHandle().dir(); }

    /** Returns the active partition sequence number. */
    int activeSeq() { return activeHandle().seq(); }

    /**
     * Discovers existing partitions or creates partition 000 if none exist.
     *
     * <p>Phase 1 (issue #443) opens only the newest partition on load; open-all-on-load
     * is deferred to Phase 2 (restart correctness).</p>
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
     * Creates a new partition directory, fresh tier stores and a fresh {@code text.dat},
     * freezes the current active handle (kept open), publishes a new immutable snapshot,
     * then atomically repoints the ingestion target at the new router, text store and
     * active sequence.</p>
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
                EpisodicRecordMemory newEpisodic = new EpisodicRecordMemory(
                        StorageLayout.episodicMem(newPartition),
                        quantizedVecBytes, episodicPartitionCapacity);

                ProceduralRecordMemory newProcedural = new ProceduralRecordMemory(
                        quantizedVecBytes, proceduralCapacity,
                        StorageLayout.proceduralMem(newPartition));

                SemanticRecordMemory newSemantic = new SemanticRecordMemory(
                        quantizedVecBytes, semanticCapacity,
                        StorageLayout.semanticMem(newPartition));

                // Fresh partition-scoped text.dat (D3b: text rolls with the partition)
                TextAppendMemory newText = new TextAppendMemory(
                        StorageLayout.textDat(newPartition), encryptor);

                // Preserve working memory (global, not partitioned)
                WorkingRecordMemory workingStore = activeRouter().working();

                // Flush index + graphs to runtime/ before rolling
                flushGlobalState();

                // Build the new active router (fully constructed before publication)
                CognitiveMemoryRouter newRouter = new CognitiveMemoryRouter(
                        workingStore, newEpisodic, newSemantic, newProcedural);

                // Freeze the current active handle (kept OPEN — leak fix) and publish
                // a new immutable snapshot = frozen… + newlyFrozen + newActive.
                List<PartitionHandle> current = registry;
                List<PartitionHandle> next = new ArrayList<>(current.size() + 1);
                for (int i = 0; i < current.size() - 1; i++) {
                    next.add(current.get(i)); // already frozen
                }
                next.add(current.get(current.size() - 1).asFrozen()); // freeze prev active
                PartitionHandle newActive = new PartitionHandle(
                        nextSeq, newPartition, newRouter, newText, true);
                next.add(newActive);
                this.registry = List.copyOf(next); // single volatile publish

                // Atomically repoint the ingestion target: router + text + active seq.
                cognitiveTarget.updateCognitiveRouter(newRouter);
                cognitiveTarget.updateTextDataStore(newText);
                cognitiveTarget.updateActivePartitionSeq(nextSeq);
                // Keep the index's active-partition seq in sync for reverse-key resolution.
                index.setActivePartitionSeq(nextSeq);

                log.info("Rolled to new partition: {} (seq={}, semantic capacity={}, live partitions={})",
                        newPartition.getFileName(), nextSeq, semanticCapacity, this.registry.size());

            } catch (IOException e) {
                throw new SpectorServerException(ErrorCode.INTERNAL_ERROR,
                        "Failed to roll partition: " + e.getMessage(), e);
            }
        } finally {
            partitionRollLock.unlock();
        }
    }

    /**
     * Closes frozen partition handles at component shutdown (leak fix, issue #443).
     *
     * <p>Only frozen (non-active) handles' partition-scoped stores plus the active
     * handle's {@code text.dat} are closed here. The active router (working + active
     * tier stores) is closed by {@code PersistenceManager}. Working memory is global
     * and is never closed by a handle. Idempotent.</p>
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        List<PartitionHandle> snap = registry;
        for (int i = 0; i < snap.size(); i++) {
            PartitionHandle h = snap.get(i);
            boolean isActive = (i == snap.size() - 1);
            if (isActive) {
                // Active tier stores + working are closed by PersistenceManager; close
                // only the active text.dat here (PersistenceManager does not own it).
                closeQuietly(h.text());
            } else {
                h.close(); // frozen: episodic/semantic/procedural + text (never working)
            }
        }
    }

    private static void closeQuietly(AutoCloseable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (Exception e) {
            log.debug("Failed to close active text store: {}", e.getMessage());
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
