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
package com.spectrayan.spector.memory.sync;

import com.spectrayan.spector.events.EventBus;
import com.spectrayan.spector.memory.StorageLayout;
import com.spectrayan.spector.memory.graph.EntityGraph;
import com.spectrayan.spector.memory.hebbian.CoActivationTracker;
import com.spectrayan.spector.memory.hebbian.HebbianGraphBase;
import com.spectrayan.spector.memory.index.MemoryIndex;

import com.spectrayan.spector.memory.cortex.TierRouter;
import com.spectrayan.spector.memory.temporal.TemporalChain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Performs periodic checkpoints of tier store segments, cognitive graphs, and WAL truncation.
 *
 * <h3>Biological Analog: Sleep-Consolidation Flush</h3>
 * <p>During slow-wave sleep, the hippocampus replays recent events and
 * consolidates them into cortical storage. The checkpoint daemon is the
 * digital equivalent — periodically flushing dirty pages to disk and
 * pruning the replay buffer (WAL).</p>
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Force all persistent tier store segments ({@code MemorySegment.force()})</li>
 *   <li>Save MemoryIndex (ID→offset, text, tags, source)</li>
 *   <li>Save HebbianGraph, TemporalChain, EntityGraph, CoActivationTracker</li>
 *   <li>Read the WAL high-water mark ({@code wal.highWaterMark()})</li>
 *   <li>Write the HWM to {@code checkpoint.meta} (atomic via temp+rename)</li>
 *   <li>Truncate WAL events ≤ HWM ({@code wal.truncateBefore(hwm)})</li>
 * </ol>
 *
 * <h3>checkpoint.meta Format (16 bytes)</h3>
 * <pre>
 *   [4B magic]   0x434B5054 ("CKPT")
 *   [4B version] 1
 *   [8B hwm]     WAL sequence number (long)
 * </pre>
 *
 * <h3>Threading</h3>
 * <p>This class is <b>not</b> responsible for its own thread lifecycle.
 * The {@link com.spectrayan.spector.commons.concurrent.DaemonSupervisor}
 * schedules periodic calls to {@link #checkpoint()} and handles restart,
 * watchdog, and shutdown. The {@code checkpoint()} method is safe to call
 * from any thread.</p>
 *
 * @see MemoryWal#truncateBefore(long)
 * @see com.spectrayan.spector.commons.concurrent.DaemonSupervisor
 */
public final class CheckpointDaemon {

    private static final Logger log = LoggerFactory.getLogger(CheckpointDaemon.class);

    /** checkpoint.meta magic: "CKPT" in ASCII. */
    static final int CKPT_MAGIC = 0x434B5054;

    /** checkpoint.meta format version. */
    static final int CKPT_VERSION = 1;

    /** Size of checkpoint.meta in bytes. */
    static final int CKPT_SIZE = 16;

    private final TierRouter tierRouter;
    private final MemoryWal wal;
    private final Path checkpointMetaPath;
    private final MemoryIndex index;   // nullable — only set for DISK mode
    private final Path indexPath;      // nullable — where to save index.midx

    // ── 3-Layer Cognitive Graph + CoActivation ──
    private final HebbianGraphBase hebbianGraph;           // nullable
    private final TemporalChain temporalChain;         // nullable
    private final EntityGraph entityGraph;             // nullable
    private final com.spectrayan.spector.memory.graph.HyperEntityGraph hyperEntityGraph; // nullable
    private final CoActivationTracker coActivationTracker; // nullable
    private final Path partitionDir;                   // nullable — active partition dir for graph saves
    private final Path basePath;                       // nullable — persistence root for coactivation

    // ── Event Bus (replaces CheckpointListener) ──
    private volatile EventBus<SpectorLifecycleEvent> eventBus;
    private volatile Map<String, String> eventContext = Map.of();

    /**
     * @deprecated Since 2.0.0. Use {@link #setEventBus(EventBus)} instead.
     *             Retained for backward compatibility during migration.
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    @FunctionalInterface
    public interface CheckpointListener {
        void onCheckpointComplete(long hwm);
    }
    @Deprecated(since = "2.0.0", forRemoval = true)
    private volatile CheckpointListener checkpointListener;

    /**
     * Creates a checkpoint daemon with full graph persistence.
     *
     * <p>Does <b>not</b> start any threads. Use
     * {@link com.spectrayan.spector.commons.concurrent.DaemonSupervisor#schedule}
     * to drive periodic checkpoint calls.</p>
     *
     * @param tierRouter            the tier router (for forcing persistent segments)
     * @param wal                   the write-ahead log
     * @param checkpointMetaPath    path to the checkpoint.meta file
     * @param index                 the memory index to persist (nullable)
     * @param indexPath             path to save the index file (nullable)
     * @param hebbianGraph          the Hebbian co-activation graph (nullable)
     * @param temporalChain         the temporal sequence chain (nullable)
     * @param entityGraph           the entity knowledge graph (nullable)
     * @param coActivationTracker   the co-activation frequency tracker (nullable)
     * @param partitionDir          active partition directory for graph saves (nullable)
     * @param basePath              persistence root for global files like coactivation (nullable)
     */
    public CheckpointDaemon(TierRouter tierRouter, MemoryWal wal,
                            Path checkpointMetaPath,
                            MemoryIndex index, Path indexPath,
                            HebbianGraphBase hebbianGraph,
                            TemporalChain temporalChain,
                            EntityGraph entityGraph,
                            com.spectrayan.spector.memory.graph.HyperEntityGraph hyperEntityGraph,
                            CoActivationTracker coActivationTracker,
                            Path partitionDir, Path basePath) {
        this.tierRouter = tierRouter;
        this.wal = wal;
        this.checkpointMetaPath = checkpointMetaPath;
        this.index = index;
        this.indexPath = indexPath;
        this.hebbianGraph = hebbianGraph;
        this.temporalChain = temporalChain;
        this.entityGraph = entityGraph;
        this.hyperEntityGraph = hyperEntityGraph;
        this.coActivationTracker = coActivationTracker;
        this.partitionDir = partitionDir;
        this.basePath = basePath;
    }

    /**
     * Creates a checkpoint daemon (legacy constructor — no graph persistence).
     *
     * @deprecated Use the full constructor that includes graph subsystems.
     */
    @Deprecated(since = "1.0.0", forRemoval = true)
    public CheckpointDaemon(TierRouter tierRouter, MemoryWal wal,
                            Path checkpointMetaPath,
                            MemoryIndex index, Path indexPath) {
        this(tierRouter, wal, checkpointMetaPath, index, indexPath,
                null, null, null, null, null, null, null);
    }

    /**
     * Performs a single checkpoint cycle.
     *
     * <p>Thread-safe. Called periodically by the {@code DaemonSupervisor},
     * and also called manually during shutdown for a final flush.</p>
     */
    public void checkpoint() {
        long start = System.nanoTime();

        // Step 1: Force all persistent tier store segments
        tierRouter.forceAll();

        // Step 2: Save MemoryIndex (ID→offset, text, tags, source)
        // This is critical for crash recovery — without it, tier store
        // records survive (mmap) but become orphaned (no ID mapping).
        if (index != null && indexPath != null && index.size() > 0) {
            try {
                index.save(indexPath);
            } catch (Exception e) {
                log.error("Checkpoint: failed to save MemoryIndex: {}", e.getMessage());
            }
        }

        // Step 3: Persist cognitive graphs to runtime/ directory (V3 layout)
        if (basePath != null) {
            saveGraph("HebbianGraph", () ->
                    hebbianGraph.save(StorageLayout.hebbianGraphRuntime(basePath)));
            saveGraph("TemporalChain", () ->
                    temporalChain.save(StorageLayout.temporalChainRuntime(basePath)));
            if (entityGraph != null) {
                saveGraph("EntityGraph", () ->
                        entityGraph.save(StorageLayout.entityGraphRuntime(basePath)));
            }
            if (hyperEntityGraph != null) {
                saveGraph("HyperEntityGraph", () ->
                        hyperEntityGraph.save(StorageLayout.hyperEntityGraphRuntime(basePath)));
            }
        }

        // Step 4: Persist CoActivationTracker (global, not partitioned)
        if (coActivationTracker != null && basePath != null) {
            saveGraph("CoActivationTracker", () ->
                    coActivationTracker.save(
                            StorageLayout.coactivationTracker(basePath)));
        }

        // Step 5: Read the WAL high-water mark
        long hwm = wal.highWaterMark();
        if (hwm <= 0) {
            log.trace("Checkpoint skipped WAL: no WAL events");
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            log.info("Checkpoint complete: hwm=0, index={} entries, elapsed={}ms",
                    index != null ? index.size() : 0, elapsed);
            fireCheckpointListener(0);
            return;
        }

        // Step 6: Write HWM to checkpoint.meta (atomic via temp+rename)
        writeCheckpointMeta(hwm);

        // Step 7: Truncate WAL events ≤ HWM
        wal.truncateBefore(hwm);

        // Step 8: Notify replication layer (enterprise)
        fireCheckpointListener(hwm);

        long elapsed = (System.nanoTime() - start) / 1_000_000;
        log.info("Checkpoint complete: hwm={}, index={} entries, elapsed={}ms",
                hwm, index != null ? index.size() : 0, elapsed);
    }

    /**
     * Sets the lifecycle event bus for publishing checkpoint events.
     *
     * <p>The event bus replaces the deprecated {@link CheckpointListener}.
     * After each successful checkpoint, a {@link CheckpointCompletedEvent}
     * is published to the bus, enabling event-driven replication, backups,
     * and analytics in enterprise mode.</p>
     *
     * @param eventBus the lifecycle event bus (nullable to unset)
     */
    public void setEventBus(EventBus<SpectorLifecycleEvent> eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Sets the context attributes included in checkpoint events.
     *
     * <p>The context carries generic identity metadata (see
     * {@link com.spectrayan.spector.events.SpectorEvent.ContextKeys}).
     * Core (OSS) mode sets the instance path; enterprise mode adds
     * tenant and namespace identifiers.</p>
     *
     * @param context the event context attributes
     */
    public void setEventContext(Map<String, String> context) {
        this.eventContext = context != null ? Map.copyOf(context) : Map.of();
    }

    /**
     * @deprecated Since 2.0.0. Use {@link #setEventBus(EventBus)} instead.
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    public void setCheckpointListener(CheckpointListener listener) {
        this.checkpointListener = listener;
    }

    /**
     * Publishes a checkpoint completion event and fires the legacy listener.
     */
    private void fireCheckpointListener(long hwm) {
        // New: publish to EventBus
        EventBus<SpectorLifecycleEvent> bus = this.eventBus;
        if (bus != null) {
            try {
                long elapsed = 0; // will be set by caller in future refactor
                bus.publish(new CheckpointCompletedEvent(
                        eventContext, hwm,
                        index != null ? index.size() : 0,
                        elapsed, Instant.now()));
            } catch (Exception e) {
                log.warn("Checkpoint event publish failed (hwm={}): {}", hwm, e.getMessage());
            }
        }
        // Legacy: fire deprecated listener
        @SuppressWarnings("deprecation")
        CheckpointListener listener = this.checkpointListener;
        if (listener != null) {
            try {
                listener.onCheckpointComplete(hwm);
            } catch (Exception e) {
                log.warn("Checkpoint listener failed (hwm={}): {}", hwm, e.getMessage());
            }
        }
    }

    /**
     * Saves a graph subsystem, logging any errors without propagating.
     */
    private void saveGraph(String name, Runnable saver) {
        if (saver == null) return;
        try {
            saver.run();
        } catch (Exception e) {
            log.error("Checkpoint: failed to save {}: {}", name, e.getMessage(), e);
        }
    }

    /**
     * Atomically writes the checkpoint metadata file.
     * Uses temp file + rename for crash safety.
     */
    private void writeCheckpointMeta(long hwm) {
        Path tempPath = checkpointMetaPath.resolveSibling(
                checkpointMetaPath.getFileName() + ".tmp");
        try {
            // Ensure parent directory exists
            Path parent = checkpointMetaPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            // Write to temp file
            ByteBuffer buf = ByteBuffer.allocate(CKPT_SIZE);
            buf.putInt(CKPT_MAGIC);
            buf.putInt(CKPT_VERSION);
            buf.putLong(hwm);
            buf.flip();

            try (FileChannel ch = FileChannel.open(tempPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ch.write(buf);
                ch.force(true);
            }

            // Atomic rename
            Files.move(tempPath, checkpointMetaPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

        } catch (IOException e) {
            log.error("Failed to write checkpoint.meta: {}", e.getMessage(), e);
            // Clean up temp file on failure
            try { Files.deleteIfExists(tempPath); } catch (IOException ignored) {}
        }
    }

    /**
     * Reads the checkpoint HWM from an existing checkpoint.meta file.
     *
     * @param path path to the checkpoint.meta file
     * @return the high-water mark, or -1 if the file doesn't exist or is invalid
     */
    public static long readCheckpointHwm(Path path) {
        if (!Files.exists(path)) return -1;

        try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
            if (ch.size() < CKPT_SIZE) return -1;

            ByteBuffer buf = ByteBuffer.allocate(CKPT_SIZE);
            ch.read(buf);
            buf.flip();

            int magic = buf.getInt();
            if (magic != CKPT_MAGIC) {
                log.warn("Invalid checkpoint.meta magic: 0x{}", Integer.toHexString(magic));
                return -1;
            }

            int version = buf.getInt();
            if (version != CKPT_VERSION) {
                log.warn("Unsupported checkpoint.meta version: {}", version);
                return -1;
            }

            return buf.getLong();
        } catch (IOException e) {
            log.warn("Failed to read checkpoint.meta: {}", e.getMessage());
            return -1;
        }
    }
}
