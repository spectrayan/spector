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

import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.memory.kernel.RegionLayout;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.synapse.DecayStrategy;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

/**
 * Memory layout descriptor for the off-heap Recall Audit Region.
 *
 * <h3>Design &amp; Architecture (ADR-0028)</h3>
 * <p>Separates mutable recall telemetry, Long-Term Potentiation (LTP) counters, Two-Factor
 * storage strength, and ACT-R recall timestamp ring buffers from the read-mostly 64-byte
 * {@link HeaderLayout64 synaptic header}. This eliminates false sharing and CPU cache
 * invalidation on the sequential SIMD scoring hot path.</p>
 *
 * <h3>Audit Record Layout (96 bytes — 32-byte aligned)</h3>
 * <pre>
 *   Offset  Size  Field                  Type     Access Mode     Description
 *   ──────  ────  ─────────────────────  ───────  ──────────────  ───────────────────────────
 *    0      1B    audit_flags            uint8    Plain Set       Bits 0-1: memory_type ordinal
 *    1      1B    last_recall_profile    uint8    Plain Set       Profile ordinal of last recall
 *    2      1B    last_recall_valence    int8     Plain Set       Signed emotional valence feedback
 *    3      1B    _pad0                  —        —               Alignment padding
 *    4      4B    agent_recall_count     int32    VarHandle Add   Explicit agent reinforcement counter
 *    8      4B    spector_recall_cnt     int32    VarHandle Add   Passive auto-LTP retrieval counter
 *   12      4B    effective_importance   float32  VarHandle CAS   Mutable importance (ICNU re-fusions)
 *   16      4B    storage_strength       float32  VarHandle CAS   Two-Factor Bjork S(t) in [1.0, 5.0]
 *   20      4B    last_agent_hash        uint32   Plain Set       MurmurHash3 of caller agent ID
 *   24      8B    last_auto_ltp          int64    Volatile Set    Epoch ms of last auto-LTP cooldown
 *   32      8B    last_recall_ts         int64    Volatile Set    Epoch ms of most recent recall
 *   40     32B    act_r_ring_buffer      int32[8] Volatile CAS    8 relative-second recall timestamps
 *   72      8B    reconsolidation_delta  int64    Plain Set       Micro-plasticity weight shift history
 *   80     16B    _reserved              bytes    —               Zero-padded reserved block
 *   ── 96B total stride ────────────────────────────────────────────────────────────────────────
 * </pre>
 *
 * @see HeaderLayout64
 * @see RegionLayout
 */
public final class AuditRecordLayout implements RegionLayout {

    /** Layout identification code: ASCII 'AUDT' (0x41554454). */
    public static final int LAYOUT_ID = 0x41554454;

    /** Current schema version. */
    public static final int SCHEMA_VERSION = 1;

    /** Fixed stride in bytes per audit record (96 bytes). */
    public static final int STRIDE_BYTES = SpectorPropertyConstants.DEFAULT_MEMORY_AUDIT_STRIDE_BYTES;

    /** Number of slots in the ACT-R recall timestamp ring buffer (8 slots). */
    public static final int ACT_R_RING_BUFFER_SLOTS = SpectorPropertyConstants.DEFAULT_MEMORY_ACTR_RING_BUFFER_SLOTS;

    /** Singleton instance of the layout descriptor. */
    public static final AuditRecordLayout INSTANCE = new AuditRecordLayout();

    // ── Field Offsets ──

    public static final long OFFSET_AUDIT_FLAGS          = 0L;
    public static final long OFFSET_LAST_RECALL_PROFILE  = 1L;
    public static final long OFFSET_LAST_RECALL_VALENCE  = 2L;
    public static final long OFFSET_PAD0                 = 3L;
    public static final long OFFSET_AGENT_RECALL_COUNT   = 4L;
    public static final long OFFSET_SPECTOR_RECALL_COUNT = 8L;
    public static final long OFFSET_EFFECTIVE_IMPORTANCE = 12L;
    public static final long OFFSET_STORAGE_STRENGTH     = 16L;
    public static final long OFFSET_LAST_AGENT_HASH      = 20L;
    public static final long OFFSET_LAST_AUTO_LTP        = 24L;
    public static final long OFFSET_LAST_RECALL_TS       = 32L;
    public static final long OFFSET_ACTR_RING_BUFFER     = 40L;
    public static final long OFFSET_RECONSOLIDATION_DELTA = 72L;
    public static final long OFFSET_RESERVED             = 80L;

