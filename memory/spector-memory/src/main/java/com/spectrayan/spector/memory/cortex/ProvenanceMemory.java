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

import com.spectrayan.spector.memory.kernel.MemoryId;
import com.spectrayan.spector.memory.kernel.layout.ProvenanceLayout;
import com.spectrayan.spector.memory.kernel.layout.ProvenanceLayout.ProvenanceState;
import com.spectrayan.spector.memory.kernel.shape.AbstractRecordMemory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Off-heap store managing the Provenance Audit Region (ADR-0029).
 *
 * <h3>Design &amp; Purpose</h3>
 * <p>Tracks the lineage between episodic conversation turns and consolidated
 * semantic (or procedural) memories. Each provenance record captures which session
 * turns contributed to which semantic fact, including multi-pass consolidation
 * tracking via {@code pass_number}.</p>
 *
 * <h3>Heap Indexes</h3>
 * <p>Two heap-based {@link ConcurrentHashMap} indexes are rebuilt on open by
 * scanning all live records:</p>
 * <ul>
 *   <li>{@code targetIndex}: target_tsid → slotId (O(1) "explain" lookups)</li>
 *   <li>{@code sessionIndex}: session_id → ordered list of slotIds (reverse provenance)</li>
 * </ul>
 *
 * <h3>Thread Safety</h3>
 * <p>Read operations are lock-free via the heap indexes. Append operations are serialized
 * via a {@link ReentrantLock} to ensure sequential slot allocation and atomic index updates.</p>
 *
 * @see ProvenanceLayout
 * @see ProvenanceEdge
 * @since 1.5.0
 */
public final class ProvenanceMemory extends AbstractRecordMemory<ProvenanceLayout> {

    private static final Logger log = LoggerFactory.getLogger(ProvenanceMemory.class);

    /** target_tsid → slotId for O(1) "explain" lookups. */
    private final Map<Long, Integer> targetIndex = new ConcurrentHashMap<>();

    /** session_id → ordered list of slotIds for reverse provenance queries. */
    private final Map<Long, List<Integer>> sessionIndex = new ConcurrentHashMap<>();

    /** Lock for serializing append operations. */
    private final ReentrantLock appendLock = new ReentrantLock();

    private ProvenanceMemory(MemoryId id, ProvenanceLayout layout, int capacity,
                             Arena arena, MemorySegment segment, int count,
                             boolean persistent, Path filePath,
                             FileChannel fileChannel, boolean bundleManaged) {
        super(id, layout, capacity, arena, segment, count, persistent, filePath, fileChannel, bundleManaged);
    }

    /**
     * Creates an in-memory heap-backed ProvenanceMemory for testing or transient runtime.
     *
     * @param capacity maximum number of provenance records
     * @return a new heap-backed ProvenanceMemory
     */
    public static ProvenanceMemory heap(int capacity) {
        Arena arena = Arena.ofShared();
        long bytes = (long) capacity * ProvenanceLayout.RECORD_STRIDE;
        MemorySegment segment = arena.allocate(bytes, 8);
        return new ProvenanceMemory(
                MemoryId.of("default", "heap-provenance"),
                ProvenanceLayout.INSTANCE,
                capacity,
                arena,
                segment,
                0,
                false,
                null,
                null,
                false);
    }

    /**
     * Creates a bundle-backed ProvenanceMemory from a pre-sliced region segment.
     *
     * @param arena         the arena managing the segment's lifetime
     * @param segment       the pre-sliced region segment
     * @param bundlePath    the path to the bundle file
     * @param memoryName    diagnostic name for this memory
     * @return a new bundle-backed ProvenanceMemory
     */
    public static ProvenanceMemory fromBundle(Arena arena, MemorySegment segment,
                                              Path bundlePath, String memoryName) {
        String name = (memoryName != null && !memoryName.isBlank()) ? memoryName : "bundle-provenance";
        int capacity = (int) (segment.byteSize() / ProvenanceLayout.RECORD_STRIDE);
        return new ProvenanceMemory(
                MemoryId.of("default", name),
                ProvenanceLayout.INSTANCE,
                capacity,
                arena,
                segment,
                0,
                true,
                bundlePath,
                null,
                true);
    }

