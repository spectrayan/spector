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
package com.spectrayan.spector.memory.cortex;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout.CognitiveHeader;
import com.spectrayan.spector.memory.model.MemoryType;

/**
 * Episodic memory store — stores time-ordered personal experiences.
 *
 * <h3>Biological Analog: Hippocampus</h3>
 * <p>The hippocampus encodes events as time-ordered episodic traces. New events are
 * appended rapidly (one-trial learning), and during sleep the hippocampus replays
 * sequences for consolidation into cortical (semantic) memory.</p>
 *
 * <h3>V4 Design: Single File Per Partition</h3>
 * <p>Each colocated partition directory contains a single {@code episodic.mem} file,
 * consistent with {@code semantic.mem} and {@code procedural.mem}. Partition rolling
 * is handled by {@code DefaultSpectorMemory} at the directory level — no daily
 * sub-partitioning within a partition.</p>
 *
 * <ul>
 *   <li>Extends {@link AbstractCognitiveRecordMemory} for common Arena/layout/segment lifecycle</li>
 *   <li>Full cognitive records — header + quantized vector in one slab</li>
 *   <li>Flat SIMD scan via the scorer</li>
 *   <li>Persistent across JVM restarts via {@code FileChannel.map()}</li>
 * </ul>
 *
 * @deprecated As of Spector 1.3.0, replaced by {@link EpisodicLogMemory} (ADR-0006 log-structured
 *             conversation store) and scheduled for removal in a future major release.
 */
@Deprecated(since = "1.3.0", forRemoval = true)
public class EpisodicRecordMemory extends AbstractCognitiveRecordMemory {

    private static final Logger log = LoggerFactory.getLogger(EpisodicRecordMemory.class);

    /**
     * Creates a volatile Episodic Memory store (in-memory only).
     *
     * @param quantizedVecBytes bytes per quantized vector
     * @param capacity          maximum number of episodic memories
     */
    public EpisodicRecordMemory(int quantizedVecBytes, int capacity) {
        super(MemoryType.EPISODIC, quantizedVecBytes, capacity,
                (long) new CognitiveRecordLayout(quantizedVecBytes).stride() * capacity);

        log.info("EpisodicRecordMemory initialized: capacity={}, stride={}B, persistent=false",
                capacity, layout.stride());
    }

    /**
     * Creates a persistent Episodic Memory store backed by an mmap file.
     *
     * @param quantizedVecBytes bytes per quantized vector
     * @param capacity          maximum number of episodic memories
     * @param filePath          path to the backing mmap file (e.g., episodic.mem)
     */
    public EpisodicRecordMemory(int quantizedVecBytes, int capacity, Path filePath) {
        super(MemoryType.EPISODIC, quantizedVecBytes, capacity,
                (long) new CognitiveRecordLayout(quantizedVecBytes).stride() * capacity,
                filePath);

        log.info("EpisodicRecordMemory initialized: capacity={}, stride={}B, persistent=true, count={}",
                capacity, layout.stride(), getCount());
    }

    public EpisodicRecordMemory(Path filePath, int quantizedVecBytes, int capacity) {
        this(quantizedVecBytes, capacity, filePath);
    }

    /**
     * Creates a bundle-backed Episodic Memory store from a pre-sliced region segment.
     *
     * @param arena        the shared arena from the owning bundle
     * @param regionSlice  the memory segment sliced from the bundle's master segment
     * @param capacity     the maximum number of episodic memories in this region
     * @param quantizedVecBytes bytes per quantized vector
     * @param bundlePath   the path to the bundle file (for diagnostics)
     * @param isNew        true if the region was just created
     * @return a new bundle-backed EpisodicRecordMemory
     */
    public static EpisodicRecordMemory fromBundle(Arena arena, MemorySegment regionSlice,
                                                   int capacity, int quantizedVecBytes,
                                                   Path bundlePath, boolean isNew) {
        return new EpisodicRecordMemory(arena, regionSlice, capacity, quantizedVecBytes, bundlePath, isNew);
    }

    private EpisodicRecordMemory(Arena arena, MemorySegment regionSlice, int capacity,
                                  int quantizedVecBytes, Path bundlePath, boolean isNew) {
        super(MemoryType.EPISODIC, new CognitiveRecordLayout(quantizedVecBytes),
              capacity, arena, regionSlice, bundlePath, isNew);
    }

