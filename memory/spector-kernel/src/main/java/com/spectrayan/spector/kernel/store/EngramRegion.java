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
package com.spectrayan.spector.kernel.store;

import java.lang.foreign.MemorySegment;
import java.nio.file.Path;

import com.spectrayan.spector.kernel.layout.RegionLayout;
import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.layout.FixedEngramLayout;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.scan.ScanFilter;
import com.spectrayan.spector.kernel.scan.SlotVisitor;

/**
 * Standardized interface for engram memory stores in Spector Memory (ADR-0030).
 *
 * <p>Unifies all four memory tiers (Semantic, Procedural, Working, Episodic)
 * under a single engram contract without constraining the physical stride or layout.</p>
 *
 * @since 1.5.0
 */
public sealed interface EngramRegion extends AutoCloseable permits AbstractEngramMemory, EpisodicMemory, SegmentEngramRegion {

    /** Size of the {@link RegionPreamble} region prologue in bytes. */
    int METADATA_PREAMBLE_BYTES = RegionPreamble.PREAMBLE_BYTES;

    /**
     * Returns the offset of the first data record, skipping the region preamble if persistent.
     */
    default long dataOffset() {
        return isPersistent() ? METADATA_PREAMBLE_BYTES : 0L;
    }

    /**
     * Writes a cognitive record (header + quantized vector payload).
     *
     * @param header cognitive header
     * @param quantized quantized vector bytes
     * @return byte offset where record was written
     */
    long write(EncodingHeader header, byte[] quantized);

    /**
     * Returns the memory tier type (WORKING, SEMANTIC, PROCEDURAL, EPISODIC).
     *
     * @return the cognitive memory type
     */
    MemoryType type();

    /**
     * Returns the maximum record index readable by concurrent readers (SWMR barrier).
     *
     * @return the visible record count
     */
    int visibleCount();

    /**
     * Returns the ratio of tombstoned records to total records (0.0 to 1.0).
     *
     * @return the tombstone ratio
     */
    float tombstoneRatio();

    /**
     * Returns whether this store is backed by an mmap persistent file.
     *
     * @return true if file-backed and persistent, false if in-memory
     */
    boolean isPersistent();

    /**
     * Returns the path to the backing file, or null if in-memory.
     *
     * @return the file path or null
     */
    Path filePath();

    /**
     * Reads the encoding header flags for the record at the given byte offset.
     */
    byte readFlags(long offset);

    /**
     * Returns true if the record at the given byte offset is tombstoned.
     */
    boolean isTombstoned(long offset);

    /**
     * Returns true if the record at the given byte offset is marked contradicted.
     */
    boolean isContradicted(long offset);

    /**
     * Reads the decoded encoding header for the record at the given byte offset.
     */
    EncodingHeader readHeader(long offset);

    /**
     * Reads the quantized vector payload for the record at the given byte offset,
     * or null if not present, unsupported, or {@linkplain #purge purged}.
     *
     * <p>Purged records return {@code null} rather than the zeros actually on disk. Returning the zeros
     * would be worse than useless: they decode to a legitimate all-zero vector and get scored as data, so a
     * purged record would quietly participate in similarity results instead of being absent from them.</p>
     */
    byte[] readVector(long offset);

    /**
     * Marks the record at the given byte offset as tombstoned.
     */
    void tombstone(long offset);

    /**
     * Physically overwrites the content of the record at the given byte offset with zeros, and marks it
     * both tombstoned and purged. Irreversible.
     *
     * <p>This is the operation {@link #tombstone(long)} is routinely mistaken for. Tombstoning sets a bit
     * and hides the record from recall; every byte of its content stays readable to anyone holding the
     * file or a backup of it. Purging destroys those bytes.</p>
     *
     * <p>No relocation, no slot reuse, no file shrink — record strides and offsets are unchanged, which is
     * what lets this run without touching any index. Reclaiming the space is compaction's job, separately.</p>
     *
     * @param offset byte offset of the record
     * @return the number of payload bytes overwritten
     */
    int purge(long offset);

    /**
     * Returns true if the record at the given byte offset has been {@linkplain #purge purged}.
     *
     * <p>Strictly stronger than {@link #isTombstoned(long)}. Any caller that dequantizes, scores or
     * reports the payload must consult this, because a zeroed payload is arithmetically a valid all-zero
     * vector, not a detectable absence.</p>
     */
    boolean isPurged(long offset);

    /**
     * Marks the record at the given byte offset as contradicted.
     */
    void markContradicted(long offset);

    /**
     * Marks the record at the given byte offset as resolved.
     */
    void markResolved(long offset);

    /**
     * Marks the record at the given byte offset as unresolved.
     */
    void markUnresolved(long offset);

    /**
     * Forces all pending writes to disk if persistent.
     */
    void force();

    /**
     * Returns true if this memory store is frozen (read-only / older partition).
     */
    default boolean isFrozen() {
        return false;
    }

    /**
     * Returns the number of active records.
     *
     * @return record count
     */
    int size();

    /**
     * Returns the maximum record capacity of this store.
     *
     * @return maximum capacity
     */
    int capacity();

    /**
     * Returns the memory region layout.
     *
     * @return the region layout
     */
    RegionLayout layout();

    /**
     * Calculates the byte offset of a record slot.
     *
     * @param index zero-based record index
     * @return byte offset of the record
     */
    default long recordOffset(long index) {
        return dataOffset() + index * layout().recordStride();
    }



    /**
     * Scans this engram region with the given filter and visitor.
     */
    void scan(float[] query, float[] mins, float[] scales, ScanFilter filter,
              StrengthMemory strengthStore, int partitionSeq, SlotVisitor visitor);

    /**
     * Scans this engram region with the given filter and visitor using default partition 0.
     */
    default void scan(float[] query, float[] mins, float[] scales, ScanFilter filter,
                      SlotVisitor visitor) {
        scan(query, mins, scales, filter, null, 0, visitor);
    }

    /**
     * Creates an engram region view over an existing memory segment.
     */
    static EngramRegion of(MemorySegment segment, int recordCount, FixedEngramLayout layout) {
        return new SegmentEngramRegion(segment, recordCount, layout);
    }

    /**
     * Creates an engram region view over an existing memory segment with explicit data offset.
     */
    static EngramRegion of(MemorySegment segment, int recordCount, FixedEngramLayout layout, long dataOffset) {
        return new SegmentEngramRegion(segment, recordCount, layout, MemoryType.WORKING, dataOffset);
    }

    /**
     * Creates an engram region view over an existing memory segment with explicit memory tier and data offset.
     */
    static EngramRegion of(MemorySegment segment, int recordCount, FixedEngramLayout layout,
                           MemoryType type, long dataOffset) {
        return new SegmentEngramRegion(segment, recordCount, layout, type, dataOffset);
    }

    /**
     * Closes the memory store and releases off-heap resources.
     */
    @Override
    void close();
}
