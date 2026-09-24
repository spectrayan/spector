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
package com.spectrayan.spector.kernel.bundle;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.zip.CRC32C;

/**
 * 64-byte partition summary header persisted at offset {@value #OFFSET} in the partition bundle header page.
 *
 * <p>Enables $O(\text{partitions})$ cold start by persisting exact temporal bounds, 128-bit synaptic
 * tag Bloom filter masks, and per-tier visible record counts at partition freeze time. Validated with
 * CRC32C over the first 60 bytes.</p>
 *
 * <h3>Binary Layout (64 bytes, cache-line aligned)</h3>
 * <pre>
 * Offset  Size  Type    Field Name            Description
 * ──────  ────  ──────  ───────────────────   ──────────────────────────────────────────────
 * 0       4     int     magic                 0x5350534D ('SPSM')
 * 4       4     int     version               Format version (1)
 * 8       4     int     seq                   Partition sequence number
 * 12      4     int     reserved              0 (padding for 8-byte alignment)
 * 16      8     long    minTimestampMs        Earliest record timestamp in partition (inclusive)
 * 24      8     long    maxTimestampMs        Latest record timestamp in partition (inclusive)
 * 32      8     long    synapticTagMaskLo     Bitwise-OR of lower 64 bits of synaptic tag bloom
 * 40      8     long    synapticTagMaskHi     Bitwise-OR of upper 64 bits of synaptic tag bloom
 * 48      4     int     semanticCount         Visible semantic records count
 * 52      4     int     episodicCount         Visible episodic records count
 * 56      4     int     proceduralCount       Visible procedural records count
 * 60      4     int     crc32c                CRC32C checksum over bytes [0..59]
 * </pre>
 *
 * @param seq partition sequence number
 * @param minTimestampMs earliest record timestamp in epoch ms
 * @param maxTimestampMs latest record timestamp in epoch ms
 * @param synapticTagMaskLo cumulative bitwise-OR lower 64 bits of synaptic tag masks
 * @param synapticTagMaskHi cumulative bitwise-OR upper 64 bits of synaptic tag masks
 * @param semanticCount visible semantic record count
 * @param episodicCount visible episodic record count
 * @param proceduralCount visible procedural record count
 */
