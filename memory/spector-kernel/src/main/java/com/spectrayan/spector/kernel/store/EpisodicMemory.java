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

import com.spectrayan.spector.kernel.id.MemoryId;

import com.spectrayan.spector.kernel.engram.EncodingHeaderLayout;

import com.spectrayan.spector.kernel.store.EngramRegion;

import com.spectrayan.spector.kernel.error.SpectorMemoryTierFullException;
import com.spectrayan.spector.kernel.shape.MemoryShape;
import com.spectrayan.spector.kernel.region.RegionPreamble;
import com.spectrayan.spector.kernel.id.SystemMemoryId;
import com.spectrayan.spector.kernel.bundle.RegionRef;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.store.codec.EpisodeCodec;
import com.spectrayan.spector.kernel.engram.EpisodicHeaderLayout;
import com.spectrayan.spector.kernel.layout.EpisodicLayout;
import com.spectrayan.spector.kernel.shape.AbstractAppendMemory;
import com.spectrayan.spector.kernel.api.ConversationRole;
import com.spectrayan.spector.kernel.api.EngramSource;
import com.spectrayan.spector.kernel.api.EpisodeRecord;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.api.SourceModality;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Log-structured episodic conversation memory store (ADR-0010 / ADR-0030, D2 Option B).
 *
 * <h3>Record Format (Option B: 80B Fixed Framing Overhead)</h3>
 * <pre>
 *   +0    prefix          16B   payloadBytes (4) | sequence_id (4) | checksum (4) | magic (4)
 *   +16   EncodingHeader  64B   I, valence, arousal, tags, source, flags, timestamp
 *   +80   payload         N     conversation metadata + CBOR body
 *   next  = 80 + N
 * </pre>
 *
 * @since 1.4.0
 * @see EpisodicLayout
 * @see EpisodeCodec
 */
public final class EpisodicMemory extends AbstractAppendMemory<EpisodicLayout> implements EngramRegion {

    private static final Logger log = LoggerFactory.getLogger(EpisodicMemory.class);

    private final ReentrantLock writeLock = new ReentrantLock();
    private final AtomicInteger liveTurnCount = new AtomicInteger(0);

    // ── Constructors ──

    /**
     * Creates a volatile (heap-backed) episodic memory store with given capacity and buffer size.
     */

    public com.spectrayan.spector.kernel.api.HeaderCursor cursor() {
        return cursor(null);
    }

    public com.spectrayan.spector.kernel.api.HeaderCursor cursor(StrengthMemory strengthMemory) {
        if (regionRef != null) {
            return new DefaultHeaderCursor(regionRef, layout(), dataOffset(), capacity(), strengthMemory);
        } else {
            return new DefaultHeaderCursor(segment(), layout(), dataOffset(), capacity(), strengthMemory);
        }
    }

    public EpisodicMemory(int capacity, long capacityBytes) {
        super(SystemMemoryId.EPISODIC.id(), EpisodicLayout.INSTANCE, capacity, capacityBytes);
    }

    /**
     * Creates a volatile (heap-backed) episodic memory store with given byte buffer size.
     */
    public EpisodicMemory(long capacityBytes) {
        this(0, capacityBytes);
    }

    /**
     * Creates a volatile (heap-backed) episodic memory store with default 16MB buffer.
     */
    public static EpisodicMemory heap() {
        return new EpisodicMemory(0, 16 * 1024 * 1024L);
    }

    /**
     * Creates a volatile (heap-backed) episodic memory store with specified byte buffer size.
     */
    public static EpisodicMemory heap(long capacityBytes) {
        return new EpisodicMemory(0, capacityBytes);
    }

    /**
     * Creates a volatile (heap-backed) episodic memory store with specified record capacity and byte buffer size.
     */
    public static EpisodicMemory heap(int capacity, long capacityBytes) {
        return new EpisodicMemory(capacity, capacityBytes);
    }

