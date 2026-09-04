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

import com.spectrayan.spector.memory.kernel.RegionPreamble;
import com.spectrayan.spector.memory.kernel.MemoryLayout;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * On-disk binary layout for longitudinal consciousness continuity (\(\Phi_{CC}\)) and identity trajectory.
 *
 * <p>The continuity header occupies 32 bytes immediately after the standard 64-byte {@link RegionPreamble},
 * followed by a fixed-stride array of 32-byte immutable snapshot records.</p>
 *
 * <h3>Continuity Sub-Header Layout (32 bytes at offset 64)</h3>
 * <pre>
 * ┌──────────────┬────────────────┬──────────────────────────┬──────────────┬──────────────┐
 * │  headIndex   │ totalSnapshots │ lastSnapshotTimestampMs  │   capacity   │ reserved     │
 * │    (4B)      │      (4B)      │           (8B)           │     (4B)     │   (12B)      │
 * └──────────────┴────────────────┴──────────────────────────┴──────────────┴──────────────┘
 * </pre>
 *
 * <h3>Snapshot Record Layout (32 bytes per record at offset \(96 + i \times 32\))</h3>
 * <pre>
 * ┌──────────────────────────┬──────────────┬──────────────┬──────────────┐
 * │      timestampMs (8B)    │  phiCc (4B)  │  traceG (4B) │priorDrift(4B)│
 * ├──────────────┬───────────┴──┬───────────┴──┬───────────┴──────────────┤
 * │ valence (1B) │ arousal (1B) │  energy (1B) │   soulVersion (2B)       │
 * ├──────────────┴──────────────┴──────────────┴──────────────────────────┤
 * │                         reserved (7B)                                 │
 * └───────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * @since 1.2.0
 */
public final class ContinuityLayout implements MemoryLayout {

    /** Singleton instance — stateless and safe to share. */
    public static final ContinuityLayout SINGLETON = new ContinuityLayout();

    /** Four-char layout identifier: {@code 'CONT'} (0x434F4E54). */
    public static final int LAYOUT_ID = 0x434F4E54;

    /** Current schema version for the continuity header and record format. */
    public static final int SCHEMA_VERSION = 1;

    /** Size of the continuity sub-header in bytes. */
    public static final int SUB_HEADER_BYTES = 32;

    /** Fixed size of each snapshot record in bytes (64-bit word aligned). */
    public static final int RECORD_STRIDE = 32;

    /** Base offset where the first snapshot record begins. */
    public static final long DATA_START = (long) RegionPreamble.PREAMBLE_BYTES + SUB_HEADER_BYTES; // 96

    // ── Sub-header offsets (relative to offset 64) ──
    public static final long OFF_SUB_HEAD_INDEX = (long) RegionPreamble.PREAMBLE_BYTES; // 64
    public static final long OFF_SUB_TOTAL_SNAPSHOTS = (long) RegionPreamble.PREAMBLE_BYTES + 4; // 68
    public static final long OFF_SUB_LAST_SNAPSHOT_TIME = (long) RegionPreamble.PREAMBLE_BYTES + 8; // 72
    public static final long OFF_SUB_CAPACITY = (long) RegionPreamble.PREAMBLE_BYTES + 16; // 80

    // ── Record field offsets (relative to individual record start) ──
    public static final long OFF_REC_TIMESTAMP = 0;
    public static final long OFF_REC_PHI_CC = 8;
    public static final long OFF_REC_TRACE_G = 12;
    public static final long OFF_REC_PRIOR_DRIFT = 16;
    public static final long OFF_REC_VALENCE = 20;
    public static final long OFF_REC_AROUSAL = 21;
    public static final long OFF_REC_ENERGY = 22;
    public static final long OFF_REC_SOUL_VERSION = 23;
    public static final long OFF_REC_RESERVED = 25;

    private ContinuityLayout() {}

    @Override
    public int layoutId() {
        return LAYOUT_ID;
    }

    @Override
    public int schemaVersion() {
        return SCHEMA_VERSION;
    }

    @Override
    public int recordStride() {
        return RECORD_STRIDE;
    }

    @Override
    public boolean crcEnabled() {
        return false;
    }

    @Override
    public String name() {
        return "ContinuityLayout";
    }

    // ── Sub-header read/write helpers ──

    public static int readHeadIndex(MemorySegment seg) {
        return seg.get(ValueLayout.JAVA_INT_UNALIGNED, OFF_SUB_HEAD_INDEX);
    }