    @Override
    public MemoryType type() {
        return MemoryType.EPISODIC;
    }

    @Override
    public long write(CognitiveHeader header, byte[] quantizedVec) {
        long offset = dataOffset() + (long) getCount() * layout.stride();
        append(header, quantizedVec);
        return offset;
    }

    /**
     * Reads the cognitive header at the given index.
     */
    public CognitiveHeader readHeader(int index) {
        long offset = dataOffset() + (long) index * layout.stride();
        return layout.readHeader(segment, offset);
    }

    /**
     * Returns the total record count.
     */
    public int totalRecords() {
        return getCount();
    }

    /**
     * Computes the byte offset for record at logical index i.
     */
    public long recordOffset(int recordIndex) {
        return dataOffset() + (long) recordIndex * layout.stride();
    }

    /**
     * Returns the header slab segment for direct scorer access.
     */
    public MemorySegment headerSlab() {
        return segment;
    }

    // ══════════════════════════════════════════════════════════════
    // BACKWARD COMPATIBILITY — EpisodicPartition shim
    // ══════════════════════════════════════════════════════════════
    // ReflectDaemon, RecallPipeline, and TombstoneCompactor reference
    // EpisodicPartition. This shim wraps the store itself as a single
    // "partition" for compatibility.

    /**
     * Returns this store wrapped as a single EpisodicPartition for backward
     * compatibility with ReflectDaemon, RecallPipeline, and TombstoneCompactor.
     */
    public List<EpisodicPartition> partitions() {
        return List.of(new EpisodicPartition(this));
    }

    /**
     * Returns the key for a partition (always "default" for single-file store).
     */
    public String keyForPartition(EpisodicPartition partition) {
        return "default";
    }

    /**
     * No-op for single-file store (partition replacement not applicable).
     */
    public boolean replacePartition(String key, EpisodicPartition oldPartition,
                                     EpisodicPartition newPartition) {
        log.debug("replacePartition called on single-file store — no-op");
        return false;
    }

    /**
     * Compatibility shim wrapping the EpisodicRecordMemory as a single "partition".
     *
     * <p>Used by ReflectDaemon, RecallPipeline, and TombstoneCompactor which
     * iterate over episodic partitions. In the new architecture, there is always
     * exactly one partition per colocated directory.</p>
     */
    public static final class EpisodicPartition {

        /** Size of the metadata header in bytes (matches AbstractCognitiveRecordMemory). */
        public static final int METADATA_PREAMBLE_BYTES = AbstractCognitiveRecordMemory.METADATA_PREAMBLE_BYTES;

        private final EpisodicRecordMemory store;
        private int tombstoneCount = 0;

        public EpisodicPartition(EpisodicRecordMemory store) {
            this.store = store;
        }

        public int count() { return store.size(); }

        /** Returns the acquire-fenced visible count for concurrent readers. */
        public int visibleCount() { return store.visibleCount(); }

        public MemorySegment segment() { return store.segment(); }

        public CognitiveRecordLayout layout() { return store.layout(); }

        public int capacity() { return store.capacity(); }

        public long recordOffset(int recordIndex) { return store.recordOffset(recordIndex); }

        /** Returns the byte offset where data records begin (0 for volatile, 64 for persistent). */
        public long dataOffset() { return store.dataOffset(); }

        public Path path() { return store.filePath(); }

        /** Partition state — always ACTIVE for the current store. */
        public PartitionState state() { return PartitionState.ACTIVE; }

        public void seal() { /* no-op for single-file store */ }

        public void setState(PartitionState newState) { /* no-op */ }

        public int tombstoneCount() { return tombstoneCount; }

        public void incrementTombstoneCount() { tombstoneCount++; }

        public float tombstoneRatio() {
            int c = count();
            return c == 0 ? 0f : (float) tombstoneCount / c;
        }

        public void close() { /* managed by the store's own close() */ }

        public void force() { store.force(); }

        /**
         * Appends to the underlying store.
         */
        public void append(CognitiveHeader header, byte[] quantizedVec) {
            store.append(header, quantizedVec);
        }
    }

    /**
     * Partition lifecycle states (kept for backward compatibility).
     */
    public enum PartitionState {
        ACTIVE, SEALED, REFLECTABLE, TOMBSTONED, COMPACTED
    }
}