    /**
     * Creates a bundle-backed ProvenanceMemory with default memory name.
     */
    public static ProvenanceMemory fromBundle(Arena arena, MemorySegment segment, Path bundlePath) {
        return fromBundle(arena, segment, bundlePath, "bundle-provenance");
    }

    public static ProvenanceMemory fromRegionRef(com.spectrayan.spector.memory.kernel.bundle.RegionRef regionRef, Path bundlePath, String memoryName) {
        String name = (memoryName != null && !memoryName.isBlank()) ? memoryName : "bundle-provenance";
        MemorySegment segment = regionRef.resolve();
        int capacity = (int) (segment.byteSize() / ProvenanceLayout.RECORD_STRIDE);
        return new ProvenanceMemory(
                MemoryId.of("default", name),
                ProvenanceLayout.INSTANCE,
                capacity,
                regionRef,
                0,
                true,
                bundlePath);
    }

    public static ProvenanceMemory fromRegionRef(com.spectrayan.spector.memory.kernel.bundle.RegionRef regionRef, Path bundlePath) {
        return fromRegionRef(regionRef, bundlePath, "bundle-provenance");
    }

    private ProvenanceMemory(MemoryId id, ProvenanceLayout layout, int capacity,
                             com.spectrayan.spector.memory.kernel.bundle.RegionRef regionRef, int count,
                             boolean persistent, Path filePath) {
        super(id, layout, capacity, regionRef, count, persistent, filePath);
    }

    // ── Append ──

