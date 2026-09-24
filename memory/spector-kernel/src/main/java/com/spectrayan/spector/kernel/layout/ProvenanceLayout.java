/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.kernel.layout;

import com.spectrayan.spector.kernel.api.ProvenanceSourceKind;
import com.spectrayan.spector.kernel.layout.RegionLayout;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * On-disk binary layout for the episodic→semantic Provenance Audit Region (ADR-0029).
 *
 * <p>Each 72-byte record captures the lineage between a batch of episodic conversation
 * turns and a single consolidated semantic (or procedural) memory. The layout supports
 * multi-pass consolidation: when new turns arrive in a previously-consolidated session,
 * a new pass produces additional provenance rows with an incremented {@code pass_number}.</p>
 *
 * <h3>Record Layout (72 bytes — 8-byte aligned, CRC32C protected)</h3>
 * <pre>
 *   Offset  Size  Field               Type     Description
 *   ──────  ────  ──────────────────  ───────  ───────────────────────────────────
 *    0      1B    flags               uint8    LIVE(0), TOMBSTONE(1), PARTIAL_RUN(2)
 *    1      1B    source_kind         uint8    EPISODIC = 1, SEMANTIC = 2, PROCEDURAL = 3
 *    2      1B    target_kind         uint8    SEMANTIC = 2, PROCEDURAL = 3
 *    3      1B    prefix_kind         uint8    Target ID prefix registry ordinal
 *    4      2B    pass_number         uint16   Monotonic consolidation pass counter (1-indexed)
 *    6      2B    turn_count          uint16   Turns covered by this row
 *    8      8B    session_id          int64    Matches episodic header session_id
 *   16      8B    target_tsid         int64    Raw 64-bit TSID of consolidated fact
 *   24      8B    consolidated_at_ms  int64    Epoch ms of the consolidation pass
 *   32      4B    partition_seq       int32    Partition holding source turns
 *   36      4B    first_seq           int32    First episodic sequence_id in the run
 *   40      4B    last_seq            int32    Last episodic sequence_id in the run
 *   44      4B    first_offset_hint   uint32   Region-relative byte offset hint
 *   48      4B    last_offset_hint    uint32   Region-relative byte offset hint
 *   52      1B    fact_index          uint8    Index of this fact within its batch
 *   53      1B    batch_fact_count    uint8    Total facts in this batch
 *   54      2B    content_hash_hi     uint16   Upper 16 bits of fact text CRC32C
 *   56      8B    source_tsid         int64    Source entity TSID when source is semantic/procedural
 *   64      4B    source_partition    int32    Source partition sequence or 0
 *   68      4B    crc32c              int32    Written/verified by AbstractRecordMemory
 *   ── 72B total stride ────────────────────────────────────────────────────────
 * </pre>
 *
 * @see com.spectrayan.spector.memory.cortex.ProvenanceMemory
 * @since 1.5.0
 */
public final class ProvenanceLayout implements RegionLayout {

    /** Singleton instance — stateless and safe to share. */
    public static final ProvenanceLayout INSTANCE = new ProvenanceLayout();

    /** Four-char layout identifier: {@code 'PROV'} (0x50524F56). */
    public static final int LAYOUT_ID = 0x50524F56;

    /** Current schema version for the provenance record format. */
    public static final int SCHEMA_VERSION = 1;

    /** Fixed size of each provenance record in bytes (68B payload + 4B CRC = 72B). */
    public static final int RECORD_STRIDE = 72;

    // ── State Constants ──
    //
    // IMPORTANT: this field is an EXCLUSIVE STATE ENUM, not a bitmask. Exactly one of these values is
    // stored; it is written by whole-byte overwrite (writeFlags, tombstone) and read back by EQUALITY
    // (isTombstoned, isLive), never by masking.
    //
    // Do not "harmonise" this with EncodingHeaderLayout's flags byte, which genuinely is a bitmask and
    // correctly uses OR. OR-ing here would corrupt state: STATE_TOMBSTONE (1) OR-ed onto STATE_PARTIAL_RUN
    // (2) yields 3, which matches no defined state, so isTombstoned's equality check would then report a
    // tombstoned record as LIVE. The constants were originally named FLAG_*, which is what invited that
    // reading; they are STATE_* now to close it off.
    //
    // Adding a state means adding a new distinct value here, not a new bit.

