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
package com.spectrayan.spector.memory.kernel.layout;

import com.spectrayan.spector.memory.kernel.MemoryLayout;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Memory layout descriptor for the co-activation tracker's compound hash tables.
 *
 * <p>The co-activation data region contains a sub-header followed by two
 * open-addressing hash tables:</p>
 * <pre>
 *   [dataOffset + 0 .. + 4)   : pairCapacity  (int, 4B)
 *   [dataOffset + 4 .. + 8)   : edgeCapacity  (int, 4B)
 *   [dataOffset + 8 .. + 8 + PAIR_SLOT_BYTES × pairCap)  : OffHeapPairTable
 *   [dataOffset + 8 + PAIR_SLOT_BYTES × pairCap .. end)   : OffHeapEdgeTable
 * </pre>
 *
 * <p>Upgraded from v2 (generics-satisfying stub) to v3 (real descriptor with
 * sub-table offset computation) as part of ADR-0009.</p>
 */
public final class CoActivationLayout implements MemoryLayout {

    /** Sub-header: 4B pairCapacity + 4B edgeCapacity. */
    public static final int SUB_HEADER_BYTES = 8;

    /** Sub-header field offset: pairCapacity (int, 4B) relative to data region start. */
    public static final int SUB_OFF_PAIR_CAPACITY = 0;
    /** Sub-header field offset: edgeCapacity (int, 4B) relative to data region start. */
    public static final int SUB_OFF_EDGE_CAPACITY = 4;

    /**
     * Bytes per slot in the {@code OffHeapPairTable} (undirected co-occurrence).
     * <pre>
     *   hashA(8B) + hashB(8B) + count(4B) + flags(4B) + pad(8B) = 32B
     * </pre>
     */
    public static final int PAIR_SLOT_BYTES = 32;

    /**
     * Bytes per slot in the {@code OffHeapEdgeTable} (directed STDP).
     * <pre>
     *   srcHash(8B) + tgtHash(8B) + weight(4B) + pad(4B)
     *   + lastActivatedMs(8B) + activationCount(4B) + flags(4B) = 40B
     * </pre>
     */
    public static final int EDGE_SLOT_BYTES = 40;

    // ── Pair slot field offsets (within a 32-byte slot) ──

    /** Pair slot: hashA (long, 8B). */
    public static final long OFF_PAIR_HASH_A = 0;
    /** Pair slot: hashB (long, 8B). */
    public static final long OFF_PAIR_HASH_B = 8;
    /** Pair slot: co-activation count (int, 4B). */
    public static final long OFF_PAIR_COUNT = 16;
    /** Pair slot: flags bitfield (int, 4B). */
    public static final long OFF_PAIR_FLAGS = 20;

    // ── Edge slot field offsets (within a 40-byte slot) ──

    /** Edge slot: source tag hash (long, 8B). */
    public static final long OFF_EDGE_SRC = 0;
    /** Edge slot: target tag hash (long, 8B). */
    public static final long OFF_EDGE_TGT = 8;
    /** Edge slot: STDP weight (float, 4B). */
    public static final long OFF_EDGE_WEIGHT = 16;
    // 4B padding at offset 20 for 8-byte alignment
    /** Edge slot: last activation timestamp (long, 8B). */
    public static final long OFF_EDGE_LAST_MS = 24;
    /** Edge slot: activation count (int, 4B). */
    public static final long OFF_EDGE_ACT_COUNT = 32;
    /** Edge slot: flags bitfield (int, 4B). */
    public static final long OFF_EDGE_FLAGS = 36;

    /** Flag: slot is occupied (used by both pair and edge tables). */
    public static final int FLAG_OCCUPIED = 1;

    // ── Hash table management constants ──

    /** Fibonacci 64-bit golden ratio multiplier for hash probing. */
    public static final long FIBONACCI_HASH_MULTIPLIER_64 = 0x9E3779B97F4A7C15L;
    /** SplitMix64 mixing constant for directed edge hash probing. */
    public static final long SPLITMIX_HASH_MULTIPLIER_64 = 0x517CC1B727220A95L;
    /** Maximum load factor threshold (capacity / 2 = 50%). */
    public static final float MAX_LOAD_FACTOR = 0.50f;
    /** Fraction of weakest entries pruned when load limit reached. */
    public static final float PRUNE_FRACTION = 0.10f;
    /** I/O chunk buffer size for persistence operations. */
    public static final int IO_CHUNK_BYTES = 64 * 1024;