    /**
     * Appends a provenance edge at the next available slot.
     *
     * @param edge the provenance edge to write
     * @return the slot ID where the record was written, or -1 if the region is full
     */
    public int append(ProvenanceEdge edge) {
        appendLock.lock();
        try {
            if (count >= capacity) {
                log.warn("Provenance region full (capacity={}). Skipping provenance edge for session=0x{}, target=0x{}",
                        capacity, Long.toHexString(edge.sessionId()), Long.toHexString(edge.targetTsid()));
                return -1;
            }

            int slot = count;
            long recordOff = recordOffset(slot);

            ProvenanceState state = new ProvenanceState(
                    ProvenanceLayout.FLAG_LIVE,
                    edge.sourceKind(),
                    edge.targetKind(),
                    edge.prefixKind(),
                    edge.passNumber(),
                    edge.turnCount(),
                    edge.sessionId(),
                    edge.targetTsid(),
                    edge.consolidatedAtMs(),
                    edge.partitionSeq(),
                    edge.firstSeq(),
                    edge.lastSeq(),
                    edge.firstOffsetHint(),
                    edge.lastOffsetHint(),
                    edge.factIndex(),
                    edge.batchFactCount(),
                    edge.contentHashHi()
            );

            // Write fields to off-heap segment
            ProvenanceLayout.writeRecord(segment(), recordOff, state);

            // Write CRC32C via base class contract
            Arena recordArena = Arena.ofConfined();
            try {
                MemorySegment recordBuf = recordArena.allocate(ProvenanceLayout.RECORD_STRIDE, 8);
                MemorySegment.copy(segment(), recordOff, recordBuf, 0, ProvenanceLayout.RECORD_STRIDE);
                write(slot, recordBuf);
            } finally {
                recordArena.close();
            }

            // Update heap indexes
            targetIndex.put(edge.targetTsid(), slot);
            sessionIndex.computeIfAbsent(edge.sessionId(), k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(slot);

            return slot;
        } finally {
            appendLock.unlock();
        }
    }

    // ── Primary Index: Explain ──

    /**
     * Finds the provenance record for a given target memory TSID.
     *
     * @param tsid raw 64-bit TSID of the consolidated memory
     * @return the provenance state if found
     */
    public Optional<ProvenanceState> findByTarget(long tsid) {
        Integer slot = targetIndex.get(tsid);
        if (slot == null) {
            return Optional.empty();
        }
        long recordOff = recordOffset(slot);
        ProvenanceState state = ProvenanceLayout.readRecord(segment(), recordOff);
        return state.isLive() ? Optional.of(state) : Optional.empty();
    }

    // ── Reverse Index: Session Provenance ──

    /**
     * Returns all live provenance records for a session, ordered by pass_number then fact_index.
     *
     * @param sessionId the episodic session ID
     * @return ordered list of provenance states (empty if no records found)
     */
    public List<ProvenanceState> findBySession(long sessionId) {
        List<Integer> slots = sessionIndex.get(sessionId);
        if (slots == null || slots.isEmpty()) {
            return List.of();
        }

        List<ProvenanceState> result = new ArrayList<>(slots.size());
        MemorySegment seg = segment();
        for (int slot : slots) {
            long recordOff = recordOffset(slot);
            ProvenanceState state = ProvenanceLayout.readRecord(seg, recordOff);
            if (state.isLive()) {
                result.add(state);
            }
        }

        result.sort(Comparator
                .comparingInt((ProvenanceState s) -> s.passNumber())
                .thenComparingInt(s -> s.factIndex()));
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns all live provenance records for a session and specific pass, ordered by fact_index.
     *
     * @param sessionId  the episodic session ID
     * @param passNumber the consolidation pass number (1-indexed)
     * @return ordered list of provenance states for the given pass
     */
    public List<ProvenanceState> findBySessionAndPass(long sessionId, int passNumber) {
        List<Integer> slots = sessionIndex.get(sessionId);
        if (slots == null || slots.isEmpty()) {
            return List.of();
        }

        List<ProvenanceState> result = new ArrayList<>();
        MemorySegment seg = segment();
        for (int slot : slots) {
            long recordOff = recordOffset(slot);
            ProvenanceState state = ProvenanceLayout.readRecord(seg, recordOff);
            if (state.isLive() && state.passNumber() == passNumber) {
                result.add(state);
            }
        }

        result.sort(Comparator.comparingInt(ProvenanceState::factIndex));
        return Collections.unmodifiableList(result);
    }

    // ── Pass Counter ──

    /**
     * Returns the next pass number for a session: max(pass_number) + 1, or 1 if no prior passes.
     *
     * @param sessionId the episodic session ID
     * @return the next pass number to use for consolidation
     */
    public int nextPassNumber(long sessionId) {
        List<Integer> slots = sessionIndex.get(sessionId);
        if (slots == null || slots.isEmpty()) {
            return 1;
        }

        int maxPass = 0;
        MemorySegment seg = segment();
        for (int slot : slots) {
            long recordOff = recordOffset(slot);
            short passNum = ProvenanceLayout.readPassNumber(seg, recordOff);
            byte flags = ProvenanceLayout.readFlags(seg, recordOff);
            if (flags != ProvenanceLayout.FLAG_TOMBSTONE && passNum > maxPass) {
                maxPass = passNum;
            }
        }
        return maxPass + 1;
    }

    // ── Index Rebuild ──

    /**
     * Rebuilds the heap indexes by sequentially scanning all slots.
     *
     * <p>Called after opening a persistent store to reconstruct the
     * {@code targetIndex} and {@code sessionIndex} from on-disk data.</p>
     */
    public void rebuildIndexes() {
        targetIndex.clear();
        sessionIndex.clear();

        MemorySegment seg = segment();
        for (int slot = 0; slot < count; slot++) {
            long recordOff = recordOffset(slot);
            byte flags = ProvenanceLayout.readFlags(seg, recordOff);
            if (flags == ProvenanceLayout.FLAG_TOMBSTONE) {
                continue;
            }

            long targetTsid = ProvenanceLayout.readTargetTsid(seg, recordOff);
            long sessionId = ProvenanceLayout.readSessionId(seg, recordOff);

            targetIndex.put(targetTsid, slot);
            sessionIndex.computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(slot);
        }

        log.info("Rebuilt provenance indexes: {} target entries, {} sessions, {} total records",
                targetIndex.size(), sessionIndex.size(), count);
    }

    // ── Replay / Inspection ──

    /**
     * Streams all live provenance records in slot order (for inspection and eval tools).
     *
     * @return stream of live provenance states
     */
    public Stream<ProvenanceState> replay() {
        MemorySegment seg = segment();
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(slot -> ProvenanceLayout.readRecord(seg, recordOffset(slot)))
                .filter(ProvenanceState::isLive);
    }

    /**
     * Returns the number of unique sessions tracked in the provenance store.
     */
    public int sessionCount() {
        return sessionIndex.size();
    }

    /**
     * Returns the number of unique target memories tracked in the provenance store.
     */
    public int targetCount() {
        return targetIndex.size();
    }
}