    /** Exclusive state 0: record is live and valid. */
    public static final byte STATE_LIVE = 0;

    /** Exclusive state 1: record has been tombstoned (logically deleted). */
    public static final byte STATE_TOMBSTONE = 1;

    /**
     * Exclusive state 2: partial run — consolidation was interrupted before all facts in the batch were
     * written. Still {@linkplain #isLive(MemorySegment, long) live}: the record itself is usable, but the
     * batch it belongs to is incomplete.
     */
    public static final byte STATE_PARTIAL_RUN = 2;

    // ── Source/Target Kind Constants ──

    /** Source kind: episodic memory (formerly EPISODIC_LOG). */
    public static final byte SOURCE_EPISODIC = com.spectrayan.spector.kernel.api.ProvenanceSourceKind.EPISODIC.code();

    /**
     * @deprecated Renamed to {@link #SOURCE_EPISODIC}.
     */
    @Deprecated
    public static final byte SOURCE_EPISODIC_LOG = SOURCE_EPISODIC;

    /** Source kind: semantic memory (ADR-0086 §5.5). */
    public static final byte SOURCE_SEMANTIC = com.spectrayan.spector.kernel.api.ProvenanceSourceKind.SEMANTIC.code();

    /** Source kind: procedural memory (ADR-0086 §5.5). */
    public static final byte SOURCE_PROCEDURAL = com.spectrayan.spector.kernel.api.ProvenanceSourceKind.PROCEDURAL.code();

    /** Target kind: semantic memory. */
    public static final byte TARGET_SEMANTIC = 2;

    /** Target kind: procedural memory. */
    public static final byte TARGET_PROCEDURAL = 3;

    // ── Field Offsets (relative to record start) ──

    public static final long OFFSET_FLAGS              = 0L;
    public static final long OFFSET_SOURCE_KIND        = 1L;
    public static final long OFFSET_TARGET_KIND        = 2L;
    public static final long OFFSET_PREFIX_KIND        = 3L;
    public static final long OFFSET_PASS_NUMBER        = 4L;
    public static final long OFFSET_TURN_COUNT         = 6L;
    public static final long OFFSET_SESSION_ID         = 8L;
    public static final long OFFSET_TARGET_TSID        = 16L;
    public static final long OFFSET_CONSOLIDATED_AT_MS = 24L;
    public static final long OFFSET_PARTITION_SEQ      = 32L;
    public static final long OFFSET_FIRST_SEQ          = 36L;
    public static final long OFFSET_LAST_SEQ           = 40L;
    public static final long OFFSET_FIRST_OFFSET_HINT  = 44L;
    public static final long OFFSET_LAST_OFFSET_HINT   = 48L;
    public static final long OFFSET_FACT_INDEX         = 52L;
    public static final long OFFSET_BATCH_FACT_COUNT   = 53L;
    public static final long OFFSET_CONTENT_HASH_HI    = 54L;
    public static final long OFFSET_RESERVED           = 56L;
    public static final long OFFSET_SOURCE_TSID        = 56L;
    public static final long OFFSET_RESERVED_2         = 64L;
    public static final long OFFSET_SOURCE_PARTITION   = 64L;
    public static final long OFFSET_CRC32C             = 68L;

    // ── ValueLayout Constants ──