public record PartitionSummaryHeader(
        int seq,
        long minTimestampMs,
        long maxTimestampMs,
        long synapticTagMaskLo,
        long synapticTagMaskHi,
        int semanticCount,
        int episodicCount,
        int proceduralCount
) {
    public static final long OFFSET = 512L;
    public static final long SIZE = 64L;
    public static final int MAGIC = 0x5350534D; // 'SPSM'
    public static final int VERSION = 1;

    // Field offsets within the 64-byte block
    private static final long VH_MAGIC = 0L;
    private static final long VH_VERSION = 4L;
    private static final long VH_SEQ = 8L;
    private static final long VH_RESERVED = 12L;
    private static final long VH_MIN_TS = 16L;
    private static final long VH_MAX_TS = 24L;
    private static final long VH_TAG_MASK_LO = 32L;
    private static final long VH_TAG_MASK_HI = 40L;
    private static final long VH_SEMANTIC_COUNT = 48L;
    private static final long VH_EPISODIC_COUNT = 52L;
    private static final long VH_PROCEDURAL_COUNT = 56L;
    private static final long VH_CRC32C = 60L;

    /**
     * Writes this summary header to the memory segment at default offset {@value #OFFSET}.
     *
     * @param seg memory segment to write to
     */
    public void write(MemorySegment seg) {
        write(seg, OFFSET);
    }

    /**
     * Writes this summary header to the memory segment at the specified offset.
     *
     * @param seg memory segment to write to
     * @param offset base offset in segment
     */
    public void write(MemorySegment seg, long offset) {
        if (seg == null || seg.byteSize() < offset + SIZE) {
            throw new IllegalArgumentException(
                    "Segment size " + (seg == null ? 0 : seg.byteSize())
                            + " too small to write PartitionSummaryHeader at " + offset);
        }
        seg.set(ValueLayout.JAVA_INT, offset + VH_MAGIC, MAGIC);
        seg.set(ValueLayout.JAVA_INT, offset + VH_VERSION, VERSION);
        seg.set(ValueLayout.JAVA_INT, offset + VH_SEQ, seq);
        seg.set(ValueLayout.JAVA_INT, offset + VH_RESERVED, 0);
        seg.set(ValueLayout.JAVA_LONG, offset + VH_MIN_TS, minTimestampMs);
        seg.set(ValueLayout.JAVA_LONG, offset + VH_MAX_TS, maxTimestampMs);
        seg.set(ValueLayout.JAVA_LONG, offset + VH_TAG_MASK_LO, synapticTagMaskLo);
        seg.set(ValueLayout.JAVA_LONG, offset + VH_TAG_MASK_HI, synapticTagMaskHi);
        seg.set(ValueLayout.JAVA_INT, offset + VH_SEMANTIC_COUNT, semanticCount);
        seg.set(ValueLayout.JAVA_INT, offset + VH_EPISODIC_COUNT, episodicCount);
        seg.set(ValueLayout.JAVA_INT, offset + VH_PROCEDURAL_COUNT, proceduralCount);

        int crc = computeCrc(seg, offset);
        seg.set(ValueLayout.JAVA_INT, offset + VH_CRC32C, crc);
    }

    /**
     * Static helper to write a header to a memory segment at {@value #OFFSET}.
     *
     * @param seg memory segment to write to
     * @param header partition summary header
     */
    public static void write(MemorySegment seg, PartitionSummaryHeader header) {
        if (header != null) {
            header.write(seg, OFFSET);
        }
    }

    /**
     * Checks if the partition summary header at {@value #OFFSET} is valid (magic, version, and CRC32C match).
     *
     * @param seg memory segment to inspect
     * @return true if magic, version, and CRC32C are valid
     */
    public static boolean isValid(MemorySegment seg) {
        return isValid(seg, OFFSET);
    }

    /**
     * Checks if the partition summary header at the specified offset is valid (magic, version, and CRC32C match).
     *
     * @param seg memory segment to inspect
     * @param offset base offset to check
     * @return true if magic, version, and CRC32C are valid
     */
    public static boolean isValid(MemorySegment seg, long offset) {
        if (seg == null || seg.byteSize() < offset + SIZE) {
            return false;
        }
        int magic = seg.get(ValueLayout.JAVA_INT, offset + VH_MAGIC);
        if (magic != MAGIC) {
            return false;
        }
        int version = seg.get(ValueLayout.JAVA_INT, offset + VH_VERSION);
        if (version != VERSION) {
            return false;
        }
        int expectedCrc = computeCrc(seg, offset);
        int actualCrc = seg.get(ValueLayout.JAVA_INT, offset + VH_CRC32C);
        return expectedCrc == actualCrc;
    }

    /**
     * Reads a validated PartitionSummaryHeader from the segment at {@value #OFFSET}.
     *
     * @param seg memory segment to read from
     * @return the header, or {@code null} if absent or CRC validation fails
     */
    public static PartitionSummaryHeader read(MemorySegment seg) {
        return read(seg, OFFSET);
    }

    /**
     * Reads a validated PartitionSummaryHeader from the segment at the specified offset.
     *
     * @param seg memory segment to read from
     * @param offset base offset to read from
     * @return the header, or {@code null} if absent or CRC validation fails
     */
    public static PartitionSummaryHeader read(MemorySegment seg, long offset) {
        if (!isValid(seg, offset)) {
            return null;
        }
        int seq = seg.get(ValueLayout.JAVA_INT, offset + VH_SEQ);
        long minTs = seg.get(ValueLayout.JAVA_LONG, offset + VH_MIN_TS);
        long maxTs = seg.get(ValueLayout.JAVA_LONG, offset + VH_MAX_TS);
        long tagLo = seg.get(ValueLayout.JAVA_LONG, offset + VH_TAG_MASK_LO);
        long tagHi = seg.get(ValueLayout.JAVA_LONG, offset + VH_TAG_MASK_HI);
        int semCount = seg.get(ValueLayout.JAVA_INT, offset + VH_SEMANTIC_COUNT);
        int epiCount = seg.get(ValueLayout.JAVA_INT, offset + VH_EPISODIC_COUNT);
        int procCount = seg.get(ValueLayout.JAVA_INT, offset + VH_PROCEDURAL_COUNT);

        return new PartitionSummaryHeader(seq, minTs, maxTs, tagLo, tagHi, semCount, epiCount, procCount);
    }

    /**
     * Computes the CRC32C over the first 60 bytes of the 64-byte block.
     *
     * @param seg memory segment containing the block
     * @param offset base offset of the 64-byte block
     * @return CRC32C checksum as a 32-bit integer
     */
    public static int computeCrc(MemorySegment seg, long offset) {
        CRC32C crc32c = new CRC32C();
        crc32c.update(seg.asSlice(offset, 60).asByteBuffer());
        return (int) crc32c.getValue();
    }
}