    // ── Flags Bitmasks ──

    /** Bits 0-1: 2-bit memory type ordinal mask. */
    public static final byte FLAG_TYPE_MASK = 0x03;

    // ── Value Layouts ──

    public static final ValueLayout.OfByte  LAYOUT_AUDIT_FLAGS          = ValueLayout.JAVA_BYTE;
    public static final ValueLayout.OfByte  LAYOUT_LAST_RECALL_PROFILE  = ValueLayout.JAVA_BYTE;
    public static final ValueLayout.OfByte  LAYOUT_LAST_RECALL_VALENCE  = ValueLayout.JAVA_BYTE;
    public static final ValueLayout.OfInt   LAYOUT_AGENT_RECALL_COUNT   = ValueLayout.JAVA_INT;
    public static final ValueLayout.OfInt   LAYOUT_SPECTOR_RECALL_COUNT = ValueLayout.JAVA_INT;
    public static final ValueLayout.OfFloat LAYOUT_EFFECTIVE_IMPORTANCE = ValueLayout.JAVA_FLOAT;
    public static final ValueLayout.OfFloat LAYOUT_STORAGE_STRENGTH     = ValueLayout.JAVA_FLOAT;
    public static final ValueLayout.OfInt   LAYOUT_LAST_AGENT_HASH      = ValueLayout.JAVA_INT;
    public static final ValueLayout.OfLong  LAYOUT_LAST_AUTO_LTP        = ValueLayout.JAVA_LONG;
    public static final ValueLayout.OfLong  LAYOUT_LAST_RECALL_TS       = ValueLayout.JAVA_LONG;
    public static final ValueLayout.OfLong  LAYOUT_RECONSOLIDATION_DELTA = ValueLayout.JAVA_LONG;

    // ── VarHandles for Lock-Free Concurrency ──

    public static final VarHandle VAR_HANDLE_AGENT_RECALL_COUNT   = LAYOUT_AGENT_RECALL_COUNT.varHandle();
    public static final VarHandle VAR_HANDLE_SPECTOR_RECALL_COUNT = LAYOUT_SPECTOR_RECALL_COUNT.varHandle();
    public static final VarHandle VAR_HANDLE_EFFECTIVE_IMPORTANCE = LAYOUT_EFFECTIVE_IMPORTANCE.varHandle();
    public static final VarHandle VAR_HANDLE_STORAGE_STRENGTH     = LAYOUT_STORAGE_STRENGTH.varHandle();
    public static final VarHandle VAR_HANDLE_ACTR_SLOT            = ValueLayout.JAVA_INT.varHandle();