    private EpisodicMemory(Arena arena, MemorySegment regionSlice, int capacity,
                           java.nio.file.Path bundlePath, boolean isNew) {
        super(SystemMemoryId.EPISODIC.id(), EpisodicLayout.INSTANCE, capacity,
              arena, regionSlice,
              isNew ? 0 : (int) RegionPreamble.readCount(regionSlice, 0),
              true, bundlePath, null, true);

        if (isNew) {
            long now = System.currentTimeMillis();
            RegionPreamble.write(segment(), 0, 1, MemoryShape.APPEND, 1, 0, 0,
                    EpisodicLayout.INSTANCE.recordStride(),
                    EpisodicLayout.INSTANCE.layoutId(), now, now);
            log.info("EpisodicMemory initialized new bundle region in: {} ({}KB, cap={})",
                    bundlePath, regionSlice.byteSize() / 1024, capacity);
        } else {
            log.info("EpisodicMemory loaded from bundle region in: {} (cursor={}B, cap={})",
                    bundlePath, count, capacity);
            this.liveTurnCount.set(countLiveTurns());
        }
    }

    private EpisodicMemory(RegionRef regionRef, int capacity,
                           java.nio.file.Path bundlePath, boolean isNew) {
        super(SystemMemoryId.EPISODIC.id(), EpisodicLayout.INSTANCE, capacity,
              regionRef,
              isNew ? 0 : (int) RegionPreamble.readCount(regionRef.resolve(), 0),
              true, bundlePath);

        if (isNew) {
            long now = System.currentTimeMillis();
            RegionPreamble.write(segment(), 0, 1, MemoryShape.APPEND, 1, 0, 0,
                    EpisodicLayout.INSTANCE.recordStride(),
                    EpisodicLayout.INSTANCE.layoutId(), now, now);
            log.info("EpisodicMemory initialized new bundle region in: {} ({}KB, cap={})",
                    bundlePath, regionRef.resolve().byteSize() / 1024, capacity);
        } else {
            log.info("EpisodicMemory loaded from bundle region in: {} (cursor={}B, cap={})",
                    bundlePath, count, capacity);
            this.liveTurnCount.set(countLiveTurns());
        }
    }

    public static EpisodicMemory fromRegionRef(RegionRef regionRef, int capacity,
                                              java.nio.file.Path bundlePath, boolean isNew) {
        return new EpisodicMemory(regionRef, capacity, bundlePath, isNew);
    }

    /**
     * Factory method for creating a bundle-backed episodic store with specified capacity.
     */
    public static EpisodicMemory fromBundle(Arena arena, MemorySegment regionSlice, int capacity,
                                            java.nio.file.Path bundlePath, boolean isNew) {
        return new EpisodicMemory(arena, regionSlice, capacity, bundlePath, isNew);
    }

    /**
     * Factory method for creating a bundle-backed episodic store.
     */
    public static EpisodicMemory fromBundle(Arena arena, MemorySegment regionSlice,
                                            java.nio.file.Path bundlePath, boolean isNew) {
        return new EpisodicMemory(arena, regionSlice, 0, bundlePath, isNew);
    }

    // ── Write path ──

