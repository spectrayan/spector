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
package com.spectrayan.spector.kernel.engram;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorStorageException;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.kernel.api.EngramSource;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.shape.AbstractRecordMemory;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields.*;

/**
 * Pure 64-byte cache-line-aligned encoding header layout (V2, ADR-0028).
 *
 * <p>This is the sole live engram encoding header layout in Spector, realizing the
 * pure-encoding identity rule from MF-001 / ADR-0028.</p>
 *
 * @see StrengthLayout
 * @see EncodingHeaderFields
 */
public class EncodingHeaderLayout {

    /** Singleton instance of the encoding header layout. */
    public static final EncodingHeaderLayout INSTANCE = new EncodingHeaderLayout();

    private static final int LOCK_STRIPES = 256;
    private static final int LOCK_MASK = LOCK_STRIPES - 1;
    private static final ReentrantReadWriteLock[] STRIPE_LOCKS = new ReentrantReadWriteLock[LOCK_STRIPES];

    static {
        for (int i = 0; i < LOCK_STRIPES; i++) {
            STRIPE_LOCKS[i] = new ReentrantReadWriteLock();
        }
    }

    /**
     * Returns the striped read-write lock guarding record header and CRC updates at {@code offset}.
     */
    public static ReentrantReadWriteLock rwLockFor(long offset) {
        int hash = Long.hashCode(offset);
        hash ^= (hash >>> 16);
        return STRIPE_LOCKS[hash & LOCK_MASK];
    }

    public EncodingHeaderLayout() {}

    public static final VarHandle VAR_HANDLE_SYNAPTIC_TAGS_LO = LAYOUT_SYNAPTIC_TAGS.varHandle();
    public static final VarHandle VAR_HANDLE_SYNAPTIC_TAGS_HI = LAYOUT_SYNAPTIC_TAGS.varHandle();
    public static final VarHandle VAR_HANDLE_VALENCE = LAYOUT_VALENCE.varHandle();
    public static final VarHandle VAR_HANDLE_IMPORTANCE_V2    = LAYOUT_IMPORTANCE.varHandle();
    public static final VarHandle VAR_HANDLE_INT = ValueLayout.JAVA_INT.varHandle();

    /** Header size in bytes (64 bytes, 1 CPU cache line). */
    public static final int HEADER_BYTES = EncodingHeaderFields.HEADER_BYTES;

    /** Default layout for all new stores (V2, 64 bytes). */
    public static EncodingHeaderLayout defaultLayout() {
        return INSTANCE;
    }

    /**
     * Returns the layout for the given version number.
     *
     * @param version layout version (supported: 2)
     * @return the corresponding layout instance
     * @throws SpectorValidationException if version is not 2
     */
    public static EncodingHeaderLayout forVersion(int version) {
        if (version != HEADER_VERSION_V2) {
            throw new SpectorValidationException(
                    ErrorCode.ARGUMENT_INVALID, "header layout version", version + " (supported: " + HEADER_VERSION_V2 + ")");
        }
        return INSTANCE;
    }

    public int headerBytes() { return HEADER_BYTES; }
    public int version() { return HEADER_VERSION_V2; }

    /**
     * Whether this header layout stores inline CRC32C checksums in bytes 52..63.
     * Overridden by {@link EpisodicHeaderLayout}, which uses bytes 48..63 for episodic tags
     * and stores record checksums in the 16-byte framing prefix.
     */
    protected boolean hasInlineHeaderCrc() {
        return true;
    }

    /**
     * Computes the non-zero CRC32C checksum over header bytes {@code 0..51} directly off-heap.
     */
    public int computeHeaderCrc(MemorySegment seg, long off) {
        return AbstractRecordMemory.nonZeroCrc32c(seg, off, V2_HEADER_CRC_COVERED_BYTES);
    }