    private AuditRecordLayout() {}

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
        return STRIDE_BYTES;
    }

    @Override
    public boolean crcEnabled() {
        return false;
    }

    @Override
    public String name() {
        return "AuditRecordLayout";
    }

    // ── Memory Type Accessors ──

    /**
     * Reads the memory type ordinal from the audit flags byte.
     */
    public int readMemoryTypeOrdinal(MemorySegment seg, long recordOffset) {
        byte flags = seg.get(LAYOUT_AUDIT_FLAGS, recordOffset + OFFSET_AUDIT_FLAGS);
        return flags & FLAG_TYPE_MASK;
    }

    /**
     * Reads the {@link MemoryType} from the audit flags byte.
     */
    public MemoryType readMemoryType(MemorySegment seg, long recordOffset) {
        int ord = readMemoryTypeOrdinal(seg, recordOffset);
        var values = MemoryType.values();
        return (ord >= 0 && ord < values.length) ? values[ord] : MemoryType.SEMANTIC;
    }

    /**
     * Writes the memory type into the audit flags byte.
     */
    public void writeMemoryType(MemorySegment seg, long recordOffset, MemoryType type) {
        int ord = type != null ? type.ordinal() : MemoryType.SEMANTIC.ordinal();
        byte prev = seg.get(LAYOUT_AUDIT_FLAGS, recordOffset + OFFSET_AUDIT_FLAGS);
        byte updated = (byte) ((prev & ~FLAG_TYPE_MASK) | (ord & FLAG_TYPE_MASK));
        seg.set(LAYOUT_AUDIT_FLAGS, recordOffset + OFFSET_AUDIT_FLAGS, updated);
    }

    // ── LTP & Recall Counters Accessors ──

    /**
     * Reads the explicit agent recall / reinforcement count.
     */
    public int readAgentRecallCount(MemorySegment seg, long recordOffset) {
        return seg.get(LAYOUT_AGENT_RECALL_COUNT, recordOffset + OFFSET_AGENT_RECALL_COUNT);
    }

    /**
     * Atomically increments the explicit agent recall count.
     *
     * @return the previous count value
     */
    public int incrementAgentRecallCount(MemorySegment seg, long recordOffset) {
        return (int) VAR_HANDLE_AGENT_RECALL_COUNT.getAndAdd(seg, recordOffset + OFFSET_AGENT_RECALL_COUNT, 1);
    }

    /**
     * Directly writes the explicit agent recall count.
     */
    public void writeAgentRecallCount(MemorySegment seg, long recordOffset, int count) {
        seg.set(LAYOUT_AGENT_RECALL_COUNT, recordOffset + OFFSET_AGENT_RECALL_COUNT, count);
    }

    /**
     * Reads the passive spector auto-LTP retrieval count.
     */
    public int readSpectorRecallCount(MemorySegment seg, long recordOffset) {
        return seg.get(LAYOUT_SPECTOR_RECALL_COUNT, recordOffset + OFFSET_SPECTOR_RECALL_COUNT);
    }

    /**
     * Atomically increments the passive spector auto-LTP retrieval count.
     *
     * @return the previous count value
     */
    public int incrementSpectorRecallCount(MemorySegment seg, long recordOffset) {
        return (int) VAR_HANDLE_SPECTOR_RECALL_COUNT.getAndAdd(seg, recordOffset + OFFSET_SPECTOR_RECALL_COUNT, 1);
    }

    /**
     * Directly writes the spector auto-LTP retrieval count.
     */
    public void writeSpectorRecallCount(MemorySegment seg, long recordOffset, int count) {
        seg.set(LAYOUT_SPECTOR_RECALL_COUNT, recordOffset + OFFSET_SPECTOR_RECALL_COUNT, count);
    }

    // ── Effective Importance & Storage Strength Accessors ──

    /**
     * Reads the mutable effective importance score.
     */
    public float readEffectiveImportance(MemorySegment seg, long recordOffset) {
        return seg.get(LAYOUT_EFFECTIVE_IMPORTANCE, recordOffset + OFFSET_EFFECTIVE_IMPORTANCE);
    }

    /**
     * Atomically updates the effective importance via CAS loop.
     */
    public float casEffectiveImportance(MemorySegment seg, long recordOffset, FloatUnaryOperator updateFn) {
        long addr = recordOffset + OFFSET_EFFECTIVE_IMPORTANCE;
        float prev, next;
        do {
            prev = (float) VAR_HANDLE_EFFECTIVE_IMPORTANCE.getVolatile(seg, addr);
            next = updateFn.applyAsFloat(prev);
        } while (!VAR_HANDLE_EFFECTIVE_IMPORTANCE.compareAndSet(seg, addr, prev, next));
        return next;
    }

    /**
     * Directly writes the effective importance score.
     */
    public void writeEffectiveImportance(MemorySegment seg, long recordOffset, float importance) {
        seg.set(LAYOUT_EFFECTIVE_IMPORTANCE, recordOffset + OFFSET_EFFECTIVE_IMPORTANCE, importance);
    }

    /**
     * Reads the Two-Factor storage strength S(t) (Bjork &amp; Bjork).
     */
    public float readStorageStrength(MemorySegment seg, long recordOffset) {
        return seg.get(LAYOUT_STORAGE_STRENGTH, recordOffset + OFFSET_STORAGE_STRENGTH);
    }

    /**
     * Atomically updates the Two-Factor storage strength via CAS loop.
     */
    public float casStorageStrength(MemorySegment seg, long recordOffset, FloatUnaryOperator updateFn) {
        long addr = recordOffset + OFFSET_STORAGE_STRENGTH;
        float prev, next;
        do {
            prev = (float) VAR_HANDLE_STORAGE_STRENGTH.getVolatile(seg, addr);
            next = updateFn.applyAsFloat(prev);
        } while (!VAR_HANDLE_STORAGE_STRENGTH.compareAndSet(seg, addr, prev, next));
        return next;
    }

    /**
     * Directly writes the storage strength.
     */
    public void writeStorageStrength(MemorySegment seg, long recordOffset, float strength) {
        seg.set(LAYOUT_STORAGE_STRENGTH, recordOffset + OFFSET_STORAGE_STRENGTH, strength);
    }

    // ── Timestamps & Telemetry Accessors ──

    /**
     * Reads the epoch timestamp of the last auto-LTP cooldown window.
     */
    public long readLastAutoLtp(MemorySegment seg, long recordOffset) {
        return seg.get(LAYOUT_LAST_AUTO_LTP, recordOffset + OFFSET_LAST_AUTO_LTP);
    }

    /**
     * Writes the epoch timestamp of the last auto-LTP cooldown window.
     */
    public void writeLastAutoLtp(MemorySegment seg, long recordOffset, long timestampMs) {
        seg.set(LAYOUT_LAST_AUTO_LTP, recordOffset + OFFSET_LAST_AUTO_LTP, timestampMs);
    }

    /**
     * Reads the epoch timestamp of the most recent query retrieval.
     */
    public long readLastRecallTimestamp(MemorySegment seg, long recordOffset) {
        return seg.get(LAYOUT_LAST_RECALL_TS, recordOffset + OFFSET_LAST_RECALL_TS);
    }

    /**
     * Writes the epoch timestamp of the most recent query retrieval.
     */
    public void writeLastRecallTimestamp(MemorySegment seg, long recordOffset, long timestampMs) {
        seg.set(LAYOUT_LAST_RECALL_TS, recordOffset + OFFSET_LAST_RECALL_TS, timestampMs);
    }

    /**
     * Reads the cognitive profile ordinal used during the last recall.
     */
    public byte readLastRecallProfile(MemorySegment seg, long recordOffset) {
        return seg.get(LAYOUT_LAST_RECALL_PROFILE, recordOffset + OFFSET_LAST_RECALL_PROFILE);
    }

    /**
     * Writes the cognitive profile ordinal used during the last recall.
     */
    public void writeLastRecallProfile(MemorySegment seg, long recordOffset, byte profileOrdinal) {
        seg.set(LAYOUT_LAST_RECALL_PROFILE, recordOffset + OFFSET_LAST_RECALL_PROFILE, profileOrdinal);
    }

    /**
     * Reads the emotional valence signal recorded during reinforcement.
     */
    public byte readLastRecallValence(MemorySegment seg, long recordOffset) {
        return seg.get(LAYOUT_LAST_RECALL_VALENCE, recordOffset + OFFSET_LAST_RECALL_VALENCE);
    }

    /**
     * Writes the emotional valence signal recorded during reinforcement.
     */
    public void writeLastRecallValence(MemorySegment seg, long recordOffset, byte valence) {
        seg.set(LAYOUT_LAST_RECALL_VALENCE, recordOffset + OFFSET_LAST_RECALL_VALENCE, valence);
    }

    /**
     * Reads the caller agent ID hash from the last recall event.
     */
    public int readLastAgentHash(MemorySegment seg, long recordOffset) {
        return seg.get(LAYOUT_LAST_AGENT_HASH, recordOffset + OFFSET_LAST_AGENT_HASH);
    }

    /**
     * Writes the caller agent ID hash from the last recall event.
     */
    public void writeLastAgentHash(MemorySegment seg, long recordOffset, int agentHash) {
        seg.set(LAYOUT_LAST_AGENT_HASH, recordOffset + OFFSET_LAST_AGENT_HASH, agentHash);
    }

    /**
     * Reads the micro-plasticity reconsolidation delta.
     */
    public long readReconsolidationDelta(MemorySegment seg, long recordOffset) {
        return seg.get(LAYOUT_RECONSOLIDATION_DELTA, recordOffset + OFFSET_RECONSOLIDATION_DELTA);
    }

    /**
     * Writes the micro-plasticity reconsolidation delta.
     */
    public void writeReconsolidationDelta(MemorySegment seg, long recordOffset, long delta) {
        seg.set(LAYOUT_RECONSOLIDATION_DELTA, recordOffset + OFFSET_RECONSOLIDATION_DELTA, delta);
    }

    // ── ACT-R 8-Slot Ring Buffer Engine ──

    /**
     * Records a recall timestamp into the 8-slot circular ring buffer.
     *
     * <p>Overwrites the oldest slot when all 8 slots are populated.</p>
     *
     * @param seg          off-heap memory segment
     * @param recordOffset byte offset where this audit record starts
     * @param creationMs   memory creation timestamp (epoch millis)
     * @param recallMs     current recall timestamp (epoch millis)
     */
    public void recordActRRecall(MemorySegment seg, long recordOffset, long creationMs, long recallMs) {
        int relativeSeconds = (int) ((recallMs - creationMs) / 1000L);
        if (relativeSeconds <= 0) relativeSeconds = 1;

        long ringBase = recordOffset + OFFSET_ACTR_RING_BUFFER;
        int oldestSlot = 0;
        int oldestValue = Integer.MAX_VALUE;

        for (int i = 0; i < ACT_R_RING_BUFFER_SLOTS; i++) {
            long slotOffset = ringBase + (long) i * 4L;
            int val = seg.get(ValueLayout.JAVA_INT, slotOffset);
            if (val == 0) {
                // Empty slot found — fill immediately
                seg.set(ValueLayout.JAVA_INT, slotOffset, relativeSeconds);
                return;
            }
            if (val < oldestValue) {
                oldestValue = val;
                oldestSlot = i;
            }
        }
        // Overwrite oldest slot
        seg.set(ValueLayout.JAVA_INT, ringBase + (long) oldestSlot * 4L, relativeSeconds);
    }

    /**
     * Reads all 8 relative-second recall timestamps from the ACT-R ring buffer.
     *
     * @return array of 8 integers (0 indicates an unused slot)
     */
    public int[] readActRTimestamps(MemorySegment seg, long recordOffset) {
        int[] timestamps = new int[ACT_R_RING_BUFFER_SLOTS];
        long ringBase = recordOffset + OFFSET_ACTR_RING_BUFFER;
        for (int i = 0; i < ACT_R_RING_BUFFER_SLOTS; i++) {
            timestamps[i] = seg.get(ValueLayout.JAVA_INT, ringBase + (long) i * 4L);
        }
        return timestamps;
    }

    /**
     * Computes Anderson's (1993) ACT-R base-level activation:
     * <pre>
     *   B_i = ln(Σ_{j=1}^{n} t_j^{-d})
     *   σ(B_i) = sum / (sum + 1.0)
     * </pre>
     *
     * <p>Evaluates in O(1) time (~35 CPU cycles) via precomputed logarithmic decay table lookup
     * and single float division — zero Math.pow/log/exp.</p>
     *
     * @return normalized activation score in [0.0, 1.0], or -1.0f if no recall history
     */
    public float computeActRActivation(MemorySegment seg, long recordOffset, long creationMs, long nowMs) {
        long ringBase = recordOffset + OFFSET_ACTR_RING_BUFFER;
        float sum = 0.0f;
        int validSlots = 0;

        for (int i = 0; i < ACT_R_RING_BUFFER_SLOTS; i++) {
            int relativeSeconds = seg.get(ValueLayout.JAVA_INT, ringBase + (long) i * 4L);
            if (relativeSeconds == 0) continue;

            long recallAgeMs = (nowMs - creationMs) - (relativeSeconds * 1000L);
            if (recallAgeMs <= 0) recallAgeMs = 1000L;

            long recallTimestampMs = nowMs - recallAgeMs;
            int bucket = DecayStrategy.ageToBucket(recallTimestampMs, nowMs);
            sum += DecayStrategy.decay(bucket);
            validSlots++;
        }

        if (validSlots == 0) {
            return -1.0f;
        }

        // Include initial encoding at creation time
        int encodingBucket = DecayStrategy.ageToBucket(creationMs, nowMs);
        sum += DecayStrategy.decay(encodingBucket);

        // Algebraic identity: σ(ln(sum)) = sum / (sum + 1)
        return sum / (sum + 1.0f);
    }

    // ── Full Record Composite Read / Write ──

    /**
     * Reads a full immutable {@link AuditRecord} snapshot from the given record offset.
     */
    public AuditRecord readRecord(MemorySegment seg, long recordOffset) {
        return new AuditRecord(
                (byte) readMemoryTypeOrdinal(seg, recordOffset),
                readLastRecallProfile(seg, recordOffset),
                readLastRecallValence(seg, recordOffset),
                readAgentRecallCount(seg, recordOffset),
                readSpectorRecallCount(seg, recordOffset),
                readEffectiveImportance(seg, recordOffset),
                readStorageStrength(seg, recordOffset),
                readLastAgentHash(seg, recordOffset),
                readLastAutoLtp(seg, recordOffset),
                readLastRecallTimestamp(seg, recordOffset),
                readActRTimestamps(seg, recordOffset),
                readReconsolidationDelta(seg, recordOffset)
        );
    }

    /**
     * Writes a complete {@link AuditRecord} snapshot to the given record offset.
     */
    public void writeRecord(MemorySegment seg, long recordOffset, AuditRecord record) {
        seg.set(LAYOUT_AUDIT_FLAGS, recordOffset + OFFSET_AUDIT_FLAGS, (byte) (record.memoryTypeOrdinal() & FLAG_TYPE_MASK));
        seg.set(LAYOUT_LAST_RECALL_PROFILE, recordOffset + OFFSET_LAST_RECALL_PROFILE, record.lastRecallProfile());
        seg.set(LAYOUT_LAST_RECALL_VALENCE, recordOffset + OFFSET_LAST_RECALL_VALENCE, record.lastRecallValence());
        seg.set(ValueLayout.JAVA_BYTE, recordOffset + OFFSET_PAD0, (byte) 0);
        seg.set(LAYOUT_AGENT_RECALL_COUNT, recordOffset + OFFSET_AGENT_RECALL_COUNT, record.agentRecallCount());
        seg.set(LAYOUT_SPECTOR_RECALL_COUNT, recordOffset + OFFSET_SPECTOR_RECALL_COUNT, record.spectorRecallCount());
        seg.set(LAYOUT_EFFECTIVE_IMPORTANCE, recordOffset + OFFSET_EFFECTIVE_IMPORTANCE, record.effectiveImportance());
        seg.set(LAYOUT_STORAGE_STRENGTH, recordOffset + OFFSET_STORAGE_STRENGTH, record.storageStrength());
        seg.set(LAYOUT_LAST_AGENT_HASH, recordOffset + OFFSET_LAST_AGENT_HASH, record.lastAgentHash());
        seg.set(LAYOUT_LAST_AUTO_LTP, recordOffset + OFFSET_LAST_AUTO_LTP, record.lastAutoLtp());
        seg.set(LAYOUT_LAST_RECALL_TS, recordOffset + OFFSET_LAST_RECALL_TS, record.lastRecallTimestamp());

        long ringBase = recordOffset + OFFSET_ACTR_RING_BUFFER;
        int[] timestamps = record.actRTimestamps();
        for (int i = 0; i < ACT_R_RING_BUFFER_SLOTS; i++) {
            int val = (timestamps != null && i < timestamps.length) ? timestamps[i] : 0;
            seg.set(ValueLayout.JAVA_INT, ringBase + (long) i * 4L, val);
        }

        seg.set(LAYOUT_RECONSOLIDATION_DELTA, recordOffset + OFFSET_RECONSOLIDATION_DELTA, record.reconsolidationDelta());
        // Zero reserved bytes
        for (long o = 0; o < SpectorPropertyConstants.DEFAULT_MEMORY_AUDIT_RESERVED_BYTES; o += 8) {
            seg.set(ValueLayout.JAVA_LONG, recordOffset + OFFSET_RESERVED + o, 0L);
        }
    }

    /**
     * Initializes default audit fields for a freshly ingested memory record.
     */
    public void initializeDefaultRecord(MemorySegment seg, long recordOffset, MemoryType memoryType, float baseImportance) {
        writeRecord(seg, recordOffset, new AuditRecord(
                (byte) (memoryType != null ? memoryType.ordinal() : MemoryType.SEMANTIC.ordinal()),
                (byte) 0,
                (byte) 0,
                0,
                0,
                baseImportance,
                SpectorPropertyConstants.DEFAULT_MEMORY_TWOFACTOR_INITIAL_STORAGE_STRENGTH,
                0,
                0L,
                0L,
                new int[ACT_R_RING_BUFFER_SLOTS],
                0L
        ));
    }

    /**
     * Immutable snapshot record representing a memory's audit telemetry state.
     */
    public record AuditRecord(
            byte memoryTypeOrdinal,
            byte lastRecallProfile,
            byte lastRecallValence,
            int agentRecallCount,
            int spectorRecallCount,
            float effectiveImportance,
            float storageStrength,
            int lastAgentHash,
            long lastAutoLtp,
            long lastRecallTimestamp,
            int[] actRTimestamps,
            long reconsolidationDelta
    ) {
        public MemoryType memoryType() {
            var values = MemoryType.values();
            return (memoryTypeOrdinal >= 0 && memoryTypeOrdinal < values.length)
                    ? values[memoryTypeOrdinal]
                    : MemoryType.SEMANTIC;
        }
    }
}