    /**
     * Appends a conversation turn with real affect and provenance (NF6, NF7).
     */
    public long appendTurn(ConversationRole role, int sequenceId,
                           long timestampMs, long sessionId,
                           byte[] body, short modelId,
                           int tokenIn, int tokenOut,
                           int latencyMs, long userId,
                           short soulVersion, SourceModality modality,
                           float importance, byte valence, byte arousal,
                           EngramSource source) {
        byte[] payload = EpisodeCodec.encode(role, sessionId, modelId, tokenIn, tokenOut, latencyMs, userId, body);
        int payloadBytes = payload.length;
        int totalRecordSize = EpisodicLayout.FIXED_OVERHEAD_BYTES + payloadBytes;

        byte flags = EncodingHeaderFields.withMemoryType((byte) 0, MemoryType.EPISODIC.ordinal());
        if (modality != null && modality != SourceModality.TEXT) {
            flags = EncodingHeaderFields.withSourceModality(flags, modality.ordinal());
        }

        writeLock.lock();
        try {
            if (capacity > 0 && liveTurnCount.get() >= capacity) {
                throw new SpectorMemoryTierFullException(MemoryType.EPISODIC.name(), capacity);
            }

            long writeOffset = dataOffset() + count;

            if (writeOffset + totalRecordSize > segment().byteSize()) {
                throw new IndexOutOfBoundsException(
                        "Episodic memory full: cursor=" + count + ", record=" + totalRecordSize
                                + ", capacity=" + (segment().byteSize() - dataOffset()));
            }

            // Write 64B EncodingHeader at writeOffset + 16 (honest episodic fields per ADR-0030)
            layout().headerLayout().writeEpisodicHeaderRecord(
                    segment(), writeOffset,
                    timestampMs, flags, valence, arousal, importance,
                    sessionId, modelId, role != null ? (byte) role.ordinal() : (byte) 0,
                    soulVersion, source != null ? source : EngramSource.EXPERIENCED,
                    0L, 0L
            );

            // Compute CRC32C over sequenceId, 64B header, and payload
            MemorySegment headerSlice = segment().asSlice(writeOffset + EpisodicLayout.PREFIX_BYTES, EpisodicLayout.HEADER_BYTES);
            int checksum = EpisodeCodec.computeChecksum(sequenceId, headerSlice, payload);

            // Write 16B prefix at writeOffset + 0
            segment().set(ValueLayout.JAVA_INT_UNALIGNED, writeOffset, payloadBytes);
            segment().set(ValueLayout.JAVA_INT_UNALIGNED, writeOffset + 4, sequenceId);
            segment().set(ValueLayout.JAVA_INT_UNALIGNED, writeOffset + 8, checksum);
            segment().set(ValueLayout.JAVA_INT_UNALIGNED, writeOffset + 12, EpisodicLayout.MAGIC);

            // Write payload at writeOffset + 80
            MemorySegment.copy(
                    MemorySegment.ofArray(payload), 0,
                    segment(), writeOffset + EpisodicLayout.FIXED_OVERHEAD_BYTES,
                    payloadBytes);

            long recordOffset = count;
            count += totalRecordSize;
            persistCount();
            liveTurnCount.incrementAndGet();

            return recordOffset;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Backward-compatible overload for turns without explicit affect (defaults to 5.0f baseline importance).
     */
    public long appendTurn(ConversationRole role, int sequenceId,
                           long timestampMs, long sessionId,
                           byte[] body, short modelId,
                           int tokenIn, int tokenOut,
                           int latencyMs, long userId,
                           short soulVersion, SourceModality modality) {
        return appendTurn(role, sequenceId, timestampMs, sessionId, body, modelId,
                tokenIn, tokenOut, latencyMs, userId, soulVersion, modality,
                5.0f, (byte) 0, (byte) 0, EngramSource.EXPERIENCED);
    }

    // ── Read path ──

    /**
     * Reads an episodic record at the given byte offset (relative to dataOffset).
     */
    public EpisodeRecord readTurn(long offset, boolean includeBody) {
        long absoluteOffset = dataOffset() + offset;
        var headerLayout = layout().headerLayout();
        if (!headerLayout.isOptionBRecord(segment(), absoluteOffset)) {
            return null;
        }
        int payloadBytes = headerLayout.readPayloadBytes(segment(), absoluteOffset);
        int sequenceId = headerLayout.readSequenceId(segment(), absoluteOffset);
        EncodingHeader header = headerLayout.readHeaderRecord(segment(), absoluteOffset);

        long payloadOffset = absoluteOffset + EpisodicLayout.FIXED_OVERHEAD_BYTES;
        EpisodeCodec.DecodedPayload decoded = EpisodeCodec.decode(segment(), payloadOffset, payloadBytes, includeBody);

        long headerSessionId = headerLayout.readSessionIdRecord(segment(), absoluteOffset);
        short headerModelId = headerLayout.readModelIdRecord(segment(), absoluteOffset);

        ConversationRole resolvedRole;
        if (headerLayout.isLegacyPunnedHeader(segment(), absoluteOffset + EpisodicLayout.PREFIX_BYTES)) {
            resolvedRole = decoded.role();
        } else {
            byte roleOrdinal = headerLayout.readRoleRecord(segment(), absoluteOffset);
            resolvedRole = (roleOrdinal != 0 || decoded.role() == null)
                    ? ConversationRole.fromOrdinal(roleOrdinal & 0xFF)
                    : decoded.role();
        }
        long resolvedSessionId = (headerSessionId != 0L) ? headerSessionId : decoded.sessionId();
        short resolvedModelId = (headerModelId != 0) ? headerModelId : decoded.modelId();

        return new EpisodeRecord(
                resolvedRole,
                sequenceId,
                header.timestampMs(),
                resolvedSessionId,
                decoded.bodyLength(),
                decoded.body(),
                resolvedModelId,
                decoded.tokenIn(),
                decoded.tokenOut(),
                decoded.latencyMs(),
                decoded.userId(),
                header.soulVersion(),
                headerLayout.readModalityRecord(segment(), absoluteOffset),
                header.flags(),
                header.importance(),
                header.valence(),
                header.arousal(),
                header.source()
        );
    }

    /**
     * Reads multiple episodic records at given byte offsets.
     */
    public List<EpisodeRecord> readTurns(List<Long> offsets, boolean includeBody) {
        List<EpisodeRecord> records = new ArrayList<>(offsets.size());
        for (long offset : offsets) {
            EpisodeRecord record = readTurn(offset, includeBody);
            if (!EncodingHeaderFields.isTombstoned(record.flags())) {
                records.add(record);
            }
        }
        return records;
    }

    /**
     * Tombstones a record at the given relative byte offset.
     */
    public void tombstone(long offset) {
        long absoluteOffset = dataOffset() + offset;
        var headerLayout = layout().headerLayout();
        boolean wasTombstoned = headerLayout.isTombstonedRecord(segment(), absoluteOffset);
        headerLayout.tombstoneRecord(segment(), absoluteOffset);
        if (!wasTombstoned) {
            liveTurnCount.decrementAndGet();
        }
    }

    /**
     * Marks a record as consolidated at the given relative byte offset.
     */
    public void markConsolidated(long offset) {
        long absoluteOffset = dataOffset() + offset;
        layout().headerLayout().markConsolidatedRecord(segment(), absoluteOffset);
    }

    /**
     * Marks a record as resolved (Zeigarnik Effect) at the given relative byte offset.
     */
    public void markResolved(long offset) {
        long absoluteOffset = dataOffset() + offset;
        layout().headerLayout().markResolvedRecord(segment(), absoluteOffset);
    }

    /**
     * Marks a record as unresolved (Zeigarnik Effect) at the given relative byte offset.
     */
    public void markUnresolved(long offset) {
        long absoluteOffset = dataOffset() + offset;
        layout().headerLayout().markUnresolvedRecord(segment(), absoluteOffset);
    }

    /**
     * Returns current write cursor position.
     */
    public long writePosition() {
        return count;
    }

    /**
     * Rebuilds session index from this store's mmap region.
     */
    public int rebuildSessionIndex(EpisodicIndexRebuilder sessionIndex) {
        try (var cur = cursor()) {
            return sessionIndex.rebuild(cur, dataOffset(), dataOffset() + count);
        }
    }

    /**
     * Scans this episodic memory from beginning to cursor, collecting offsets of all live,
     * non-consolidated turns.
     */
    public List<Long> unconsolidatedTurnOffsets() {
        List<Long> offsets = new ArrayList<>();
        long base = dataOffset();
        long limit = base + count;
        long current = base;

        var headerLayout = layout().headerLayout();
        while (current + EpisodicLayout.HEADER_BYTES <= limit) {
            if (headerLayout.isOptionBRecord(segment(), current)) {
                int payloadBytes = headerLayout.readPayloadBytes(segment(), current);
                if (payloadBytes < 0 || current + EpisodicLayout.FIXED_OVERHEAD_BYTES + payloadBytes > limit) {
                    break;
                }
                byte flags = headerLayout.readFlagsRecord(segment(), current);
                if (!EncodingHeaderFields.isTombstoned(flags) && !EncodingHeaderFields.isConsolidated(flags)) {
                    offsets.add(current - base);
                }
                current += EpisodicLayout.FIXED_OVERHEAD_BYTES + payloadBytes;
            } else {
                byte flags = segment().get(EncodingHeaderFields.LAYOUT_FLAGS, current + EncodingHeaderFields.OFFSET_FLAGS);
                int bodyLength = segment().get(ValueLayout.JAVA_INT_UNALIGNED, current + 56);
                if (bodyLength < 0 || current + EncodingHeaderFields.HEADER_BYTES + bodyLength > limit) {
                    break;
                }
                if (!EncodingHeaderFields.isTombstoned(flags) && !EncodingHeaderFields.isConsolidated(flags)) {
                    offsets.add(current - base);
                }
                current += EncodingHeaderFields.HEADER_BYTES + bodyLength;
            }
        }

        return offsets;
    }

    /**
     * Directly scans the episodic log slab for consolidated turn offsets belonging to the given session.
     *
     * <p>Used as an offline/isolated fallback during REM sleep reflection when {@link EpisodicSessionIndex}
     * is unavailable or un-rebuilt on the signal. Returns up to {@code maxTurns} relative offsets
     * ({@code cursor - dataOffset()}) in chronological order.</p>
     *
     * @param sessionId the target episodic session ID
     * @param maxTurns  maximum number of most recent consolidated turn offsets to return (must be > 0)
     * @return chronological list of relative byte offsets for the session's consolidated turns (at most {@code maxTurns})
     */
    public List<Long> lastConsolidatedTurnOffsets(long sessionId, int maxTurns) {
        if (maxTurns <= 0) {
            return List.of();
        }
        List<Long> matching = new ArrayList<>();
        long base = dataOffset();
        long limit = base + this.count;
        long current = base;

        var headerLayout = layout().headerLayout();
        while (current + EpisodicLayout.FIXED_OVERHEAD_BYTES <= limit) {
            if (!headerLayout.isOptionBRecord(segment(), current)) {
                break;
            }
            int payloadBytes = headerLayout.readPayloadBytes(segment(), current);
            if (payloadBytes < 0) {
                break;
            }
            long recordEnd = current + EpisodicLayout.FIXED_OVERHEAD_BYTES + payloadBytes;
            if (recordEnd > limit) {
                break;
            }
            byte flags = headerLayout.readFlagsRecord(segment(), current);
            long headerSessionId = headerLayout.readSessionIdRecord(segment(), current);
            long recordSessionId;
            if (headerSessionId != 0L) {
                recordSessionId = headerSessionId;
            } else {
                long payloadOffset = current + EpisodicLayout.FIXED_OVERHEAD_BYTES;
                recordSessionId = (payloadBytes >= EpisodeCodec.PAYLOAD_METADATA_BYTES)
                        ? segment().get(ValueLayout.JAVA_LONG_UNALIGNED, payloadOffset + EpisodeCodec.OFFSET_SESSION_ID)
                        : 0L;
            }

            if (recordSessionId == sessionId
                    && !EncodingHeaderFields.isTombstoned(flags)
                    && EncodingHeaderFields.isConsolidated(flags)) {
                matching.add(current - base);
            }

            current = recordEnd;
        }

        if (matching.size() > maxTurns) {
            return matching.subList(matching.size() - maxTurns, matching.size());
        }
        return matching;
    }

    /**
     * Returns the total count of live (non-tombstoned) turns in this episodic store.
     */
    public int liveTurnCount() {
        return liveTurnCount.get();
    }

    @Override
    public int size() {
        return liveTurnCount.get();
    }

    private int countLiveTurns() {
        int liveCount = 0;
        long base = dataOffset();
        long limit = base + this.count;
        long current = base;

        var headerLayout = layout().headerLayout();
        while (current + EpisodicLayout.HEADER_BYTES <= limit) {
            if (headerLayout.isOptionBRecord(segment(), current)) {
                int payloadBytes = headerLayout.readPayloadBytes(segment(), current);
                if (payloadBytes < 0 || current + EpisodicLayout.FIXED_OVERHEAD_BYTES + payloadBytes > limit) {
                    break;
                }
                byte flags = headerLayout.readFlagsRecord(segment(), current);
                if (!EncodingHeaderFields.isTombstoned(flags)) {
                    liveCount++;
                }
                current += EpisodicLayout.FIXED_OVERHEAD_BYTES + payloadBytes;
            } else {
                byte flags = segment().get(EncodingHeaderFields.LAYOUT_FLAGS, current + EncodingHeaderFields.OFFSET_FLAGS);
                int bodyLength = segment().get(ValueLayout.JAVA_INT_UNALIGNED, current + 56);
                if (bodyLength < 0 || current + EncodingHeaderFields.HEADER_BYTES + bodyLength > limit) {
                    break;
                }
                if (!EncodingHeaderFields.isTombstoned(flags)) {
                    liveCount++;
                }
                current += EncodingHeaderFields.HEADER_BYTES + bodyLength;
            }
        }
        return liveCount;
    }

    /**
     * Returns remaining unallocated bytes in this store.
     */
    public long remainingBytes() {
        return segment().byteSize() - dataOffset() - count;
    }

    /**
     * Flushes buffered state to underlying backing file.
     */
    public void force() {
        flush();
    }

    /**
     * Decays importance of unconsolidated turns older than thresholdMs by multiplying by factor.
     *
     * @param thresholdMs timestamp before which turns should decay
     * @param factor decay multiplier in [0.0, 1.0]
     * @return number of records decayed
     */
    public int decayOldTurns(long thresholdMs, float factor) {
        int decayed = 0;
        long base = dataOffset();
        long limit = base + count;
        long current = base;

        var headerLayout = layout().headerLayout();
        while (current + EpisodicLayout.FIXED_OVERHEAD_BYTES <= limit) {
            if (!headerLayout.isOptionBRecord(segment(), current)) {
                break;
            }
            int payloadBytes = headerLayout.readPayloadBytes(segment(), current);
            if (payloadBytes < 0 || current + EpisodicLayout.FIXED_OVERHEAD_BYTES + payloadBytes > limit) {
                break;
            }
            byte flags = headerLayout.readFlagsRecord(segment(), current);
            if (!EncodingHeaderFields.isTombstoned(flags)) {
                long ts = headerLayout.readTimestampRecord(segment(), current);
                if (ts < thresholdMs) {
                    float oldImp = headerLayout.readImportanceRecord(segment(), current);
                    headerLayout.writeImportanceRecord(segment(), current, oldImp * factor);
                    decayed++;
                }
            }
            current += EpisodicLayout.FIXED_OVERHEAD_BYTES + payloadBytes;
        }
        return decayed;
    }

    // ── EngramRegion Implementation (ADR-0030) ──

    @Override
    public MemoryType type() {
        return MemoryType.EPISODIC;
    }

    @Override
    public int visibleCount() {
        return liveTurnCount.get();
    }

    @Override
    public float tombstoneRatio() {
        return 0.0f;
    }

    /**
     * Scans episodic framing records and returns summary statistics without leaking raw segments.
     */
    public EpisodicLayout.FramingStats scanFraming() {
        if (writePosition() <= 0) {
            return new EpisodicLayout.FramingStats(0, 0L, 0L);
        }
        long base = dataOffset();
        long limit = base + writePosition();
        return EpisodicLayout.walkFraming(segment(), base, limit);
    }

    /**
     * Reads the encoding header flags for the record starting at the given offset.
     */
    public byte readFlags(long recordOffset) {
        return layout().headerLayout().readFlagsRecord(segment(), recordOffset);
    }

    @Override
    public long recordOffset(long index) {
        throw new UnsupportedOperationException("EpisodicMemory records are variable-length; recordOffset by index is unsupported");
    }

    @Override
    public long write(EncodingHeader header, byte[] payload) {
        return appendTurn(
                ConversationRole.SYSTEM, 0,
                header.timestampMs(), header.synapticTags(),
                payload != null ? payload : new byte[0],
                (short) 0, 0, 0, 0, 0L,
                header.soulVersion(),
                SourceModality.fromOrdinal(EncodingHeaderFields.sourceModalityOrdinal(header.flags())),
                header.importance(), header.valence(), header.arousal(),
                header.source()
        );
    }

    /**
     * Returns true if this store is backed by a fixed-stride record layout (e.g. legacy COG layout).
     */
    public boolean isFixedRecordLayout() {
        MemorySegment seg = segment();
        return seg != null && RegionPreamble.isValid(seg, 0L)
                && (RegionPreamble.readShape(seg, 0L) == MemoryShape.RECORD
                    || RegionPreamble.readLayoutId(seg, 0L) == com.spectrayan.spector.kernel.layout.EngramLayout.LAYOUT_ID);
    }

    /**
     * Reads the INT8 quantized vector from a fixed-stride record at the given record offset.
     */
    public byte[] readVector(long offset) {
        if (!isFixedRecordLayout()) return null;
        MemorySegment seg = segment();
        if (seg == null) return null;
        int stride = RegionPreamble.readRecordStride(seg, 0L);
        int vecBytes = stride > com.spectrayan.spector.kernel.engram.EncodingHeaderLayout.HEADER_BYTES
                ? stride - com.spectrayan.spector.kernel.engram.EncodingHeaderLayout.HEADER_BYTES : 0;
        if (vecBytes <= 0 || offset + com.spectrayan.spector.kernel.engram.EncodingHeaderLayout.HEADER_BYTES + vecBytes > seg.byteSize()) {
            return null;
        }
        byte[] vec = new byte[vecBytes];
        MemorySegment.copy(seg, ValueLayout.JAVA_BYTE, offset + com.spectrayan.spector.kernel.engram.EncodingHeaderLayout.HEADER_BYTES,
                MemorySegment.ofArray(vec), ValueLayout.JAVA_BYTE, 0, vecBytes);
        return vec;
    }

    /**
     * Checks if the record at the given byte offset is tombstoned.
     */
    public boolean isTombstoned(long offset) {
        if (isFixedRecordLayout()) {
            if (offset >= 0 && offset + com.spectrayan.spector.kernel.engram.EncodingHeaderLayout.HEADER_BYTES <= segment().byteSize()) {
                byte flags = segment().get(ValueLayout.JAVA_BYTE, offset + com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields.OFFSET_FLAGS);
                return EncodingHeaderFields.isTombstoned(flags);
            }
            return false;
        }
        long absoluteOffset = dataOffset() + offset;
        var headerLayout = layout().headerLayout();
        return headerLayout.isTombstonedRecord(segment(), absoluteOffset);
    }

    /**
     * Reads the 64-byte encoding header of a record at the given byte offset.
     * Supports both fixed-stride records (direct offset) and append-log records (relative to dataOffset).
     */
    public EncodingHeader readHeader(long offset) {
        if (isFixedRecordLayout()) {
            if (offset >= 0 && offset + com.spectrayan.spector.kernel.engram.EncodingHeaderLayout.HEADER_BYTES <= segment().byteSize()) {
                return com.spectrayan.spector.kernel.engram.EncodingHeaderLayout.INSTANCE.readHeader(segment(), offset);
            }
            return null;
        }
        long absoluteOffset = dataOffset() + offset;
        return layout().headerLayout().readHeaderRecord(segment(), absoluteOffset);
    }

    @Override
    public boolean isContradicted(long offset) {
        return false;
    }

    @Override
    public void markContradicted(long offset) {
        // Contradiction resolution is not applied to episodic records
    }

    public void reinforceValence(long offset, byte outcome, float learningRate) {
        byte currentValence = EpisodicHeaderLayout.INSTANCE.readValenceRecord(segment(), offset);
        byte blended = com.spectrayan.spector.kernel.score.Valence.blend(currentValence, outcome, learningRate);
        EpisodicHeaderLayout.INSTANCE.writeValenceRecord(segment(), offset, blended);
    }

    public float readImportance(long offset) {
        return EpisodicHeaderLayout.INSTANCE.readImportanceRecord(segment(), offset);
    }

    public void writeImportance(long offset, float importance) {
        EpisodicHeaderLayout.INSTANCE.writeImportanceRecord(segment(), offset, importance);
    }
}