    /**
     * Recomputes and stores the inline header CRC32C at {@code off + 52} and sets the CRC marker at {@code off + 63}.
     */
    public void updateHeaderCrc(MemorySegment seg, long off) {
        if (!hasInlineHeaderCrc() || seg == null || seg.byteSize() < off + HEADER_BYTES) {
            return;
        }
        var writeLock = rwLockFor(off).writeLock();
        writeLock.lock();
        try {
            int crc = computeHeaderCrc(seg, off);
            seg.set(ValueLayout.JAVA_INT_UNALIGNED, off + OFFSET_V2_HEADER_CRC32C, crc);
            seg.set(ValueLayout.JAVA_BYTE, off + OFFSET_V2_CRC_MARKER, (byte) 1);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Verifies the inline header CRC32C at {@code off + 52} when present (skipping legacy v1 headers where CRC is 0).
     */
    public void verifyHeaderCrc(MemorySegment seg, long off, Object recordRef) {
        if (!hasInlineHeaderCrc() || seg == null || seg.byteSize() < off + HEADER_BYTES) {
            return;
        }
        int storedCrc = seg.get(ValueLayout.JAVA_INT_UNALIGNED, off + OFFSET_V2_HEADER_CRC32C);
        if (storedCrc == 0) {
            return;
        }
        int actualCrc = computeHeaderCrc(seg, off);
        if (storedCrc == actualCrc) {
            return;
        }
        var readLock = rwLockFor(off).readLock();
        readLock.lock();
        try {
            storedCrc = seg.get(ValueLayout.JAVA_INT_UNALIGNED, off + OFFSET_V2_HEADER_CRC32C);
            if (storedCrc == 0) {
                return;
            }
            actualCrc = computeHeaderCrc(seg, off);
            if (storedCrc != actualCrc) {
                throw new SpectorStorageException(ErrorCode.RECORD_CRC_CORRUPTED, recordRef);
            }
        } finally {
            readLock.unlock();
        }
    }

    // ── Field reads ──

    public long readTimestamp(MemorySegment seg, long off) {
        return seg.get(ValueLayout.JAVA_LONG_UNALIGNED, off + OFFSET_TIMESTAMP);
    }

    public long readSynapticTags(MemorySegment seg, long off) {
        return seg.get(ValueLayout.JAVA_LONG_UNALIGNED, off + OFFSET_V2_SYNAPTIC_TAGS_LO);
    }

    public long readSynapticTagsLo(MemorySegment seg, long off) {
        return seg.get(ValueLayout.JAVA_LONG_UNALIGNED, off + OFFSET_V2_SYNAPTIC_TAGS_LO);
    }

    public long readSynapticTagsHi(MemorySegment seg, long off) {
        return seg.get(ValueLayout.JAVA_LONG_UNALIGNED, off + OFFSET_V2_SYNAPTIC_TAGS_HI);
    }

    public float readExactNorm(MemorySegment seg, long off) {
        return seg.get(ValueLayout.JAVA_FLOAT_UNALIGNED, off + OFFSET_V2_EXACT_NORM);
    }

    public float readImportance(MemorySegment seg, long off) {
        return seg.get(ValueLayout.JAVA_FLOAT_UNALIGNED, off + OFFSET_IMPORTANCE);
    }

    public int readAgentRecallCount(MemorySegment seg, long off) {
        return 0; // Pure V2 header does not store recall counters; lives in StrengthLayout
    }

    public short readCentroidId(MemorySegment seg, long off) {
        return seg.get(ValueLayout.JAVA_SHORT_UNALIGNED, off + OFFSET_V2_CENTROID_ID);
    }

    public byte readValence(MemorySegment seg, long off) {
        return seg.get(LAYOUT_VALENCE, off + OFFSET_VALENCE);
    }

    public byte readFlags(MemorySegment seg, long off) {
        return seg.get(LAYOUT_FLAGS, off + OFFSET_FLAGS);
    }

    public byte readArousal(MemorySegment seg, long off) {
        return seg.get(LAYOUT_AROUSAL, off + OFFSET_AROUSAL);
    }

    public float readStorageStrength(MemorySegment seg, long off) {
        return 1.0f;
    }

    public byte readEncodingProfile(MemorySegment seg, long off) {
        return seg.get(LAYOUT_ENCODING_PROFILE, off + OFFSET_V2_ENCODING_PROFILE);
    }

    public byte readEncodingAlpha(MemorySegment seg, long off) {
        return seg.get(LAYOUT_ENCODING_ALPHA, off + OFFSET_V2_ENCODING_ALPHA);
    }

    public byte readEncodingBeta(MemorySegment seg, long off) {
        return seg.get(LAYOUT_ENCODING_BETA, off + OFFSET_V2_ENCODING_BETA);
    }

    public short readSoulVersion(MemorySegment seg, long off) {
        return seg.get(ValueLayout.JAVA_SHORT_UNALIGNED, off + OFFSET_V2_SOUL_VERSION);
    }

    public float readEncodingSurprise(MemorySegment seg, long off) {
        return seg.get(ValueLayout.JAVA_FLOAT_UNALIGNED, off + OFFSET_V2_ENCODING_SURPRISE);
    }

    public byte readConsolidationFlags(MemorySegment seg, long off) {
        return seg.get(LAYOUT_CONSOLIDATION_FLAGS, off + OFFSET_V2_CONSOLIDATION_FLAGS);
    }

    /**
     * Reads the raw 1-byte source code at offset 46 (zero-allocation hot-path check).
     */
    public byte readSourceCode(MemorySegment seg, long off) {
        return seg.get(LAYOUT_SOURCE, off + OFFSET_V2_SOURCE);
    }

    /**
     * Reads the trace provenance source enum from offset 46 (NF7).
     */
    public EngramSource readSource(MemorySegment seg, long off) {
        return EngramSource.fromCode(readSourceCode(seg, off));
    }

    // ── Field writes ──

    public byte readHeaderVersion(MemorySegment seg, long off) {
        return seg.get(LAYOUT_HEADER_VERSION, off + OFFSET_HEADER_VERSION);
    }

    public void writeFlags(MemorySegment seg, long off, byte flags) {
        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            seg.set(LAYOUT_FLAGS, off + OFFSET_FLAGS, flags);
            updateHeaderCrc(seg, off);
        } finally {
            lock.unlock();
        }
    }

    public void writeExactNorm(MemorySegment seg, long off, float exactNorm) {
        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            seg.set(ValueLayout.JAVA_FLOAT_UNALIGNED, off + OFFSET_V2_EXACT_NORM, exactNorm);
            updateHeaderCrc(seg, off);
        } finally {
            lock.unlock();
        }
    }