    private static final int LAYOUT_ID = 0x434F4158; // 'COAX'
    private static final int VERSION = 3;

    @Override
    public int layoutId() {
        return LAYOUT_ID;
    }

    @Override
    public int schemaVersion() {
        return VERSION;
    }

    /**
     * Returns 1 for backward compatibility with the SMKM header's {@code recordStride}
     * field. Hash-table memories do not use stride-based access — sub-table offsets
     * are computed via {@link #pairTableOffset()} and {@link #edgeTableOffset(int)}.
     */
    @Override
    public int recordStride() {
        return 1;
    }

    @Override
    public boolean crcEnabled() {
        return false;
    }

    @Override
    public String name() {
        return "CoActivationLayout";
    }

    // ── Sub-table offset computation ──

    /**
     * Returns the byte offset of the pair table within the data region.
     * The pair table begins immediately after the 8-byte sub-header.
     *
     * @return byte offset from start of data region
     */
    public int pairTableOffset() {
        return SUB_HEADER_BYTES;
    }

    /**
     * Returns the byte offset of the edge table within the data region.
     *
     * @param pairCapacity the capacity (slot count) of the pair table
     * @return byte offset from start of data region
     */
    public int edgeTableOffset(int pairCapacity) {
        return SUB_HEADER_BYTES + pairCapacity * PAIR_SLOT_BYTES;
    }

    /**
     * Computes the total bytes needed for the data region
     * (sub-header + pair table + edge table).
     *
     * @param pairCapacity the capacity (slot count) of the pair table
     * @param edgeCapacity the capacity (slot count) of the edge table
     * @return total data region size in bytes
     */
    public int totalDataBytes(int pairCapacity, int edgeCapacity) {
        return SUB_HEADER_BYTES
                + pairCapacity * PAIR_SLOT_BYTES
                + edgeCapacity * EDGE_SLOT_BYTES;
    }

    // ── Boundary Record Types (Two-Tier Projection Model) ──

    /**
     * Immutable projection of a 32-byte pair slot. Used at API boundaries,
     * checkpoint serialization, diagnostics, and unit tests — never in hot-path
     * inner loops.
     *
     * @param tagHashA first tag hash (canonical: hashA ≤ hashB)
     * @param tagHashB second tag hash
     * @param count    co-activation count
     * @param flags    flags bitfield (see {@link #FLAG_OCCUPIED})
     */
    public record CoActivationPair(long tagHashA, long tagHashB, int count, int flags) {
        /** Returns {@code true} if this slot is occupied. */
        public boolean isOccupied() {
            return (flags & FLAG_OCCUPIED) != 0;
        }
    }

    /**
     * Immutable projection of a 40-byte STDP edge slot. Used at API boundaries,
     * checkpoint serialization, diagnostics, and unit tests — never in hot-path
     * inner loops.
     *
     * @param sourceHash      source tag hash
     * @param targetHash      target tag hash
     * @param weight          STDP weight [0.0, 1.0]
     * @param lastActivatedMs epoch millis of last activation
     * @param activationCount total activation count
     * @param flags           flags bitfield (see {@link #FLAG_OCCUPIED})
     */
    public record StdpEdge(long sourceHash, long targetHash, float weight,
                           long lastActivatedMs, int activationCount, int flags) {
        /** Returns {@code true} if this slot is occupied. */
        public boolean isOccupied() {
            return (flags & FLAG_OCCUPIED) != 0;
        }
    }

    // ── Zero-allocation primitive field accessors (hot-path) ──

    /** Reads pair count from a pair slot at the given byte offset. */
    public static int readPairCount(MemorySegment seg, long slotOffset) {
        return seg.get(ValueLayout.JAVA_INT, slotOffset + OFF_PAIR_COUNT);
    }

    /** Reads pair flags from a pair slot at the given byte offset. */
    public static int readPairFlags(MemorySegment seg, long slotOffset) {
        return seg.get(ValueLayout.JAVA_INT, slotOffset + OFF_PAIR_FLAGS);
    }

