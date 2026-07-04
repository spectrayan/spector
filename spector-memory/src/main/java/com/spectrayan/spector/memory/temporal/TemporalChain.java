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
package com.spectrayan.spector.memory.temporal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import com.spectrayan.spector.memory.error.SpectorGraphPersistenceException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Off-heap temporal causal chain linking memories within a session.
 *
 * <h3>Biological Analog: Episodic Sequence Memory</h3>
 * <p>In the hippocampus, episodic memories are linked in temporal order.
 * When you recall one event from a day, you naturally remember what happened
 * next ("what happened after the meeting?"). This chain stores explicit
 * prev/next pointers between memories ingested within the same session.</p>
 *
 * <h3>Layout Per Node (16 bytes)</h3>
 * <pre>
 *   [prevIdx:4B] [nextIdx:4B] [sessionId:4B] [pad:4B]
 * </pre>
 *
 * <p>-1 is used as sentinel for "no link" (beginning or end of chain).</p>
 *
 * <h3>Persistence</h3>
 * <p>Supports save/load via raw segment serialization with "TPCH" magic header.</p>
 */
public final class TemporalChain implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TemporalChain.class);

    /** File magic: "TPCH" in ASCII. */
    private static final int FILE_MAGIC = 0x54504348;
    private static final int FILE_VERSION = 2;
    private static final int FILE_HEADER_BYTES = 16;

    /** Bytes per node: prevIdx(4) + nextIdx(4) + sessionId(4) + epochSec(4). */
    static final int NODE_BYTES = 16;

    /** Sentinel value for "no link". */
    private static final int NO_LINK = -1;

    // Offsets within each node
    private static final long OFF_PREV = 0;
    private static final long OFF_NEXT = 4;
    private static final long OFF_SESSION = 8;
    private static final long OFF_EPOCH_SEC = 12;

    private final Arena arena;
    private final MemorySegment segment;
    private final int capacity;
    private final FileChannel mappedChannel;
    private final boolean fileBacked;

    /**
     * Creates a heap-allocated temporal chain (in-memory mode).
     *
     * @param capacity maximum number of nodes (memories)
     */
    public TemporalChain(int capacity) {
        this.capacity = capacity;
        this.arena = Arena.ofShared();
        this.segment = arena.allocate((long) NODE_BYTES * capacity);
        this.mappedChannel = null;
        this.fileBacked = false;
        // Initialize all prev/next to NO_LINK (-1)
        for (int i = 0; i < capacity; i++) {
            long offset = (long) i * NODE_BYTES;
            segment.set(ValueLayout.JAVA_INT, offset + OFF_PREV, NO_LINK);
            segment.set(ValueLayout.JAVA_INT, offset + OFF_NEXT, NO_LINK);
            segment.set(ValueLayout.JAVA_INT, offset + OFF_SESSION, 0);
        }

        log.info("TemporalChain initialized (heap): capacity={}, memory={}KB",
                capacity, (long) NODE_BYTES * capacity / 1024);
    }

    /**
     * Creates or opens a file-backed (mmap) temporal chain.
     *
     * @param filePath path to the chain file
     * @param capacity maximum number of nodes (used only for new files)
     */
    public TemporalChain(Path filePath, int capacity) {
        Path parent = filePath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new SpectorGraphPersistenceException("TemporalChain", parent, e);
            }
        }

        long dataBytes = (long) NODE_BYTES * capacity;
        boolean isNew = !Files.exists(filePath);

        try {
            FileChannel ch = FileChannel.open(filePath,
                    StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            this.mappedChannel = ch;

            if (isNew || ch.size() < FILE_HEADER_BYTES) {
                ByteBuffer header = ByteBuffer.allocate(FILE_HEADER_BYTES);
                header.putInt(FILE_MAGIC);
                header.putInt(FILE_VERSION);
                header.putInt(capacity);
                header.putInt(0);
                header.flip();
                ch.position(0);
                ch.write(header);
                long totalBytes = FILE_HEADER_BYTES + dataBytes;
                if (ch.size() < totalBytes) {
                    ch.position(totalBytes - 1);
                    ch.write(ByteBuffer.wrap(new byte[]{0}));
                }
                ch.force(true);
                this.capacity = capacity;
            } else {
                ByteBuffer header = ByteBuffer.allocate(FILE_HEADER_BYTES);
                ch.position(0);
                ch.read(header);
                header.flip();
                int magic = header.getInt();
                int version = header.getInt();
                int fileCapacity = header.getInt();
                header.getInt();

                if (magic != FILE_MAGIC || (version != FILE_VERSION && version != 1)) {
                    log.warn("Invalid TemporalChain file, reinitializing: {}", filePath);
                    ch.truncate(0);
                    ByteBuffer newHeader = ByteBuffer.allocate(FILE_HEADER_BYTES);
                    newHeader.putInt(FILE_MAGIC);
                    newHeader.putInt(FILE_VERSION);
                    newHeader.putInt(capacity);
                    newHeader.putInt(0);
                    newHeader.flip();
                    ch.position(0);
                    ch.write(newHeader);
                    long totalBytes = FILE_HEADER_BYTES + dataBytes;
                    ch.position(totalBytes - 1);
                    ch.write(ByteBuffer.wrap(new byte[]{0}));
                    ch.force(true);
                    this.capacity = capacity;
                } else {
                    this.capacity = fileCapacity;
                    dataBytes = (long) NODE_BYTES * fileCapacity;
                }
            }

            this.arena = Arena.ofShared();
            this.segment = ch.map(FileChannel.MapMode.READ_WRITE, FILE_HEADER_BYTES,
                    dataBytes, arena);
            this.fileBacked = true;

            log.info("TemporalChain initialized (mmap): capacity={}, file={}",
                    this.capacity, filePath.getFileName());

        } catch (IOException e) {
            throw new SpectorGraphPersistenceException("TemporalChain", filePath, e);
        }
    }

    private TemporalChain(int capacity, Arena arena, MemorySegment segment,
                           FileChannel mappedChannel, boolean fileBacked) {
        this.capacity = capacity;
        this.arena = arena;
        this.segment = segment;
        this.mappedChannel = mappedChannel;
        this.fileBacked = fileBacked;
    }

    /**
     * Links two memories in temporal order within the same session.
     *
     * <p>After this call, {@code previousIdx.next = currentIdx} and
     * {@code currentIdx.prev = previousIdx}.</p>
     *
     * @param currentIdx  index of the memory just ingested
     * @param previousIdx index of the memory ingested immediately before
     * @param sessionId   session identifier (e.g., hash of session start time)
     */
    public void link(int currentIdx, int previousIdx, int sessionId) {
        if (currentIdx < 0 || currentIdx >= capacity) return;
        if (previousIdx < 0 || previousIdx >= capacity) return;
        if (currentIdx == previousIdx) return;

        long currentOffset = (long) currentIdx * NODE_BYTES;
        long previousOffset = (long) previousIdx * NODE_BYTES;

        // currentIdx.prev = previousIdx
        segment.set(ValueLayout.JAVA_INT, currentOffset + OFF_PREV, previousIdx);
        segment.set(ValueLayout.JAVA_INT, currentOffset + OFF_SESSION, sessionId);
        segment.set(ValueLayout.JAVA_INT, currentOffset + OFF_EPOCH_SEC,
                (int) (System.currentTimeMillis() / 1000));

        // previousIdx.next = currentIdx
        segment.set(ValueLayout.JAVA_INT, previousOffset + OFF_NEXT, currentIdx);
        // Also stamp previousIdx epoch if it was never set (backfill for first node)
        if (segment.get(ValueLayout.JAVA_INT, previousOffset + OFF_EPOCH_SEC) == 0) {
            segment.set(ValueLayout.JAVA_INT, previousOffset + OFF_EPOCH_SEC,
                    (int) (System.currentTimeMillis() / 1000));
        }
    }

    /**
     * Follows the chain forward from a starting memory.
     *
     * @param startIdx the starting memory index
     * @param maxHops  maximum number of hops to follow
     * @return array of memory indices in temporal order (excludes startIdx)
     */
    public int[] followForward(int startIdx, int maxHops) {
        if (startIdx < 0 || startIdx >= capacity) return new int[0];
        int[] chain = new int[maxHops];
        int count = 0;
        int current = startIdx;
        for (int hop = 0; hop < maxHops; hop++) {
            long offset = (long) current * NODE_BYTES;
            int next = segment.get(ValueLayout.JAVA_INT, offset + OFF_NEXT);
            if (next == NO_LINK || next < 0 || next >= capacity) break;
            chain[count++] = next;
            current = next;
        }
        return count == maxHops ? chain : java.util.Arrays.copyOf(chain, count);
    }

    /**
     * Follows the chain backward from a starting memory.
     *
     * @param startIdx the starting memory index
     * @param maxHops  maximum number of hops to follow
     * @return array of memory indices in reverse temporal order (excludes startIdx)
     */
    public int[] followBackward(int startIdx, int maxHops) {
        if (startIdx < 0 || startIdx >= capacity) return new int[0];
        int[] chain = new int[maxHops];
        int count = 0;
        int current = startIdx;
        for (int hop = 0; hop < maxHops; hop++) {
            long offset = (long) current * NODE_BYTES;
            int prev = segment.get(ValueLayout.JAVA_INT, offset + OFF_PREV);
            if (prev == NO_LINK || prev < 0 || prev >= capacity) break;
            chain[count++] = prev;
            current = prev;
        }
        return count == maxHops ? chain : java.util.Arrays.copyOf(chain, count);
    }

    /**
     * Returns the session ID for a memory.
     */
    public int sessionOf(int idx) {
        if (idx < 0 || idx >= capacity) return 0;
        return segment.get(ValueLayout.JAVA_INT, (long) idx * NODE_BYTES + OFF_SESSION);
    }

    /**
     * Returns whether a memory has any temporal links.
     */
    public boolean isLinked(int idx) {
        if (idx < 0 || idx >= capacity) return false;
        long offset = (long) idx * NODE_BYTES;
        int prev = segment.get(ValueLayout.JAVA_INT, offset + OFF_PREV);
        int next = segment.get(ValueLayout.JAVA_INT, offset + OFF_NEXT);
        return prev != NO_LINK || next != NO_LINK;
    }

    /**
     * Returns the capacity.
     */
    public int capacity() {
        return capacity;
    }

    /**
     * Returns the epoch-second timestamp for a memory node.
     *
     * @param idx the memory index
     * @return epoch seconds (0 if unlinked or from a V1 file)
     */
    public int epochSecOf(int idx) {
        if (idx < 0 || idx >= capacity) return 0;
        return segment.get(ValueLayout.JAVA_INT, (long) idx * NODE_BYTES + OFF_EPOCH_SEC);
    }

    /**
     * Prunes temporal chain nodes older than the given cutoff.
     *
     * <p>For each linked node whose {@code epochSec * 1000L < cutoffEpochMs},
     * the node is unlinked by re-stitching its neighbors' prev/next pointers.
     * Nodes with {@code epochSec == 0} (unknown age, e.g., from V1 files) are
     * never pruned.</p>
     *
     * @param cutoffEpochMs cutoff timestamp in milliseconds
     * @return number of nodes pruned
     */
    public int pruneOlderThan(long cutoffEpochMs) {
        int cutoffEpochSec = (int) (cutoffEpochMs / 1000);
        int pruned = 0;

        for (int i = 0; i < capacity; i++) {
            long offset = (long) i * NODE_BYTES;
            int prev = segment.get(ValueLayout.JAVA_INT, offset + OFF_PREV);
            int next = segment.get(ValueLayout.JAVA_INT, offset + OFF_NEXT);

            // Skip unlinked nodes
            if (prev == NO_LINK && next == NO_LINK) continue;

            int epochSec = segment.get(ValueLayout.JAVA_INT, offset + OFF_EPOCH_SEC);
            // Never prune nodes with unknown age (epochSec=0, from V1 migration)
            if (epochSec == 0) continue;
            if (epochSec >= cutoffEpochSec) continue;

            // Unlink: re-stitch neighbors
            if (prev >= 0 && prev < capacity) {
                long prevOffset = (long) prev * NODE_BYTES;
                segment.set(ValueLayout.JAVA_INT, prevOffset + OFF_NEXT, next);
            }
            if (next >= 0 && next < capacity) {
                long nextOffset = (long) next * NODE_BYTES;
                segment.set(ValueLayout.JAVA_INT, nextOffset + OFF_PREV, prev);
            }

            // Clear this node
            segment.set(ValueLayout.JAVA_INT, offset + OFF_PREV, NO_LINK);
            segment.set(ValueLayout.JAVA_INT, offset + OFF_NEXT, NO_LINK);
            segment.set(ValueLayout.JAVA_INT, offset + OFF_SESSION, 0);
            segment.set(ValueLayout.JAVA_INT, offset + OFF_EPOCH_SEC, 0);
            pruned++;
        }

        if (pruned > 0) {
            log.info("TemporalChain pruned {} nodes older than {}s", pruned, cutoffEpochSec);
        }
        return pruned;
    }

    /**
     * Provides importance scores for memory indices — used by importance-based
     * temporal pruning to decide which session chains to protect.
     *
     * <p>Typical implementation reads the synaptic header importance field
     * from the cortex tier store.</p>
     */
    @FunctionalInterface
    public interface ImportanceProvider {
        /**
         * Returns the importance score for a memory at the given index.
         *
         * @param memoryIndex the memory slot index
         * @return importance score (higher = more important), or 0 if unavailable
         */
        float importance(int memoryIndex);
    }

    /**
     * Prunes low-importance temporal chain entries older than the cutoff.
     *
     * <p>Unlike {@link #pruneOlderThan}, this method considers the importance
     * of each memory before pruning. A node is only pruned if:</p>
     * <ol>
     *   <li>It is older than {@code cutoffEpochMs}</li>
     *   <li>Its importance (from the provider) is below {@code importanceThreshold}</li>
     * </ol>
     *
     * <p>High-importance temporal links survive beyond the retention window,
     * preserving causal chains for significant memories. This mirrors the
     * Zeigarnik effect — incomplete or important tasks resist forgetting.</p>
     *
     * @param cutoffEpochMs       cutoff timestamp in milliseconds
     * @param importanceThreshold importance below this value is prunable
     * @param provider            importance score provider for memory indices
     * @return number of nodes pruned
     */
    public int pruneByImportance(long cutoffEpochMs, float importanceThreshold,
                                  ImportanceProvider provider) {
        if (provider == null) return 0;
        int cutoffEpochSec = (int) (cutoffEpochMs / 1000);
        int pruned = 0;

        for (int i = 0; i < capacity; i++) {
            long offset = (long) i * NODE_BYTES;
            int prev = segment.get(ValueLayout.JAVA_INT, offset + OFF_PREV);
            int next = segment.get(ValueLayout.JAVA_INT, offset + OFF_NEXT);

            // Skip unlinked nodes
            if (prev == NO_LINK && next == NO_LINK) continue;

            int epochSec = segment.get(ValueLayout.JAVA_INT, offset + OFF_EPOCH_SEC);
            // Never prune nodes with unknown age (epochSec=0, from V1 migration)
            if (epochSec == 0) continue;
            // Only prune if old enough
            if (epochSec >= cutoffEpochSec) continue;

            // Check importance — protect high-importance memories
            float importance = provider.importance(i);
            if (importance >= importanceThreshold) continue;

            // Unlink: re-stitch neighbors
            if (prev >= 0 && prev < capacity) {
                long prevOffset = (long) prev * NODE_BYTES;
                segment.set(ValueLayout.JAVA_INT, prevOffset + OFF_NEXT, next);
            }
            if (next >= 0 && next < capacity) {
                long nextOffset = (long) next * NODE_BYTES;
                segment.set(ValueLayout.JAVA_INT, nextOffset + OFF_PREV, prev);
            }

            // Clear this node
            segment.set(ValueLayout.JAVA_INT, offset + OFF_PREV, NO_LINK);
            segment.set(ValueLayout.JAVA_INT, offset + OFF_NEXT, NO_LINK);
            segment.set(ValueLayout.JAVA_INT, offset + OFF_SESSION, 0);
            segment.set(ValueLayout.JAVA_INT, offset + OFF_EPOCH_SEC, 0);
            pruned++;
        }

        if (pruned > 0) {
            log.info("TemporalChain importance-pruned {} low-importance nodes (threshold={})",
                    pruned, importanceThreshold);
        }
        return pruned;
    }

    // ══════════════════════════════════════════════════════════════
    // PERSISTENCE: save / load
    // ══════════════════════════════════════════════════════════════

    /**
     * Saves the chain to a binary file.
     *
     * @param filePath path to write
     */
    public void save(Path filePath) {
        if (fileBacked) {
            try {
                segment.force();
                if (mappedChannel != null) mappedChannel.force(true);
                log.info("TemporalChain flushed (mmap): capacity={}", capacity);
            } catch (IOException e) {
                throw new SpectorGraphPersistenceException("TemporalChain", filePath, e);
            }
            return;
        }

        // Heap-backed: serialize to file
        Path parent = filePath.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new SpectorGraphPersistenceException("TemporalChain", parent, e);
            }
        }

        try (FileChannel ch = FileChannel.open(filePath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            ByteBuffer header = ByteBuffer.allocate(FILE_HEADER_BYTES);
            header.putInt(FILE_MAGIC);
            header.putInt(FILE_VERSION);
            header.putInt(capacity);
            header.putInt(0);
            header.flip();
            ch.write(header);

            long totalBytes = (long) NODE_BYTES * capacity;
            long written = 0;
            int chunkSize = 64 * 1024;
            while (written < totalBytes) {
                int toWrite = (int) Math.min(chunkSize, totalBytes - written);
                ByteBuffer buf = segment.asSlice(written, toWrite)
                        .asByteBuffer().asReadOnlyBuffer();
                ch.write(buf);
                written += toWrite;
            }

            ch.force(true);
            log.info("TemporalChain saved (heap→file): capacity={} → {}", capacity, filePath);

        } catch (IOException e) {
            throw new SpectorGraphPersistenceException("TemporalChain", filePath, e);
        }
    }

    public static TemporalChain load(Path filePath, int defaultCapacity) {
        if (filePath == null || !Files.exists(filePath)) {
            log.info("TemporalChain file not found, creating fresh: {}", filePath);
            return new TemporalChain(defaultCapacity);
        }

        try {
            return new TemporalChain(filePath, defaultCapacity);
        } catch (Exception e) {
            log.error("Failed to mmap TemporalChain from {}, creating fresh: {}",
                    filePath, e.getMessage());
            return new TemporalChain(defaultCapacity);
        }
    }

    /**
     * Resets all temporal links by re-initializing all nodes to NO_LINK.
     *
     * <p>Unlike {@link #close()}, this does NOT release the arena. The chain
     * remains usable for new links after the reset. Used by privacy wipe.</p>
     */
    public void reset() {
        for (int i = 0; i < capacity; i++) {
            long offset = (long) i * NODE_BYTES;
            segment.set(ValueLayout.JAVA_INT, offset + OFF_PREV, NO_LINK);
            segment.set(ValueLayout.JAVA_INT, offset + OFF_NEXT, NO_LINK);
            segment.set(ValueLayout.JAVA_INT, offset + OFF_SESSION, 0);
            segment.set(ValueLayout.JAVA_INT, offset + OFF_EPOCH_SEC, 0);
        }
        log.info("TemporalChain reset: capacity={}", capacity);
    }

    @Override
    public void close() {
        log.info("TemporalChain closing (capacity={}, fileBacked={})", capacity, fileBacked);
        if (fileBacked && mappedChannel != null) {
            try {
                segment.force();
                mappedChannel.close();
            } catch (IOException e) {
                log.warn("Failed to close TemporalChain channel: {}", e.getMessage());
            }
        }
        arena.close();
    }
}
