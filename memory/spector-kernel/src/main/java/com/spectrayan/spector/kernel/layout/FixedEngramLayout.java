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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorStorageException;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.kernel.api.EngramSource;
import com.spectrayan.spector.kernel.api.SourceModality;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.engram.EncodingHeaderLayout;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.shape.AbstractRecordMemory;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Common layout interface for fixed-stride cognitive engrams (ADR-0030).
 *
 * <p>Implemented by {@link SemanticLayout}, {@link ProceduralLayout},
 * {@link WorkingLayout}, and {@link EngramLayout}.</p>
 *
 * <p>A fixed-stride engram layout pairs a 64-byte {@link EncodingHeaderLayout}
 * with a trailing quantized vector of {@code quantizedVecBytes} bytes.</p>
 *
 * @since 1.5.0
 * @see SemanticLayout
 * @see ProceduralLayout
 * @see WorkingLayout
 * @see EngramLayout
 */
public interface FixedEngramLayout extends RegionLayout {

    /** Common layout ID for all fixed-stride engrams ('COG\0'). */
    int LAYOUT_ID = 0x434F4700;

    /** Current schema version for fixed-stride engram regions (v2 adds per-record CRC32C). */
    int SCHEMA_VERSION = 2;

    /** Minimum readable schema version (v1 regions written without per-record CRC32C). */
    int MIN_READABLE_SCHEMA_VERSION = 1;

    /** Number of bytes allocated for the quantized vector payload. */
    int quantizedVecBytes();

    /** The encoding header layout composed by this engram layout. */
    EncodingHeaderLayout headerLayout();

    /** Size of the encoding header in bytes (always 64 bytes). */
    default int headerBytes() {
        return headerLayout().headerBytes();
    }

    /**
     * Total record stride in bytes: header bytes (64) + quantized vector bytes.
     */
    default int stride() {
        return headerBytes() + quantizedVecBytes();
    }

    @Override
    default int recordStride() {
        return stride();
    }

    @Override
    default boolean crcEnabled() {
        return true;
    }

    /**
     * Offset where the quantized vector payload begins within a record.
     */
    default long vectorOffset(long recordOffset) {
        return recordOffset + headerBytes();
    }