    /** Reads edge weight from an edge slot at the given byte offset. */
    public static float readEdgeWeight(MemorySegment seg, long slotOffset) {
        return seg.get(ValueLayout.JAVA_FLOAT, slotOffset + OFF_EDGE_WEIGHT);
    }

    /** Reads edge flags from an edge slot at the given byte offset. */
    public static int readEdgeFlags(MemorySegment seg, long slotOffset) {
        return seg.get(ValueLayout.JAVA_INT, slotOffset + OFF_EDGE_FLAGS);
    }

    // ── Full-record projection accessors (boundary/cold path) ──

    /**
     * Reads a full {@link CoActivationPair} from the given slot offset.
     * Use only at API boundaries, never in hot-path inner loops.
     */
    public static CoActivationPair readPair(MemorySegment seg, long slotOffset) {
        long hA = seg.get(ValueLayout.JAVA_LONG, slotOffset + OFF_PAIR_HASH_A);
        long hB = seg.get(ValueLayout.JAVA_LONG, slotOffset + OFF_PAIR_HASH_B);
        int count = seg.get(ValueLayout.JAVA_INT, slotOffset + OFF_PAIR_COUNT);
        int flags = seg.get(ValueLayout.JAVA_INT, slotOffset + OFF_PAIR_FLAGS);
        return new CoActivationPair(hA, hB, count, flags);
    }

    /**
     * Writes a {@link CoActivationPair} to the given slot offset.
     */
    public static void writePair(MemorySegment seg, long slotOffset, CoActivationPair pair) {
        seg.set(ValueLayout.JAVA_LONG, slotOffset + OFF_PAIR_HASH_A, pair.tagHashA());
        seg.set(ValueLayout.JAVA_LONG, slotOffset + OFF_PAIR_HASH_B, pair.tagHashB());
        seg.set(ValueLayout.JAVA_INT, slotOffset + OFF_PAIR_COUNT, pair.count());
        seg.set(ValueLayout.JAVA_INT, slotOffset + OFF_PAIR_FLAGS, pair.flags());
    }

    /**
     * Reads a full {@link StdpEdge} from the given slot offset.
     * Use only at API boundaries, never in hot-path inner loops.
     */
    public static StdpEdge readEdge(MemorySegment seg, long slotOffset) {
        long src = seg.get(ValueLayout.JAVA_LONG, slotOffset + OFF_EDGE_SRC);
        long tgt = seg.get(ValueLayout.JAVA_LONG, slotOffset + OFF_EDGE_TGT);
        float weight = seg.get(ValueLayout.JAVA_FLOAT, slotOffset + OFF_EDGE_WEIGHT);
        long lastMs = seg.get(ValueLayout.JAVA_LONG, slotOffset + OFF_EDGE_LAST_MS);
        int actCount = seg.get(ValueLayout.JAVA_INT, slotOffset + OFF_EDGE_ACT_COUNT);
        int flags = seg.get(ValueLayout.JAVA_INT, slotOffset + OFF_EDGE_FLAGS);
        return new StdpEdge(src, tgt, weight, lastMs, actCount, flags);
    }

    /**
     * Writes a {@link StdpEdge} to the given slot offset.
     */
    public static void writeEdge(MemorySegment seg, long slotOffset, StdpEdge edge) {
        seg.set(ValueLayout.JAVA_LONG, slotOffset + OFF_EDGE_SRC, edge.sourceHash());
        seg.set(ValueLayout.JAVA_LONG, slotOffset + OFF_EDGE_TGT, edge.targetHash());
        seg.set(ValueLayout.JAVA_FLOAT, slotOffset + OFF_EDGE_WEIGHT, edge.weight());
        seg.set(ValueLayout.JAVA_LONG, slotOffset + OFF_EDGE_LAST_MS, edge.lastActivatedMs());
        seg.set(ValueLayout.JAVA_INT, slotOffset + OFF_EDGE_ACT_COUNT, edge.activationCount());
        seg.set(ValueLayout.JAVA_INT, slotOffset + OFF_EDGE_FLAGS, edge.flags());
    }
}
