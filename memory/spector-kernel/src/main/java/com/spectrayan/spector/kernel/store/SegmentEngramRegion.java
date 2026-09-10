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

import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.layout.FixedEngramLayout;
import com.spectrayan.spector.kernel.layout.RegionLayout;
import com.spectrayan.spector.kernel.scan.ScanFilter;
import com.spectrayan.spector.kernel.scan.SlabScanner;
import com.spectrayan.spector.kernel.scan.SlotVisitor;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Adapter implementing {@link EngramRegion} over an existing off-heap {@link MemorySegment}.
 * Primarily used for tests, fixtures, and decoupled memory scans.
 */
public final class SegmentEngramRegion implements EngramRegion {

    private final MemorySegment segment;
    private final int recordCount;
    private final FixedEngramLayout layout;
    private final MemoryType type;
    private final long dataOffset;

    public SegmentEngramRegion(MemorySegment segment, int recordCount, FixedEngramLayout layout,
                              MemoryType type, long dataOffset) {
        this.segment = Objects.requireNonNull(segment, "segment");
        this.recordCount = recordCount;
        this.layout = Objects.requireNonNull(layout, "layout");
        this.type = type != null ? type : MemoryType.WORKING;
        this.dataOffset = dataOffset;
    }

    public SegmentEngramRegion(MemorySegment segment, int recordCount, FixedEngramLayout layout) {
        this(segment, recordCount, layout, MemoryType.WORKING, 0L);
    }

    public MemorySegment segment() {
        return segment;
    }

    @Override
    public long dataOffset() {
        return dataOffset;
    }

    @Override
    public long write(EncodingHeader header, byte[] quantized) {
        throw new UnsupportedOperationException("SegmentEngramRegion does not support write");
    }

    @Override
    public MemoryType type() {
        return type;
    }

    @Override
    public int visibleCount() {
        return recordCount;
    }

    @Override
    public float tombstoneRatio() {
        return 0.0f;
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public Path filePath() {
        return null;
    }

    @Override
    public byte readFlags(long offset) {
        return layout.readFlags(segment, offset);
    }

    @Override
    public boolean isTombstoned(long offset) {
        return EncodingHeaderFields.isTombstoned(readFlags(offset));
    }

    @Override
    public boolean isContradicted(long offset) {
        return EncodingHeaderFields.isContradicted(layout.readConsolidationFlags(segment, offset));
    }

    @Override
    public EncodingHeader readHeader(long offset) {
        return layout.readHeader(segment, offset);
    }

    @Override
    public byte[] readVector(long offset) {
        int vecBytes = layout.quantizedVecBytes();
        byte[] bytes = new byte[vecBytes];
        MemorySegment.copy(segment, ValueLayout.JAVA_BYTE,
                layout.vectorOffset(offset), bytes, 0, vecBytes);
        return bytes;
    }

    @Override
    public void tombstone(long offset) {
        layout.tombstone(segment, offset);
    }

    @Override
    public void markContradicted(long offset) {
        layout.markContradicted(segment, offset);
    }

    @Override
    public void markResolved(long offset) {
        layout.markResolved(segment, offset);
    }

    @Override
    public void markUnresolved(long offset) {
        layout.markUnresolved(segment, offset);
    }

    @Override
    public void force() {
        // no-op for in-memory segment
    }

    @Override
    public int size() {
        return recordCount;
    }

    @Override
    public int capacity() {
        return recordCount;
    }

    @Override
    public RegionLayout layout() {
        return layout;
    }

    @Override
    public void scan(float[] query, float[] mins, float[] scales, ScanFilter filter,
                     StrengthMemory strengthStore, int partitionSeq, SlotVisitor visitor) {
        SlabScanner.scan(segment, recordCount, layout, query, mins, scales, filter,
                strengthStore, type, dataOffset, partitionSeq, visitor);
    }

    @Override
    public void close() {
        // no-op: segment lifecycle is owned by caller
    }
}