    public static final ValueLayout.OfByte  LAYOUT_FLAGS           = ValueLayout.JAVA_BYTE;
    public static final ValueLayout.OfByte  LAYOUT_SOURCE_KIND     = ValueLayout.JAVA_BYTE;
    public static final ValueLayout.OfByte  LAYOUT_TARGET_KIND     = ValueLayout.JAVA_BYTE;
    public static final ValueLayout.OfByte  LAYOUT_PREFIX_KIND     = ValueLayout.JAVA_BYTE;
    public static final ValueLayout.OfShort LAYOUT_PASS_NUMBER     = ValueLayout.JAVA_SHORT_UNALIGNED;
    public static final ValueLayout.OfShort LAYOUT_TURN_COUNT      = ValueLayout.JAVA_SHORT_UNALIGNED;
    public static final ValueLayout.OfLong  LAYOUT_SESSION_ID      = ValueLayout.JAVA_LONG_UNALIGNED;
    public static final ValueLayout.OfLong  LAYOUT_TARGET_TSID     = ValueLayout.JAVA_LONG_UNALIGNED;
    public static final ValueLayout.OfLong  LAYOUT_CONSOLIDATED_AT = ValueLayout.JAVA_LONG_UNALIGNED;
    public static final ValueLayout.OfInt   LAYOUT_PARTITION_SEQ   = ValueLayout.JAVA_INT_UNALIGNED;
    public static final ValueLayout.OfInt   LAYOUT_FIRST_SEQ       = ValueLayout.JAVA_INT_UNALIGNED;
    public static final ValueLayout.OfInt   LAYOUT_LAST_SEQ        = ValueLayout.JAVA_INT_UNALIGNED;
    public static final ValueLayout.OfInt   LAYOUT_FIRST_OFFSET    = ValueLayout.JAVA_INT_UNALIGNED;
    public static final ValueLayout.OfInt   LAYOUT_LAST_OFFSET     = ValueLayout.JAVA_INT_UNALIGNED;
    public static final ValueLayout.OfByte  LAYOUT_FACT_INDEX      = ValueLayout.JAVA_BYTE;
    public static final ValueLayout.OfByte  LAYOUT_BATCH_FACT_CNT  = ValueLayout.JAVA_BYTE;
    public static final ValueLayout.OfShort LAYOUT_CONTENT_HASH    = ValueLayout.JAVA_SHORT_UNALIGNED;
    public static final ValueLayout.OfLong  LAYOUT_SOURCE_TSID     = ValueLayout.JAVA_LONG_UNALIGNED;
    public static final ValueLayout.OfInt   LAYOUT_SOURCE_PART     = ValueLayout.JAVA_INT_UNALIGNED;