    /**
     * Computes and writes the non-zero CRC32C checksum for the quantized vector payload
     * directly over the off-heap segment slice.
     */
    default void writePayloadCrc(MemorySegment segment, long recordOffset) {
        int payloadBytes = quantizedVecBytes();
        long vecOff = vectorOffset(recordOffset);
        if (segment == null || payloadBytes <= 0 || segment.byteSize() < vecOff + payloadBytes) {
            return;
        }
        var lock = EncodingHeaderLayout.rwLockFor(recordOffset).writeLock();
        lock.lock();
        try {
            int payloadCrc = AbstractRecordMemory.nonZeroCrc32c(segment, vecOff, payloadBytes);
            segment.set(ValueLayout.JAVA_INT_UNALIGNED,
                    recordOffset + EncodingHeaderFields.OFFSET_V2_PAYLOAD_CRC32C, payloadCrc);
            segment.set(ValueLayout.JAVA_BYTE,
                    recordOffset + EncodingHeaderFields.OFFSET_V2_CRC_MARKER, (byte) 1);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Computes and writes both the header and payload CRC32C checksums for the record at {@code recordOffset}.
     */
    default void writeRecordCrc(MemorySegment segment, long recordOffset) {
        var lock = EncodingHeaderLayout.rwLockFor(recordOffset).writeLock();
        lock.lock();
        try {
            headerLayout().updateHeaderCrc(segment, recordOffset);
            writePayloadCrc(segment, recordOffset);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Verifies the quantized vector payload CRC32C when present (skipping v1 records where CRC is 0).
     */
    default void verifyPayloadCrc(MemorySegment segment, long recordOffset, Object recordRef) {
        if (segment == null || segment.byteSize() < recordOffset + headerBytes()) {
            return;
        }
        int storedPayloadCrc = segment.get(ValueLayout.JAVA_INT_UNALIGNED,
                recordOffset + EncodingHeaderFields.OFFSET_V2_PAYLOAD_CRC32C);
        if (storedPayloadCrc == 0) {
            return;
        }
        int payloadBytes = quantizedVecBytes();
        long vecOff = vectorOffset(recordOffset);
        if (payloadBytes <= 0 || segment.byteSize() < vecOff + payloadBytes) {
            return;
        }
        int actualPayloadCrc = AbstractRecordMemory.nonZeroCrc32c(segment, vecOff, payloadBytes);
        if (storedPayloadCrc == actualPayloadCrc) {
            return;
        }
        var readLock = EncodingHeaderLayout.rwLockFor(recordOffset).readLock();
        readLock.lock();
        try {
            storedPayloadCrc = segment.get(ValueLayout.JAVA_INT_UNALIGNED,
                    recordOffset + EncodingHeaderFields.OFFSET_V2_PAYLOAD_CRC32C);
            if (storedPayloadCrc == 0) {
                return;
            }
            actualPayloadCrc = AbstractRecordMemory.nonZeroCrc32c(segment, vecOff, payloadBytes);
            if (storedPayloadCrc != actualPayloadCrc) {
                throw new SpectorStorageException(ErrorCode.RECORD_CRC_CORRUPTED, recordRef);
            }
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Verifies both the header and payload CRC32C checksums for the record at {@code recordOffset}.
     */
    default void verifyRecordCrc(MemorySegment segment, long recordOffset, Object recordRef) {
        headerLayout().verifyHeaderCrc(segment, recordOffset, recordRef);
        verifyPayloadCrc(segment, recordOffset, recordRef);
    }

    // ── Write operations (delegate to headerLayout) ──

    default byte readHeaderVersion(MemorySegment segment, long offset) {
        return headerLayout().readHeaderVersion(segment, offset);
    }

    default void writeFlags(MemorySegment segment, long offset, byte flags) {
        headerLayout().writeFlags(segment, offset, flags);
    }

    default void writeExactNorm(MemorySegment segment, long offset, float exactNorm) {
        headerLayout().writeExactNorm(segment, offset, exactNorm);
    }

    default void writeCentroidId(MemorySegment segment, long offset, short centroidId) {
        headerLayout().writeCentroidId(segment, offset, centroidId);
    }

    default void writeSourceCode(MemorySegment segment, long offset, byte sourceCode) {
        headerLayout().writeSourceCode(segment, offset, sourceCode);
    }

    default boolean compareAndSetValence(MemorySegment segment, long offset, byte expected, byte update) {
        return headerLayout().compareAndSetValence(segment, offset, expected, update);
    }

    default void writeValenceRelease(MemorySegment segment, long offset, byte valence) {
        headerLayout().writeValenceRelease(segment, offset, valence);
    }

    default void writeHeader(MemorySegment segment, long offset, EncodingHeader header) {
        headerLayout().writeHeader(segment, offset, header);
    }

    default EncodingHeader readHeader(MemorySegment segment, long offset) {
        return headerLayout().readHeader(segment, offset);
    }

    // ── Field-level accessors (delegate to headerLayout) ──

    default byte readFlags(MemorySegment segment, long offset) {
        return headerLayout().readFlags(segment, offset);
    }

    default long readSynapticTags(MemorySegment segment, long offset) {
        return headerLayout().readSynapticTags(segment, offset);
    }

    default long readSynapticTagsLo(MemorySegment segment, long offset) {
        return headerLayout().readSynapticTagsLo(segment, offset);
    }

    default long readSynapticTagsHi(MemorySegment segment, long offset) {
        return headerLayout().readSynapticTagsHi(segment, offset);
    }

    default void writeSynapticTags(MemorySegment segment, long offset, long tagsLo, long tagsHi) {
        headerLayout().writeSynapticTags(segment, offset, tagsLo, tagsHi);
    }

    default void mergeSynapticTags128(MemorySegment segment, long offset, long tagsLo, long tagsHi) {
        headerLayout().mergeSynapticTags128(segment, offset, tagsLo, tagsHi);
    }

    default SourceModality readSourceModality(MemorySegment segment, long offset) {
        byte flags = headerLayout().readFlags(segment, offset);
        return SourceModality.fromOrdinal(EncodingHeaderFields.sourceModalityOrdinal(flags));
    }

    default byte readSourceCode(MemorySegment segment, long offset) {
        return headerLayout().readSourceCode(segment, offset);
    }

    default EngramSource readSource(MemorySegment segment, long offset) {
        return headerLayout().readSource(segment, offset);
    }

    default void writeSource(MemorySegment segment, long offset, EngramSource source) {
        headerLayout().writeSource(segment, offset, source);
    }

    default byte readValence(MemorySegment segment, long offset) {
        return headerLayout().readValence(segment, offset);
    }

    default long readTimestamp(MemorySegment segment, long offset) {
        return headerLayout().readTimestamp(segment, offset);
    }

    default float readImportance(MemorySegment segment, long offset) {
        return headerLayout().readImportance(segment, offset);
    }

    default int readAgentRecallCount(MemorySegment segment, long offset) {
        return headerLayout().readAgentRecallCount(segment, offset);
    }

    default byte readArousal(MemorySegment segment, long offset) {
        return headerLayout().readArousal(segment, offset);
    }

    default float readStorageStrength(MemorySegment segment, long offset) {
        return headerLayout().readStorageStrength(segment, offset);
    }

    default float readExactNorm(MemorySegment segment, long offset) {
        return headerLayout().readExactNorm(segment, offset);
    }

    default short readCentroidId(MemorySegment segment, long offset) {
        return headerLayout().readCentroidId(segment, offset);
    }

    default int incrementAgentRecallCount(MemorySegment segment, long offset) {
        return headerLayout().incrementAgentRecallCount(segment, offset);
    }

    default int readSpectorRecallCount(MemorySegment segment, long offset) {
        return headerLayout().readSpectorRecallCount(segment, offset);
    }

    default int incrementSpectorRecallCount(MemorySegment segment, long offset) {
        return headerLayout().incrementSpectorRecallCount(segment, offset);
    }

    default long readLastAutoLtp(MemorySegment segment, long offset) {
        return headerLayout().readLastAutoLtp(segment, offset);
    }

    default void writeLastAutoLtp(MemorySegment segment, long offset, long timestampMs) {
        headerLayout().writeLastAutoLtp(segment, offset, timestampMs);
    }

    default void tombstone(MemorySegment segment, long offset) {
        headerLayout().markTombstoned(segment, offset);
    }

    default int purge(MemorySegment segment, long offset) {
        var lock = EncodingHeaderLayout.rwLockFor(offset).writeLock();
        lock.lock();
        try {
            int payloadBytes = quantizedVecBytes();
            segment.asSlice(vectorOffset(offset), payloadBytes).fill((byte) 0);
            EncodingHeaderLayout hdr = headerLayout();
            hdr.writeExactNorm(segment, offset, 0.0f);
            hdr.writeSynapticTags(segment, offset, 0L, 0L);
            hdr.writeCentroidId(segment, offset, (short) 0);
            hdr.markTombstoned(segment, offset);
            hdr.markPurged(segment, offset);
            writeRecordCrc(segment, offset);
            return payloadBytes;
        } finally {
            lock.unlock();
        }
    }

    default boolean isPurged(MemorySegment segment, long offset) {
        return headerLayout().isPurged(segment, offset);
    }

    default void markConsolidated(MemorySegment segment, long offset) {
        headerLayout().markConsolidated(segment, offset);
    }

    default void pin(MemorySegment segment, long offset) {
        headerLayout().markPinned(segment, offset);
    }

    default void markResolved(MemorySegment segment, long offset) {
        headerLayout().markResolved(segment, offset);
    }

    default void markUnresolved(MemorySegment segment, long offset) {
        headerLayout().markUnresolved(segment, offset);
    }

    default byte readConsolidationFlags(MemorySegment segment, long offset) {
        return headerLayout().readConsolidationFlags(segment, offset);
    }

    default void markContradicted(MemorySegment segment, long offset) {
        headerLayout().markContradicted(segment, offset);
    }

    default void markRetracted(MemorySegment segment, long offset) {
        headerLayout().markRetracted(segment, offset);
    }

    default void writeImportance(MemorySegment segment, long offset, float importance) {
        headerLayout().writeImportance(segment, offset, importance);
    }

    default void writeTimestamp(MemorySegment segment, long offset, long timestampMs) {
        headerLayout().writeTimestamp(segment, offset, timestampMs);
    }

    default void mergeSynapticTags(MemorySegment segment, long offset, long additionalTags) {
        headerLayout().mergeSynapticTags(segment, offset, additionalTags);
    }

    default void writeArousal(MemorySegment segment, long offset, byte arousal) {
        headerLayout().writeArousal(segment, offset, arousal);
    }

    default void writeStorageStrength(MemorySegment segment, long offset, float strength) {
        headerLayout().writeStorageStrength(segment, offset, strength);
    }

    default byte readEncodingProfile(MemorySegment segment, long offset) {
        return headerLayout().readEncodingProfile(segment, offset);
    }

    default byte readEncodingAlpha(MemorySegment segment, long offset) {
        return headerLayout().readEncodingAlpha(segment, offset);
    }

    default byte readEncodingBeta(MemorySegment segment, long offset) {
        return headerLayout().readEncodingBeta(segment, offset);
    }

    default short readSoulVersion(MemorySegment segment, long offset) {
        return headerLayout().readSoulVersion(segment, offset);
    }

    default void writeSoulVersion(MemorySegment segment, long offset, short version) {
        headerLayout().writeSoulVersion(segment, offset, version);
    }

    default float readEncodingSurprise(MemorySegment segment, long offset) {
        return headerLayout().readEncodingSurprise(segment, offset);
    }

    default void writeEncodingSurprise(MemorySegment segment, long offset, float surprise) {
        headerLayout().writeEncodingSurprise(segment, offset, surprise);
    }

    default void writeQuantizedVector(MemorySegment segment, long recordOffset, byte[] quantizedVec) {
        var lock = EncodingHeaderLayout.rwLockFor(recordOffset).writeLock();
        lock.lock();
        try {
            MemorySegment.copy(MemorySegment.ofArray(quantizedVec), ValueLayout.JAVA_BYTE, 0,
                    segment, ValueLayout.JAVA_BYTE, vectorOffset(recordOffset), quantizedVec.length);
            writeRecordCrc(segment, recordOffset);
        } finally {
            lock.unlock();
        }
    }

    default void writeQuantizedVector(MemorySegment segment, long recordOffset,
                                      float[] vector, ScalarQuantizer quantizer) {
        byte[] quantized = quantizer.encode(vector);
        writeQuantizedVector(segment, recordOffset, quantized);
    }
}