    public void writeCentroidId(MemorySegment seg, long off, short centroidId) {
        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            seg.set(ValueLayout.JAVA_SHORT_UNALIGNED, off + OFFSET_V2_CENTROID_ID, centroidId);
            updateHeaderCrc(seg, off);
        } finally {
            lock.unlock();
        }
    }

    public void writeSourceCode(MemorySegment seg, long off, byte sourceCode) {
        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            seg.set(LAYOUT_SOURCE, off + OFFSET_V2_SOURCE, sourceCode);
            updateHeaderCrc(seg, off);
        } finally {
            lock.unlock();
        }
    }

    public boolean compareAndSetValence(MemorySegment seg, long off, byte expected, byte update) {
        long wordOff = off + (OFFSET_VALENCE & ~3L);
        int byteIndex = (int) (OFFSET_VALENCE & 3L);
        int shift = java.nio.ByteOrder.nativeOrder() == java.nio.ByteOrder.LITTLE_ENDIAN
                ? (byteIndex * 8)
                : ((3 - byteIndex) * 8);
        int mask = 0xFF << shift;
        int expectedBits = (expected & 0xFF) << shift;
        int updateBits = (update & 0xFF) << shift;

        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            while (true) {
                int currentWord = (int) VAR_HANDLE_INT.getVolatile(seg, wordOff);
                if ((currentWord & mask) != expectedBits) {
                    return false;
                }
                int nextWord = (currentWord & ~mask) | updateBits;
                int witness = (int) VAR_HANDLE_INT.compareAndExchange(seg, wordOff, currentWord, nextWord);
                if (witness == currentWord) {
                    updateHeaderCrc(seg, off);
                    return true;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void writeArousal(MemorySegment seg, long off, byte arousal) {
        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            seg.set(LAYOUT_AROUSAL, off + OFFSET_AROUSAL, arousal);
            updateHeaderCrc(seg, off);
        } finally {
            lock.unlock();
        }
    }

    public void writeStorageStrength(MemorySegment seg, long off, float strength) {
        // No-op on EncodingHeaderLayout — storage strength written to StrengthLayout
    }

    public void writeEncodingProfile(MemorySegment seg, long off, byte profile) {
        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            seg.set(LAYOUT_ENCODING_PROFILE, off + OFFSET_V2_ENCODING_PROFILE, profile);
            updateHeaderCrc(seg, off);
        } finally {
            lock.unlock();
        }
    }

    public void writeEncodingAlpha(MemorySegment seg, long off, byte alpha) {
        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            seg.set(LAYOUT_ENCODING_ALPHA, off + OFFSET_V2_ENCODING_ALPHA, alpha);
            updateHeaderCrc(seg, off);
        } finally {
            lock.unlock();
        }
    }

    public void writeEncodingBeta(MemorySegment seg, long off, byte beta) {
        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            seg.set(LAYOUT_ENCODING_BETA, off + OFFSET_V2_ENCODING_BETA, beta);
            updateHeaderCrc(seg, off);
        } finally {
            lock.unlock();
        }
    }

    public void writeSoulVersion(MemorySegment seg, long off, short version) {
        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            seg.set(ValueLayout.JAVA_SHORT_UNALIGNED, off + OFFSET_V2_SOUL_VERSION, version);
            updateHeaderCrc(seg, off);
        } finally {
            lock.unlock();
        }
    }

    public void writeSource(MemorySegment seg, long off, EngramSource source) {
        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            seg.set(LAYOUT_SOURCE, off + OFFSET_V2_SOURCE, source != null ? source.code() : SOURCE_EXPERIENCED);
            updateHeaderCrc(seg, off);
        } finally {
            lock.unlock();
        }
    }

    public void writeEncodingSurprise(MemorySegment seg, long off, float surprise) {
        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            seg.set(ValueLayout.JAVA_FLOAT_UNALIGNED, off + OFFSET_V2_ENCODING_SURPRISE, surprise);
            updateHeaderCrc(seg, off);
        } finally {
            lock.unlock();
        }
    }

    public void writeConsolidationFlags(MemorySegment seg, long off, byte consolidationFlags) {
        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            seg.set(LAYOUT_CONSOLIDATION_FLAGS, off + OFFSET_V2_CONSOLIDATION_FLAGS, consolidationFlags);
            updateHeaderCrc(seg, off);
        } finally {
            lock.unlock();
        }
    }

    public void writeImportance(MemorySegment seg, long off, float importance) {
        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            seg.set(ValueLayout.JAVA_FLOAT_UNALIGNED, off + OFFSET_IMPORTANCE, importance);
            updateHeaderCrc(seg, off);
        } finally {
            lock.unlock();
        }
    }

    public void writeTimestamp(MemorySegment seg, long off, long timestampMs) {
        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            seg.set(ValueLayout.JAVA_LONG_UNALIGNED, off + OFFSET_TIMESTAMP, timestampMs);
            updateHeaderCrc(seg, off);
        } finally {
            lock.unlock();
        }
    }

    public void writeSynapticTags(MemorySegment seg, long off, long tagsLo, long tagsHi) {
        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            seg.set(ValueLayout.JAVA_LONG_UNALIGNED, off + OFFSET_V2_SYNAPTIC_TAGS_LO, tagsLo);
            seg.set(ValueLayout.JAVA_LONG_UNALIGNED, off + OFFSET_V2_SYNAPTIC_TAGS_HI, tagsHi);
            updateHeaderCrc(seg, off);
        } finally {
            lock.unlock();
        }
    }

    public void mergeSynapticTags(MemorySegment seg, long off, long additionalTags) {
        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            VAR_HANDLE_SYNAPTIC_TAGS_LO.getAndBitwiseOr(seg, off + OFFSET_V2_SYNAPTIC_TAGS_LO, additionalTags);
            updateHeaderCrc(seg, off);
        } finally {
            lock.unlock();
        }
    }

    public void mergeSynapticTags128(MemorySegment seg, long off, long additionalLo, long additionalHi) {
        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            VAR_HANDLE_SYNAPTIC_TAGS_LO.getAndBitwiseOr(seg, off + OFFSET_V2_SYNAPTIC_TAGS_LO, additionalLo);
            if (additionalHi != 0L) {
                VAR_HANDLE_SYNAPTIC_TAGS_HI.getAndBitwiseOr(seg, off + OFFSET_V2_SYNAPTIC_TAGS_HI, additionalHi);
            }
            updateHeaderCrc(seg, off);
        } finally {
            lock.unlock();
        }
    }

    public void markTombstoned(MemorySegment seg, long off) {
        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            byte flags = readFlags(seg, off);
            seg.set(LAYOUT_FLAGS, off + OFFSET_FLAGS, (byte) (flags | FLAG_TOMBSTONE));
            updateHeaderCrc(seg, off);
        } finally {
            lock.unlock();
        }
    }

    public void markConsolidated(MemorySegment seg, long off) {
        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            byte flags = readFlags(seg, off);
            seg.set(LAYOUT_FLAGS, off + OFFSET_FLAGS, (byte) (flags | FLAG_CONSOLIDATED));
            updateHeaderCrc(seg, off);
        } finally {
            lock.unlock();
        }
    }

    public void markPinned(MemorySegment seg, long off) {
        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            byte flags = readFlags(seg, off);
            seg.set(LAYOUT_FLAGS, off + OFFSET_FLAGS, (byte) (flags | FLAG_PINNED));
            updateHeaderCrc(seg, off);
        } finally {
            lock.unlock();
        }
    }

    public void markResolved(MemorySegment seg, long off) {
        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            byte flags = readFlags(seg, off);
            seg.set(LAYOUT_FLAGS, off + OFFSET_FLAGS, (byte) (flags | FLAG_RESOLVED));
            updateHeaderCrc(seg, off);
        } finally {
            lock.unlock();
        }
    }

    public void markUnresolved(MemorySegment seg, long off) {
        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            byte flags = readFlags(seg, off);
            seg.set(LAYOUT_FLAGS, off + OFFSET_FLAGS, (byte) (flags & ~FLAG_RESOLVED));
            updateHeaderCrc(seg, off);
        } finally {
            lock.unlock();
        }
    }

    public void markContradicted(MemorySegment seg, long off) {
        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            byte cFlags = readConsolidationFlags(seg, off);
            writeConsolidationFlags(seg, off, (byte) (cFlags | FLAG_CONTRADICTED));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets {@link EncodingHeaderFields#FLAG_RETRACTED} in the consolidation-flags byte. Retracted
     * records are never released by recall (the governed release gate drops them).
     */
    public void markRetracted(MemorySegment seg, long off) {
        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            byte cFlags = readConsolidationFlags(seg, off);
            writeConsolidationFlags(seg, off, (byte) (cFlags | EncodingHeaderFields.FLAG_RETRACTED));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Marks the record at {@code off} as purged — its payload has been physically zeroed.
     *
     * @see EncodingHeaderFields#FLAG_PURGED
     */
    public void markPurged(MemorySegment seg, long off) {
        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            byte cFlags = readConsolidationFlags(seg, off);
            writeConsolidationFlags(seg, off, (byte) (cFlags | FLAG_PURGED));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns whether the record at {@code off} has been purged (payload zeroed).
     *
     * @see EncodingHeaderFields#FLAG_PURGED
     */
    public boolean isPurged(MemorySegment seg, long off) {
        return EncodingHeaderFields.isPurged(readConsolidationFlags(seg, off));
    }

    public int incrementAgentRecallCount(MemorySegment seg, long off) {
        return 0; // Routed to StrengthLayout in dual-region architecture
    }

    public float casStorageStrength(MemorySegment seg, long off, FloatUnaryOperator updateFn) {
        return 1.0f; // Routed to StrengthLayout in dual-region architecture
    }

    public float casImportance(MemorySegment seg, long off, FloatUnaryOperator updateFn) {
        long addr = off + OFFSET_IMPORTANCE;
        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            float prev, next;
            do {
                prev = (float) VAR_HANDLE_IMPORTANCE_V2.getVolatile(seg, addr);
                next = updateFn.applyAsFloat(prev);
            } while (!VAR_HANDLE_IMPORTANCE_V2.compareAndSet(seg, addr, prev, next));
            updateHeaderCrc(seg, off);
            return next;
        } finally {
            lock.unlock();
        }
    }

    public void writeValenceRelease(MemorySegment seg, long off, byte valence) {
        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            VAR_HANDLE_VALENCE.setRelease(seg, off + OFFSET_VALENCE, valence);
            updateHeaderCrc(seg, off);
        } finally {
            lock.unlock();
        }
    }

    public int readSpectorRecallCount(MemorySegment seg, long off) {
        return 0;
    }

    public int incrementSpectorRecallCount(MemorySegment seg, long off) {
        return 0;
    }

    public long readLastAutoLtp(MemorySegment seg, long off) {
        return 0L;
    }

    public void writeLastAutoLtp(MemorySegment seg, long off, long timestampMs) {
    }

    // ── Full header read/write ──

    public EncodingHeader readHeader(MemorySegment seg, long off) {
        verifyHeaderCrc(seg, off, off);
        return new EncodingHeader(
                readTimestamp(seg, off),
                readSynapticTagsLo(seg, off),
                readSynapticTagsHi(seg, off),
                readExactNorm(seg, off),
                readImportance(seg, off),
                0, // agentRecallCount lives in strength region
                readCentroidId(seg, off),
                readValence(seg, off),
                readFlags(seg, off),
                readArousal(seg, off),
                1.0f, // storageStrength lives in strength region
                readEncodingProfile(seg, off),
                readEncodingAlpha(seg, off),
                readEncodingBeta(seg, off),
                readSoulVersion(seg, off),
                readEncodingSurprise(seg, off),
                readConsolidationFlags(seg, off),
                readSource(seg, off)
        );
    }

    public void writeHeader(MemorySegment seg, long off, EncodingHeader header) {
        var lock = rwLockFor(off).writeLock();
        lock.lock();
        try {
            seg.set(LAYOUT_HEADER_VERSION, off + OFFSET_HEADER_VERSION, (byte) HEADER_VERSION_V2);
            seg.set(LAYOUT_FLAGS,         off + OFFSET_FLAGS,          header.flags());
            seg.set(LAYOUT_VALENCE,       off + OFFSET_VALENCE,        header.valence());
            seg.set(LAYOUT_AROUSAL,       off + OFFSET_AROUSAL,        header.arousal());
            seg.set(ValueLayout.JAVA_FLOAT_UNALIGNED,    off + OFFSET_IMPORTANCE,     header.importance());
            seg.set(ValueLayout.JAVA_LONG_UNALIGNED,     off + OFFSET_TIMESTAMP,      header.timestampMs());
            seg.set(ValueLayout.JAVA_FLOAT_UNALIGNED,    off + OFFSET_V2_EXACT_NORM,  header.exactNorm());
            seg.set(ValueLayout.JAVA_SHORT_UNALIGNED,   off + OFFSET_V2_CENTROID_ID, header.centroidId());
            seg.set(ValueLayout.JAVA_SHORT_UNALIGNED, off + OFFSET_V2_PAD0,     (short) 0);
            seg.set(ValueLayout.JAVA_LONG_UNALIGNED, off + OFFSET_V2_SYNAPTIC_TAGS_LO, header.synapticTagsLo());
            seg.set(ValueLayout.JAVA_LONG_UNALIGNED, off + OFFSET_V2_SYNAPTIC_TAGS_HI, header.synapticTagsHi());
            seg.set(LAYOUT_CONSOLIDATION_FLAGS, off + OFFSET_V2_CONSOLIDATION_FLAGS, header.consolidationFlags());
            seg.set(LAYOUT_ENCODING_PROFILE, off + OFFSET_V2_ENCODING_PROFILE, header.encodingProfile());
            seg.set(LAYOUT_ENCODING_ALPHA, off + OFFSET_V2_ENCODING_ALPHA, header.encodingAlpha());
            seg.set(LAYOUT_ENCODING_BETA, off + OFFSET_V2_ENCODING_BETA, header.encodingBeta());
            seg.set(ValueLayout.JAVA_SHORT_UNALIGNED, off + OFFSET_V2_SOUL_VERSION, header.soulVersion());
            EngramSource src = header.source() != null ? header.source() : EngramSource.EXPERIENCED;
            seg.set(LAYOUT_SOURCE, off + OFFSET_V2_SOURCE, src.code());
            seg.set(ValueLayout.JAVA_BYTE, off + OFFSET_V2_PAD_SOURCE, (byte) 0);
            seg.set(ValueLayout.JAVA_FLOAT_UNALIGNED, off + OFFSET_V2_ENCODING_SURPRISE, header.encodingSurprise());
            // Zero reserved block (12 bytes at offset 52) before writing header CRC32C
            seg.set(ValueLayout.JAVA_INT_UNALIGNED, off + OFFSET_V2_RESERVED, 0);
            seg.set(ValueLayout.JAVA_INT_UNALIGNED, off + OFFSET_V2_RESERVED + 4L, 0);
            seg.set(ValueLayout.JAVA_INT_UNALIGNED, off + OFFSET_V2_RESERVED + 8L, 0);
            updateHeaderCrc(seg, off);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Static utility to decode an EncodingHeader from a segment offset using the singleton layout.
     */
    public static EncodingHeader read(MemorySegment seg, long off) {
        return INSTANCE.readHeader(seg, off);
    }

    /**
     * Static utility to encode an EncodingHeader into a segment offset using the singleton layout.
     */
    public static void write(MemorySegment seg, long off, EncodingHeader header) {
        INSTANCE.writeHeader(seg, off, header);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof EncodingHeaderLayout;
    }

    @Override
    public int hashCode() {
        return EncodingHeaderLayout.class.hashCode();
    }

    @Override
    public String toString() {
        return "EncodingHeaderLayout[]";
    }
}