    private ProvenanceLayout() {}

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
        return true;
    }

    @Override
    public String name() {
        return "ProvenanceLayout";
    }

    // ── Field Read Helpers ──

    /**
     * Reads the record's lifecycle state byte.
     *
     * <p>Compare the result by <b>equality</b> against {@link #STATE_LIVE}, {@link #STATE_TOMBSTONE} or
     * {@link #STATE_PARTIAL_RUN} — this byte is an exclusive state enum, not a bitmask. Masking it will
     * appear to work for {@code STATE_TOMBSTONE} and silently misclassify {@code STATE_PARTIAL_RUN}.</p>
     */
    public static byte readFlags(MemorySegment seg, long recordOff) {
        return seg.get(LAYOUT_FLAGS, recordOff + OFFSET_FLAGS);
    }

    public static byte readSourceKind(MemorySegment seg, long recordOff) {
        return seg.get(LAYOUT_SOURCE_KIND, recordOff + OFFSET_SOURCE_KIND);
    }

    public static ProvenanceSourceKind readSourceKindEnum(MemorySegment seg, long recordOff) {
        return ProvenanceSourceKind.fromCode(readSourceKind(seg, recordOff));
    }

    public static byte readTargetKind(MemorySegment seg, long recordOff) {
        return seg.get(LAYOUT_TARGET_KIND, recordOff + OFFSET_TARGET_KIND);
    }

    public static byte readPrefixKind(MemorySegment seg, long recordOff) {
        return seg.get(LAYOUT_PREFIX_KIND, recordOff + OFFSET_PREFIX_KIND);
    }

    public static short readPassNumber(MemorySegment seg, long recordOff) {
        return seg.get(LAYOUT_PASS_NUMBER, recordOff + OFFSET_PASS_NUMBER);
    }

    public static short readTurnCount(MemorySegment seg, long recordOff) {
        return seg.get(LAYOUT_TURN_COUNT, recordOff + OFFSET_TURN_COUNT);
    }

    public static long readSessionId(MemorySegment seg, long recordOff) {
        return seg.get(LAYOUT_SESSION_ID, recordOff + OFFSET_SESSION_ID);
    }

    public static long readTargetTsid(MemorySegment seg, long recordOff) {
        return seg.get(LAYOUT_TARGET_TSID, recordOff + OFFSET_TARGET_TSID);
    }

    public static long readConsolidatedAtMs(MemorySegment seg, long recordOff) {
        return seg.get(LAYOUT_CONSOLIDATED_AT, recordOff + OFFSET_CONSOLIDATED_AT_MS);
    }

    public static int readPartitionSeq(MemorySegment seg, long recordOff) {
        return seg.get(LAYOUT_PARTITION_SEQ, recordOff + OFFSET_PARTITION_SEQ);
    }

    public static int readFirstSeq(MemorySegment seg, long recordOff) {
        return seg.get(LAYOUT_FIRST_SEQ, recordOff + OFFSET_FIRST_SEQ);
    }

    public static int readLastSeq(MemorySegment seg, long recordOff) {
        return seg.get(LAYOUT_LAST_SEQ, recordOff + OFFSET_LAST_SEQ);
    }

    public static int readFirstOffsetHint(MemorySegment seg, long recordOff) {
        return seg.get(LAYOUT_FIRST_OFFSET, recordOff + OFFSET_FIRST_OFFSET_HINT);
    }

    public static int readLastOffsetHint(MemorySegment seg, long recordOff) {
        return seg.get(LAYOUT_LAST_OFFSET, recordOff + OFFSET_LAST_OFFSET_HINT);
    }

    public static byte readFactIndex(MemorySegment seg, long recordOff) {
        return seg.get(LAYOUT_FACT_INDEX, recordOff + OFFSET_FACT_INDEX);
    }

    public static byte readBatchFactCount(MemorySegment seg, long recordOff) {
        return seg.get(LAYOUT_BATCH_FACT_CNT, recordOff + OFFSET_BATCH_FACT_COUNT);
    }

    public static short readContentHashHi(MemorySegment seg, long recordOff) {
        return seg.get(LAYOUT_CONTENT_HASH, recordOff + OFFSET_CONTENT_HASH_HI);
    }

    public static long readSourceTsid(MemorySegment seg, long recordOff) {
        return seg.get(LAYOUT_SOURCE_TSID, recordOff + OFFSET_SOURCE_TSID);
    }

    public static int readSourcePartition(MemorySegment seg, long recordOff) {
        return seg.get(LAYOUT_SOURCE_PART, recordOff + OFFSET_SOURCE_PARTITION);
    }

    // ── Field Write Helpers ──

    /**
     * Writes the record's lifecycle state byte, replacing whatever was there.
     *
     * <p>Whole-byte overwrite is <b>correct</b> here and must stay that way: the byte holds one exclusive
     * state, not a set of flags. Pass exactly one of {@link #STATE_LIVE}, {@link #STATE_TOMBSTONE} or
     * {@link #STATE_PARTIAL_RUN}. Never OR a new value onto the old one.</p>
     */
    public static void writeFlags(MemorySegment seg, long recordOff, byte flags) {
        seg.set(LAYOUT_FLAGS, recordOff + OFFSET_FLAGS, flags);
    }

    public static void writeSourceKind(MemorySegment seg, long recordOff, byte kind) {
        seg.set(LAYOUT_SOURCE_KIND, recordOff + OFFSET_SOURCE_KIND, kind);
    }

    public static void writeSourceKind(MemorySegment seg, long recordOff, ProvenanceSourceKind kind) {
        writeSourceKind(seg, recordOff, kind.code());
    }

    public static void writeTargetKind(MemorySegment seg, long recordOff, byte kind) {
        seg.set(LAYOUT_TARGET_KIND, recordOff + OFFSET_TARGET_KIND, kind);
    }

    public static void writePrefixKind(MemorySegment seg, long recordOff, byte kind) {
        seg.set(LAYOUT_PREFIX_KIND, recordOff + OFFSET_PREFIX_KIND, kind);
    }

    public static void writePassNumber(MemorySegment seg, long recordOff, short passNumber) {
        seg.set(LAYOUT_PASS_NUMBER, recordOff + OFFSET_PASS_NUMBER, passNumber);
    }

    public static void writeTurnCount(MemorySegment seg, long recordOff, short turnCount) {
        seg.set(LAYOUT_TURN_COUNT, recordOff + OFFSET_TURN_COUNT, turnCount);
    }

    public static void writeSessionId(MemorySegment seg, long recordOff, long sessionId) {
        seg.set(LAYOUT_SESSION_ID, recordOff + OFFSET_SESSION_ID, sessionId);
    }

    public static void writeTargetTsid(MemorySegment seg, long recordOff, long tsid) {
        seg.set(LAYOUT_TARGET_TSID, recordOff + OFFSET_TARGET_TSID, tsid);
    }

    public static void writeConsolidatedAtMs(MemorySegment seg, long recordOff, long timestampMs) {
        seg.set(LAYOUT_CONSOLIDATED_AT, recordOff + OFFSET_CONSOLIDATED_AT_MS, timestampMs);
    }

    public static void writePartitionSeq(MemorySegment seg, long recordOff, int partitionSeq) {
        seg.set(LAYOUT_PARTITION_SEQ, recordOff + OFFSET_PARTITION_SEQ, partitionSeq);
    }

    public static void writeFirstSeq(MemorySegment seg, long recordOff, int firstSeq) {
        seg.set(LAYOUT_FIRST_SEQ, recordOff + OFFSET_FIRST_SEQ, firstSeq);
    }

    public static void writeLastSeq(MemorySegment seg, long recordOff, int lastSeq) {
        seg.set(LAYOUT_LAST_SEQ, recordOff + OFFSET_LAST_SEQ, lastSeq);
    }

    public static void writeFirstOffsetHint(MemorySegment seg, long recordOff, int offset) {
        seg.set(LAYOUT_FIRST_OFFSET, recordOff + OFFSET_FIRST_OFFSET_HINT, offset);
    }

    public static void writeLastOffsetHint(MemorySegment seg, long recordOff, int offset) {
        seg.set(LAYOUT_LAST_OFFSET, recordOff + OFFSET_LAST_OFFSET_HINT, offset);
    }

    public static void writeFactIndex(MemorySegment seg, long recordOff, byte index) {
        seg.set(LAYOUT_FACT_INDEX, recordOff + OFFSET_FACT_INDEX, index);
    }

    public static void writeBatchFactCount(MemorySegment seg, long recordOff, byte count) {
        seg.set(LAYOUT_BATCH_FACT_CNT, recordOff + OFFSET_BATCH_FACT_COUNT, count);
    }

    public static void writeContentHashHi(MemorySegment seg, long recordOff, short hash) {
        seg.set(LAYOUT_CONTENT_HASH, recordOff + OFFSET_CONTENT_HASH_HI, hash);
    }

    public static void writeSourceTsid(MemorySegment seg, long recordOff, long sourceTsid) {
        seg.set(LAYOUT_SOURCE_TSID, recordOff + OFFSET_SOURCE_TSID, sourceTsid);
    }

    public static void writeSourcePartition(MemorySegment seg, long recordOff, int sourcePartition) {
        seg.set(LAYOUT_SOURCE_PART, recordOff + OFFSET_SOURCE_PARTITION, sourcePartition);
    }

    // ── Composite Read/Write ──

    /**
     * Reads a complete immutable {@link ProvenanceState} snapshot from the given record offset.
     *
     * @param seg       off-heap memory segment
     * @param recordOff byte offset where this provenance record starts
     * @return immutable snapshot of the provenance record
     */
    public static ProvenanceState readRecord(MemorySegment seg, long recordOff) {
        return new ProvenanceState(
                readFlags(seg, recordOff),
                readSourceKind(seg, recordOff),
                readTargetKind(seg, recordOff),
                readPrefixKind(seg, recordOff),
                readPassNumber(seg, recordOff),
                readTurnCount(seg, recordOff),
                readSessionId(seg, recordOff),
                readTargetTsid(seg, recordOff),
                readConsolidatedAtMs(seg, recordOff),
                readPartitionSeq(seg, recordOff),
                readFirstSeq(seg, recordOff),
                readLastSeq(seg, recordOff),
                readFirstOffsetHint(seg, recordOff),
                readLastOffsetHint(seg, recordOff),
                readFactIndex(seg, recordOff),
                readBatchFactCount(seg, recordOff),
                readContentHashHi(seg, recordOff),
                readSourceTsid(seg, recordOff),
                readSourcePartition(seg, recordOff)
        );
    }

    /**
     * Writes a complete {@link ProvenanceState} snapshot to the given record offset.
     *
     * <p>Does NOT write the CRC32C — that is handled by {@link
     * com.spectrayan.spector.kernel.shape.AbstractRecordMemory#write}.</p>
     *
     * @param seg       off-heap memory segment
     * @param recordOff byte offset where this provenance record starts
     * @param state     the provenance state to write
     */
    public static void writeRecord(MemorySegment seg, long recordOff, ProvenanceState state) {
        writeFlags(seg, recordOff, state.flags());
        writeSourceKind(seg, recordOff, state.sourceKind());
        writeTargetKind(seg, recordOff, state.targetKind());
        writePrefixKind(seg, recordOff, state.prefixKind());
        writePassNumber(seg, recordOff, state.passNumber());
        writeTurnCount(seg, recordOff, state.turnCount());
        writeSessionId(seg, recordOff, state.sessionId());
        writeTargetTsid(seg, recordOff, state.targetTsid());
        writeConsolidatedAtMs(seg, recordOff, state.consolidatedAtMs());
        writePartitionSeq(seg, recordOff, state.partitionSeq());
        writeFirstSeq(seg, recordOff, state.firstSeq());
        writeLastSeq(seg, recordOff, state.lastSeq());
        writeFirstOffsetHint(seg, recordOff, state.firstOffsetHint());
        writeLastOffsetHint(seg, recordOff, state.lastOffsetHint());
        writeFactIndex(seg, recordOff, state.factIndex());
        writeBatchFactCount(seg, recordOff, state.batchFactCount());
        writeContentHashHi(seg, recordOff, state.contentHashHi());
        writeSourceTsid(seg, recordOff, state.sourceTsid());
        writeSourcePartition(seg, recordOff, state.sourcePartition());
    }

    /**
     * Tombstones a provenance record by setting its flags to {@link #STATE_TOMBSTONE}.
     *
     * @param seg       off-heap memory segment
     * @param recordOff byte offset where the record starts
     */
    public static void tombstone(MemorySegment seg, long recordOff) {
        writeFlags(seg, recordOff, STATE_TOMBSTONE);
    }

    /**
     * Returns {@code true} if the record at the given offset is tombstoned.
     */
    public static boolean isTombstoned(MemorySegment seg, long recordOff) {
        return readFlags(seg, recordOff) == STATE_TOMBSTONE;
    }

    /**
     * Returns {@code true} if the record at the given offset is live (not tombstoned).
     */
    public static boolean isLive(MemorySegment seg, long recordOff) {
        byte flags = readFlags(seg, recordOff);
        return flags == STATE_LIVE || flags == STATE_PARTIAL_RUN;
    }

    // ── Immutable Snapshot Record ──

    /**
     * Immutable snapshot record representing a single provenance edge.
     *
     * <p>Captures the relationship between a range of episodic turns in a session
     * and a single consolidated semantic or procedural memory.</p>
     */
    public record ProvenanceState(
            byte flags,
            byte sourceKind,
            byte targetKind,
            byte prefixKind,
            short passNumber,
            short turnCount,
            long sessionId,
            long targetTsid,
            long consolidatedAtMs,
            int partitionSeq,
            int firstSeq,
            int lastSeq,
            int firstOffsetHint,
            int lastOffsetHint,
            byte factIndex,
            byte batchFactCount,
            short contentHashHi,
            long sourceTsid,
            int sourcePartition
    ) {
        /**
         * Backward-compatible constructor for 17-field provenance records (prior to ADR-0086 §5.5).
         */
        public ProvenanceState(
                byte flags, byte sourceKind, byte targetKind, byte prefixKind,
                short passNumber, short turnCount, long sessionId, long targetTsid,
                long consolidatedAtMs, int partitionSeq, int firstSeq, int lastSeq,
                int firstOffsetHint, int lastOffsetHint, byte factIndex, byte batchFactCount,
                short contentHashHi
        ) {
            this(flags, sourceKind, targetKind, prefixKind, passNumber, turnCount,
                    sessionId, targetTsid, consolidatedAtMs, partitionSeq, firstSeq, lastSeq,
                    firstOffsetHint, lastOffsetHint, factIndex, batchFactCount, contentHashHi,
                    0L, 0);
        }

        /** Returns {@code true} if this record is live (not tombstoned). */
        public boolean isLive() {
            return flags == STATE_LIVE || flags == STATE_PARTIAL_RUN;
        }

        /** Returns the source kind as a {@link ProvenanceSourceKind} enum. */
        public ProvenanceSourceKind sourceKindEnum() {
            return ProvenanceSourceKind.fromCode(sourceKind);
        }
    }
}