    public static void writeHeadIndex(MemorySegment seg, int headIndex) {
        seg.set(ValueLayout.JAVA_INT_UNALIGNED, OFF_SUB_HEAD_INDEX, headIndex);
    }

    public static int readTotalSnapshots(MemorySegment seg) {
        return seg.get(ValueLayout.JAVA_INT_UNALIGNED, OFF_SUB_TOTAL_SNAPSHOTS);
    }

    public static void writeTotalSnapshots(MemorySegment seg, int total) {
        seg.set(ValueLayout.JAVA_INT_UNALIGNED, OFF_SUB_TOTAL_SNAPSHOTS, total);
    }

    public static long readLastSnapshotTimestamp(MemorySegment seg) {
        return seg.get(ValueLayout.JAVA_LONG_UNALIGNED, OFF_SUB_LAST_SNAPSHOT_TIME);
    }

    public static void writeLastSnapshotTimestamp(MemorySegment seg, long ts) {
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED, OFF_SUB_LAST_SNAPSHOT_TIME, ts);
    }

    public static int readCapacity(MemorySegment seg) {
        return seg.get(ValueLayout.JAVA_INT_UNALIGNED, OFF_SUB_CAPACITY);
    }

    public static void writeCapacity(MemorySegment seg, int capacity) {
        seg.set(ValueLayout.JAVA_INT_UNALIGNED, OFF_SUB_CAPACITY, capacity);
    }

    // ── Record read/write helpers ──

    public static long recordOffset(int slot) {
        return DATA_START + (long) slot * RECORD_STRIDE;
    }

    public static void writeRecord(MemorySegment seg, long recordOff,
                                    long timestamp, float phiCc, float traceG, float priorDrift,
                                    byte valence, byte arousal, byte energy, short soulVersion) {
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED, recordOff + OFF_REC_TIMESTAMP, timestamp);
        seg.set(ValueLayout.JAVA_FLOAT_UNALIGNED, recordOff + OFF_REC_PHI_CC, phiCc);
        seg.set(ValueLayout.JAVA_FLOAT_UNALIGNED, recordOff + OFF_REC_TRACE_G, traceG);
        seg.set(ValueLayout.JAVA_FLOAT_UNALIGNED, recordOff + OFF_REC_PRIOR_DRIFT, priorDrift);
        seg.set(ValueLayout.JAVA_BYTE, recordOff + OFF_REC_VALENCE, valence);
        seg.set(ValueLayout.JAVA_BYTE, recordOff + OFF_REC_AROUSAL, arousal);
        seg.set(ValueLayout.JAVA_BYTE, recordOff + OFF_REC_ENERGY, energy);
        seg.set(ValueLayout.JAVA_SHORT_UNALIGNED, recordOff + OFF_REC_SOUL_VERSION, soulVersion);
    }

    public static long readTimestamp(MemorySegment seg, long recordOff) {
        return seg.get(ValueLayout.JAVA_LONG_UNALIGNED, recordOff + OFF_REC_TIMESTAMP);
    }

    public static float readPhiCc(MemorySegment seg, long recordOff) {
        return seg.get(ValueLayout.JAVA_FLOAT_UNALIGNED, recordOff + OFF_REC_PHI_CC);
    }

    public static float readTraceG(MemorySegment seg, long recordOff) {
        return seg.get(ValueLayout.JAVA_FLOAT_UNALIGNED, recordOff + OFF_REC_TRACE_G);
    }

    public static float readPriorDrift(MemorySegment seg, long recordOff) {
        return seg.get(ValueLayout.JAVA_FLOAT_UNALIGNED, recordOff + OFF_REC_PRIOR_DRIFT);
    }

    public static byte readValence(MemorySegment seg, long recordOff) {
        return seg.get(ValueLayout.JAVA_BYTE, recordOff + OFF_REC_VALENCE);
    }

    public static byte readArousal(MemorySegment seg, long recordOff) {
        return seg.get(ValueLayout.JAVA_BYTE, recordOff + OFF_REC_AROUSAL);
    }

    public static byte readEnergy(MemorySegment seg, long recordOff) {
        return seg.get(ValueLayout.JAVA_BYTE, recordOff + OFF_REC_ENERGY);
    }

    public static short readSoulVersion(MemorySegment seg, long recordOff) {
        return seg.get(ValueLayout.JAVA_SHORT_UNALIGNED, recordOff + OFF_REC_SOUL_VERSION);
    }
}
